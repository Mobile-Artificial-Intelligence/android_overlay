package com.requiemz.overlay_pop_up

import android.content.pm.ActivityInfo
import android.view.WindowManager
import android.view.Gravity

object Overlay {
    var height: Int = WindowManager.LayoutParams.MATCH_PARENT
    var width: Int = WindowManager.LayoutParams.MATCH_PARENT
    var alignment = Gravity.CENTER
    var backgroundBehavior = 1
    var screenOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    var closeWhenTapBackButton = false
    var draggable = false
    var snapping = false
    var lastX = 0
    var lastY = 0
    var entryPointMethodName: String = ""
}
