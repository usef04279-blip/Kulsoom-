package com.example.service

import java.util.Locale
import kotlin.random.Random

/**
 * Manages rotated, natural conversational phrases for wake-up acknowledgments,
 * task completions, and daily briefing sign-offs across English, Urdu, and Roman Urdu.
 * Operates purely locally with zero latency.
 */
object ResponseVarietyManager {

    private var lastWakeIndex: Int = -1
    private var lastCompletionIndex: Int = -1
    private var lastClosingIndex: Int = -1

    // Pool of wake-up acknowledgments (spoken right after wake-word detection or orb tap)
    private val wakeEnglish = listOf(
        "Yes? I'm listening.",
        "How can I help you?",
        "Ready. What's on your mind?",
        "Yes, go ahead.",
        "At your service.",
        "Kulsoom here. How can I help?",
        "I'm all ears."
    )

    private val wakeUrdu = listOf(
        "جی فرمائیے؟",
        "میں سن رہی ہوں، بتائیے۔",
        "حکم کیجئے، کیا مدد کروں؟",
        "جی، میں حاضر ہوں۔",
        "بتائیے، کیا کام ہے؟",
        "جی، فرمائیں۔"
    )

    private val wakeRomanUrdu = listOf(
        "Jee farmayiye?",
        "Main sun rahi hoon, bataiye.",
        "Hukam kijiye, kya madad karoon?",
        "Jee, main hazir hoon.",
        "Bataiye, kya kaam hai?",
        "Jee, farmayein."
    )

    // Pool of task completion confirmations
    private val completionEnglish = listOf(
        "Done!",
        "All set.",
        "Got it, taken care of.",
        "Completed successfully.",
        "Here you go.",
        "Right away, all done.",
        "Finished for you."
    )

    private val completionUrdu = listOf(
        "ہو گیا!",
        "بالکل تیار ہے۔",
        "مکمل ہو گیا ہے۔",
        "کام ہو گیا!",
        "یہ لیجئے، ہو گیا۔"
    )

    private val completionRomanUrdu = listOf(
        "Ho gaya!",
        "Bilkul tayyar hai.",
        "Mukammal ho gaya hai.",
        "Kaam ho gaya!",
        "Yeh lijiye, ho gaya."
    )

    // Pool of briefing closing sign-offs
    private val briefingClosingsEnglish = listOf(
        "Have a productive and wonderful day ahead!",
        "You're all set to take on the day.",
        "Let me know whenever you need anything else today.",
        "Wishing you a great and fruitful day!",
        "Ready to assist whenever you need me."
    )

    private val briefingClosingsUrdu = listOf(
        "آپ کا دن شاندار اور پرامن گزرے!",
        "آپ کا دن بہت اچھا گزرے۔ میں ہر وقت حاضر ہوں۔",
        "انشاءاللہ آج کا دن آپ کے لیے بہترین رہے گا۔"
    )

    private val briefingClosingsRomanUrdu = listOf(
        "Aap ka din shandaar aur pur-aman guzray!",
        "Aap ka din bohot acha guzray, main har waqt hazir hoon.",
        "InshaAllah aaj ka din aap ke liye behtareen rahay ga."
    )

    /**
     * Retrieves an acknowledgment phrase, rotating through the pool avoiding immediate repeats.
     */
    fun getWakeAcknowledgment(language: String, allowVariety: Boolean = true): String {
        val lang = language.lowercase(Locale.ROOT)
        val pool = when {
            lang.startsWith("ur-pk") || lang == "ur" -> wakeUrdu
            lang.contains("roman") -> wakeRomanUrdu
            else -> wakeEnglish
        }

        if (!allowVariety) {
            return pool.first()
        }

        val nextIndex = getNextNonRepeatingIndex(pool.size, lastWakeIndex)
        lastWakeIndex = nextIndex
        return pool[nextIndex]
    }

    /**
     * Retrieves a short task completion confirmation phrase.
     */
    fun getTaskCompletionPhrase(language: String, allowVariety: Boolean = true): String {
        val lang = language.lowercase(Locale.ROOT)
        val pool = when {
            lang.startsWith("ur-pk") || lang == "ur" -> completionUrdu
            lang.contains("roman") -> completionRomanUrdu
            else -> completionEnglish
        }

        if (!allowVariety) {
            return pool.first()
        }

        val nextIndex = getNextNonRepeatingIndex(pool.size, lastCompletionIndex)
        lastCompletionIndex = nextIndex
        return pool[nextIndex]
    }

    /**
     * Retrieves a varied sign-off for the daily briefing.
     */
    fun getBriefingClosingPhrase(language: String, allowVariety: Boolean = true): String {
        val lang = language.lowercase(Locale.ROOT)
        val pool = when {
            lang.startsWith("ur-pk") || lang == "ur" -> briefingClosingsUrdu
            lang.contains("roman") -> briefingClosingsRomanUrdu
            else -> briefingClosingsEnglish
        }

        if (!allowVariety) {
            return pool.first()
        }

        val nextIndex = getNextNonRepeatingIndex(pool.size, lastClosingIndex)
        lastClosingIndex = nextIndex
        return pool[nextIndex]
    }

    private fun getNextNonRepeatingIndex(size: Int, lastIndex: Int): Int {
        if (size <= 1) return 0
        var newIndex = Random.nextInt(size)
        if (newIndex == lastIndex) {
            newIndex = (lastIndex + 1) % size
        }
        return newIndex
    }
}
