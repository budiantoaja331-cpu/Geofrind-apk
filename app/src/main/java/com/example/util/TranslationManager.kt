package com.example.util

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import java.util.Locale

object TranslationManager {

    private const val TAG = "TranslationManager"
    private val translatorCache = mutableMapOf<String, Translator>()

    /**
     * Translates text from sourceLanguageCode to targetLanguageCode using Google ML Kit.
     * If source and target are identical or invalid, returns the original text.
     */
    suspend fun translateText(
        text: String,
        sourceLanguageCode: String,
        targetLanguageCode: String = Locale.getDefault().language
    ): String {
        if (text.isBlank() || sourceLanguageCode.equals(targetLanguageCode, ignoreCase = true)) {
            return text
        }

        val sourceLang = TranslateLanguage.fromLanguageTag(sourceLanguageCode) ?: sourceLanguageCode
        val targetLang = TranslateLanguage.fromLanguageTag(targetLanguageCode) ?: targetLanguageCode

        val cacheKey = "${sourceLang}_$targetLang"

        return try {
            val translator = translatorCache.getOrPut(cacheKey) {
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(sourceLang)
                    .setTargetLanguage(targetLang)
                    .build()
                Translation.getClient(options)
            }

            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions).await()
            val translated = translator.translate(text).await()
            translated
        } catch (e: Exception) {
            Log.e(TAG, "Translation error from $sourceLang to $targetLang: ${e.message}", e)
            text // Fallback to original text on failure or network delay
        }
    }
}
