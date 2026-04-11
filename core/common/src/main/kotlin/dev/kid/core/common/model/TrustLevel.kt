package dev.kid.core.common.model

/** Trust level for Circle of Trust memory system. */
enum class TrustLevel {
    /** Device owner — full access to everything. */
    OWNER,

    /** Close friends/family — shared project contexts visible. */
    INNER_CIRCLE,

    /** Known contacts — minimal memory access. */
    ACQUAINTANCE,
}
