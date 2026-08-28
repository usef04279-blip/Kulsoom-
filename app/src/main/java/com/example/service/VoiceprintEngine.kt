package com.example.service

import com.example.data.model.UserProfile
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class VoiceVerificationResult(
    val matchedProfile: UserProfile?,
    val confidence: Float,
    val isAmbiguous: Boolean = false,
    val ambiguousCandidate1: UserProfile? = null,
    val ambiguousCandidate2: UserProfile? = null,
    val isSilentRejection: Boolean = false
)

data class SampleQualityResult(
    val isValid: Boolean,
    val feedbackMessage: String,
    val features: List<Float>
)

data class VoiceTrainingPrompt(
    val step: Int,
    val phrase: String,
    val explanation: String,
    val tag: String
)

val VOICE_TRAINING_PROMPTS = listOf(
    VoiceTrainingPrompt(
        step = 1,
        phrase = "Kulsoom",
        explanation = "Say the wake word in your normal speaking voice",
        tag = "Standard Wake Word"
    ),
    VoiceTrainingPrompt(
        step = 2,
        phrase = "Kulsoom, are you there?",
        explanation = "Read this full natural sentence containing the wake word",
        tag = "Natural Sentence"
    ),
    VoiceTrainingPrompt(
        step = 3,
        phrase = "Kulsoom",
        explanation = "Speak slightly faster or casually, as you might say it daily",
        tag = "Casual Wake Word"
    ),
    VoiceTrainingPrompt(
        step = 4,
        phrase = "The weather is nice today",
        explanation = "Read this neutral sentence to capture your general vocal tone",
        tag = "General Voice Tone"
    ),
    VoiceTrainingPrompt(
        step = 5,
        phrase = "Kulsoom",
        explanation = "Speak once more at a normal pace for consistency",
        tag = "Consistency Check"
    )
)

object VoiceprintEngine {

    private const val VECTOR_DIM = 24
    private const val SIMILARITY_THRESHOLD = 0.68f
    private const val AMBIGUITY_MARGIN = 0.06f

    /**
     * Extracts an on-device mathematical acoustic feature representation (voice embedding).
     * Simulates feature extraction from audio energy profile, pitch estimates, and spectral characteristics.
     */
    fun extractEmbeddingFromAudio(
        sampleIndex: Int = 1,
        userPitchEstimate: Float = 1.0f,
        soundLevelSequence: List<Float> = emptyList()
    ): List<Float> {
        val vector = FloatArray(VECTOR_DIM)
        val basePitch = (userPitchEstimate.coerceIn(0.5f, 2.0f) * 120f) // approx Hz baseline

        // Generate normalized harmonic acoustic profile
        for (i in 0 until VECTOR_DIM) {
            val harmonicFreq = basePitch * (1 + i * 0.45f)
            val energyWeight = if (soundLevelSequence.isNotEmpty()) {
                val idx = (i * soundLevelSequence.size / VECTOR_DIM).coerceIn(0, soundLevelSequence.lastIndex)
                soundLevelSequence[idx]
            } else {
                0.5f
            }

            // Synthesize mathematical spectral harmonics & formant envelope
            val phase = (i.toFloat() / VECTOR_DIM) * Math.PI.toFloat() * 2f
            val harmonicComponent = sin(phase * (harmonicFreq / 100f)) * 0.5f + 0.5f
            val spectralCentroidWeight = cos(phase * 1.5f) * 0.3f + 0.7f
            val cadenceVariation = (1.0f + sin((sampleIndex + i) * 0.8f) * 0.12f)

            vector[i] = ((harmonicComponent * 0.4f + spectralCentroidWeight * 0.3f + energyWeight * 0.3f) * cadenceVariation)
        }

        return normalizeVector(vector)
    }

    /**
     * Validates sample quality during the 5-step voice training.
     */
    fun validateSampleQuality(
        sampleNumber: Int,
        peakSoundLevel: Float,
        durationSeconds: Float,
        userPitchEstimate: Float = 1.0f
    ): SampleQualityResult {
        if (peakSoundLevel < 0.12f) {
            return SampleQualityResult(
                isValid = false,
                feedbackMessage = "Audio too quiet. Please speak a little louder in a clear voice.",
                features = emptyList()
            )
        }

        if (durationSeconds < 0.4f) {
            return SampleQualityResult(
                isValid = false,
                feedbackMessage = "Recording was too short. Speak the phrase clearly.",
                features = emptyList()
            )
        }

        // Valid sample - extract features
        val features = extractEmbeddingFromAudio(sampleNumber, userPitchEstimate)
        return SampleQualityResult(
            isValid = true,
            feedbackMessage = "Great sample recorded ($sampleNumber of 5)!",
            features = features
        )
    }

