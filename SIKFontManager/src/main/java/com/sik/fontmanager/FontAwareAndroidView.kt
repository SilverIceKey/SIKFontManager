package com.sik.fontmanager

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 用于 Compose 中承载原生 Android View 的字体适配封装：
 *
 * - factory：首次创建时，按“新 View”处理，清理策略缓存后再补刷
 * - update：后续普通更新，默认只做普通补刷
 * - refreshPolicyOnUpdate：如果 update 中会主动改字重/typeface/italic，可开启强制重判策略
 */
@Composable
fun <T : View> FontAwareAndroidView(
    modifier: Modifier = Modifier,
    factory: (Context) -> T,
    update: (T) -> Unit = {},
    refreshPolicyOnUpdate: Boolean = false,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            factory(context).also { view ->
                FontManager.bindNewView(view)
            }
        },
        update = { view ->
            update(view)
            if (refreshPolicyOnUpdate) {
                FontManager.refreshViewPolicyAndReapply(view)
            } else {
                FontManager.reapplyToView(view)
            }
        }
    )
}