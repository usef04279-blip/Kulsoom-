package com.example.service

import java.util.Locale

/**
 * High-quality phonetic transliteration engine for Roman Urdu <-> Urdu Script.
 * When Android's Urdu TTS engine receives Roman Urdu (Latin script), it fails or pronounces
 * it with harsh English phonetics. By converting Roman Urdu responses to authentic Urdu script
 * before passing to the Urdu TTS engine, the pronunciation is native, natural, and clear.
 */
object UrduTransliterationHelper {

    // Common Roman Urdu word and phrase dictionary to Urdu script
    private val wordMap = mapOf(
        // Greetings & Introductions
        "salam" to "سلام",
        "assalam" to "السلام",
        "assalamu" to "السلام",
        "alaikum" to "علیکم",
        "o" to "و",
        "walekum" to "وعلیکم",
        "kaise" to "کیسے",
        "kaisa" to "کیسا",
        "kaisi" to "کیسی",
        "ho" to "ہو",
        "hain" to "ہیں",
        "hai" to "ہے",
        "hoon" to "ہوں",
        "theek" to "ٹھیک",
        "shukriya" to "شکریہ",
        "khush" to "خوش",
        "amdeed" to "آمدید",
        "khuda" to "خدا",
        "hafiz" to "حافظ",
        "allah" to "اللہ",

        // Assistant Identity & Persona
        "kulsoom" to "کلثوم",
        "munib" to "منیب",
        "u" to "ال",
        "rehman" to "رحمان",
        "ur" to "ال",
        "mera" to "میرا",
        "meri" to "میری",
        "mere" to "میرے",
        "naam" to "نام",
        "ai" to "اے آئی",
        "assistant" to "اسسٹنٹ",
        "banaya" to "بنایا",
        "kya" to "کیا",
        "haal" to "حال",
        "madad" to "مدد",
        "kar" to "کر",
        "sakti" to "سکتی",
        "sakta" to "سکتا",

        // Device actions & Verbs
        "torch" to "ٹارچ",
        "flashlight" to "فلیش لائٹ",
        "batti" to "بتی",
        "on" to "آن",
        "off" to "آف",
        "chalao" to "چلاؤ",
        "chala" to "چلا",
        "band" to "بند",
        "kholo" to "کھولو",
        "khol" to "کھول",
        "alarm" to "الارم",
        "timer" to "ٹائمر",
        "minute" to "منٹ",
        "sec" to "سیکنڈ",
        "second" to "سیکنڈ",
        "shuru" to "شروع",
        "gaya" to "گیا",
        "gayi" to "گئی",
        "diya" to "دیا",
        "di" to "دی",
        "call" to "کال",
        "phone" to "فون",
        "milao" to "ملاؤ",
        "mila" to "ملا",
        "message" to "میسج",
        "bhejo" to "بھیجو",
        "bhej" to "بھیج",
        "note" to "نوٹ",
        "likh" to "لکھ",
        "liya" to "لیا",
        "yad" to "یاد",
        "rakhna" to "رکھنا",
        "dilana" to "دلانا",
        "waqt" to "وقت",
        "time" to "ٹائم",
        "tareekh" to "تاریخ",
        "aaj" to "آج",
        "battery" to "بیٹری",
        "charging" to "چارجنگ",
        "awaz" to "آواز",
        "volume" to "والیم",
        "tez" to "تیز",
        "kam" to "کم",
        "dheemi" to "دھیمی",
        "badhao" to "بڑھاؤ",
        "gana" to "گانا",
        "music" to "میوزک",
        "sunao" to "سناؤ",
        "search" to "سرچ",
        "talaash" to "تلاش",

        // Common Grammar & Function Words
        "main" to "میں",
        "aap" to "آپ",
        "aapka" to "آپ کا",
        "aapki" to "آپ کی",
        "aapke" to "آپ کے",
        "tum" to "تم",
        "tumhara" to "تمہارا",
        "tumhari" to "تمہاری",
        "yeh" to "یہ",
        "woh" to "وہ",
        "iss" to "اس",
        "uss" to "اس",
        "ka" to "کا",
        "ki" to "کی",
        "ke" to "کے",
        "ko" to "کو",
        "se" to "سے",
        "par" to "پر",
        "pe" to "پہ",
        "tak" to "تک",
        "aur" to "اور",
        "lekin" to "لیکن",
        "bhi" to "بھی",
        "toh" to "تو",
        "agar" to "اگر",
        "kyun" to "کیوں",
        "kyunki" to "کیونکہ",
        "kab" to "کب",
        "kahan" to "کہاں",
        "kaun" to "کون",
        "kitna" to "کتنا",
        "kitni" to "کتنی",
        "baje" to "بجے",
        "subah" to "صبح",
        "dopahar" to "دوپہر",
        "sham" to "شام",
        "raat" to "رات",
        "bohot" to "بہت",
        "achha" to "اچھا",
        "achhi" to "اچھی",
        "hoga" to "ہوگا",
        "hogi" to "ہوگی",
        "honge" to "ہوں گے",
        "raha" to "رہا",
        "rahi" to "رہی",
        "rahe" to "رہے"
    )

    /**
     * Checks whether a given text string contains Arabic/Urdu script characters.
     */
    fun containsUrduScript(text: String): Boolean {
        for (char in text) {
            val block = Character.UnicodeBlock.of(char)
            if (block == Character.UnicodeBlock.ARABIC ||
                block == Character.UnicodeBlock.ARABIC_SUPPLEMENT ||
                block == Character.UnicodeBlock.ARABIC_EXTENDED_A ||
                block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A ||
                block == Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_B
            ) {
                return true
            }
        }
        return false
    }

    /**
     * Converts Roman Urdu words in a response to authentic Urdu script so that
     * the on-device Urdu TTS engine (`ur-PK`) can pronounce it accurately and naturally.
     */
    fun prepareTextForUrduTts(text: String): String {
        if (text.isBlank()) return text
        try {
            if (containsUrduScript(text)) {
                // Already authentic Urdu script
                return text
            }

            // Split keeping spaces and common punctuation as distinct tokens
            val tokens = text.split(Regex("(?<=\\s)|(?=\\s)|(?<=[.,!?;:\"]) |(?=[.,!?;:\"])"))
            val builder = StringBuilder()

            for (token in tokens) {
                val cleanToken = token.trim().lowercase(Locale.ROOT)
                val mapped = wordMap[cleanToken]
                if (mapped != null) {
                    builder.append(mapped)
                } else {
                    builder.append(token)
                }
            }

            val result = builder.toString().trim()
            return if (result.isBlank()) text else result
        } catch (e: Exception) {
            return text
        }
    }
}
