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
import android.content.Context
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SpellCheckerSession
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import android.view.textservice.TextServicesManager
import androidx.recyclerview.widget.RecyclerView
import com.google.mediapipe.examples.gesturerecognizer.databinding.ItemGestureRecognizerResultBinding
import com.google.mediapipe.tasks.components.containers.Category
import java.util.Locale
import kotlin.math.min

class GestureRecognizerResultsAdapter(private val context: Context) :
    RecyclerView.Adapter<GestureRecognizerResultsAdapter.ViewHolder>(),
    SpellCheckerSession.SpellCheckerSessionListener {
    companion object {
        private const val NO_VALUE = "--"
    }

    private var adapterCategories: MutableList<Category?> = mutableListOf()
    private var adapterSize: Int = 0

    private var previous = "A"
    private var how_much = 0
    public var total = ""
    public var corrected = ""

    public var t1: TextToSpeech? = null

    private var spellCheckerSession: SpellCheckerSession? = null

    var minGestureConfidence : Float = 0.65F
    var minFramesConfidence : Int = 20

    init {
        // Initialize the SpellCheckerSession
        val textServicesManager = context.getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE) as TextServicesManager
        spellCheckerSession = textServicesManager.newSpellCheckerSession(null, Locale.getDefault(), this, true)

        t1 = TextToSpeech(
            context.applicationContext
        ) { status ->
            if (status != TextToSpeech.ERROR) {
                t1!!.setLanguage(Locale.ENGLISH)
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateResults(categories: List<Category>?) {
        adapterCategories = MutableList(adapterSize) { null }
        if (categories != null) {
            val sortedCategories = categories.sortedByDescending { it.score() }
            val min = min(sortedCategories.size, adapterCategories.size)
            for (i in 0 until min) {
                adapterCategories[i] = sortedCategories[i]
            }
            adapterCategories.sortedBy { it?.index() }
            notifyDataSetChanged()
        }
    }

    fun updateAdapterSize(size: Int) {
        adapterSize = size
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding = ItemGestureRecognizerResultBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        adapterCategories[position].let { category ->
            if (category?.score() != null && category.score() > minGestureConfidence) {
                var ls = category.categoryName()
                if(ls=="space") ls = " "
                if (ls == previous) {
                    how_much += 1
                } else {
                    how_much = 0
                    previous = ls
                }
            } else {
                how_much = 0
            }

            if (how_much == minFramesConfidence) {
                total += previous
                corrected += previous
                if (previous == " ") {
                    // Trigger the spell checker for the total sentence
                    spellCheckerSession?.getSentenceSuggestions(arrayOf(TextInfo(total)), 1)
                }
            }

            holder.bind(corrected, category?.score())
        }
    }

    override fun getItemCount(): Int = adapterCategories.size

    override fun onGetSuggestions(p0: Array<out SuggestionsInfo>?) {
    }

    override fun onGetSentenceSuggestions(results: Array<SentenceSuggestionsInfo>?) {
        results?.forEach { sentenceSuggestion ->
            val correctedSentence = StringBuilder()
            for (i in 0 until sentenceSuggestion.suggestionsCount) {
                val wordSuggestions = sentenceSuggestion.getSuggestionsInfoAt(i)
                if (wordSuggestions.suggestionsCount > 0) {
                    // Use the first suggestion for each misspelled word
                    correctedSentence.append(wordSuggestions.getSuggestionAt(0)).append(" ")
                } else {
                    // Use the original word if no suggestions are available
                    val originalStart = sentenceSuggestion.getOffsetAt(i)
                    val originalLength = sentenceSuggestion.getLengthAt(i)
                    correctedSentence.append(total.substring(originalStart, originalStart + originalLength)).append(" ")
                }
            }
            // Update the corrected sentence
            corrected = correctedSentence.toString().trim().capitalizeWords()
            val lastSpaceIndex = corrected.lastIndexOf(" ")
            t1?.setLanguage(Locale.ENGLISH)
            if (lastSpaceIndex == -1){
                t1?.speak(corrected, TextToSpeech.QUEUE_FLUSH, null);
            }else{
                t1?.speak(corrected.substring(lastSpaceIndex + 1)  , TextToSpeech.QUEUE_FLUSH, null);
            }
            corrected+=" ";
        }
    }

    private fun String.capitalizeWords(delimiter: String = " ") =
        split(delimiter).joinToString(delimiter) { word ->

            val smallCaseWord = word.lowercase()
            smallCaseWord.replaceFirstChar(Char::titlecaseChar)

        }

    inner class ViewHolder(private val binding: ItemGestureRecognizerResultBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(label: String?, score: Float?) {
            with(binding) {
                tvLabel.text = label ?: NO_VALUE
                tvScore.text = if (score != null) String.format(
                    Locale.US,
                    "%.2f",
                    score
                ) else NO_VALUE
            }
        }
    }
}
