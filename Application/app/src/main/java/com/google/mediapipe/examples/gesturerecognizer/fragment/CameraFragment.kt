/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.mediapipe.examples.gesturerecognizer.fragment

import android.annotation.SuppressLint
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import android.content.res.Configuration
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.mediapipe.examples.gesturerecognizer.GestureRecognizerHelper
import com.google.mediapipe.examples.gesturerecognizer.R
import com.google.mediapipe.examples.gesturerecognizer.databinding.FragmentCameraBinding
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CameraFragment : Fragment(),
    GestureRecognizerHelper.GestureRecognizerListener {

    companion object {
        private const val TAG = "Hand gesture recognizer"
    }

    private var _fragmentCameraBinding: FragmentCameraBinding? = null

    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!

    private lateinit var gestureRecognizerHelper: GestureRecognizerHelper
    private var defaultNumResults = 1
    private val gestureRecognizerResultAdapter: GestureRecognizerResultsAdapter by lazy {
        GestureRecognizerResultsAdapter(requireContext()).apply {
            updateAdapterSize(defaultNumResults)
        }
    }
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraFacing = CameraSelector.LENS_FACING_FRONT

    /** Blocking ML operations are performed using this executor */
    private lateinit var backgroundExecutor: ExecutorService

    // Parameters
    private var minHandDetectionConfidence = GestureRecognizerHelper.DEFAULT_HAND_DETECTION_CONFIDENCE
    private var minHandTrackingConfidence = GestureRecognizerHelper.DEFAULT_HAND_TRACKING_CONFIDENCE
    private var minHandPresenceConfidence = GestureRecognizerHelper.DEFAULT_HAND_PRESENCE_CONFIDENCE
    private var minGestureConfidence = GestureRecognizerHelper.DEFAULT_MIN_GESTURE_CONFIDENCE
    private var minFramesConfidence = GestureRecognizerHelper.DEFAULT_MIN_FRAMES_CONFIDENCE
    private var currentDelegate = GestureRecognizerHelper.DELEGATE_CPU
    private var currentLanguage = GestureRecognizerHelper.LANGUAGE_ENGLISH

    // Condition for downloading
    private val conditions = DownloadConditions.Builder()
        .build()

    // Languages
    private val languageArray = arrayOf("en",
        "hi", "mr", "brx", "doi", "gu", "as", "kn", "ks",
        "kok", "mai", "ml", "mni", "bn", "ne", "or", "pa",
        "sa", "sat", "sd", "ta", "te", "ur"
    )
    private val fullLanguageArray = arrayOf("English",
        "Hindi", "Marathi", "Bodo", "Dogri", "Gujarati", "Assamese",
        "Kannada", "Kashmiri", "Konkani", "Maithili", "Malayalam",
        "Manipuri", "Bengali", "Nepali", "Odia", "Punjabi", "Sanskrit",
        "Santali", "Sindhi", "Tamil", "Telugu", "Urdu"
    )

    // Translators
    private val translatorArray = Array<Translator?>(languageArray.size) {null}

    // Preferences
    private var sharedPreferences : SharedPreferences? = null
    private var editor : Editor? = null

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(
                requireActivity(), R.id.fragment_container
            ).navigate(R.id.action_camera_to_permissions)
        }

        // Start the GestureRecognizerHelper again when users come back
        // to the foreground.
        backgroundExecutor.execute {
            if (gestureRecognizerHelper.isClosed()) {
                gestureRecognizerHelper.setupGestureRecognizer()
            }
        }
    }

    override fun onPause() = super.onPause()

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()

        // Shut down our background executor
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(
            Long.MAX_VALUE, TimeUnit.NANOSECONDS
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding =
            FragmentCameraBinding.inflate(inflater, container, false)

        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(fragmentCameraBinding.recyclerviewResults) {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = gestureRecognizerResultAdapter
        }

        // Initialize our background executor
        backgroundExecutor = Executors.newSingleThreadExecutor()

        // Wait for the views to be properly laid out
        fragmentCameraBinding.viewFinder.post {
            // Set up the camera and its use cases
            setUpCamera()
        }
        sharedPreferences = requireContext().getSharedPreferences("KshamPreference", MODE_PRIVATE)
        editor = sharedPreferences!!.edit()
        getLocalParam()

        // Create the Hand Gesture Recognition Helper that will handle the
        // inference
        backgroundExecutor.execute {
            gestureRecognizerHelper = GestureRecognizerHelper(
                context = requireContext(),
                runningMode = RunningMode.LIVE_STREAM,
                minHandDetectionConfidence = minHandDetectionConfidence,
                minHandTrackingConfidence = minHandTrackingConfidence,
                minHandPresenceConfidence = minHandPresenceConfidence,
                currentDelegate = currentDelegate,
                gestureRecognizerListener = this
            )
        }

        updateVariables()

        for (i in translatorArray.indices)
            if(i!=0)
                translatorArray[i] = createTranslator(languageArray[i])

        downloadTranslator(1)
        downloadTranslator(2)

        // Attach listeners to UI control widgets
        initBottomSheetControls()

        //How to use
        fragmentCameraBinding.buttonHtu.setOnClickListener{ // Handle the "HTU" button click here
            fragmentCameraBinding.viewFinder.visibility = View.INVISIBLE
            fragmentCameraBinding.buttons.visibility = View.INVISIBLE
            fragmentCameraBinding.overlay.visibility = View.INVISIBLE
            fragmentCameraBinding.recyclerviewResults.visibility = View.INVISIBLE
            fragmentCameraBinding.upArrow.visibility = View.INVISIBLE

            fragmentCameraBinding.htuPage.visibility = View.VISIBLE
        }

        fragmentCameraBinding.closeButton.setOnClickListener{ // Handle the "close HTU" button click here}
            fragmentCameraBinding.viewFinder.visibility = View.VISIBLE
            fragmentCameraBinding.buttons.visibility = View.VISIBLE
            fragmentCameraBinding.overlay.visibility = View.VISIBLE
            fragmentCameraBinding.recyclerviewResults.visibility = View.VISIBLE
            fragmentCameraBinding.upArrow.visibility = View.VISIBLE

            fragmentCameraBinding.htuPage.visibility = View.INVISIBLE
        }

            //Add btns clicks
        fragmentCameraBinding.buttonClear.setOnClickListener{ // Handle the "Clear" button click here
            gestureRecognizerResultAdapter.corrected = ""
            gestureRecognizerResultAdapter.total = ""
        }

        fragmentCameraBinding.buttonSpeak.setOnClickListener{ // Handle the "Speak" button click here
            if(currentLanguage == 0){
                gestureRecognizerResultAdapter.t1?.setLanguage(Locale.ENGLISH)
                gestureRecognizerResultAdapter.t1?.speak(gestureRecognizerResultAdapter.corrected, TextToSpeech.QUEUE_FLUSH, null);
            }
            else{
                translatorArray[currentLanguage]!!.translate(gestureRecognizerResultAdapter.corrected)
                    .addOnSuccessListener { translatedText ->
                        gestureRecognizerResultAdapter.t1?.setLanguage(Locale(languageArray[1], "IN"))
                        gestureRecognizerResultAdapter.t1?.speak(translatedText, TextToSpeech.QUEUE_FLUSH, null);
                    }
                    .addOnFailureListener { _ ->
                        // Error.
                        // ...
                    }
            }
        }
    }

    private fun initBottomSheetControls() {
        setToUi()

        // When clicked, lower hand detection score threshold floor
        fragmentCameraBinding.bottomSheetLayout.detectionThresholdMinus.setOnClickListener {
            if (minHandDetectionConfidence >= 0.2) {
                minHandDetectionConfidence -= 0.1f
                updateControlsUi()
            }
        }

        // When clicked, raise hand detection score threshold floor
        fragmentCameraBinding.bottomSheetLayout.detectionThresholdPlus.setOnClickListener {
            if (minHandDetectionConfidence <= 0.8) {
                minHandDetectionConfidence += 0.1f
                updateControlsUi()
            }
        }

        // When clicked, lower hand tracking score threshold floor
        fragmentCameraBinding.bottomSheetLayout.trackingThresholdMinus.setOnClickListener {
            if (minHandTrackingConfidence >= 0.2) {
                minHandTrackingConfidence -= 0.1f
                updateControlsUi()
            }
        }

        // When clicked, raise hand tracking score threshold floor
        fragmentCameraBinding.bottomSheetLayout.trackingThresholdPlus.setOnClickListener {
            if (minHandTrackingConfidence <= 0.8) {
                minHandTrackingConfidence += 0.1f
                updateControlsUi()
            }
        }

        // When clicked, lower hand presence score threshold floor
        fragmentCameraBinding.bottomSheetLayout.presenceThresholdMinus.setOnClickListener {
            if (minHandPresenceConfidence >= 0.2) {
                minHandPresenceConfidence -= 0.1f
                updateControlsUi()
            }
        }

        // When clicked, raise hand presence score threshold floor
        fragmentCameraBinding.bottomSheetLayout.presenceThresholdPlus.setOnClickListener {
            if (minHandPresenceConfidence <= 0.8) {
                minHandPresenceConfidence += 0.1f
                updateControlsUi()
            }
        }

        // When clicked, lower hand presence score threshold floor
        fragmentCameraBinding.bottomSheetLayout.gestureThresholdMinus.setOnClickListener {
            if (minGestureConfidence >= 0.5) {
                minGestureConfidence -= 0.05f
                updateControlsUi()
            }
        }

        // When clicked, raise hand presence score threshold floor
        fragmentCameraBinding.bottomSheetLayout.gestureThresholdPlus.setOnClickListener {
            if (minGestureConfidence <= 1) {
                minGestureConfidence += 0.05f
                updateControlsUi()
            }
        }

        // When clicked, lower hand presence score threshold floor
        fragmentCameraBinding.bottomSheetLayout.framesThresholdMinus.setOnClickListener {
            if (minFramesConfidence >= 5) {
                minFramesConfidence -= 2
                updateControlsUi()
            }
        }

        // When clicked, raise hand presence score threshold floor
        fragmentCameraBinding.bottomSheetLayout.framesThresholdPlus.setOnClickListener {
            if (minFramesConfidence <= 30) {
                minFramesConfidence += 2
                updateControlsUi()
            }
        }

        fragmentCameraBinding.bottomSheetLayout.spinnerLanguage.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long
                ) {
                    try {
                        currentLanguage = p2
                        if(p2!=0)
                            downloadTranslator(currentLanguage)
                        updateControlsUi()
                    } catch(e: UninitializedPropertyAccessException) {
                        Log.e(TAG, "GestureRecognizerHelper has not been initialized yet.")

                    }
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                    /* no op */
                }
            }

        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long
                ) {
                    try {
                        currentDelegate = p2
                        updateControlsUi()
                    } catch(e: UninitializedPropertyAccessException) {
                        Log.e(TAG, "GestureRecognizerHelper has not been initialized yet.")

                    }
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                    /* no op */
                }
            }
    }

    // Update the values displayed in the bottom sheet. Reset recognition
    // helper.
    private fun updateControlsUi() {
        setToUi()
        updateVariables()
        storeLocalParam()
        // Needs to be cleared instead of reinitialized because the GPU
        // delegate needs to be initialized on the thread using it when applicable
        backgroundExecutor.execute {
            gestureRecognizerHelper.clearGestureRecognizer()
            gestureRecognizerHelper.setupGestureRecognizer()
        }
        fragmentCameraBinding.overlay.clear()
    }

    private fun setToUi() {
        fragmentCameraBinding.bottomSheetLayout.detectionThresholdValue.text =
            String.format(
                Locale.US,
                "%.2f",
                minHandDetectionConfidence
            )
        fragmentCameraBinding.bottomSheetLayout.trackingThresholdValue.text =
            String.format(
                Locale.US,
                "%.2f",
                minHandTrackingConfidence
            )
        fragmentCameraBinding.bottomSheetLayout.presenceThresholdValue.text =
            String.format(
                Locale.US,
                "%.2f",
                minHandPresenceConfidence
            )
        fragmentCameraBinding.bottomSheetLayout.gestureThresholdValue.text =
            String.format(
                Locale.US,
                "%.2f",
                minGestureConfidence
            )
        fragmentCameraBinding.bottomSheetLayout.framesThresholdValue.text =
            String.format(
                Locale.US,
                "%d",
                minFramesConfidence
            )
        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(currentDelegate, false)
        fragmentCameraBinding.bottomSheetLayout.spinnerLanguage.setSelection(currentLanguage, false)
    }

    private fun updateVariables() {
        gestureRecognizerResultAdapter.minGestureConfidence = minGestureConfidence
        gestureRecognizerResultAdapter.minFramesConfidence = minFramesConfidence

        if(this::gestureRecognizerHelper.isInitialized) {
            gestureRecognizerHelper.minHandDetectionConfidence = minHandDetectionConfidence
            gestureRecognizerHelper.minHandPresenceConfidence = minHandPresenceConfidence
            gestureRecognizerHelper.minHandTrackingConfidence = minHandTrackingConfidence
            gestureRecognizerHelper.currentDelegate = currentDelegate
        }
    }

    private fun createTranslator(lang: String): Translator {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(lang)
            .build()
        val translator = Translation.getClient(options)
        return translator
    }

    private fun downloadTranslator(which: Int){
        (translatorArray[which])!!.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                Toast.makeText(context, "Downloaded " + fullLanguageArray[which], Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { _ ->
                Toast.makeText(context, "Failed Downloaded " + fullLanguageArray[which], Toast.LENGTH_SHORT).show()
            }
    }

    private fun storeLocalParam() {
        editor!!.putFloat("minHandDetectionConfidence", minHandDetectionConfidence)
            .putFloat("minHandTrackingConfidence", minHandTrackingConfidence)
            .putFloat("minHandPresenceConfidence", minHandPresenceConfidence)
            .putFloat("minGestureConfidence", minGestureConfidence)
            .putInt("minFramesConfidence", minFramesConfidence)
            .putInt("currentDelegate", currentDelegate)
            .putInt("currentLanguage", currentLanguage)

        editor!!.apply()
    }

    private fun getLocalParam() {
        minHandDetectionConfidence = sharedPreferences!!.getFloat("minHandDetectionConfidence", minHandDetectionConfidence)
        minHandTrackingConfidence = sharedPreferences!!.getFloat("minHandTrackingConfidence", minHandTrackingConfidence)
        minHandPresenceConfidence = sharedPreferences!!.getFloat("minHandPresenceConfidence", minHandPresenceConfidence)
        minGestureConfidence = sharedPreferences!!.getFloat("minGestureConfidence", minGestureConfidence)
        minFramesConfidence = sharedPreferences!!.getInt("minFramesConfidence", minFramesConfidence)
        currentDelegate = sharedPreferences!!.getInt("currentDelegate", currentDelegate)
        currentLanguage = sharedPreferences!!.getInt("currentLanguage", currentLanguage)
    }

    // Initialize CameraX, and prepare to bind the camera use cases
    private fun setUpCamera() {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // Build and bind the camera use cases
                bindCameraUseCases()
            }, ContextCompat.getMainExecutor(requireContext())
        )
    }

    // Declare and bind preview, capture and analysis use cases
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {

        // CameraProvider
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(cameraFacing).build()

        // Preview. Only using the 4:3 ratio because this is the closest to our models
        preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
            .build()

        // ImageAnalysis. Using RGBA 8888 to match how our models work
        imageAnalyzer =
            ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                // The analyzer can then be assigned to the instance
                .also {
                    it.setAnalyzer(backgroundExecutor) { image ->
                        recognizeHand(image)
                    }
                }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalyzer
            )

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun recognizeHand(imageProxy: ImageProxy) {
        gestureRecognizerHelper.recognizeLiveStream(
            imageProxy = imageProxy,
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation =
            fragmentCameraBinding.viewFinder.display.rotation
    }

    // Update UI after a hand gesture has been recognized. Extracts original
    // image height/width to scale and place the landmarks properly through
    // OverlayView. Only one result is expected at a time. If two or more
    // hands are seen in the camera frame, only one will be processed.
    override fun onResults(
        resultBundle: GestureRecognizerHelper.ResultBundle
    ) {
        activity?.runOnUiThread {
            if (_fragmentCameraBinding != null) {
                // Show result of recognized gesture
                val gestureCategories = resultBundle.results.first().gestures()
                if (gestureCategories.isNotEmpty()) {
                    gestureRecognizerResultAdapter.updateResults(
                        gestureCategories.first()
                    )
                } else {
                    gestureRecognizerResultAdapter.updateResults(emptyList())
                }
                fragmentCameraBinding.bottomSheetLayout.inferenceTimeVal.text =
                    String.format("%d ms", resultBundle.inferenceTime)

                // Pass necessary information to OverlayView for drawing on the canvas
                fragmentCameraBinding.overlay.setResults(
                    resultBundle.results.first(),
                    resultBundle.inputImageHeight,
                    resultBundle.inputImageWidth,
                    RunningMode.LIVE_STREAM
                )

                // Force a redraw
                fragmentCameraBinding.overlay.invalidate()
            }
        }
    }

    override fun onError(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            gestureRecognizerResultAdapter.updateResults(emptyList())

            if (errorCode == GestureRecognizerHelper.GPU_ERROR) {
                fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(
                    GestureRecognizerHelper.DELEGATE_CPU, false
                )
            }
        }
    }
}
