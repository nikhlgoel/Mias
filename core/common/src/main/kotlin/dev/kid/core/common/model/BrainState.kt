package dev.kid.core.common.model

/** Which AI brain is currently active. */
enum class BrainState {
    /** Gemma-4-e4b running on NPU — full capability. */
    GEMMA_NPU,

    /** MobileLLM-R1.5 on CPU — thermal/battery survival mode. */
    MOBILELLM_SURVIVAL,

    /** Qwen3-Coder-Next on desktop PC via Tailscale mesh. */
    QWEN_DESKTOP,

    /** Wake-on-LAN sent, waiting for desktop to come online. */
    QWEN_WAKING,

    /** All options constrained — minimal canned responses only. */
    DEGRADED,
}
