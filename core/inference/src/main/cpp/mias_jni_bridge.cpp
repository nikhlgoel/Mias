#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include <mutex>

#include "llama.h"

#define TAG "MiasInference"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Global state (Chat)
static llama_model *model = nullptr;
static llama_context *ctx = nullptr;
static llama_sampler *sampler = nullptr;
static std::mutex llama_mutex;

// Global state (Embedding)
static llama_model *emb_model = nullptr;
static llama_context *emb_ctx = nullptr;
static std::mutex emb_mutex;

extern "C" JNIEXPORT void JNICALL
Java_dev_kid_core_inference_engine_LlamaCppEngine_nativeInit(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(llama_mutex);
    llama_backend_init();
    LOGI("llama.cpp backend initialized");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_kid_core_inference_engine_LlamaCppEngine_nativeLoadModel(JNIEnv *env, jobject thiz, jstring jpath) {
    std::lock_guard<std::mutex> lock(llama_mutex);
    
    if (model != nullptr) {
        LOGE("Model already loaded");
        return JNI_FALSE;
    }

    const char *path = env->GetStringUTFChars(jpath, nullptr);
    
    llama_model_params mparams = llama_model_default_params();
    model = llama_load_model_from_file(path, mparams);
    
    if (model == nullptr) {
        LOGE("Failed to load model from %s", path);
        env->ReleaseStringUTFChars(jpath, path);
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = 4096; // Adjust later dynamically or expose to Kotlin
    cparams.n_threads = 4;
    cparams.n_threads_batch = 4;

    ctx = llama_new_context_with_model(model, cparams);
    
    if (ctx == nullptr) {
        LOGE("Failed to create context");
        llama_model_free(model);
        model = nullptr;
        env->ReleaseStringUTFChars(jpath, path);
        return JNI_FALSE;
    }

    // Initialize sampler chain
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.7f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(1234)); // Seed

    LOGI("Model loaded successfully from %s", path);
    env->ReleaseStringUTFChars(jpath, path);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_kid_core_inference_engine_LlamaCppEngine_nativeGenerate(JNIEnv *env, jobject thiz, jstring jprompt, jint jmax_tokens) {
    std::lock_guard<std::mutex> lock(llama_mutex);
    
    if (model == nullptr || ctx == nullptr) {
        LOGE("Cannot generate: model not loaded");
        return env->NewStringUTF("");
    }

    const struct llama_vocab * vocab = llama_model_get_vocab(model);
    const char *prompt = env->GetStringUTFChars(jprompt, nullptr);
    std::string response = "";

    // Very basic generation loop for Phase 1 verification
    // 1. Tokenize prompt
    const int n_prompt = -llama_tokenize(vocab, prompt, strlen(prompt), NULL, 0, true, true);
    std::vector<llama_token> prompt_tokens(n_prompt);
    if (llama_tokenize(vocab, prompt, strlen(prompt), prompt_tokens.data(), prompt_tokens.size(), true, true) < 0) {
        LOGE("Failed to tokenize prompt");
        env->ReleaseStringUTFChars(jprompt, prompt);
        return env->NewStringUTF("");
    }

    // 2. Decode prompt batch
    llama_batch batch = llama_batch_get_one(prompt_tokens.data(), prompt_tokens.size());
    if (llama_decode(ctx, batch) != 0) {
        LOGE("llama_decode failed");
        env->ReleaseStringUTFChars(jprompt, prompt);
        return env->NewStringUTF("");
    }

    // 3. Autoregressive loop
    int n_curr = prompt_tokens.size();
    while (n_curr < prompt_tokens.size() + jmax_tokens) {
        llama_token id = llama_sampler_sample(sampler, ctx, -1);
        llama_sampler_accept(sampler, id);

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

        batch = llama_batch_get_one(&id, 1);
        if (llama_decode(ctx, batch) != 0) {
            LOGE("llama_decode failed during generation");
            break;
        }
        n_curr++;
    }

    env->ReleaseStringUTFChars(jprompt, prompt);
    return env->NewStringUTF(response.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_dev_kid_core_inference_engine_LlamaCppEngine_nativeGenerateStream(JNIEnv *env, jobject thiz, jstring jprompt, jint jmax_tokens, jobject jcallback) {
    std::lock_guard<std::mutex> lock(llama_mutex);
    
    if (model == nullptr || ctx == nullptr) {
        LOGE("Cannot stream: model not loaded");
        return;
    }

    const struct llama_vocab * vocab = llama_model_get_vocab(model);
    const char *prompt = env->GetStringUTFChars(jprompt, nullptr);
    jclass callbackClass = env->GetObjectClass(jcallback);
    jmethodID invokeMethod = env->GetMethodID(callbackClass, "invoke", "(Ljava/lang/Object;)Ljava/lang/Object;");

    if (invokeMethod == nullptr) {
        LOGE("Cannot find callback invoke method");
        env->ReleaseStringUTFChars(jprompt, prompt);
        return;
    }

    // 1. Tokenize prompt
    const int n_prompt = -llama_tokenize(vocab, prompt, strlen(prompt), NULL, 0, true, true);
    std::vector<llama_token> prompt_tokens(n_prompt);
    if (llama_tokenize(vocab, prompt, strlen(prompt), prompt_tokens.data(), prompt_tokens.size(), true, true) < 0) {
        LOGE("Failed to tokenize prompt for streaming");
        env->ReleaseStringUTFChars(jprompt, prompt);
        return;
    }

    // 2. Decode batch
    llama_batch batch = llama_batch_get_one(prompt_tokens.data(), prompt_tokens.size());
    if (llama_decode(ctx, batch) != 0) {
        LOGE("llama_decode failed for streaming prompt");
        env->ReleaseStringUTFChars(jprompt, prompt);
        return;
    }

    // 3. Streaming loop
    int n_curr = prompt_tokens.size();
    while (n_curr < prompt_tokens.size() + jmax_tokens) {
        llama_token id = llama_sampler_sample(sampler, ctx, -1);
        llama_sampler_accept(sampler, id);

        if (llama_vocab_is_eog(vocab, id)) {
            break;
        }

        char buf[128];
        int n = llama_token_to_piece(vocab, id, buf, sizeof(buf), 0, true);
        if (n >= 0) {
             jstring jtoken_str = env->NewStringUTF(std::string(buf, n).c_str());
             env->CallObjectMethod(jcallback, invokeMethod, jtoken_str);
             env->DeleteLocalRef(jtoken_str);
        }

        batch = llama_batch_get_one(&id, 1);
        if (llama_decode(ctx, batch) != 0) {
            break;
        }
        n_curr++;
    }

    env->ReleaseStringUTFChars(jprompt, prompt);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_kid_core_inference_engine_LlamaCppEngine_nativeUnload(JNIEnv *env, jobject thiz) {
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
Java_dev_kid_core_inference_engine_EmbeddingEngine_nativeLoadEmbeddingModel(JNIEnv *env, jobject thiz, jstring jpath) {
    std::lock_guard<std::mutex> lock(emb_mutex);
    
    if (emb_model != nullptr) {
        LOGI("Embedding model already loaded");
        return JNI_TRUE;
    }

    const char *path = env->GetStringUTFChars(jpath, nullptr);
    
    llama_model_params mparams = llama_model_default_params();
    emb_model = llama_load_model_from_file(path, mparams);
    
    if (emb_model == nullptr) {
        LOGE("Failed to load embedding model from %s", path);
        env->ReleaseStringUTFChars(jpath, path);
        return JNI_FALSE;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = 8192; // Nomic Embed v2 supports 8192
    cparams.n_threads = 4;
    cparams.n_threads_batch = 4;
    cparams.embeddings = true; // Crucial for embedding extraction

    emb_ctx = llama_new_context_with_model(emb_model, cparams);
    
    if (emb_ctx == nullptr) {
        LOGE("Failed to create embedding context");
        llama_model_free(emb_model);
        emb_model = nullptr;
        env->ReleaseStringUTFChars(jpath, path);
        return JNI_FALSE;
    }

    LOGI("Embedding model loaded successfully from %s", path);
    env->ReleaseStringUTFChars(jpath, path);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_dev_kid_core_inference_engine_EmbeddingEngine_nativeGetEmbedding(JNIEnv *env, jobject thiz, jstring jtext) {
    std::lock_guard<std::mutex> lock(emb_mutex);

    if (emb_model == nullptr || emb_ctx == nullptr) {
        LOGE("Cannot get embedding: model not loaded");
        return nullptr;
    }

    const struct llama_vocab * vocab = llama_model_get_vocab(emb_model);
    const char *text = env->GetStringUTFChars(jtext, nullptr);

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

    // 2. Decode batch
    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
    if (llama_decode(emb_ctx, batch) != 0) {
        LOGE("llama_decode failed for embedding");
        env->ReleaseStringUTFChars(jtext, text);
        return nullptr;
    }

    // 3. Extract embedding
    // For n_tokens, the embedding is usually associated with the sequence or the last token.
    // Llama.cpp stores embeddings for tokens if cparams.embeddings = true.
    const int idx = n_tokens - 1; // Last token has the pooled embedding in many models, wait, Nomic might require sequence-level pooling.
    // Llama_get_embeddings gives sequence embeddings if requested, or we just grab the token embeddings.
    const float * embd = llama_get_embeddings_seq(emb_ctx, 0); 
    if (embd == nullptr) {
        embd = llama_get_embeddings_ith(emb_ctx, idx);
        if (embd == nullptr) {
            LOGE("Failed to get embeddings pointer");
            env->ReleaseStringUTFChars(jtext, text);
            return nullptr;
        }
    }

    const int n_embd = llama_model_n_embd(emb_model);
    jfloatArray result = env->NewFloatArray(n_embd);
    env->SetFloatArrayRegion(result, 0, n_embd, embd);

    env->ReleaseStringUTFChars(jtext, text);

    // (KV cache clears automatically if positions overlap or can be done manually if API is present)

    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_kid_core_inference_engine_EmbeddingEngine_nativeUnloadEmbeddingModel(JNIEnv *env, jobject thiz) {
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
