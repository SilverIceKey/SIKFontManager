package com.sik.fontmanager

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.res.ResourcesCompat
import java.io.File
import java.lang.ref.WeakReference

object FontManager {

    private var defaultTypeface: Typeface? = null
    private var defaultFontWeight: FontWeightEnum = FontWeightEnum.NORMAL
    private var variableFontEnabled: Boolean = false

    private val activities = mutableListOf<WeakReference<Activity>>()

    @RequiresApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    fun init(context: Context) {
        try {
            val fontSource = getMetaDataString(context, "fontSource")
            val fontType =
                FontSourceTypeEnums.getFontSourceType(getMetaDataString(context, "fontType") ?: "")
            defaultFontWeight = FontWeightEnum.parse(getMetaDataString(context, "fontWeight"))
            variableFontEnabled = getMetaDataBoolean(context, "fontIsVariable")

            if (fontSource.isNullOrEmpty() || fontType == FontSourceTypeEnums.UNKNOW) {
                return
            }

            setDefaultFont(
                context = context,
                fontSource = fontSource,
                fontType = fontType,
                fontWeight = defaultFontWeight,
                isVariableFont = variableFontEnabled,
            )

            (context as? Application)?.registerActivityLifecycleCallbacks(FontLifecycleCallback())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @RequiresApi(Build.VERSION_CODES.DONUT)
    fun setDefaultFont(
        context: Context,
        fontSource: String,
        fontType: FontSourceTypeEnums,
        fontWeight: FontWeightEnum = defaultFontWeight,
        isVariableFont: Boolean = variableFontEnabled,
    ) {
        try {
            defaultFontWeight = fontWeight
            variableFontEnabled = isVariableFont

            defaultTypeface = when (fontType) {
                FontSourceTypeEnums.ASSETS -> {
                    Typeface.createFromAsset(context.assets, fontSource)
                }

                FontSourceTypeEnums.RES -> {
                    resolveResTypeface(
                        context = context,
                        fontSource = fontSource,
                        isVariableFont = isVariableFont,
                    )
                }

                FontSourceTypeEnums.FILE -> {
                    Typeface.createFromFile(File(fontSource))
                }

                else -> null
            }

            updateAllActivities()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * RES 字体解析：
     *
     * - variable font：fontSource 必须是 res/font 下实际资源名
     * - static font：兼容旧逻辑，支持基准名 + 后缀猜测
     */
    private fun resolveResTypeface(
        context: Context,
        fontSource: String,
        isVariableFont: Boolean,
    ): Typeface? {
        if (isVariableFont) {
            val vfId = getResId(context, fontSource)
            if (vfId != 0) {
                return ResourcesCompat.getFont(context, vfId)
            }
            return null
        }

        val directId = getResId(context, fontSource)
        if (directId != 0) {
            return ResourcesCompat.getFont(context, directId)
        }

        val candidates = listOf(
            "${fontSource}_regular",
            fontSource,
            "${fontSource}_medium",
            "${fontSource}_bold",
            "${fontSource}_semibold",
            "${fontSource}_light",
            "${fontSource}_thin",
            "${fontSource}_black",
            "${fontSource}_extralight",
            "${fontSource}_extrabold",
        )

        for (name in candidates) {
            val id = getResId(context, name)
            if (id != 0) {
                return ResourcesCompat.getFont(context, id)
            }
        }

        return null
    }

    internal fun onActivityCreated(activity: Activity) {
        activities.add(WeakReference(activity))
    }

    internal fun onActivityDestroyed(activity: Activity) {
        activities.removeAll { it.get() == null || it.get() == activity }
    }

    internal fun onActivityResumed(activity: Activity) {
        applyFontToViews(activity.window.decorView)
    }

    private fun updateAllActivities() {
        activities.forEach { weakRef ->
            weakRef.get()?.let { activity ->
                applyFontToViews(activity.window.decorView)
            }
        }
        activities.removeAll { it.get() == null }
    }

    internal fun applyFontToViews(view: View) {
        try {
            when (view) {
                is ViewGroup -> {
                    for (i in 0 until view.childCount) {
                        applyFontToViews(view.getChildAt(i))
                    }
                }

                is TextView -> {
                    val baseTypeface = defaultTypeface ?: return
                    view.typeface = applyWeight(
                        baseTypeface = baseTypeface,
                        fontWeight = defaultFontWeight,
                        italic = view.typeface?.isItalic == true,
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * View 侧默认字重处理策略：
     *
     * - API 28+：支持 Typeface.create(typeface, weight, italic)
     * - API 26~27：系统支持 variable font，但这里先不做伪精确控制
     * - API < 26：只能降级到 NORMAL/BOLD
     */
    private fun applyWeight(
        baseTypeface: Typeface,
        fontWeight: FontWeightEnum,
        italic: Boolean,
    ): Typeface {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                Typeface.create(baseTypeface, fontWeight.weight, italic)
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                baseTypeface
            }

            else -> {
                Typeface.create(
                    baseTypeface,
                    if (fontWeight.weight >= FontWeightEnum.SEMI_BOLD.weight) {
                        Typeface.BOLD
                    } else {
                        Typeface.NORMAL
                    }
                )
            }
        }
    }

    private fun getMetaDataString(context: Context, key: String): String? {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            appInfo.metaData?.get(key)?.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getMetaDataBoolean(context: Context, key: String): Boolean {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            val value = appInfo.metaData?.get(key)
            when (value) {
                is Boolean -> value
                is String -> value.equals("true", ignoreCase = true)
                is Int -> value != 0
                else -> false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun getResId(context: Context, resName: String): Int {
        return context.resources.getIdentifier(resName, "font", context.packageName)
    }

    fun getDefaultTypeface(): Typeface? = defaultTypeface

    fun getDefaultFontWeight(): FontWeightEnum = defaultFontWeight

    fun getDefaultFontWeightValue(): Int = defaultFontWeight.weight

    fun isVariableFontEnabled(): Boolean = variableFontEnabled
}