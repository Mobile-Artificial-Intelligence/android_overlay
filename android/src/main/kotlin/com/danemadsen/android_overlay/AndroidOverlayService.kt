package com.danemadsen.android_overlay

import kotlin.math.hypot
import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import io.flutter.embedding.android.FlutterTextureView
import io.flutter.embedding.android.FlutterView
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.plugin.common.BasicMessageChannel
import io.flutter.plugin.common.JSONMessageCodec

class AndroidOverlayService : Service(), BasicMessageChannel.MessageHandler<Any?>, View.OnTouchListener {
    companion object {
        var isActive: Boolean = false
        val handler = Handler(Looper.getMainLooper())
        var windowManager: WindowManager? = null
        lateinit var flutterView: FlutterView
    }

    private lateinit var overlayMessageChannel: BasicMessageChannel<Any?>

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()
        val engine = FlutterEngineCache.getInstance().get(AndroidOverlayPlugin.CACHE_ENGINE_ID)
        if (engine == null) {
            println("[AndroidOverlay] FlutterEngine not available in cache. Stopping service.")
            stopSelf()
            return
        }
        engine.lifecycleChannel.appIsResumed()
        flutterView = object : FlutterView(applicationContext, FlutterTextureView(applicationContext)) {}
        flutterView.attachToFlutterEngine(engine)
        flutterView.fitsSystemWindows = true
        flutterView.setBackgroundColor(Color.TRANSPARENT)
        flutterView.setOnTouchListener(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager?
        val windowConfig = WindowManager.LayoutParams(
            Overlay.width,
            Overlay.height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSPARENT
        ).apply {
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            windowConfig.flags = windowConfig.flags or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        }
        windowConfig.gravity = Overlay.alignment
        windowManager?.addView(flutterView, windowConfig)
        isActive = true
        println("[AndroidOverlay] Overlay successfully initialized.")
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        windowManager?.removeView(flutterView)
        isActive = false
    }

    override fun onMessage(message: Any?, reply: BasicMessageChannel.Reply<Any?>) {
        val engine = FlutterEngineCache.getInstance().get(AndroidOverlayPlugin.CACHE_ENGINE_ID)
        if (engine != null) {
            val overlayMessageChannel = BasicMessageChannel(
                engine.dartExecutor,
                AndroidOverlayPlugin.OVERLAY_MESSAGE_CHANNEL_NAME,
                JSONMessageCodec.INSTANCE
            )
            overlayMessageChannel.send(message, reply)
        } else {
            println("[AndroidOverlay] FlutterEngine not available in cache.")
            reply.reply(null)
        }
    }

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        if (!Overlay.draggable) return false
        val params = flutterView.layoutParams as LayoutParams
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                Overlay.x = event.rawX
                Overlay.y = event.rawY
            }
    
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - Overlay.x
                val dy = event.rawY - Overlay.y
                if (dx * dx + dy * dy < 25) {
                    return false
                }
                Overlay.x = event.rawX
                Overlay.y = event.rawY
                val finalX = params.x + dx.toInt()
                val finalY = params.y + dy.toInt()
                params.x = finalX
                params.y = finalY
                windowManager?.updateViewLayout(flutterView, params)
            }
    
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (Overlay.snapping) {
                    snap(params)
                }
            }
    
            else -> return false
        }
        return false
    }

    private fun snap(params: LayoutParams) {
        // Get screen width
        val displayMetrics = applicationContext.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val overlayWidth = flutterView.width
        val width = screenWidth - overlayWidth
        val snapX = Overlay.getSnapX(params.x, width)

        // Animate to snap position
        animateOverlayToPosition(params, snapX, params.y)
    }
    
    private fun animateOverlayToPosition(params: LayoutParams, destX: Int, destY: Int) {   
        val startX = params.x
        val startY = params.y
        val distance = hypot((destX - startX).toDouble(), (destY - startY).toDouble())
        
        val speed = 3.0 // Pixels per millisecond (adjust as needed)
        val duration = (distance / speed).toLong().coerceAtLeast(100) // Ensure a minimum duration
        val frameRate = 16L // ~60 FPS
        val totalFrames = (duration / frameRate).toInt()
        
        if (totalFrames == 0) {
            params.x = destX
            params.y = destY
            windowManager?.updateViewLayout(flutterView, params)
            return
        }
        
        val deltaX = (destX - startX) / totalFrames.toFloat()
        val deltaY = (destY - startY) / totalFrames.toFloat()
        
        var currentFrame = 0
        val animationRunnable = object : Runnable {
            override fun run() {
                if (currentFrame < totalFrames) {
                    params.x = (startX + deltaX * currentFrame).toInt()
                    params.y = (startY + deltaY * currentFrame).toInt()
                    windowManager?.updateViewLayout(flutterView, params)
                    currentFrame++
                    handler.postDelayed(this, frameRate)
                } else {
                    // Ensure final position is set
                    params.x = destX
                    params.y = destY
                    windowManager?.updateViewLayout(flutterView, params)
                }
            }
        }
        handler.post(animationRunnable)
    }
}