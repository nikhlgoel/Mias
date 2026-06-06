// JNI bridge for on-device llama.cpp inference (chat + embeddings).
#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include <mutex>
#include <atomic>

#include "llama.h"

#define TAG "MiasInference"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Global state (Chat)
static llama_model *model = nullptr;
static llama_context *ctx = nullptr;
static llama_sampler *sampler = nullptr;
static std::mutex llama_mutex;

// Cooperative-cancellation flag. Set by nativeStopGeneration() WITHOUT taking
// llama_mutex (the generation loop holds it for the whole run), and polled by
// the generation loops each iteration so the stop button can break out
// instantly mid-inference.
static std::atomic<bool> g_abort_generation{false};

// Repetition-penalty sampler settings. Small on-device models otherwise fall
// into runaway loops emitting gibberish clusters. 1.0 disables; ~1.15–1.2 is a
// good range, applied over the last N tokens.
static const int32_t REPEAT_LAST_N = 64;
static const float REPEAT_PENALTY = 1.17f;

// Chat-template stop markers. llama_vocab_is_eog only catches tokens the GGUF
// explicitly tags as end-of-generation; many conversions leave ChatML / Phi
// control tokens untagged, so they leak into the output as plain text and the
// model "runs away" — regurgitating its system prompt, templates, and the
// start of a new turn. We additionally halt the moment any of these appears.
static const char *const STOP_MARKERS[] = {
    "<|im_end|>", "<|im_start|>",            // Qwen ChatML
    "<|end|>", "<|endoftext|>",               // Phi-3.5
    "<|user|>", "<|system|>", "<|assistant|>", // generic turn boundaries
    "## Instruction", "## Instructions",        // Phi instruction header
};
// Longest marker length — used as the streaming hold-back window so we never
// emit a partial marker that completes on the next token.
static const size_t STOP_HOLDBACK = 16;

// Index of the earliest stop marker in `text`, or std::string::npos if none.
static size_t find_stop_marker(const std::string &text) {
    size_t earliest = std::string::npos;
    for (const char *m : STOP_MARKERS) {
        size_t pos = text.find(m);
        if (pos != std::string::npos && pos < earliest) earliest = pos;
    }
    return earliest;
}

