package com.sik.fontmanager

import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

internal object FontFragmentLifecycleCallback : FragmentManager.FragmentLifecycleCallbacks() {

    override fun onFragmentViewCreated(
        fm: FragmentManager,
        f: Fragment,
        v: View,
        savedInstanceState: android.os.Bundle?
    ) {
        FontManager.onFragmentViewCreated(v)
    }

    override fun onFragmentResumed(
        fm: FragmentManager,
        f: Fragment
    ) {
        FontManager.onFragmentResumed(f.view)
    }

    override fun onFragmentViewDestroyed(
        fm: FragmentManager,
        f: Fragment
    ) {
        // 这里不需要额外处理，rootView 的 pending task 在 WeakHashMap 下可自然释放
        // 如果后面你想更狠一点，也可以额外加 cancelRoot(view) 接口
    }
}