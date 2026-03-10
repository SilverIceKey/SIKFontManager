package com.sik.fontmanager

import androidx.compose.ui.text.font.FontWeight

/**
 * 字体字重枚举
 *
 * Manifest 推荐写字符串，不要写数字：
 *
 * THIN
 * EXTRA_LIGHT
 * LIGHT
 * NORMAL
 * MEDIUM
 * SEMI_BOLD
 * BOLD
 * EXTRA_BOLD
 * BLACK
 */
enum class FontWeightEnum(
    val weight: Int,
    val compose: FontWeight
) {

    THIN(100, FontWeight.W100),

    EXTRA_LIGHT(200, FontWeight.W200),

    LIGHT(300, FontWeight.W300),

    NORMAL(400, FontWeight.W400),

    MEDIUM(500, FontWeight.W500),

    SEMI_BOLD(600, FontWeight.W600),

    BOLD(700, FontWeight.W700),

    EXTRA_BOLD(800, FontWeight.W800),

    BLACK(900, FontWeight.W900);

    companion object {

        fun parse(value: String?): FontWeightEnum {

            if (value.isNullOrBlank()) {
                return NORMAL
            }

            return try {
                valueOf(value.uppercase())
            } catch (_: Exception) {

                // 兼容旧数字写法
                when (value.toIntOrNull()) {
                    100 -> THIN
                    200 -> EXTRA_LIGHT
                    300 -> LIGHT
                    400 -> NORMAL
                    500 -> MEDIUM
                    600 -> SEMI_BOLD
                    700 -> BOLD
                    800 -> EXTRA_BOLD
                    900 -> BLACK
                    else -> NORMAL
                }
            }
        }
    }
}