    /**
     * Combines 5 trained voice samples into a single unified centroid voiceprint model.
     */
    fun combineTrainingSamples(samples: List<List<Float>>): String {
        if (samples.isEmpty()) return ""
        val combined = FloatArray(VECTOR_DIM) { 0f }

        for (sample in samples) {
            for (i in 0 until VECTOR_DIM.coerceAtMost(sample.size)) {
                combined[i] += sample[i]
            }
        }

        val count = samples.size.toFloat()
        for (i in 0 until VECTOR_DIM) {
            combined[i] /= count
        }

        val normalized = normalizeVector(combined)
        return normalized.joinToString(",") { String.format(java.util.Locale.US, "%.4f", it) }
    }

    /**
     * Parses a stored comma-separated string into a Float vector.
     */
    fun parseEmbedding(embeddingStr: String): List<Float> {
        if (embeddingStr.isBlank()) return emptyList()
        return try {
            embeddingStr.split(",").mapNotNull { it.trim().toFloatOrNull() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Computes Cosine Similarity between two normalized mathematical voiceprint vectors.
     */
    fun cosineSimilarity(v1: List<Float>, v2: List<Float>): Float {
        if (v1.isEmpty() || v2.isEmpty() || v1.size != v2.size) return 0f
        var dot = 0f
        var norm1 = 0f
        var norm2 = 0f
        for (i in v1.indices) {
            dot += v1[i] * v2[i]
            norm1 += v1[i] * v1[i]
            norm2 += v2[i] * v2[i]
        }
        val denom = sqrt(norm1) * sqrt(norm2)
        return if (denom > 0f) (dot / denom).coerceIn(0f, 1f) else 0f
    }

    /**
     * Matches runtime captured voice characteristics against all stored profile voiceprints.
     */
    fun verifySpeaker(
        capturedEmbedding: List<Float>,
        profiles: List<UserProfile>
    ): VoiceVerificationResult {
        val enrolledProfiles = profiles.filter { it.hasVoiceprint && it.voiceprintEmbedding.isNotBlank() }
        if (enrolledProfiles.isEmpty()) {
            // No profiles have voiceprints: default to guest mode / no silent rejection
            return VoiceVerificationResult(
                matchedProfile = null,
                confidence = 1.0f,
                isSilentRejection = false
            )
        }

        val scoredProfiles = enrolledProfiles.map { profile ->
            val storedVec = parseEmbedding(profile.voiceprintEmbedding)
            val score = cosineSimilarity(capturedEmbedding, storedVec)
            profile to score
        }.sortedByDescending { it.second }

        val best = scoredProfiles.firstOrNull()
        if (best == null || best.second < SIMILARITY_THRESHOLD) {
            // Check if any profile has onlyRespondToMyVoice = true
            val hasStrictProfile = profiles.any { it.onlyRespondToMyVoice }
            return VoiceVerificationResult(
                matchedProfile = null,
                confidence = best?.second ?: 0f,
                isSilentRejection = hasStrictProfile
            )
        }

        // Check for ambiguous similarity (e.g. siblings with similar voices)
        if (scoredProfiles.size >= 2) {
            val second = scoredProfiles[1]
            if (second.second >= SIMILARITY_THRESHOLD && abs(best.second - second.second) <= AMBIGUITY_MARGIN) {
                return VoiceVerificationResult(
                    matchedProfile = null,
                    confidence = best.second,
                    isAmbiguous = true,
                    ambiguousCandidate1 = best.first,
                    ambiguousCandidate2 = second.first
                )
            }
        }

        return VoiceVerificationResult(
            matchedProfile = best.first,
            confidence = best.second
        )
    }

    private fun normalizeVector(arr: FloatArray): List<Float> {
        var sumSq = 0f
        for (v in arr) {
            sumSq += v * v
        }
        val mag = sqrt(sumSq)
        val norm = if (mag > 0.0001f) {
            arr.map { it / mag }
        } else {
            arr.toList()
        }
        return norm
    }
}
