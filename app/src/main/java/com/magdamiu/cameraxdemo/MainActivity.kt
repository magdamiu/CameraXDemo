package com.magdamiu.cameraxdemo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>

    private lateinit var previewView: PreviewView
    private lateinit var helperTextView: TextView

    private lateinit var imageCapture: ImageCapture
    private lateinit var imageAnalysis: ImageAnalysis
    private lateinit var cameraExecutor: Executor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cameraExecutor = ContextCompat.getMainExecutor(this)

        previewView = findViewById(R.id.preview)
        helperTextView = findViewById(R.id.helperTextView)

        if (areAllPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, PERMISSIONS, CAMERA_REQUEST_PERMISSION_CODE
            )
        }
    }

    private fun startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(Runnable {
            val cameraProvider = cameraProviderFuture.get()
            bindPreview(cameraProvider)
        }, cameraExecutor)
    }

    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        var preview: Preview = Preview.Builder()
            .build()

        var cameraSelector: CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        setupImageAnalysis()

        setupImageCapture()

        var camera = cameraProvider.bindToLifecycle(
            this as LifecycleOwner,
            cameraSelector,
            imageCapture,
            imageAnalysis,
            preview
        )

        preview.setSurfaceProvider(previewView.createSurfaceProvider(camera.cameraInfo))
    }

    private fun setupImageAnalysis() {
        imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(cameraExecutor, PurpleColorAnalyser())
    }

    private fun setupImageCapture() {
        imageCapture = ImageCapture.Builder()
            .setTargetRotation(previewView.display.rotation)
            .build()
    }

    fun captureImageOnClick(view: View) {
        val file = File(
            externalMediaDirs.first(),
            "${System.currentTimeMillis()}.jpg"
        )
        val outputFileOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        imageCapture.takePicture(outputFileOptions, cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val msg = "Photo capture succeeded: ${file.absolutePath}"
                    Log.e("CameraXApp", msg)
                    helperTextView.append("\n$msg")
                }

                override fun onError(exception: ImageCaptureException) {
                    val msg = "Photo capture failed: ${exception.message}"
                    Log.e("CameraXApp", msg)
                    helperTextView.append("\n$msg")
                }
            })
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == CAMERA_REQUEST_PERMISSION_CODE) {
            if (areAllPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun areAllPermissionsGranted() = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val CAMERA_REQUEST_PERMISSION_CODE = 10
        private val PERMISSIONS =
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    inner class PurpleColorAnalyser() : ImageAnalysis.Analyzer {
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
                var averagePurplePixels = pixels.average()
                helperTextView.text = "Average purple pixels: $averagePurplePixels"
                Log.e("PURPLE", "Average purple pixels: $averagePurplePixels")
                lastAnalyzedTimestamp = currentTimestamp
            }
            image.close()
        }
    }
}
