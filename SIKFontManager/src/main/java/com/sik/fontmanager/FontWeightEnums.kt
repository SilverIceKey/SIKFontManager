package com.sik.fontmanager

import android.graphics.Typeface
import androidx.compose.ui.text.font.FontWeight

enum class FontWeightEnums(
    val composeWeight: FontWeight,
    val api28Weight: Int,
    val legacyStyle: Int,
) {
    THIN(FontWeight.Thin, 100, Typeface.NORMAL),
    EXTRA_LIGHT(FontWeight.ExtraLight, 200, Typeface.NORMAL),
    LIGHT(FontWeight.Light, 300, Typeface.NORMAL),
    NORMAL(FontWeight.Normal, 400, Typeface.NORMAL),
    MEDIUM(FontWeight.Medium, 500, Typeface.NORMAL),
    SEMI_BOLD(FontWeight.SemiBold, 600, Typeface.BOLD),
    BOLD(FontWeight.Bold, 700, Typeface.BOLD),
    EXTRA_BOLD(FontWeight.ExtraBold, 800, Typeface.BOLD),
    BLACK(FontWeight.Black, 900, Typeface.BOLD);

    companion object {
        fun from(value: String?): FontWeightEnums {
            return when (value?.trim()?.uppercase()) {
                "THIN" -> THIN
                "EXTRA_LIGHT", "EXTRALIGHT" -> EXTRA_LIGHT
                "LIGHT" -> LIGHT
                "NORMAL", "REGULAR" -> NORMAL
                "MEDIUM" -> MEDIUM
                "SEMI_BOLD", "SEMIBOLD" -> SEMI_BOLD
                "BOLD" -> BOLD
                "EXTRA_BOLD", "EXTRABOLD" -> EXTRA_BOLD
                "BLACK" -> BLACK
                else -> NORMAL
            }
        }
    }
}