// Builds a fresh sampler chain for one generation. When grammar_str is
// non-empty and parses, a GBNF grammar constraint is prepended so the model
// can only emit schema-valid tokens (used for the ReAct/agentic JSON loop).
// The grammar comes first so it masks the full vocabulary before the
// probabilistic samplers narrow it. Returns nullptr on allocation failure;
// the caller owns the chain and frees it with llama_sampler_free.
//
// Robustness: llama_sampler_init_grammar returns NULL if the GBNF fails to
// parse — we log and continue UNCONSTRAINED rather than dereference null, so a
// malformed grammar (or unexpected unicode in it) never crashes the JNI/C++
// boundary. The grammar's own `char` rule accepts any non-quote/backslash byte,
// so multi-byte UTF-8 and \t / \n / \uXXXX escapes pass through safely.
static llama_sampler *build_local_sampler(const llama_vocab *vocab, const char *grammar_str) {
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler *chain = llama_sampler_chain_init(sparams);
    if (chain == nullptr) return nullptr;

    if (grammar_str != nullptr && grammar_str[0] != '\0') {
        llama_sampler *gram = llama_sampler_init_grammar(vocab, grammar_str, "root");
        if (gram != nullptr) {
            llama_sampler_chain_add(chain, gram);
        } else {
            LOGE("GBNF grammar failed to parse; running unconstrained");
        }
    }
    // Repetition penalty first so it adjusts logits before truncation. Without
    // it, small models fall into runaway loops emitting gibberish clusters.
    llama_sampler_chain_add(chain, llama_sampler_init_penalties(
        REPEAT_LAST_N, REPEAT_PENALTY, /*freq*/ 0.0f, /*present*/ 0.0f));
    llama_sampler_chain_add(chain, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(chain, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(chain, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(chain, llama_sampler_init_dist(1234));
    return chain;
}

// Global state (Embedding)
static llama_model *emb_model = nullptr;
static llama_context *emb_ctx = nullptr;
static std::mutex emb_mutex;

extern "C" JNIEXPORT void JNICALL
Java_dev_mias_core_inference_engine_LlamaCppEngine_nativeInit(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(llama_mutex);
    llama_backend_init();
    LOGI("llama.cpp backend initialized");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_mias_core_inference_engine_LlamaCppEngine_nativeLoadModel(JNIEnv *env, jobject thiz, jstring jpath) {
    std::lock_guard<std::mutex> lock(llama_mutex);

    // Self-heal: if a model is already bound (e.g. a state desync, or switching
    // models without an explicit unload), free it and load the requested one
    // rather than failing with "already loaded". Makes model switching robust.
    if (model != nullptr) {
        LOGI("A model is already loaded; freeing it before loading the new one");
        if (sampler != nullptr) { llama_sampler_free(sampler); sampler = nullptr; }
        if (ctx != nullptr) { llama_free(ctx); ctx = nullptr; }
        llama_model_free(model);
        model = nullptr;
    }

    const char *path = env->GetStringUTFChars(jpath, nullptr);
    if (path == nullptr) {
        LOGE("Failed to get path string");
        return JNI_FALSE;
    }
    
    llama_model_params mparams = llama_model_default_params();
    model = llama_model_load_from_file(path, mparams);
    
    if (model == nullptr) {
        LOGE("Failed to load model from %s", path);
        env->ReleaseStringUTFChars(jpath, path);
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = 4096; // Adjust later dynamically or expose to Kotlin
    cparams.n_threads = 4;
    cparams.n_threads_batch = 4;

    ctx = llama_init_from_model(model, cparams);
    
    if (ctx == nullptr) {
        LOGE("Failed to create context");
        llama_model_free(model);
        model = nullptr;
        env->ReleaseStringUTFChars(jpath, path);
        return JNI_FALSE;
    }

    // Initialize sampler chain. Penalty first to suppress repetition loops.
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(
        REPEAT_LAST_N, REPEAT_PENALTY, /*freq*/ 0.0f, /*present*/ 0.0f));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(1234)); // Seed

    LOGI("Model loaded successfully from %s", path);
    env->ReleaseStringUTFChars(jpath, path);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_mias_core_inference_engine_LlamaCppEngine_nativeGenerate(JNIEnv *env, jobject thiz, jstring jprompt, jint jmax_tokens, jstring jgrammar) {
    std::lock_guard<std::mutex> lock(llama_mutex);

    if (model == nullptr || ctx == nullptr) {
        LOGE("Cannot generate: model not loaded");
        return env->NewStringUTF("");
    }

    const struct llama_vocab * vocab = llama_model_get_vocab(model);
    const char *prompt = env->GetStringUTFChars(jprompt, nullptr);
    if (prompt == nullptr) {
        LOGE("Failed to get prompt string");
        return env->NewStringUTF("");
    }

    // Grammar-constrained chain only when a (non-empty) grammar was passed —
    // i.e. the ReAct/agentic JSON loop. Plain chat passes "" → global sampler.
    const char *grammar = (jgrammar != nullptr) ? env->GetStringUTFChars(jgrammar, nullptr) : nullptr;
    bool use_grammar = grammar != nullptr && grammar[0] != '\0';
    llama_sampler *active_sampler = use_grammar ? build_local_sampler(vocab, grammar) : sampler;
    if (active_sampler == nullptr) active_sampler = sampler; // build failure → global
    bool owns_sampler = use_grammar && active_sampler != sampler;

    // Frees the local grammar chain (if any) and releases the grammar string.
    auto cleanup_grammar = [&]() {
        if (owns_sampler) llama_sampler_free(active_sampler);
        if (grammar != nullptr) env->ReleaseStringUTFChars(jgrammar, grammar);
    };

    std::string response = "";

    // Very basic generation loop for Phase 1 verification
    // 1. Tokenize prompt
    const int n_prompt = -llama_tokenize(vocab, prompt, strlen(prompt), NULL, 0, true, true);
    std::vector<llama_token> prompt_tokens(n_prompt);
    if (llama_tokenize(vocab, prompt, strlen(prompt), prompt_tokens.data(), prompt_tokens.size(), true, true) < 0) {
        LOGE("Failed to tokenize prompt");
        cleanup_grammar();
        env->ReleaseStringUTFChars(jprompt, prompt);
        return env->NewStringUTF("");
    }

    // 2. Decode prompt batch. Clear the KV cache first — the full prompt is sent
    // every call, so generation must start from an empty cache.
    llama_memory_clear(llama_get_memory(ctx), true);
    llama_batch batch = llama_batch_get_one(prompt_tokens.data(), prompt_tokens.size());
    if (llama_decode(ctx, batch) != 0) {
        LOGE("llama_decode failed");
        cleanup_grammar();
        env->ReleaseStringUTFChars(jprompt, prompt);
        return env->NewStringUTF("");
    }

    // 3. Autoregressive loop
    g_abort_generation.store(false);
    int n_curr = prompt_tokens.size();
    while (n_curr < prompt_tokens.size() + jmax_tokens) {
        if (g_abort_generation.load()) {
            break; // Stop button / cancellation.
        }

        llama_token id = llama_sampler_sample(active_sampler, ctx, -1);
        llama_sampler_accept(active_sampler, id);

        if (llama_vocab_is_eog(vocab, id)) {
            break;
        }

        char buf[128];
        int n = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, true);
        if (n < 0) {
            LOGE("Failed to convert token to piece");
            break;
        }
        response += std::string(buf, n);

        // Halt on any chat-template stop marker that leaked as text, and trim
        // it (and anything after) from the returned response.
        size_t stop = find_stop_marker(response);
        if (stop != std::string::npos) {
            response.erase(stop);
            break;
        }

        batch = llama_batch_get_one(&id, 1);
        if (llama_decode(ctx, batch) != 0) {
            LOGE("llama_decode failed during generation");
            break;
        }
        n_curr++;
    }

    cleanup_grammar();
    env->ReleaseStringUTFChars(jprompt, prompt);
    return env->NewStringUTF(response.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_dev_mias_core_inference_engine_LlamaCppEngine_nativeGenerateStream(JNIEnv *env, jobject thiz, jstring jprompt, jint jmax_tokens, jstring jgrammar, jobject jcallback) {
    std::lock_guard<std::mutex> lock(llama_mutex);

    if (model == nullptr || ctx == nullptr) {
        LOGE("Cannot stream: model not loaded");
        return;
    }

    const struct llama_vocab * vocab = llama_model_get_vocab(model);
    const char *prompt = env->GetStringUTFChars(jprompt, nullptr);
    if (prompt == nullptr) {
        LOGE("Failed to get prompt string");
        return;
    }

    // Grammar-constrained chain only when a non-empty grammar was passed (the
    // ReAct/agentic JSON loop). Plain chat passes "" → global sampler.
    const char *grammar = (jgrammar != nullptr) ? env->GetStringUTFChars(jgrammar, nullptr) : nullptr;
    bool use_grammar = grammar != nullptr && grammar[0] != '\0';
    llama_sampler *active_sampler = use_grammar ? build_local_sampler(vocab, grammar) : sampler;
    if (active_sampler == nullptr) active_sampler = sampler; // build failure → global
    bool owns_sampler = use_grammar && active_sampler != sampler;
    auto cleanup_grammar = [&]() {
        if (owns_sampler) llama_sampler_free(active_sampler);
        if (grammar != nullptr) env->ReleaseStringUTFChars(jgrammar, grammar);
    };

    jclass callbackClass = env->GetObjectClass(jcallback);
    if (callbackClass == nullptr) {
        LOGE("Failed to get callback class");
        cleanup_grammar();
        env->ReleaseStringUTFChars(jprompt, prompt);
        return;
    }

    jmethodID invokeMethod = env->GetMethodID(callbackClass, "invoke", "(Ljava/lang/Object;)Ljava/lang/Object;");
    env->DeleteLocalRef(callbackClass); // Release local reference

    if (invokeMethod == nullptr) {
        LOGE("Cannot find callback invoke method");
        cleanup_grammar();
        env->ReleaseStringUTFChars(jprompt, prompt);
        return;
    }

    // 1. Tokenize prompt
    const int n_prompt = -llama_tokenize(vocab, prompt, strlen(prompt), NULL, 0, true, true);
    std::vector<llama_token> prompt_tokens(n_prompt);
    if (llama_tokenize(vocab, prompt, strlen(prompt), prompt_tokens.data(), prompt_tokens.size(), true, true) < 0) {
        LOGE("Failed to tokenize prompt for streaming");
        cleanup_grammar();
        env->ReleaseStringUTFChars(jprompt, prompt);
        return;
    }

    // 2. Decode batch. Clear the KV cache first: each call sends the COMPLETE
    // prompt (system + history + user), so the cache must start empty. Without
    // this, positions accumulate across turns — after a few messages n_past
    // exceeds n_ctx and decode yields nothing (empty replies), and a brand-new
    // conversation inherits the previous chat's cache (stale answers).
    llama_memory_clear(llama_get_memory(ctx), true);
    llama_batch batch = llama_batch_get_one(prompt_tokens.data(), prompt_tokens.size());
    if (llama_decode(ctx, batch) != 0) {
        LOGE("llama_decode failed for streaming prompt");
        cleanup_grammar();
        env->ReleaseStringUTFChars(jprompt, prompt);
        return;
    }

    // Emits a chunk back to the Kotlin callback. Empty chunks are skipped.
    auto emit = [&](const std::string &chunk) {
        if (chunk.empty()) return;
        jstring js = env->NewStringUTF(chunk.c_str());
        env->CallObjectMethod(jcallback, invokeMethod, js);
        env->DeleteLocalRef(js);
    };

    // 3. Streaming loop.
    // We accumulate the full decoded text and only emit text we're certain is
    // not part of a stop marker, holding back the last STOP_HOLDBACK chars
    // until they're confirmed safe (so a marker split across token pieces is
    // never streamed to the UI).
    g_abort_generation.store(false);
    std::string accumulated;
    size_t emitted = 0;
    int n_curr = prompt_tokens.size();
    while (n_curr < prompt_tokens.size() + jmax_tokens) {
        if (g_abort_generation.load()) {
            break; // Stop button / cancellation.
        }

        llama_token id = llama_sampler_sample(active_sampler, ctx, -1);
        llama_sampler_accept(active_sampler, id);

        if (llama_vocab_is_eog(vocab, id)) {
            break;
        }

        char buf[128];
        int n = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, true);
        if (n >= 0) {
            accumulated.append(buf, n);

            // Hard stop on any chat-template marker that leaked as text.
            size_t stop = find_stop_marker(accumulated);
            if (stop != std::string::npos) {
                if (stop > emitted) emit(accumulated.substr(emitted, stop - emitted));
                emitted = accumulated.size();
                break;
            }

            // Emit everything except a possible partial-marker tail.
            if (accumulated.size() > emitted + STOP_HOLDBACK) {
                size_t safe_end = accumulated.size() - STOP_HOLDBACK;
                emit(accumulated.substr(emitted, safe_end - emitted));
                emitted = safe_end;
            }
        }

        batch = llama_batch_get_one(&id, 1);
        if (llama_decode(ctx, batch) != 0) {
            break;
        }
        n_curr++;
    }

    // Flush whatever safe text remains (no stop marker was hit), trimming a
    // trailing marker if one is present in the held-back tail.
    if (emitted < accumulated.size()) {
        std::string tail = accumulated.substr(emitted);
        size_t stop = find_stop_marker(tail);
        if (stop != std::string::npos) tail.erase(stop);
        emit(tail);
    }

    cleanup_grammar();
    env->ReleaseStringUTFChars(jprompt, prompt);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_mias_core_inference_engine_LlamaCppEngine_nativeStopGeneration(JNIEnv *env, jobject thiz) {
    // Intentionally does NOT acquire llama_mutex: an in-flight generation holds
    // it for the whole run. Flipping this atomic lets the loop, which polls it
    // every iteration, break out within one token.
    g_abort_generation.store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_mias_core_inference_engine_LlamaCppEngine_nativeUnload(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(llama_mutex);
    if (sampler != nullptr) {
        llama_sampler_free(sampler);
        sampler = nullptr;
    }
    if (ctx != nullptr) {
        llama_free(ctx);
        ctx = nullptr;
    }
    if (model != nullptr) {
        llama_model_free(model);
        model = nullptr;
    }
    LOGI("Model unloaded");
}

// ─── Embedding Engine Bindings ───────────────────────────────────────────────────

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_mias_core_inference_engine_EmbeddingEngine_nativeLoadEmbeddingModel(JNIEnv *env, jobject thiz, jstring jpath) {
    std::lock_guard<std::mutex> lock(emb_mutex);
    
    if (emb_model != nullptr) {
        LOGI("Embedding model already loaded");
        return JNI_TRUE;
    }

    const char *path = env->GetStringUTFChars(jpath, nullptr);
    if (path == nullptr) {
        LOGE("Failed to get path string");
        return JNI_FALSE;
    }
    
    llama_model_params mparams = llama_model_default_params();
    emb_model = llama_model_load_from_file(path, mparams);
    
    env->ReleaseStringUTFChars(jpath, path);
    
    if (emb_model == nullptr) {
        LOGE("Failed to load embedding model from %s", path);
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = 8192; // Nomic Embed v2 supports 8192
    cparams.n_threads = 4;
    cparams.n_threads_batch = 4;
    cparams.embeddings = true; // Crucial for embedding extraction

    emb_ctx = llama_init_from_model(emb_model, cparams);
    
    if (emb_ctx == nullptr) {
        LOGE("Failed to create embedding context");
        llama_model_free(emb_model);
        emb_model = nullptr;
        return JNI_FALSE;
    }

    LOGI("Embedding model loaded successfully");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_dev_mias_core_inference_engine_EmbeddingEngine_nativeGetEmbedding(JNIEnv *env, jobject thiz, jstring jtext) {
    std::lock_guard<std::mutex> lock(emb_mutex);

    if (emb_model == nullptr || emb_ctx == nullptr) {
        LOGE("Cannot get embedding: model not loaded");
        return nullptr;
    }

    const struct llama_vocab * vocab = llama_model_get_vocab(emb_model);
    const char *text = env->GetStringUTFChars(jtext, nullptr);
    if (text == nullptr) {
        LOGE("Failed to get text string");
        return nullptr;
    }

    // 1. Tokenize (prepend BOS, specific to Nomic usually, true true here)
    const int n_tokens_max = strlen(text) + 2; 
    std::vector<llama_token> tokens(n_tokens_max);
    int n_tokens = llama_tokenize(vocab, text, strlen(text), tokens.data(), tokens.size(), true, true);
    
    if (n_tokens < 0) {
        LOGE("Failed to tokenize embedding text");
        env->ReleaseStringUTFChars(jtext, text);
        return nullptr;
    }
    tokens.resize(n_tokens);

    // 2. Decode batch. Clear first so each embedding is computed independently
    // (no carry-over from the previous text).
    llama_memory_clear(llama_get_memory(emb_ctx), true);
    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
    if (llama_decode(emb_ctx, batch) != 0) {
        LOGE("llama_decode failed for embedding");
        env->ReleaseStringUTFChars(jtext, text);
        return nullptr;
    }

    // 3. Extract embedding
    const int idx = n_tokens - 1; // Last token has the pooled embedding in many models
    const float * embd = llama_get_embeddings_ith(emb_ctx, idx);
    if (embd == nullptr) {
        LOGE("Failed to get embeddings pointer");
        env->ReleaseStringUTFChars(jtext, text);
        return nullptr;
    }

    const int n_embd = llama_model_n_embd(emb_model);
    jfloatArray result = env->NewFloatArray(n_embd);
    if (result == nullptr) {
        LOGE("Failed to create float array");
        env->ReleaseStringUTFChars(jtext, text);
        return nullptr;
    }
    env->SetFloatArrayRegion(result, 0, n_embd, embd);

    env->ReleaseStringUTFChars(jtext, text);

    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_mias_core_inference_engine_EmbeddingEngine_nativeUnloadEmbeddingModel(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(emb_mutex);
    if (emb_ctx != nullptr) {
        llama_free(emb_ctx);
        emb_ctx = nullptr;
    }
    if (emb_model != nullptr) {
        llama_model_free(emb_model);
        emb_model = nullptr;
    }
    LOGI("Embedding model unloaded");
}
