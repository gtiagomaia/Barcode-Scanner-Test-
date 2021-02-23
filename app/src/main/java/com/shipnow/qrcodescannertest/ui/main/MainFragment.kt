package com.shipnow.qrcodescannertest.ui.main

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.Snackbar
import com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.shipnow.qrcodescannertest.MainActivity
import com.shipnow.qrcodescannertest.databinding.MainFragmentBinding
import com.shipnow.qrcodescannertest.utils.CameraUtils
import kotlinx.android.synthetic.main.main_fragment.*
import kotlinx.coroutines.MainScope
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * https://developers.google.com/ml-kit/vision/barcode-scanning/android
 */
/** Helper type alias used for analysis use case callbacks */
typealias barcodeListener = (rawcode: String) -> Unit

class MainFragment : Fragment() {

    companion object {
        fun newInstance() = MainFragment()
        const val REQUEST_CAMERA = 3020
        const val TAG = "MainFragment"
    }

    private lateinit var viewModel: MainViewModel
    private lateinit var binding: MainFragmentBinding

    private var displayId: Int = -1
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    /** Blocking camera operations are performed using this executor */
    private lateinit var cameraExecutor: ExecutorService
    private val displayManager by lazy {
        requireContext().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }


    /**
     * We need a display listener for orientation changes that do not trigger a configuration
     * change, for example if we choose to override config change in manifest or for 180-degree
     * orientation changes.
     */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) = view?.let { view ->
            if (displayId == this@MainFragment.displayId) {
                Log.d(TAG, "Rotation changed: ${view.display.rotation}")
//                imageCapture?.targetRotation = view.display.rotation
//                imageAnalyzer?.targetRotation = view.display.rotation
            }
        } ?: Unit
    }

    /**
     * onCreateView
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return MainFragmentBinding.inflate(inflater, container, false).also {
            binding = it
        }.root
    }

    /**
     * onViewCreated
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


//        binding.cameraPreview.holder.addCallback(object : SurfaceHolder.Callback {
//            override fun surfaceCreated(holder: SurfaceHolder) {
//
//            }
//
//            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
//
//            }
//
//            override fun surfaceDestroyed(holder: SurfaceHolder) {
//
//            }
//        })

        // Initialize our background executor
        cameraExecutor = Executors.newSingleThreadExecutor()
        Snackbar
            .make(requireView(), "Start Scanning", Snackbar.LENGTH_SHORT)
            .show()

    }

    /**
     * get screen aspect ratio from preview
     */
    private val screenAspectRatio: Int
        get() {
            // Get screen metrics used to setup camera for full screen resolution
            val metrics =
                DisplayMetrics().also { binding.cameraPreview.display?.getRealMetrics(it) }
            return CameraUtils.aspectRatio(metrics.widthPixels, metrics.heightPixels)
        }



    private fun startCamera(){
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            //setup camera
            // Preview, settarget aspect ratio, rotation, and other configurations
            val previewUseCase = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
                }
            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            imageCapture = ImageCapture.Builder().build()



            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()
                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, previewUseCase)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
            //analyser
            //val imageAnalyzer = ImageAnalyzer()
            bindAnalyseUseCase()
        }, ContextCompat.getMainExecutor(requireContext()))
    }
    private val outputDirectory by lazy {
        MainActivity.getOutputDirectory(requireContext())
    }
    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time-stamped output file to hold the image
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat("QRC", Locale.getDefault()
            ).format(System.currentTimeMillis()) + ".jpg")

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions, ContextCompat.getMainExecutor(requireContext()), object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    val msg = "Photo capture succeeded: $savedUri"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            })
    }


    private fun bindAnalyseUseCase(){
//        val analisysUseCase = ImageAnalysis.Builder().build()
//        analisysUseCase.setAnalyzer(cameraExecutor, ImageAnalysis.Analyzer {
//            imageProxy ->
//
//        })

        val analisysUseCase = ImageAnalysis.Builder().build()
        analisysUseCase.setAnalyzer(cameraExecutor, ImageAnalyzer {
            binding.tvResult.text = it
        })
    }



