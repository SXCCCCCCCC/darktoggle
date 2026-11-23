package com.github.sxcccccccc.darktoggle

import android.app.Application
import com.google.android.material.color.DynamicColors

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // 官方推荐：Android 12+ 为所有 Activity 应用系统 Material You 动态色；
        // 不支持时保留 Material 3 默认主题，不做自定义 fallback。
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
