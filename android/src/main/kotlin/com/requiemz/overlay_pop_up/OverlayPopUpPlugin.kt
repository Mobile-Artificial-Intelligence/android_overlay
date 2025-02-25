package com.requiemz.overlay_pop_up

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


class OverlayPopUpPlugin : FlutterPlugin, MethodCallHandler, ActivityAware,
    BasicMessageChannel.MessageHandler<Any?>, PluginRegistry.ActivityResultListener {
    private var channel: MethodChannel? = null
    private var context: Context? = null
    private var activity: Activity? = null
    private var messageChannel: BasicMessageChannel<Any?>? = null
    private var pendingResult: Result? = null

    companion object {
        const val OVERLAY_CHANNEL_NAME = "overlay_pop_up"
        const val OVERLAY_MESSAGE_CHANNEL_NAME = "overlay_pop_up_mssg"
        const val CACHE_ENGINE_ID = "overlay_pop_up_engine_id"
        const val OVERLAY_POP_UP_ENTRY_BY_DEFAULT = "overlayPopUp"
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
            "isActive" -> result.success(OverlayService.isActive)
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
                println("[OverlayPopUp] A permission request is already in progress.")
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
        val i = Intent(context, OverlayService::class.java)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK and Intent.FLAG_ACTIVITY_SINGLE_TOP
        Overlay.width = call.argument<Int>("width") ?: Overlay.width
        Overlay.height = call.argument<Int>("height") ?: Overlay.height
        Overlay.alignment = call.argument<Int>("alignment") ?: Overlay.alignment
        Overlay.backgroundBehavior = call.argument<Int>("backgroundBehavior") ?: Overlay.backgroundBehavior
        Overlay.screenOrientation = call.argument<Int>("screenOrientation") ?: Overlay.screenOrientation
        Overlay.closeWhenTapBackButton = call.argument<Boolean>("closeWhenTapBackButton") ?: Overlay.closeWhenTapBackButton
        Overlay.draggable = call.argument<Boolean>("draggable") ?: Overlay.draggable
        Overlay.snapping = call.argument<Boolean>("snapping") ?: Overlay.snapping
        Overlay.entryPointMethodName = call.argument<String>("entryPointMethodName") ?: OVERLAY_POP_UP_ENTRY_BY_DEFAULT

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
            println("[OverlayPopUpPlugin] Creating and caching FlutterEngine.")
            val engineGroup = FlutterEngineGroup(context!!)
            val dartEntryPoint = DartExecutor.DartEntrypoint(
                FlutterInjector.instance().flutterLoader().findAppBundlePath(),
                Overlay.entryPointMethodName
            )
            val flutterEngine = engineGroup.createAndRunEngine(context!!, dartEntryPoint)
            FlutterEngineCache.getInstance().put(CACHE_ENGINE_ID, flutterEngine)
        } else {
            println("[OverlayPopUpPlugin] FlutterEngine already cached.")
        }
    }

    private fun closeOverlay(result: Result) {
        if (OverlayService.isActive) {
            val i = Intent(context, OverlayService::class.java)
            i.putExtra("closeOverlay", true)
            context?.stopService(i)
            OverlayService.isActive = false
            result.success(true)
            return
        }
        result.success(true)
    }

    override fun onMessage(message: Any?, reply: BasicMessageChannel.Reply<Any?>) {
        val engine = FlutterEngineCache.getInstance().get(CACHE_ENGINE_ID)
        if (engine == null) {
            println("[OverlayPopUpPlugin] FlutterEngineCache returned null for CACHE_ENGINE_ID")
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
        if (OverlayService.windowManager != null) {
            Overlay.width = call.argument<Int>("width") ?: Overlay.width
            Overlay.height = call.argument<Int>("height") ?: Overlay.height
            Overlay.x = call.argument<Float>("x") ?: Overlay.x
            Overlay.y = call.argument<Float>("y") ?: Overlay.y
            Overlay.draggable = call.argument<Boolean>("draggable") ?: Overlay.draggable
            Overlay.snapping = call.argument<Boolean>("snapping") ?: Overlay.snapping

            val params = OverlayService.flutterView.layoutParams as WindowManager.LayoutParams
            params.width = Overlay.width
            params.height = Overlay.height
            params.x = Overlay.x.toInt()
            params.y = Overlay.y.toInt()
            OverlayService.windowManager!!.updateViewLayout(
                OverlayService.flutterView, params
            )
            result.success(true)
        } else result.notImplemented()
    }

    private fun getOverlayPosition(result: Result) {
        if (Overlay.draggable) {
            result.success(
                mapOf(
                    "overlayPosition" to mapOf(
                        "x" to Overlay.x,
                        "y" to Overlay.y
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
        if (OverlayService.windowManager != null) {
            val windowConfig = OverlayService.flutterView.layoutParams
            windowConfig.width = Overlay.width
            windowConfig.height = Overlay.height
            OverlayService.windowManager!!.updateViewLayout(
                OverlayService.flutterView,
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
