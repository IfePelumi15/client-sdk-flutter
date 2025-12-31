package io.livekit.flutter

import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import java.util.concurrent.atomic.AtomicBoolean

class ProxiShopFrameScanPlugin : FlutterPlugin, MethodChannel.MethodCallHandler, EventChannel.StreamHandler {

  private lateinit var mc: MethodChannel
  private lateinit var ec: EventChannel
  private var sink: EventChannel.EventSink? = null

  private val enabled = AtomicBoolean(false)
  private var throttleMs: Long = 300
  private var last = 0L
  private val scanner = BarcodeScanning.getClient()

  val videoSink = VideoSink { frame: VideoFrame ->
    if (!enabled.get()) return@VideoSink
    val now = System.currentTimeMillis()
    if (now - last < throttleMs) return@VideoSink
    last = now

    try {
      val buf = frame.buffer.toI420()
      val bytes = I420ToNV21.convert(buf)
      buf.release()

      val img = InputImage.fromByteArray(
        bytes,
        frame.buffer.width,
        frame.buffer.height,
        frame.rotation,
        InputImage.IMAGE_FORMAT_NV21
      )

      scanner.process(img)
        .addOnSuccessListener {
          if (it.isNotEmpty()) sink?.success(it[0].rawValue)
        }
        .addOnFailureListener {
          Log.w("ProxiScan", it.message ?: "")
        }

    } catch (e: Exception) {
      Log.w("ProxiScan", e.message ?: "")
    }
  }

  override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    mc = MethodChannel(binding.binaryMessenger, "proxishop/livekit_frame_scan")
    ec = EventChannel(binding.binaryMessenger, "proxishop/livekit_frame_scan_events")
    mc.setMethodCallHandler(this)
    ec.setStreamHandler(this)
    LiveKitFrameScanRegistry.plugin = this
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    LiveKitFrameScanRegistry.plugin = null
  }

  override fun onMethodCall(call: MethodChannel.MethodCall, result: MethodChannel.Result) {
    when (call.method) {
      "start" -> {
        throttleMs = (call.argument<Int>("throttleMs") ?: 300).toLong()
        enabled.set(true)
        result.success(true)
      }
      "stop" -> {
        enabled.set(false)
        result.success(true)
      }
      else -> result.notImplemented()
    }
  }

  override fun onListen(args: Any?, events: EventChannel.EventSink?) {
    sink = events
  }

  override fun onCancel(args: Any?) {
    sink = null
  }
}
