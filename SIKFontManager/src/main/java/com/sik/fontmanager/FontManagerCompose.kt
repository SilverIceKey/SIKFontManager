package com.sik.fontmanager

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

@Composable
fun ProvideFontManager(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val config = remember(context) { getFontConfigFromMeta(context) }
    val defaultComposeWeight = config.defaultFontWeight.compose

    val fontFamily = remember(context, config) {
        resolveFontFamily(
            context = context,
            config = config,
        ) ?: FontManager.getDefaultTypeface()?.let { FontFamily(it) }
    }

    if (fontFamily == null) {
        content()
        return
    }

    val mergedLocalTextStyle = LocalTextStyle.current.withGlobalFontDefaults(
        fontFamily = fontFamily,
        defaultFontWeight = defaultComposeWeight,
    )

    val patchedTypography = MaterialTheme.typography.withGlobalFontDefaults(
        fontFamily = fontFamily,
        defaultFontWeight = defaultComposeWeight,
    )

    CompositionLocalProvider(
        LocalTextStyle provides mergedLocalTextStyle,
    ) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme,
            shapes = MaterialTheme.shapes,
            typography = patchedTypography,
            content = content,
        )
    }
}

private fun resolveFontFamily(
    context: Context,
    config: FontMetaConfig,
): FontFamily? {
    val fontSource = config.fontSource ?: return null
    if (config.fontType != FontSourceTypeEnums.RES) return null

    return if (config.isVariableFont) {
        resolveVariableFontFamily(
            context = context,
            fontResName = fontSource,
        )
    } else {
        resolveStaticFontFamily(
            context = context,
            baseName = fontSource,
        )
    }
}

@OptIn(ExperimentalTextApi::class)
private fun resolveVariableFontFamily(
    context: Context,
    fontResName: String,
): FontFamily? {
    val id = context.resources.getIdentifier(fontResName, "font", context.packageName)
    if (id == 0) return null

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        return FontFamily(
            Font(id, weight = FontWeight.Normal)
        )
    }

    fun vf(weight: Int): Font {
        val safe = weight.coerceIn(1, 1000)
        return Font(
            resId = id,
            weight = FontWeight(safe),
            variationSettings = FontVariation.Settings(
                FontVariation.weight(safe)
            )
        )
    }

    return FontFamily(
        vf(100),
        vf(200),
        vf(300),
        vf(400),
        vf(500),
        vf(600),
        vf(700),
        vf(800),
        vf(900),
    )
}

private fun resolveStaticFontFamily(
    context: Context,
    baseName: String,
): FontFamily? {
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
        val resName = baseName + suffix
        val id = res.getIdentifier(resName, "font", pkg)
        if (id != 0 && seenResIds.add(id)) {
            fonts += Font(id, weight = weight)
        }
    }

    if (fonts.isEmpty()) return null
    return FontFamily(fonts)
}

private data class FontMetaConfig(
    val fontSource: String?,
    val fontType: FontSourceTypeEnums,
    val isVariableFont: Boolean,
    val defaultFontWeight: FontWeightEnum,
)

private fun getFontConfigFromMeta(context: Context): FontMetaConfig {
    return try {
        val appInfo = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA
        )
        val meta = appInfo.metaData
        val source = meta?.get("fontSource")?.toString()
        val typeString = meta?.get("fontType")?.toString().orEmpty()
        val type = FontSourceTypeEnums.getFontSourceType(typeString)

        val isVariable = when (val raw = meta?.get("fontIsVariable")) {
            is Boolean -> raw
            is String -> raw.equals("true", ignoreCase = true)
            is Int -> raw != 0
            else -> false
        }

        val weightEnum = FontWeightEnum.parse(meta?.get("fontWeight")?.toString())

        FontMetaConfig(
            fontSource = source,
            fontType = type,
            isVariableFont = isVariable,
            defaultFontWeight = weightEnum,
        )
    } catch (e: Exception) {
        e.printStackTrace()
        FontMetaConfig(
            fontSource = null,
            fontType = FontSourceTypeEnums.UNKNOW,
            isVariableFont = false,
            defaultFontWeight = FontWeightEnum.NORMAL,
        )
    }
}

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
    return copy(
        fontFamily = fontFamily,
        fontWeight = fontWeight ?: defaultFontWeight,
        fontSynthesis = FontSynthesis.None,
    )
}