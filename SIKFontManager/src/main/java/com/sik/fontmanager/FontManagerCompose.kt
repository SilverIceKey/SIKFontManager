package com.sik.fontmanager

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * Compose 全局字体入口。
 *
 * 功能：
 * 1. 给 LocalTextStyle 注入默认 fontFamily + 默认 fontWeight
 * 2. 给 MaterialTheme.typography 全量补齐 fontFamily
 * 3. 把 Typography 里“默认型字重（null / Normal）”替换成 Manifest 配置的默认字重
 *
 * 注意：
 * - 这里不会强行覆盖 Typography 中已经明确指定的 Medium / Bold / SemiBold 等字重
 * - 这样做是为了“设置默认字重”，不是“把全世界都改成 thin”
 */
@Composable
fun ProvideFontManager(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val defaultWeight = FontManager.getDefaultFontWeight().composeWeight

    val familyFromRes = resolveFontFamilyFromManifest(context)

    val fontFamily = familyFromRes ?: run {
        val typeface = FontManager.getDefaultTypeface()
        typeface?.let { FontFamily(it) }
    }

    if (fontFamily == null) {
        content()
        return
    }

    val mergedLocalTextStyle = LocalTextStyle.current.merge(
        TextStyle(
            fontFamily = fontFamily,
            fontWeight = defaultWeight,
        )
    )

    val patchedTypography = MaterialTheme.typography.withGlobalFontDefaults(
        fontFamily = fontFamily,
        defaultFontWeight = defaultWeight,
    )

    CompositionLocalProvider(
        LocalTextStyle provides mergedLocalTextStyle,
    ) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme,
            shapes = MaterialTheme.shapes,
            typography = patchedTypography,
            content = content
        )
    }
}

/**
 * 从 Manifest 里读取 fontSource / fontType，根据 “基准名字 + _字重后缀”
 * 自动拼 res/font 资源并创建 FontFamily。
 *
 * 只在 fontType=RES 时做多字重识别，其它类型（ASSETS/FILE）直接返回 null，
 * 让上层走 Typeface 回退逻辑。
 */
private fun resolveFontFamilyFromManifest(context: Context): FontFamily? {
    val (baseName, type) = getFontConfigFromMeta(context)
    if (baseName.isNullOrEmpty()) return null
    if (type != FontSourceTypeEnums.RES) return null

    val suffixWeightPairs: List<Pair<String, FontWeight>> = listOf(
        "_thin" to FontWeight.Thin,
        "_extralight" to FontWeight.ExtraLight,
        "_light" to FontWeight.Light,
        "" to FontWeight.Normal,
        "_regular" to FontWeight.Normal,
        "_medium" to FontWeight.Medium,
        "_semibold" to FontWeight.SemiBold,
        "_bold" to FontWeight.Bold,
        "_extrabold" to FontWeight.ExtraBold,
        "_black" to FontWeight.Black,
    )

    val fonts = mutableListOf<Font>()
    val res = context.resources
    val pkg = context.packageName
    val seenResIds = mutableSetOf<Int>()

    for ((suffix, weight) in suffixWeightPairs) {
        val resName = buildString {
            append(baseName)
            append(suffix)
        }
        val id = res.getIdentifier(resName, "font", pkg)
        if (id != 0 && seenResIds.add(id)) {
            fonts += Font(id, weight)
        }
    }

    if (fonts.isEmpty()) {
        return null
    }

    return FontFamily(fonts)
}

/**
 * 读取 Manifest 里的 fontSource / fontType。
 */
private fun getFontConfigFromMeta(context: Context): Pair<String?, FontSourceTypeEnums> {
    return try {
        val appInfo = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA
        )
        val meta = appInfo.metaData
        val source = meta?.getString("fontSource")
        val typeString = meta?.getString("fontType") ?: ""
        val type = FontSourceTypeEnums.getFontSourceType(typeString)
        source to type
    } catch (e: Exception) {
        e.printStackTrace()
        null to FontSourceTypeEnums.UNKNOW
    }
}

/**
 * 给 Material3 Typography 全量注入 fontFamily / 默认字重。
 *
 * 策略：
 * - fontFamily：全部替换为全局 fontFamily
 * - fontWeight：
 *   - 如果原本是 null 或 Normal，则替换成 defaultFontWeight
 *   - 如果原本已经是 Medium / SemiBold / Bold 等显式权重，则保留
 */
private fun Typography.withGlobalFontDefaults(
    fontFamily: FontFamily,
    defaultFontWeight: FontWeight,
): Typography {
    return Typography(
        displayLarge = displayLarge.withGlobalFontDefaults(fontFamily, defaultFontWeight),
        displayMedium = displayMedium.withGlobalFontDefaults(fontFamily, defaultFontWeight),
        displaySmall = displaySmall.withGlobalFontDefaults(fontFamily, defaultFontWeight),

        headlineLarge = headlineLarge.withGlobalFontDefaults(fontFamily, defaultFontWeight),
        headlineMedium = headlineMedium.withGlobalFontDefaults(fontFamily, defaultFontWeight),
        headlineSmall = headlineSmall.withGlobalFontDefaults(fontFamily, defaultFontWeight),

        titleLarge = titleLarge.withGlobalFontDefaults(fontFamily, defaultFontWeight),
        titleMedium = titleMedium.withGlobalFontDefaults(fontFamily, defaultFontWeight),
        titleSmall = titleSmall.withGlobalFontDefaults(fontFamily, defaultFontWeight),

        bodyLarge = bodyLarge.withGlobalFontDefaults(fontFamily, defaultFontWeight),
        bodyMedium = bodyMedium.withGlobalFontDefaults(fontFamily, defaultFontWeight),
        bodySmall = bodySmall.withGlobalFontDefaults(fontFamily, defaultFontWeight),

        labelLarge = labelLarge.withGlobalFontDefaults(fontFamily, defaultFontWeight),
        labelMedium = labelMedium.withGlobalFontDefaults(fontFamily, defaultFontWeight),
        labelSmall = labelSmall.withGlobalFontDefaults(fontFamily, defaultFontWeight),
    )
}

private fun TextStyle.withGlobalFontDefaults(
    fontFamily: FontFamily,
    defaultFontWeight: FontWeight,
): TextStyle {
    val patchedWeight = when {
        this.fontWeight == null -> defaultFontWeight
        this.fontWeight == FontWeight.Normal -> defaultFontWeight
        else -> this.fontWeight
    }

    return this.copy(
        fontFamily = fontFamily,
        fontWeight = patchedWeight,
    )
}