package io.livekit.flutter

import org.webrtc.VideoFrame
import java.nio.ByteBuffer

object I420ToNV21 {
  fun convert(i420: VideoFrame.I420Buffer): ByteArray {
    val width = i420.width
    val height = i420.height
    val ySize = width * height
    val uvSize = (width * height) / 4
    val out = ByteArray(ySize + uvSize * 2)

    val yBuf = i420.dataY
    val uBuf = i420.dataU
    val vBuf = i420.dataV

    val yStride = i420.strideY
    val uStride = i420.strideU
    val vStride = i420.strideV

    var pos = 0
    for (row in 0 until height) {
      yBuf.position(row * yStride)
      yBuf.get(out, pos, width)
      pos += width
    }

    for (row in 0 until height / 2) {
      for (col in 0 until width / 2) {
        out[pos++] = vBuf.get(row * vStride + col)
        out[pos++] = uBuf.get(row * uStride + col)
      }
    }

    return out
  }
}
