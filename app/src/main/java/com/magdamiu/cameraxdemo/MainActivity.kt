package com.magdamiu.cameraxdemo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.CameraSelector.LensFacing
import androidx.camera.extensions.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.util.concurrent.Executor
import kotlin.reflect.KClass


class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var helperTextView: TextView
    private lateinit var cameraSelectorSwitch: Switch
    private lateinit var extensionsSpinner: Spinner

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraExecutor: Executor
    private lateinit var preview: Preview
    private lateinit var camera: Camera
    private lateinit var cameraSelector: CameraSelector
    private lateinit var imageCapture: ImageCapture
    private lateinit var imageAnalysis: ImageAnalysis
    private var useCases: MutableList<UseCase> = mutableListOf<UseCase>()
    private val pairsOfImageCaptureExtenders = listOf(
        "Auto" to AutoImageCaptureExtender::class,
        "Beauty" to BeautyImageCaptureExtender::class,
        "Bokeh" to BokehImageCaptureExtender::class,
        "HDR" to HdrImageCaptureExtender::class,
        "Night" to NightImageCaptureExtender::class
    )
    private var extensionsAvailable = pairsOfImageCaptureExtenders.map { it.first }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()

        setupCameraSelector(CameraSelector.LENS_FACING_BACK)

        switchCameraSelector()

        extensionsSpinnerSelection()

        if (areAllPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, PERMISSIONS, CAMERA_REQUEST_PERMISSION_CODE
            )
        }
    }

    private fun initViews() {
        previewView = findViewById(R.id.preview)
        helperTextView = findViewById(R.id.helperTextView)
        cameraSelectorSwitch = findViewById(R.id.cameraSelectorSwitch)
        extensionsSpinner = findViewById(R.id.extensionsSpinner)
        extensionsSpinner.adapter =
            ArrayAdapter(this, R.layout.support_simple_spinner_dropdown_item, extensionsAvailable)
    }

    @SuppressLint("RestrictedApi")
    private fun switchCameraSelector() {
        cameraSelectorSwitch.setOnCheckedChangeListener { view, isFrontCamera ->
            cameraProviderFuture.get().shutdown()
            useCases = mutableListOf<UseCase>()
            if (isFrontCamera) {
                cameraSelectorSwitch.text = view.context.getString(R.string.front_camera)
                setupCameraSelector(CameraSelector.LENS_FACING_FRONT)
            } else {
                cameraSelectorSwitch.text = view.context.getString(R.string.back_camera)
                setupCameraSelector(CameraSelector.LENS_FACING_BACK)
            }
            startCamera()
        }
    }

    private fun extensionsSpinnerSelection() {
        extensionsSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val currentSelectedExtension = extensionsAvailable[position]
                pairsOfImageCaptureExtenders.find { it.first == currentSelectedExtension }?.second?.let {
                    enableExtension(
                        it
                    )
                }
            }
        }
    }

    private fun startCamera() {
        fun initCamera() {
            cameraExecutor = ContextCompat.getMainExecutor(this)
            cameraProviderFuture = ProcessCameraProvider.getInstance(this);
            preview = Preview.Builder().build()
        }

        fun setupImageCapture() {
            imageCapture = ImageCapture.Builder()
                .setTargetRotation(Surface.ROTATION_0)
                .build()

            useCases.add(imageCapture)
        }

        fun setupImageAnalysis() {
            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor, PurpleColorAnalyser(helperTextView))

            useCases.add(imageAnalysis)
        }

        fun bindPreview(cameraProvider: ProcessCameraProvider) {
            camera = cameraProvider.bindToLifecycle(
                this as LifecycleOwner,
                cameraSelector,
                *useCases.toTypedArray(),
                preview
            )

            preview.setSurfaceProvider(previewView.createSurfaceProvider(camera.cameraInfo))
        }

        initCamera()

        cameraProviderFuture.addListener(Runnable {
            setupImageCapture()
            setupImageAnalysis()
            bindPreview(cameraProviderFuture.get())
        }, cameraExecutor)
    }

    private fun enableExtension(type: KClass<*>) {
        val imageCaptureExtender = getImageCaptureExtender(type)
        if (imageCaptureExtender.isExtensionAvailable(cameraSelector)) {
            imageCaptureExtender.enableExtension(cameraSelector)
            Toast.makeText(this, "${type.simpleName} enabled", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "${type.simpleName} NOT available", Toast.LENGTH_LONG).show()
        }
    }

    private fun getImageCaptureExtender(type: KClass<*>): ImageCaptureExtender {
        val builder = ImageCapture.Builder()
        return when (type) {
            AutoImageCaptureExtender::class -> AutoImageCaptureExtender.create(builder)
            BeautyImageCaptureExtender::class -> BeautyImageCaptureExtender.create(builder)
            BokehImageCaptureExtender::class -> BokehImageCaptureExtender.create(builder)
            HdrImageCaptureExtender::class -> HdrImageCaptureExtender.create(builder)
            NightImageCaptureExtender::class -> NightImageCaptureExtender.create(builder)
            else -> throw RuntimeException()
        }
    }

    private fun setupCameraSelector(@LensFacing selector: Int) {
        cameraSelector = CameraSelector.Builder()
            .requireLensFacing(selector)
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
                    Log.e("CameraXDemo", msg)
                    helperTextView.append("\n$msg")
                }

                override fun onError(exception: ImageCaptureException) {
                    val msg = "Photo capture failed: ${exception.message}"
                    Log.e("CameraXDemo", msg)
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
}
