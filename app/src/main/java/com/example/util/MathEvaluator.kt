package com.example.util

import java.util.Locale

object MathEvaluator {

    fun evaluate(query: String): EvaluationResult? {
        val raw = query.lowercase(Locale.ROOT).trim()

        // 1. Percentage check: e.g. "what is 18% of 2400", "18 percent of 2400", "calculate 15% of 850"
        val pctRegex = Regex("(?:what(?:'s|\\s+is)?\\s+|calculate\\s+)?([0-9.]+)\\s*(?:%|\\s*percent\\s*(?:of)?|\\s*per\\s*cent\\s*(?:of)?|%\\s*of)\\s*([0-9.]+)", RegexOption.IGNORE_CASE)
        val pctMatch = pctRegex.find(raw)
        if (pctMatch != null) {
            val pctVal = pctMatch.groupValues[1].toDoubleOrNull() ?: return null
            val baseVal = pctMatch.groupValues[2].toDoubleOrNull() ?: return null
            val result = (pctVal * baseVal) / 100.0
            val formattedResult = formatNumber(result)
            return EvaluationResult(
                expression = "${formatNumber(pctVal)}% of ${formatNumber(baseVal)}",
                resultValue = result,
                formattedAnswer = "${formatNumber(pctVal)}% of ${formatNumber(baseVal)} is $formattedResult",
                spokenAnswer = "${formatNumber(pctVal)} percent of ${formatNumber(baseVal)} is $formattedResult"
            )
        }

        // 2. Clean arithmetic string
        var cleaned = raw.replace("calculate", "")
            .replace("what is", "")
            .replace("what's", "")
            .replace("whats", "")
            .replace("how much is", "")
            .replace("times", "*")
            .replace("multiplied by", "*")
            .replace("multiply by", "*")
            .replace("multiply", "*")
            .replace("divided by", "/")
            .replace("divide by", "/")
            .replace("divide", "/")
            .replace("plus", "+")
            .replace("minus", "-")
            .replace("x", "*")
            .replace("X", "*")
            .trim()

        // Replace common number words with digits if user spoke "twenty plus thirty"
        cleaned = replaceWordNumbers(cleaned)

        // Validate that cleaned contains at least one operator and numbers
        if (!cleaned.contains("+") && !cleaned.contains("-") && !cleaned.contains("*") && !cleaned.contains("/")) {
            return null
        }

        // Remove invalid characters
        val sanitized = cleaned.filter { it.isDigit() || it == '.' || it == '+' || it == '-' || it == '*' || it == '/' || it == '(' || it == ')' || it == ' ' }.trim()
        if (sanitized.isBlank()) return null

        val result = try {
            evalSimpleExpression(sanitized)
        } catch (_: Exception) {
            null
        } ?: return null

        val formattedResult = formatNumber(result)
        return EvaluationResult(
            expression = sanitized,
            resultValue = result,
            formattedAnswer = "$sanitized = $formattedResult",
            spokenAnswer = "$sanitized is $formattedResult"
        )
    }

    private fun formatNumber(num: Double): String {
        return if (num % 1.0 == 0.0) {
            num.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", num).trimEnd('0').trimEnd('.')
        }
    }

    private fun replaceWordNumbers(input: String): String {
        val wordMap = mapOf(
            "zero" to "0", "one" to "1", "two" to "2", "three" to "3", "four" to "4",
            "five" to "5", "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9",
            "ten" to "10", "twenty" to "20", "thirty" to "30", "forty" to "40",
            "fifty" to "50", "sixty" to "60", "seventy" to "70", "eighty" to "80",
            "ninety" to "90", "hundred" to "100", "thousand" to "1000"
        )
        var out = input
        for ((word, digit) in wordMap) {
            out = out.replace(Regex("\\b$word\\b", RegexOption.IGNORE_CASE), digit)
        }
        return out
    }

    private fun evalSimpleExpression(expr: String): Double {
        val tokens = tokenize(expr)
        if (tokens.isEmpty()) throw IllegalArgumentException("Empty expression")
        return parseAdditionSubtraction(tokens, 0).first
    }

    private fun tokenize(expr: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < expr.length) {
            val c = expr[i]
            if (c.isWhitespace()) {
                i++
                continue
            }
            if (c in "+-*/()") {
                tokens.add(c.toString())
                i++
            } else if (c.isDigit() || c == '.') {
                val sb = StringBuilder()
                while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) {
                    sb.append(expr[i])
                    i++
                }
                tokens.add(sb.toString())
            } else {
                i++
            }
        }
        return tokens
    }

    private fun parseAdditionSubtraction(tokens: List<String>, index: Int): Pair<Double, Int> {
        var (result, nextIdx) = parseMultiplicationDivision(tokens, index)
        while (nextIdx < tokens.size) {
            val op = tokens[nextIdx]
            if (op == "+" || op == "-") {
                val (rhs, afterRhs) = parseMultiplicationDivision(tokens, nextIdx + 1)
                result = if (op == "+") result + rhs else result - rhs
                nextIdx = afterRhs
            } else {
                break
            }
        }
        return Pair(result, nextIdx)
    }

    private fun parseMultiplicationDivision(tokens: List<String>, index: Int): Pair<Double, Int> {
        var (result, nextIdx) = parseFactor(tokens, index)
        while (nextIdx < tokens.size) {
            val op = tokens[nextIdx]
            if (op == "*" || op == "/") {
                val (rhs, afterRhs) = parseFactor(tokens, nextIdx + 1)
                result = if (op == "*") result * rhs else (if (rhs != 0.0) result / rhs else 0.0)
                nextIdx = afterRhs
            } else {
                break
            }
        }
        return Pair(result, nextIdx)
    }

    private fun parseFactor(tokens: List<String>, index: Int): Pair<Double, Int> {
        if (index >= tokens.size) return Pair(0.0, index)
        val token = tokens[index]
        if (token == "(") {
            val (subResult, afterSub) = parseAdditionSubtraction(tokens, index + 1)
            val closingIdx = if (afterSub < tokens.size && tokens[afterSub] == ")") afterSub + 1 else afterSub
            return Pair(subResult, closingIdx)
        }
        if (token == "-") {
            val (subResult, afterSub) = parseFactor(tokens, index + 1)
            return Pair(-subResult, afterSub)
        }
        val value = token.toDoubleOrNull() ?: 0.0
        return Pair(value, index + 1)
    }

    data class EvaluationResult(
        val expression: String,
        val resultValue: Double,
        val formattedAnswer: String,
        val spokenAnswer: String
    )
}