//    private fun startCameraPreview() {
//        try {
//            // TODO
//            val cameraBkgHandler = Handler(Looper.getMainLooper())
//
//            val cameraManager = activity!!.getSystemService(Context.CAMERA_SERVICE) as CameraManager
//
//            cameraManager.cameraIdList.find {
//                val characteristics = cameraManager.getCameraCharacteristics(it)
//                val cameraDirection = characteristics.get(CameraCharacteristics.LENS_FACING)
//
//                return@find cameraDirection != null && cameraDirection == CameraCharacteristics.LENS_FACING_BACK
//            }?.let {
//                val cameraStateCallback = object : CameraDevice.StateCallback() {
//                    override fun onOpened(camera: CameraDevice) {
//                        val captureStateCallback = object : CameraCaptureSession.StateCallback() {
//                            override fun onConfigured(session: CameraCaptureSession) {
//                                val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
//                                builder.addTarget(binding.cameraPreview.holder.surface)
//                                session.setRepeatingRequest(builder.build(), null, null)
//                            }
//
//                            override fun onConfigureFailed(session: CameraCaptureSession) {
//                                // TODO
//                                Log.d(TAG, "onConfigureFailed")
//                            }
//                        }
//
//                        camera.createCaptureSession(
//                            listOf(binding.cameraPreview.holder.surface),
//                            captureStateCallback,
//                            cameraBkgHandler
//                        )
//                    }
//
//                    override fun onClosed(camera: CameraDevice) {
//                        // TODO
//                        Log.d(TAG, "onClosed")
//                    }
//
//                    override fun onDisconnected(camera: CameraDevice) {
//                        // TODO
//                        Log.d(TAG, "onDisconnected")
//                    }
//
//                    override fun onError(camera: CameraDevice, error: Int) {
//                        // TODO
//                        Log.d(TAG, "onError")
//                    }
//                }
//
//                cameraManager.openCamera(it, cameraStateCallback, cameraBkgHandler)
//                return
//            }
//
//            // TODO: - No available camera found case
//
//        } catch (e: CameraAccessException) {
//            // TODO
//            Log.e(TAG, e.message)
//        } catch (e: SecurityException) {
//            // TODO
//            Log.e(TAG, e.message)
//        }
//    }
//


    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
    }

    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_QR_CODE,
            Barcode.FORMAT_AZTEC)
        .build()
    private val barcodeScanner = BarcodeScanning.getClient() //BarcodeScanning.getClient(options)
    //using camerax

    inner class ImageAnalyzer (val listener: barcodeListener? = null) : ImageAnalysis.Analyzer {
        @Override
        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image =
                    InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                //
                // Pass image to an ML Kit Vision API
                //
                val result = barcodeScanner.process(image)
                    .addOnSuccessListener {
                        // Task completed successfully
                        onSuccess(it, listener)
                    }
                    .addOnFailureListener {
                        // Task failed with an exception
                        Snackbar
                            .make(requireView(),  "Scanning failure: ${it.message}", Snackbar.LENGTH_LONG)
                            .show()
                    }.addOnCompleteListener {
                        imageProxy.close()
                    }

            }
        }

        fun onSuccess(barcodesList: MutableList<Barcode>, listener: barcodeListener?){
            for (barcode in barcodesList) {
                val bounds = barcode.boundingBox
                val corners = barcode.cornerPoints
                val rawValue = barcode.rawValue
                val valueType = barcode.valueType
                // See API reference for complete list of supported types
 /*               when (valueType) {
                    Barcode.TYPE_WIFI -> {
                        val ssid = barcode.wifi!!.ssid
                        val password = barcode.wifi!!.password
                        val type = barcode.wifi!!.encryptionType
                    }
                    Barcode.TYPE_URL -> {
                        val title = barcode.url!!.title
                        val url = barcode.url!!.url
                    }
                }
  */
//                Snackbar
//                    .make(requireView(),  "onSuccess ${barcode.rawValue}", Snackbar.LENGTH_LONG)
//                    .show()
//               listener?.let {
//                   it(barcode.rawValue ?: "UNKNOWN")
//               }
                Log.i(TAG, "barcode.rawValue: ${barcode.rawValue}")
            }
        }
    }


    override fun onStart() {
        super.onStart()
        validatePermissionCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
       if(::cameraExecutor.isInitialized) cameraExecutor.shutdown()
    }

    private fun validatePermissionCamera(){
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(arrayOf(Manifest.permission.CAMERA), Companion.REQUEST_CAMERA)
        } else {
            // TODO: - Start live camera feed
            startCamera()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            && requestCode == Companion.REQUEST_CAMERA
        ) {
            // TODO: - Start live camera feed
            startCamera()
        } else {
            Snackbar
                .make(requireView(),  "Camera: permission denied", Snackbar.LENGTH_LONG)
                .setAction("Ask permission", View.OnClickListener {
                    validatePermissionCamera()
                })
                .show()
        }
    }











    private fun drawOverlay(
        holder: SurfaceHolder,
        heightCropPercent: Int,
        widthCropPercent: Int
    ) {
        val canvas = holder.lockCanvas()
        val bgPaint = Paint().apply {
            alpha = 140
        }
        canvas.drawPaint(bgPaint)
        val rectPaint = Paint()
        rectPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        rectPaint.style = Paint.Style.FILL
        rectPaint.color = Color.WHITE
        val outlinePaint = Paint()
        outlinePaint.style = Paint.Style.STROKE
        outlinePaint.color = Color.WHITE
        outlinePaint.strokeWidth = 4f
        val surfaceWidth = holder.surfaceFrame.width()
        val surfaceHeight = holder.surfaceFrame.height()

        val cornerRadius = 25f
        // Set rect centered in frame
        val rectTop = surfaceHeight * heightCropPercent / 2 / 100f
        val rectLeft = surfaceWidth * widthCropPercent / 2 / 100f
        val rectRight = surfaceWidth * (1 - widthCropPercent / 2 / 100f)
        val rectBottom = surfaceHeight * (1 - heightCropPercent / 2 / 100f)
        val rect = RectF(rectLeft, rectTop, rectRight, rectBottom)
        canvas.drawRoundRect(
            rect, cornerRadius, cornerRadius, rectPaint
        )
        canvas.drawRoundRect(
            rect, cornerRadius, cornerRadius, outlinePaint
        )
        val textPaint = Paint()
        textPaint.color = Color.YELLOW
        textPaint.textSize = 50F

        val overlayText = "over"
        val textBounds = Rect()
        textPaint.getTextBounds(overlayText, 0, overlayText.length, textBounds)
        val textX = (surfaceWidth - textBounds.width()) / 2f
        val textY = rectBottom + textBounds.height() + 15f // put text below rect and 15f padding
        canvas.drawText("scanning...", textX, textY, textPaint)
        holder.unlockCanvasAndPost(canvas)
    }

}