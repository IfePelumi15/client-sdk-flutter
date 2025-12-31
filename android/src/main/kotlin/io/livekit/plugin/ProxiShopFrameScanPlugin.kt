package io.livekit.plugin

import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import org.webrtc.VideoFrame
import java.nio.ByteBuffer

class ProxiShopFrameScanPlugin : FlutterPlugin, MethodChannel.MethodCallHandler {

    private lateinit var channel: MethodChannel

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(binding.binaryMessenger, "proxishop/frame_scan")
        channel.setMethodCallHandler(this)
        LiveKitFrameScanRegistry.register(::onFrame)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        LiveKitFrameScanRegistry.unregister()
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        result.success(null)
    }

    // 🔥 Called for every LiveKit video frame
    private fun onFrame(frame: VideoFrame) {
        val buffer = frame.buffer.toI420()
        if (buffer == null) return

        try {
            val width = buffer.width
            val height = buffer.height

            val yBuffer: ByteBuffer = buffer.dataY
            val uBuffer: ByteBuffer = buffer.dataU
            val vBuffer: ByteBuffer = buffer.dataV

            val nv21 = I420ToNV21.convert(
                yBuffer, uBuffer, vBuffer,
                buffer.strideY, buffer.strideU, buffer.strideV,
                width, height
            )

            val image = InputImage.fromByteArray(
                nv21,
                width,
                height,
                0,
                InputImage.IMAGE_FORMAT_NV21
            )

            val scanner = BarcodeScanning.getClient()
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (b in barcodes) {
                        val payload = mapOf(
                            "barcode" to b.rawValue,
                            "format" to b.format,
                            "confidence" to 1.0
                        )
                        channel.invokeMethod("barcode", payload)
                    }
                }
        } catch (e: Exception) {
            Log.e("ProxiShopScan", "Scan failed", e)
        } finally {
            buffer.release()
        }
    }
}
