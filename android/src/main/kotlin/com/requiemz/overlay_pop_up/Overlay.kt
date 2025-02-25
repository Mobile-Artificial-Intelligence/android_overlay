package com.requiemz.overlay_pop_up

import android.content.pm.ActivityInfo
import android.view.WindowManager
import android.view.Gravity

object Overlay {
    var x = 0.0f
    var y = 0.0f
    var height: Int = WindowManager.LayoutParams.MATCH_PARENT
    var width: Int = WindowManager.LayoutParams.MATCH_PARENT
    var alignment = Gravity.CENTER
    var draggable = false
    var snapping = false
    var entryPointMethodName: String = ""
}
