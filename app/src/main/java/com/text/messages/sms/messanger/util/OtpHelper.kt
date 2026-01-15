package com.text.messages.sms.messanger.util

import java.util.regex.Pattern

object OtpHelper {

    // Keywords that indicate OTP messages
    private val OTP_KEYWORDS = listOf(
        "otp",
        "one time password",
        "one-time password",
        "verification code",
        "verify code",
        "login code",
        "access code",
        "security code",
        "auth code",
        "authentication code",
        "confirmation code",
        "pin code",
        "passcode",
        "delivery authentication code",
        "dac",
        "upi pin",
        "generate pin"
    )

    /**
     * Checks if a message contains an OTP
     */
    fun isOTPMessage(messageBody: String): Boolean {
        val bodyLower = messageBody.lowercase()

        // Check for OTP keywords
        for (keyword in OTP_KEYWORDS) {
            if (bodyLower.contains(keyword)) {
                return true
            }
        }

        // Also check if we can extract an OTP
        return extractOTP(messageBody) != null
    }

    /**
     * Extracts OTP from message text using common patterns
     * Returns the OTP string if found, null otherwise
     */
    fun extractOTP(messageBody: String): String? {
        // First, try to find OTP that might be split (e.g., "1803 05" or "1803\n05")
        val splitPatterns = listOf(
            Pattern.compile("OTP[\\s:]*([0-9]{2,6})[\\s]+([0-9]{2,6})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("([0-9]{2,6})[\\s]+([0-9]{2,6})[\\s]*is[\\s]*(?:your[\\s]*)?(?:OTP|code)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:OTP|code)[\\s:]*([0-9]{2,6})[\\s]+([0-9]{2,6})", Pattern.CASE_INSENSITIVE)
        )

        for (pattern in splitPatterns) {
            val matcher = pattern.matcher(messageBody)
            if (matcher.find() && matcher.groupCount() >= 2) {
                val part1 = matcher.group(1) ?: ""
                val part2 = matcher.group(2) ?: ""
                val combined = part1 + part2
                if (combined.length in 4..8 && combined.all { it.isDigit() }) {
                    return combined
                }
            }
        }

        // Common OTP patterns - ordered by specificity
        val patterns = listOf(
            // === HIGH PRIORITY: Specific patterns with "is" at the end ===

            // "your OTP to generate UPI PIN is 288158"
            // "Your OTP for login on PhonePe is 17022"
            // "OTP for BHIM registration is 927583"
            // "Your OTP for signing up with Razorpay is 346038"
            Pattern.compile("(?:your\\s+)?OTP\\s+(?:to|for)\\s+[^0-9]+?\\s+is\\s+([0-9]{4,8})\\b", Pattern.CASE_INSENSITIVE),

            // "Your OTP for your request to view account details is 1056"
            Pattern.compile("OTP\\s+for\\s+[^0-9]+is\\s+([0-9]{4,8})\\b", Pattern.CASE_INSENSITIVE),

            // "OTP is 123456"
            Pattern.compile("OTP[\\s:]*is[\\s:]*([0-9]{4,8})\\b", Pattern.CASE_INSENSITIVE),

            // "OTP: 123456" or "OTP 180305"
            Pattern.compile("OTP[\\s:]+([0-9]{4,8})\\b", Pattern.CASE_INSENSITIVE),

            // === NUMBER is your OTP patterns ===

            // "299925 is your Paytm login OTP."
            // "489161 is your One Time Password (OTP)"
            Pattern.compile("\\b([0-9]{4,8})\\s+is\\s+your\\s+[^.]*?(?:OTP|one[\\s-]?time[\\s-]?password)", Pattern.CASE_INSENSITIVE),

            // "123456 is your OTP"
            Pattern.compile("\\b([0-9]{4,8})\\s+is\\s+(?:your\\s+)?(?:OTP|code|verification\\s*code)", Pattern.CASE_INSENSITIVE),

            // "123456 is the OTP"
            Pattern.compile("\\b([0-9]{4,8})\\s+is\\s+the\\s+(?:OTP|code|verification\\s*code)", Pattern.CASE_INSENSITIVE),

            // === Delivery Authentication Code (DAC) patterns ===

            // "Your DAC is 123456"
            Pattern.compile("DAC[\\s:]+(?:is[\\s:]+)?([0-9]{4,8})\\b", Pattern.CASE_INSENSITIVE),

            // "Delivery authentication code is 123456"
            Pattern.compile("delivery\\s+authentication\\s+code[\\s:]+(?:is[\\s:]+)?([0-9]{4,8})\\b", Pattern.CASE_INSENSITIVE),

            // "123456 is your DAC"
            Pattern.compile("\\b([0-9]{4,8})\\s+is\\s+(?:your\\s+)?(?:DAC|delivery\\s+authentication\\s+code)", Pattern.CASE_INSENSITIVE),

            // === Verification code patterns ===

            // "verification code: 123456"
            Pattern.compile("verification\\s+code[\\s:]+([0-9]{4,8})\\b", Pattern.CASE_INSENSITIVE),

            // "code: 123456"
            Pattern.compile("\\bcode[\\s:]+([0-9]{4,8})\\b", Pattern.CASE_INSENSITIVE),

            // "your code is 123456"
            Pattern.compile("your\\s+(?:OTP|code|verification\\s*code|one[\\s-]?time[\\s-]?password)[\\s:]+is[\\s:]+([0-9]{4,8})\\b", Pattern.CASE_INSENSITIVE),

            // "your code: 123456"
            Pattern.compile("your\\s+(?:OTP|code|verification\\s*code|one[\\s-]?time[\\s-]?password)[\\s:]+([0-9]{4,8})\\b", Pattern.CASE_INSENSITIVE),

            // === PIN patterns ===

            // "PIN is 123456" or "PIN: 123456"
            Pattern.compile("\\bPIN[\\s:]+(?:is[\\s:]+)?([0-9]{4,8})\\b", Pattern.CASE_INSENSITIVE),

            // "generate PIN is 123456"
            Pattern.compile("generate\\s+(?:UPI\\s+)?PIN[\\s:]+(?:is[\\s:]+)?([0-9]{4,8})\\b", Pattern.CASE_INSENSITIVE),

            // === Login/Signup specific patterns ===

            // "login OTP is 123456"
            Pattern.compile("login\\s+(?:OTP|code)[\\s:]+(?:is[\\s:]+)?([0-9]{4,8})\\b", Pattern.CASE_INSENSITIVE),

            // "123456 is your login OTP"
            Pattern.compile("\\b([0-9]{4,8})\\s+is\\s+your\\s+\\w+\\s+(?:login\\s+)?OTP", Pattern.CASE_INSENSITIVE),

            // === Security/Auth patterns ===

            // "security code is 123456"
            Pattern.compile("security\\s+code[\\s:]+(?:is[\\s:]+)?([0-9]{4,8})\\b", Pattern.CASE_INSENSITIVE),

            // "authentication code is 123456"
            Pattern.compile("authentication\\s+code[\\s:]+(?:is[\\s:]+)?([0-9]{4,8})\\b", Pattern.CASE_INSENSITIVE),

            // "auth code is 123456"
            Pattern.compile("auth\\s+code[\\s:]+(?:is[\\s:]+)?([0-9]{4,8})\\b", Pattern.CASE_INSENSITIVE),

            // === Use patterns (generic) ===

            // "Use 123456 as your OTP"
            Pattern.compile("use\\s+([0-9]{4,8})\\s+(?:as|for)", Pattern.CASE_INSENSITIVE),

            // "Enter 123456 to verify"
            Pattern.compile("enter\\s+([0-9]{4,8})\\s+to", Pattern.CASE_INSENSITIVE)
        )

        // Collect all potential OTPs
        val candidates = mutableListOf<Pair<String, Int>>()

        for (pattern in patterns) {
            val matcher = pattern.matcher(messageBody)
            while (matcher.find()) {
                val otp = if (matcher.groupCount() > 0) {
                    matcher.group(1)
                } else {
                    matcher.group(0)
                }

                if (otp != null) {
                    val cleanOtp = otp.replace("\\s".toRegex(), "")
                    if (cleanOtp.length in 4..8 && cleanOtp.all { it.isDigit() }) {
                        candidates.add(Pair(cleanOtp, cleanOtp.length))
                    }
                }
            }
        }

        // Return the longest OTP found
        if (candidates.isNotEmpty()) {
            return candidates.maxByOrNull { it.second }?.first
        }

        // Last resort: find all 4-8 digit numbers near OTP keywords
        val allNumbersPattern = Pattern.compile("\\b([0-9]{4,8})\\b")
        val allNumbers = mutableListOf<Pair<String, Int>>()
        val matcher = allNumbersPattern.matcher(messageBody)

        while (matcher.find()) {
            val number = matcher.group(1) ?: ""
            if (number.length in 4..8 && number.all { it.isDigit() }) {
                val startPos = matcher.start()
                val contextStart = (startPos - 30).coerceAtLeast(0)
                val contextEnd = (startPos + number.length + 30).coerceAtMost(messageBody.length)
                val context = messageBody.substring(contextStart, contextEnd).lowercase()

                // Check if number is near any OTP keyword
                val nearKeyword = OTP_KEYWORDS.any { keyword -> context.contains(keyword) }

                if (nearKeyword) {
                    allNumbers.add(Pair(number, number.length))
                }
            }
        }

        // Return the longest number found near OTP keywords
        if (allNumbers.isNotEmpty()) {
            return allNumbers.maxByOrNull { it.second }?.first
        }

        return null
    }
}
