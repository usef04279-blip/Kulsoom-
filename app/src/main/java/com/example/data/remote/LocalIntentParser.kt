package com.example.data.remote

import com.example.data.model.ParsedIntent
import com.example.util.MathEvaluator
import java.util.Calendar
import java.util.Locale

object LocalIntentParser {
    fun parse(rawQuery: String): ParsedIntent {
        val query = rawQuery.trim().lowercase(Locale.ROOT)

        // 0. Daily Briefing
        if (query.contains("daily briefing") || query.contains("briefing") || query.contains("what's my day look like") ||
            query.contains("whats my day look like") || query.contains("aaj ka din") || query.contains("day summary") ||
            query.contains("brief me") || (query.contains("good morning") && !query.contains("say good morning to"))
        ) {
            return ParsedIntent(
                intent = "DAILY_BRIEFING",
                spokenResponse = "Preparing your daily briefing"
            )
        }

        // 0.1 Look and Tell / Vision Camera Trigger
        if (query.contains("look and tell") || query.contains("look at this") || query.contains("what is this") ||
            query.contains("what am i looking at") || query.contains("read this for me") || query.contains("scan this") ||
            query.contains("ye kya hai") || query.contains("yeh kya hai") || query.contains("ise dekho")
        ) {
            return ParsedIntent(
                intent = "LOOK_AND_TELL",
                spokenResponse = "Opening camera to look and tell"
            )
        }

        // 0.2 Long-Term Memory Save / Remember
        if (query.startsWith("remember that ") || query.startsWith("remember my ") || query.startsWith("remember i ") ||
            query.startsWith("remember ") || query.startsWith("yad rakhna ke ") || query.startsWith("yaad rakhna ")
        ) {
            val fact = query.replace("remember that", "")
                .replace("remember my", "my")
                .replace("remember i", "i")
                .replace("remember", "")
                .replace("yad rakhna ke", "")
                .replace("yaad rakhna", "")
                .trim()
            if (fact.isNotBlank()) {
                val cleanKey = fact.take(30).replace(Regex("[^a-zA-Z0-9_ ]"), "").trim().replace(" ", "_")
                return ParsedIntent(
                    intent = "SAVE_MEMORY",
                    spokenResponse = "I will remember that: $fact",
                    memoryKey = cleanKey,
                    memoryValue = fact
                )
            }
        }

        // 0.3 Long-Term Memory Forget
        if (query.startsWith("forget that ") || query.startsWith("forget my ") || query.startsWith("forget ") ||
            query.startsWith("bhool jao ") || query.startsWith("bhol jao ")
        ) {
            val factToForget = query.replace("forget that", "")
                .replace("forget my", "")
                .replace("forget", "")
                .replace("bhool jao", "")
                .replace("bhol jao", "")
                .trim()
            if (factToForget.isNotBlank()) {
                return ParsedIntent(
                    intent = "FORGET_MEMORY",
                    spokenResponse = "I will forget about $factToForget",
                    memoryKey = factToForget,
                    memoryValue = factToForget
                )
            }
        }

        // 1. Flashlight / Torch
        if (query.contains("flashlight") || query.contains("torch") || query.contains("batti") || (query.contains("light") && !query.contains("flight"))) {
            val isOff = query.contains("off") || query.contains("band") || query.contains("disable") || query.contains("turn off")
            val isOn = query.contains("on") || query.contains("chalao") || query.contains("kholo") || query.contains("enable") || query.contains("turn on")
            val state = if (isOff) "off" else if (isOn) "on" else "toggle"
            val resp = if (isOff) "Turning off the flashlight" else "Turning on the flashlight"
            return ParsedIntent(
                intent = "TOGGLE_FLASHLIGHT",
                spokenResponse = resp,
                flashlightState = state
            )
        }

        // 2. Battery
        if (query.contains("battery") || query.contains("charge") || query.contains("charging") || query.contains("kitni charging") || query.contains("battery kitni")) {
            return ParsedIntent(
                intent = "GET_BATTERY",
                spokenResponse = "Checking your battery level"
            )
        }

        // 3. Time / Date
        if (query.contains("time") || query.contains("waqt") || query.contains("date") || query.contains("tareekh") || query.contains("aaj kya din") || query.contains("what day is it") || query.contains("what's the time")) {
            return ParsedIntent(
                intent = "GET_TIME_DATE",
                spokenResponse = "Checking the current time and date"
            )
        }

        // 4. Do Not Disturb (DND)
        if (query.contains("do not disturb") || query.contains("dnd") || query.contains("disturb") || query.contains("silent mode")) {
            val isOff = query.contains("off") || query.contains("disable") || query.contains("band") || query.contains("remove")
            val isOn = query.contains("on") || query.contains("enable") || query.contains("chalao") || query.contains("turn on")
            val state = if (isOff) "off" else if (isOn) "on" else "toggle"
            return ParsedIntent(
                intent = "SET_DND",
                spokenResponse = if (isOff) "Disabling Do Not Disturb" else "Enabling Do Not Disturb",
                flashlightState = state
            )
        }

        // 5. Brightness
        if (query.contains("brightness") || query.contains("roshni") || query.contains("screen light")) {
            val isUp = query.contains("up") || query.contains("increase") || query.contains("badhao") || query.contains("tez") || query.contains("high") || query.contains("raise")
            val isMax = query.contains("max") || query.contains("full") || query.contains("poori")
            val isDown = query.contains("down") || query.contains("decrease") || query.contains("kam") || query.contains("dheemi") || query.contains("low")
            val dir = if (isMax) "max" else if (isDown) "down" else "up"
            return ParsedIntent(
                intent = "ADJUST_BRIGHTNESS",
                spokenResponse = "Adjusting screen brightness $dir",
                volumeDirection = dir
            )
        }

        // 6. Volume
        if (query.contains("volume") || query.contains("awaz")) {
            val isUp = query.contains("up") || query.contains("increase") || query.contains("badhao") || query.contains("tez")
            val isMute = query.contains("mute") || query.contains("silent") || query.contains("band")
            val isDown = query.contains("down") || query.contains("decrease") || query.contains("kam") || query.contains("dheemi")
            val dir = if (isMute) "mute" else if (isUp) "up" else if (isDown) "down" else "up"
            return ParsedIntent(
                intent = "ADJUST_VOLUME",
                spokenResponse = "Adjusting volume $dir",
                volumeDirection = dir
            )
        }

        // 7. Math / Quick Calculation (Supports percentages like 18% of 2400, arithmetic + - * /)
        val mathResult = MathEvaluator.evaluate(rawQuery)
        if (mathResult != null) {
            return ParsedIntent(
                intent = "CALCULATE",
                spokenResponse = mathResult.spokenAnswer,
                calculationExpression = mathResult.expression
            )
        }

        // 8. Alarm
        if (query.contains("alarm") || query.contains("jaga dena") || query.contains("uthana")) {
            val hourRegex = Regex("(\\d{1,2})\\s*(?:am|pm|baje|o'clock|:(\\d{2}))?", RegexOption.IGNORE_CASE)
            val match = hourRegex.find(query)
            var hour = 7
            var minute = 0
            if (match != null) {
                val rawH = match.groupValues[1].toIntOrNull() ?: 7
                val isPm = query.contains("pm") || query.contains("sham") || query.contains("raat")
                val isAm = query.contains("am") || query.contains("subah")
                hour = if (isPm && rawH < 12) rawH + 12 else if (isAm && rawH == 12) 0 else rawH
                minute = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
            }
            return ParsedIntent(
                intent = "SET_ALARM",
                spokenResponse = "Setting an alarm for $hour:${String.format(Locale.US, "%02d", minute)}",
                alarmHour = hour,
                alarmMinute = minute,
                alarmLabel = "Alarm by Kulsoom"
            )
        }

        // 9. Timer
        if (query.contains("timer") || query.contains("minute ka timer") || query.contains("sec ka timer")) {
            val numRegex = Regex("(\\d+)\\s*(minute|min|sec|second)", RegexOption.IGNORE_CASE)
            val match = numRegex.find(query)
            val count = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 5
            val isMin = !query.contains("sec")
            val seconds = if (isMin) count * 60 else count
            return ParsedIntent(
                intent = "SET_TIMER",
                spokenResponse = "Starting a $count ${if (isMin) "minute" else "second"} timer",
                timerSeconds = seconds
            )
        }

        // 10. Read Reminders
        if (query.contains("read reminder") || query.contains("read my reminder") || query.contains("show reminder") ||
            query.contains("what are my reminder") || query.contains("my reminder") || query.contains("reminders dikhao") ||
            query.contains("kya koi reminder hai") || query.contains("reminders sunao")
        ) {
            return ParsedIntent(
                intent = "READ_REMINDERS",
                spokenResponse = "Checking your reminders"
            )
        }

        // 11. Read Notes
        if (query.contains("read note") || query.contains("read my note") || query.contains("show note") ||
            query.contains("what are my note") || query.contains("my note") || query.contains("notes dikhao") ||
            query.contains("notes sunao")
        ) {
            return ParsedIntent(
                intent = "READ_NOTES",
                spokenResponse = "Checking your notes"
            )
        }

        // 12. Create Reminders
        if (query.contains("remind") || query.contains("yad dilana")) {
            val rem = query.replace("remind me to", "")
                .replace("remind me", "")
                .replace("yad dilana ke", "")
                .trim()
            return ParsedIntent(
                intent = "SET_REMINDER",
                spokenResponse = "I will remind you: $rem",
                reminderText = rem,
                reminderMinutesFromNow = 30
            )
        }

        // 13. Create Notes
        if (query.startsWith("note ") || query.contains("take a note") || query.contains("yad rakhna") || query.contains("note that") || query.contains("likh lo")) {
            val note = query.replace("take a note", "")
                .replace("note that", "")
                .replace("note", "")
                .replace("likh lo", "")
                .replace("yad rakhna", "")
                .trim()
            return ParsedIntent(
                intent = "TAKE_NOTE",
                spokenResponse = "Saved note: $note",
                noteText = note
            )
        }

        // 14. Make Call
        if (query.startsWith("call ") || query.startsWith("dial ") || query.contains("ko call") || query.contains("phone milao")) {
            var contact = query.replace("call", "")
                .replace("dial", "")
                .replace("ko call karo", "")
                .replace("ko call lagao", "")
                .replace("please", "")
                .trim()
            val isNumber = contact.matches(Regex("[0-9+\\-\\s()]+"))
            val phone = if (isNumber) contact else null
            val name = if (!isNumber) contact.replaceFirstChar { it.uppercase() } else "Unknown"
            return ParsedIntent(
                intent = "MAKE_CALL",
                spokenResponse = "Calling ${if (phone != null) phone else name}",
                contactName = name,
                phoneNumber = phone,
                requiresConfirmation = true
            )
        }

        // 15. Send SMS
        if (query.startsWith("text ") || query.startsWith("sms ") || query.contains("message") || query.contains("paigham")) {
            var target = "Contact"
            var msg = "Hello from Kulsoom"
            if (query.contains("saying")) {
                val parts = query.split("saying", limit = 2)
                target = parts[0].replace("text", "").replace("send sms to", "").replace("message", "").trim()
                msg = parts.getOrNull(1)?.trim() ?: msg
            } else if (query.contains("ko")) {
                val parts = query.split("ko", limit = 2)
                target = parts[0].replace("sms", "").replace("message", "").trim()
                msg = parts.getOrNull(1)?.replace("bolo", "")?.replace("bhejo", "")?.trim() ?: msg
            }
            return ParsedIntent(
                intent = "SEND_SMS",
                spokenResponse = "Preparing text message to $target: '$msg'",
                contactName = target.replaceFirstChar { it.uppercase() },
                messageText = msg,
                requiresConfirmation = true
            )
        }

        // 16. Open App
        if (query.startsWith("open ") || query.startsWith("launch ") || query.contains("kholo") || query.contains("chalao")) {
            val app = query.replace("open", "")
                .replace("launch", "")
                .replace("app", "")
                .replace("kholo", "")
                .replace("chalao", "")
                .replace("application", "")
                .trim()
            return ParsedIntent(
                intent = "OPEN_APP",
                spokenResponse = "Opening $app",
                appName = app
            )
        }

        // 17. Play Music
        if (query.contains("play") || query.contains("gana") || query.contains("music") || query.contains("song") || query.contains("spotify")) {
            val musicQ = query.replace("play", "")
                .replace("on spotify", "")
                .replace("on youtube", "")
                .replace("music", "")
                .replace("song", "")
                .replace("gana chalao", "")
                .trim()
            return ParsedIntent(
                intent = "PLAY_MUSIC",
                spokenResponse = "Playing ${if (musicQ.isNotBlank()) musicQ else "music"}",
                musicQuery = if (musicQ.isNotBlank()) musicQ else "Top Hits"
            )
        }

        // 18. Search
        if (query.startsWith("search ") || query.startsWith("google ") || query.contains("talaash")) {
            val q = query.replace("search for", "")
                .replace("search", "")
                .replace("google", "")
                .replace("talaash karo", "")
                .trim()
            return ParsedIntent(
                intent = "WEB_SEARCH",
                spokenResponse = "Searching the web for $q",
                searchQuery = q
            )
        }

        // 19. Calculation Fallback (Operators)
        if (query.contains("+") || query.contains("-") || query.contains("*") || query.contains("/") ||
            query.contains("calculate") || query.contains("plus") || query.contains("minus") || query.contains("multiply") || query.contains("% of")
        ) {
            return ParsedIntent(
                intent = "CALCULATE",
                spokenResponse = "Calculating $query",
                calculationExpression = query
            )
        }

        // 20. Creator / About / General Chat
        if (query.contains("who made you") || query.contains("who created you") || query.contains("owner") || query.contains("developer") || query.contains("aap ko kisne banaya") || query.contains("tumhe kisne banaya")) {
            return ParsedIntent(
                intent = "GENERAL_CHAT",
                spokenResponse = "I am Kulsoom, an intelligent personal AI companion created and developed by Munib u Rehman."
            )
        }

        if (query.contains("kesi ho") || query.contains("kaisi ho") || query.contains("kaise ho") || query.contains("kese ho") || query.contains("kya haal") || query.contains("how are you")) {
            return ParsedIntent(
                intent = "GENERAL_CHAT",
                spokenResponse = if (query.contains("kesi") || query.contains("kaisi") || query.contains("kaise") || query.contains("haal")) {
                    "Main bilkul theek hoon, shukriya! Aap suniye, aap ka din kaisa guzar raha hai?"
                } else {
                    "I'm doing wonderfully! Ready to help you with questions, tasks, or anything else on your mind."
                }
            )
        }

        if (query.contains("mujhe se baat") || query.contains("mujh se baat") || query.contains("baat karo") || query.contains("talk to me") || query.contains("let's talk") || query.contains("gup shup")) {
            return ParsedIntent(
                intent = "GENERAL_CHAT",
                spokenResponse = if (query.contains("baat") || query.contains("gup")) {
                    "Jee bilkul, main haazir hoon! Bataiye aaj kya khaas baat karni hai?"
                } else {
                    "I'd love to chat! What's on your mind today?"
                }
            )
        }

        if (query.contains("kya kar rahi") || query.contains("what are you doing")) {
            return ParsedIntent(
                intent = "GENERAL_CHAT",
                spokenResponse = if (query.contains("kya kar")) {
                    "Main aap ke hukum ki muntazir hoon! Bataiye kya madad karoon?"
                } else {
                    "I'm right here, ready and listening. How can I assist or chat with you?"
                }
            )
        }

        if (query.contains("joke") || query.contains("latifa") || query.contains("kuch sunao")) {
            return ParsedIntent(
                intent = "GENERAL_CHAT",
                spokenResponse = "Ek teacher ne student se poocha: 'Agar tumhare paas 10 aam hon aur 5 kisi ko de do toh kya bachega?' Student bola: 'Sir, bacha kuch nahi, bas aam lene wale ki daawat ho jayegi!'"
            )
        }

        if (query.contains("shukriya") || query.contains("thank you") || query.contains("thanks") || query.contains("meherbani")) {
            return ParsedIntent(
                intent = "GENERAL_CHAT",
                spokenResponse = if (query.contains("shukriya") || query.contains("meherbani")) {
                    "Aap ka bohot shukriya! Main har waqt aap ki khidmat ke liye haazir hoon."
                } else {
                    "You're very welcome! Always happy to help."
                }
            )
        }

        if (query.contains("khuda hafiz") || query.contains("allah hafiz") || query.contains("goodbye") || query.contains("bye")) {
            return ParsedIntent(
                intent = "GENERAL_CHAT",
                spokenResponse = if (query.contains("hafiz")) {
                    "Allah Hafiz! Apna bohot khayal rakhiyega."
                } else {
                    "Goodbye! Have a great time ahead."
                }
            )
        }

        if (query.contains("hello") || query.contains("hi") || query.contains("salam") || query.contains("hey")) {
            return ParsedIntent(
                intent = "GENERAL_CHAT",
                spokenResponse = if (query.contains("salam")) {
                    "Wa Alaikum Assalam! Kaise hain aap? Bataiye main kya madad kar sakti hoon?"
                } else {
                    "Hello! Great to hear from you. What's on your mind today?"
                }
            )
        }

        // General Chat fallback
        return ParsedIntent(
            intent = "GENERAL_CHAT",
            spokenResponse = "I heard: \"$rawQuery\". I'm here and ready to help explore this with you."
        )
    }
}

