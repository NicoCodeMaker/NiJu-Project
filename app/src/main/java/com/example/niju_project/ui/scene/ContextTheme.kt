package com.example.niju_project.ui.scene

import android.graphics.Color

/**
 * Identidad visual de cada contexto: su "cielo" (degradado), su color de acento
 * (barras de progreso, botones, selección) y su tinta (títulos sobre fondo claro).
 * Todos los acentos tienen contraste ≥ 4,5:1 con el blanco (texto blanco en botones);
 * los títulos usan [ink] (≥ 7:1). En modo oscuro el acento es [skyStart] (≥ 4,5:1 sobre el fondo).
 */
data class ContextTheme(
    val slug: String,
    val skyStart: Int,
    val skyEnd: Int,
    val accent: Int,
    val ink: Int,
) {
    /** Acento con transparencia (0–255), para fondos suaves y resaltados. */
    fun accentAlpha(alpha: Int): Int = Color.argb(alpha, Color.red(accent), Color.green(accent), Color.blue(accent))

    companion object {
        private fun t(slug: String, a: String, b: String, accent: String, ink: String) =
            ContextTheme(slug, Color.parseColor(a), Color.parseColor(b), Color.parseColor(accent), Color.parseColor(ink))

        private val THEMES = listOf(
            t("home", "#FFB27A", "#FF7A85", "#C5383F", "#5A2230"),
            t("school", "#3FBF9B", "#1F7E78", "#127A70", "#0E3B38"),
            t("beach", "#5ED3F3", "#2A8FE0", "#1677B8", "#0B3558"),
            t("restaurant", "#FF9F5A", "#D9483B", "#C63D31", "#4A1A12"),
            t("airport", "#7FB2FF", "#4E5BE8", "#4E5BE8", "#1C1F5C"),
            t("supermarket", "#9BE15D", "#2EB872", "#197A48", "#123D24"),
            t("shopping", "#FF8FC7", "#C445C9", "#B0379F", "#4A1446"),
            t("health", "#FF8A9B", "#E23E64", "#C92E55", "#4F0F22"),
            t("transport", "#FFD25E", "#F59E2B", "#9E5A06", "#4A2C05"),
            t("technology", "#7383FF", "#2A1F7A", "#4B5BEF", "#14104A"),
            t("work", "#8DA2C0", "#3E4C6B", "#3E5A8C", "#1A2233"),
            t("travel", "#B58CFF", "#6A3FD8", "#6A3FD8", "#2A1260"),
        ).associateBy { it.slug }

        /** Tema por defecto (marca NiJu) para contextos nuevos sin escena propia. */
        val DEFAULT = t("default", "#7FB2FF", "#6D4FE0", "#5B4BD8", "#1E1A4A")

        fun of(slug: String?): ContextTheme = THEMES[slug] ?: DEFAULT

        /** Contextos con escena dibujada. */
        val SLUGS: Set<String> get() = THEMES.keys
    }
}
