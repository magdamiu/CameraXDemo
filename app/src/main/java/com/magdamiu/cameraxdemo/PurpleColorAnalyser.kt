package com.magdamiu.cameraxdemo

import android.util.Log
import android.widget.TextView
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

class PurpleColorAnalyser(val onScreenTextView: TextView) : ImageAnalysis.Analyzer {
        private var lastAnalyzedTimestamp = 0L

        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()
            val data = ByteArray(remaining())
            get(data)
            return data
        }

        override fun analyze(image: ImageProxy) {
            val currentTimestamp = System.currentTimeMillis()
            val oneSecond = TimeUnit.SECONDS.toMillis(1)
            if (currentTimestamp - lastAnalyzedTimestamp >= oneSecond) {
                val buffer = image.planes[0].buffer
                val data = buffer.toByteArray()
                val pixels = data.map { it.toInt() and 0x9370DB }
                val averagePurplePixels = pixels.average()
                onScreenTextView.text = "Average purple pixels: $averagePurplePixels"
                Log.e("PURPLE", "Average purple pixels: $averagePurplePixels")
                lastAnalyzedTimestamp = currentTimestamp
            }
            image.close()
        }
    }