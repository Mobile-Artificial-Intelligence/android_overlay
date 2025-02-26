package com.danemadsen.android_overlay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import androidx.annotation.RequiresApi
import io.flutter.FlutterInjector
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.embedding.engine.FlutterEngineGroup
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BasicMessageChannel
import io.flutter.plugin.common.JSONMessageCodec
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener


class AndroidOverlayPlugin : FlutterPlugin, MethodCallHandler, ActivityAware,
    BasicMessageChannel.MessageHandler<Any?>, PluginRegistry.ActivityResultListener {
    private var channel: MethodChannel? = null
    private var context: Context? = null
    private var activity: Activity? = null
    private var messageChannel: BasicMessageChannel<Any?>? = null
    private var pendingResult: Result? = null

    companion object {
        const val OVERLAY_CHANNEL_NAME = "android_overlay"
        const val OVERLAY_MESSAGE_CHANNEL_NAME = "android_overlay_msg"
        const val CACHE_ENGINE_ID = "android_overlay_engine_id"
        const val ANDROID_OVERLAY_ENTRY_BY_DEFAULT = "androidOverlay"
        const val PERMISSION_CODE = 1996
    }

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, OVERLAY_CHANNEL_NAME)
        channel?.setMethodCallHandler(this)
        this.context = flutterPluginBinding.applicationContext
        messageChannel = BasicMessageChannel<Any?>(
            flutterPluginBinding.binaryMessenger,
            OVERLAY_MESSAGE_CHANNEL_NAME,
            JSONMessageCodec.INSTANCE
        )
        messageChannel?.setMessageHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "requestPermission" -> requestOverlayPermission(result)
            "checkPermission" -> result.success(checkPermission())
            "showOverlay" -> showOverlay(call, result)
            "closeOverlay" -> closeOverlay(result)
            "backToApp" -> backToApp(result)
            "isActive" -> result.success(AndroidOverlayService.isActive)
            "getOverlayPosition" -> getOverlayPosition(result)
            "updateOverlay" -> updateOverlay(call, result)
            else -> result.notImplemented()
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel?.setMethodCallHandler(null)
        messageChannel?.setMessageHandler(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addActivityResultListener(this)
    }

    private fun requestOverlayPermission(result: Result) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (pendingResult != null) {
                println("[AndroidOverlay] A permission request is already in progress.")
                result.error("ERROR", "A permission request is already in progress.", null)
                return
            }

            pendingResult = result
            val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            i.data = Uri.parse("package:${activity?.packageName}")
            activity?.startActivityForResult(i, PERMISSION_CODE)
        } else {
            result.success(true)
        }
    }

    private fun checkPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else true
    }

    private fun showOverlay(call: MethodCall, result: Result) {
        val i = Intent(context, AndroidOverlayService::class.java)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK and Intent.FLAG_ACTIVITY_SINGLE_TOP
        Overlay.x = call.argument<Double>("x")?.toFloat() ?: Overlay.x
        Overlay.y = call.argument<Double>("y")?.toFloat() ?: Overlay.y
        Overlay.width = call.argument<Int>("width") ?: Overlay.width
        Overlay.height = call.argument<Int>("height") ?: Overlay.height
        Overlay.alignment = call.argument<Int>("alignment") ?: Overlay.alignment
        Overlay.draggable = call.argument<Boolean>("draggable") ?: Overlay.draggable
        Overlay.snapping = call.argument<Boolean>("snapping") ?: Overlay.snapping
        Overlay.entryPoint = call.argument<String>("entryPoint") ?: ANDROID_OVERLAY_ENTRY_BY_DEFAULT

        // Initialize and cache the FlutterEngine before starting the service
        initializeAndCacheFlutterEngine()

        if (activity == null) {
            context?.applicationContext?.startService(i)
        } else {
            activity?.startService(i)
        }

        result.success(true)
    }

    private fun initializeAndCacheFlutterEngine() {
        val cachedEngine = FlutterEngineCache.getInstance().get(CACHE_ENGINE_ID)
        if (cachedEngine == null) {
            println("[AndroidOverlayPlugin] Creating and caching FlutterEngine.")
            val engineGroup = FlutterEngineGroup(context!!)
            val dartEntryPoint = DartExecutor.DartEntrypoint(
                FlutterInjector.instance().flutterLoader().findAppBundlePath(),
                Overlay.entryPoint
            )
            val flutterEngine = engineGroup.createAndRunEngine(context!!, dartEntryPoint)
            FlutterEngineCache.getInstance().put(CACHE_ENGINE_ID, flutterEngine)
        } else {
            println("[AndroidOverlayPlugin] FlutterEngine already cached.")
        }
    }

    private fun closeOverlay(result: Result) {
        if (AndroidOverlayService.isActive) {
            val i = Intent(context, AndroidOverlayService::class.java)
            i.putExtra("closeOverlay", true)
            context?.stopService(i)
            AndroidOverlayService.isActive = false
            result.success(true)
            return
        }
        result.success(true)
    }

    private fun backToApp(result: Result) {
        val packageManager = context?.packageManager
        val intent = packageManager?.getLaunchIntentForPackage(context?.packageName!!)
        
        if (intent != null) {
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK
            context?.startActivity(intent)
            result.success(true)
        } else {
            result.error("ERROR", "Could not find launch intent for package", null)
        }
    }

    override fun onMessage(message: Any?, reply: BasicMessageChannel.Reply<Any?>) {
        val engine = FlutterEngineCache.getInstance().get(CACHE_ENGINE_ID)
        if (engine == null) {
            println("[AndroidOverlayPlugin] FlutterEngineCache returned null for CACHE_ENGINE_ID")
            reply.reply(null) // Respond to the Dart side with an error or null
            return
        }
        val overlayMessageChannel = BasicMessageChannel(
            engine.dartExecutor,
            OVERLAY_MESSAGE_CHANNEL_NAME,
            JSONMessageCodec.INSTANCE
        )
        overlayMessageChannel.send(message, reply)
    }

    private fun updateOverlay(call: MethodCall, result: Result) {
        if (AndroidOverlayService.windowManager != null) {
            val params = AndroidOverlayService.flutterView.layoutParams as WindowManager.LayoutParams

            Overlay.x = call.argument<Double>("x")?.toFloat() ?: params.x.toFloat()
            Overlay.y = call.argument<Double>("y")?.toFloat() ?: params.y.toFloat()
            Overlay.width = call.argument<Int>("width") ?: Overlay.width
            Overlay.height = call.argument<Int>("height") ?: Overlay.height
            Overlay.draggable = call.argument<Boolean>("draggable") ?: Overlay.draggable
            Overlay.snapping = call.argument<Boolean>("snapping") ?: Overlay.snapping

            params.width = Overlay.width
            params.height = Overlay.height
            params.x = Overlay.x.toInt()
            params.y = Overlay.y.toInt()
            AndroidOverlayService.windowManager!!.updateViewLayout(
                AndroidOverlayService.flutterView, params
            )
            result.success(true)
        } else result.notImplemented()
    }

    private fun getOverlayPosition(result: Result) {
        if (Overlay.draggable) {
            val params = AndroidOverlayService.flutterView.layoutParams as WindowManager.LayoutParams
            result.success(
                mapOf(
                    "overlayPosition" to mapOf(
                        "x" to params.x,
                        "y" to params.y
                    )
                )
            )
        } else {
            result.success(null)
        }
    }

    override fun onDetachedFromActivityForConfigChanges() {
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        if (AndroidOverlayService.windowManager != null) {
            val windowConfig = AndroidOverlayService.flutterView.layoutParams
            windowConfig.width = Overlay.width
            windowConfig.height = Overlay.height
            AndroidOverlayService.windowManager!!.updateViewLayout(
                AndroidOverlayService.flutterView,
                windowConfig
            )
        }
    }

    override fun onDetachedFromActivity() {
        activity = null
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode == PERMISSION_CODE) {
            pendingResult?.let {
                it.success(Settings.canDrawOverlays(activity))
                pendingResult = null
            }
            return true
        }

        pendingResult?.let {
            it.success(false)
            pendingResult = null
        }
        return false
    }
}
