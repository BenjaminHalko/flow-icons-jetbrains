package dev.flowicons.settings

/**
 * The eight Flow Icons palettes. Each maps to a folder of SVGs of the same name
 * (e.g. `deep`, `deep-light`) bundled under `/icons/<folder>/` on the classpath
 * (base set) or written to the per-user config directory (premium / Flow You).
 */
enum class ThemeVariant(
    val displayName: String,
    val folder: String,
    val light: Boolean,
) {
    DEEP("Flow Deep", "deep", false),
    DEEP_LIGHT("Flow Deep (Light)", "deep-light", true),
    DIM("Flow Dim", "dim", false),
    DIM_LIGHT("Flow Dim (Light)", "dim-light", true),
    DAWN("Flow Dawn", "dawn", false),
    DAWN_LIGHT("Flow Dawn (Light)", "dawn-light", true),
    YOU("Flow You", "you", false),
    YOU_LIGHT("Flow You (Light)", "you-light", true);

    /** Flow You variants are generated from templates + a user color palette. */
    val isYou: Boolean get() = this == YOU || this == YOU_LIGHT

    override fun toString(): String = displayName

    companion object {
        val DEFAULT: ThemeVariant = DEEP

        fun fromNameOrDefault(name: String?): ThemeVariant =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
