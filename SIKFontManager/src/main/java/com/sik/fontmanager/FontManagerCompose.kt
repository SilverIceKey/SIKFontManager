package com.sik.fontmanager

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * Compose 支持入口（可直接整体替换你现在的 FontManagerCompose.kt）。
 *
 * 约定：
 * - 在 AndroidManifest.xml 里只配置 “基准名字”，比如：
 *
 *   <meta-data
 *       android:name="fontSource"
 *       android:value="myfont" />   // 不带 _bold / _medium 等后缀
 *   <meta-data
 *       android:name="fontType"
 *       android:value="res" />      // 目前多字重自动识别只推荐 RES
 *
 * - 对应的 res/font 里放一堆同前缀、不同后缀的字体文件：
 *
 *   res/font/myfont_thin.ttf
 *   res/font/myfont_light.ttf
 *   res/font/myfont_regular.ttf
 *   res/font/myfont_medium.ttf
 *   res/font/myfont_semibold.ttf
 *   res/font/myfont_bold.ttf
 *   res/font/myfont_extrabold.ttf
 *   res/font/myfont_black.ttf
 *
 * 哪些文件存在就用哪些，不要求全部都有，缺的权重会回落到「最近的」一个
 * —— 这部分是 Compose 自带 FontFamily 的匹配逻辑完成的。
 */

/**
 * 在 Compose 中应用通过 Manifest 配置的字体：
 *
 * - 优先：尝试根据你配置的基准名 + 约定后缀，构建一个多字重 FontFamily
 * - 如果没找到任何 res/font 资源，则退回到旧逻辑：使用 FontManager.getDefaultTypeface()
 */
@Composable
fun ProvideFontManager(content: @Composable () -> Unit) {
    val context = LocalContext.current

    // 1. 优先尝试走多字重 FontFamily（仅 RES 类型）
    val familyFromRes = resolveFontFamilyFromManifest(context)

    val fontFamily = familyFromRes ?: run {
        // 2. 退回到旧逻辑：单个 Typeface → FontFamily
        val typeface = FontManager.getDefaultTypeface()
        typeface?.let { FontFamily(it) }
    }

    // 如果仍然拿不到字体，就不改 LocalTextStyle，直接渲染
    if (fontFamily == null) {
        content()
        return
    }

    val current = LocalTextStyle.current
    val merged = current.merge(TextStyle(fontFamily = fontFamily))

    CompositionLocalProvider(LocalTextStyle provides merged) {
        content()
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

    // 约定的后缀 -> FontWeight 映射
    val suffixWeightPairs: List<Pair<String, FontWeight>> = listOf(
        "_thin" to FontWeight.Thin,
        "_extralight" to FontWeight.ExtraLight,
        "_light" to FontWeight.Light,
        // 无后缀 / _regular 都视作 Normal
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
    val seenResIds = mutableSetOf<Int>() // 防止无后缀和 _regular 指向同一个 id

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
 *
 * - fontSource：我们约定为「基准名字」，如 "myfont"
 * - fontType：使用你已有的 FontSourceTypeEnums 解析
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
