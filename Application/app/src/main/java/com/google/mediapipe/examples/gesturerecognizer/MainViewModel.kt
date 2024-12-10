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
package com.google.mediapipe.examples.gesturerecognizer

import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {
    private var _language: Int = GestureRecognizerHelper.LANGUAGE_ENGLISH
    private var _delegate: Int = GestureRecognizerHelper.DELEGATE_CPU
    private var _minHandDetectionConfidence: Float =
        GestureRecognizerHelper.DEFAULT_HAND_DETECTION_CONFIDENCE
    private var _minHandTrackingConfidence: Float = GestureRecognizerHelper
        .DEFAULT_HAND_TRACKING_CONFIDENCE
    private var _minHandPresenceConfidence: Float = GestureRecognizerHelper
        .DEFAULT_HAND_PRESENCE_CONFIDENCE
    private var _minGestureConfidence: Float = GestureRecognizerHelper
        .DEFAULT_MIN_GESTURE_CONFIDENCE
    private var _minFramesConfidence: Int = GestureRecognizerHelper
        .DEFAULT_MIN_FRAMES_CONFIDENCE
    val currentLanguage: Int get() = _language
    val currentDelegate: Int get() = _delegate
    val currentMinHandDetectionConfidence: Float
        get() =
            _minHandDetectionConfidence
    val currentMinHandTrackingConfidence: Float
        get() =
            _minHandTrackingConfidence
    val currentMinHandPresenceConfidence: Float
        get() =
            _minHandPresenceConfidence
    val currentMinGestureConfidence: Float
        get() =
            _minGestureConfidence
    val currentMinFramesConfidence: Int
        get() =
            _minFramesConfidence

    fun setLanguage(language: Int) {
        _language = language
    }

    fun setDelegate(delegate: Int) {
        _delegate = delegate
    }

    fun setMinHandDetectionConfidence(confidence: Float) {
        _minHandDetectionConfidence = confidence
    }

    fun setMinHandTrackingConfidence(confidence: Float) {
        _minHandTrackingConfidence = confidence
    }

    fun setMinHandPresenceConfidence(confidence: Float) {
        _minHandPresenceConfidence = confidence
    }

    fun setMinGestureConfidence(confidence: Float) {
        _minGestureConfidence = confidence
    }

    fun setMinFramesConfidence(confidence: Int) {
        _minFramesConfidence = confidence
    }
}
