package com.example.keynest.domain.usecase

/**
 * Validates Android package names.
 *
 * Requirements: 1.3
 *
 * Format (matches design.md State Invariants):
 *   `^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$`
 *
 * Notes:
 * - At least one dot is required (a single-segment id like "foo" is not a
 *   valid Android package name).
 * - Each segment must start with a letter; subsequent characters may be
 *   letters, digits or underscore.
 * - No leading/trailing dots, no empty segments.
 */
internal object PackageNameValidator {

    private val PATTERN = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")

    fun isValid(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return PATTERN.matches(packageName)
    }
}
