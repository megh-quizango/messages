package com.text.messages.sms.messanger.util

import java.util.regex.Pattern

object OtpHelper {
    
    /**
     * Checks if a message contains an OTP
     */
    fun isOTPMessage(messageBody: String): Boolean {
        val bodyLower = messageBody.lowercase()
        return bodyLower.contains("otp") || 
               bodyLower.contains("one time password") || 
               bodyLower.contains("one-time password") ||
               bodyLower.contains("verification code") ||
               bodyLower.contains("verification") ||
               bodyLower.contains("login code") ||
               bodyLower.contains("access code") ||
               extractOTP(messageBody) != null
    }
    
    /**
     * Extracts OTP from message text using common patterns
     * Returns the OTP string if found, null otherwise
     */
    fun extractOTP(messageBody: String): String? {
        // First, try to find OTP that might be split (e.g., "1803 05" or "1803\n05")
        // Look for patterns like "OTP 1803 05" or "1803 05 is your OTP"
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
                // Check if combined length is 4-8 digits
                if (combined.length in 4..8 && combined.all { it.isDigit() }) {
                    return combined
                }
            }
        }
        
        // Common OTP patterns - ordered by specificity
        // Find all matches and prefer the longest one to get complete OTP
        val patterns = listOf(
            // "OTP: 123456" or "OTP is 123456" - try to match longest number
            Pattern.compile("OTP[\\s:]*is[\\s]*([0-9]{4,8})\\b", Pattern.CASE_INSENSITIVE),
            // "OTP 180305" - match number immediately after OTP (use word boundary to ensure complete number)
            Pattern.compile("OTP[\\s:]+([0-9]{4,8})\\b", Pattern.CASE_INSENSITIVE),
            // "489161 is your One Time Password (OTP)" or "5580 is your one time password (OTP)"
            Pattern.compile("\\b([0-9]{4,8})[\\s]+is[\\s]+your[\\s]+(?:one[\\s-]?time[\\s-]?password|OTP|code|verification[\\s]*code)", Pattern.CASE_INSENSITIVE),
            // "123456 is your OTP" or "123456 is your code" (shorter version)
            Pattern.compile("\\b([0-9]{4,8})[\\s]+is[\\s]*(?:your[\\s]*)?(?:OTP|code|verification[\\s]*code)", Pattern.CASE_INSENSITIVE),
            // "code: 123456" or "verification code: 123456"
            Pattern.compile("(?:verification[\\s]+)?code[\\s:]*([0-9]{4,8})\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("verification[\\s:]*([0-9]{4,8})\\b", Pattern.CASE_INSENSITIVE),
            // "your code is 123456"
            Pattern.compile("your[\\s]*(?:OTP|code|verification[\\s]*code|one[\\s-]?time[\\s-]?password)[\\s:]*is[\\s]*([0-9]{4,8})\\b", Pattern.CASE_INSENSITIVE),
            // "your code: 123456"
            Pattern.compile("your[\\s]*(?:OTP|code|verification[\\s]*code|one[\\s-]?time[\\s-]?password)[\\s:]*([0-9]{4,8})\\b", Pattern.CASE_INSENSITIVE),
            // "for login/signup" patterns - "489161 is your OTP for login"
            Pattern.compile("\\b([0-9]{4,8})[\\s]+is[\\s]+your[\\s]+(?:OTP|one[\\s-]?time[\\s-]?password)[\\s]+for", Pattern.CASE_INSENSITIVE)
        )
        
        // Collect all potential OTPs and prefer the longest one
        val candidates = mutableListOf<Pair<String, Int>>()
        
        for (pattern in patterns) {
            val matcher = pattern.matcher(messageBody)
            while (matcher.find()) {
                val otp = if (matcher.groupCount() > 0) {
                    matcher.group(1)
                } else {
                    matcher.group(0)
                }
                
                // Validate OTP length (typically 4-8 digits) and remove any whitespace
                if (otp != null) {
                    val cleanOtp = otp.replace("\\s".toRegex(), "")
                    if (cleanOtp.length in 4..8 && cleanOtp.all { it.isDigit() }) {
                        candidates.add(Pair(cleanOtp, cleanOtp.length))
                    }
                }
            }
        }
        
        // If we found candidates, return the longest one (most likely to be the complete OTP)
        if (candidates.isNotEmpty()) {
            return candidates.maxByOrNull { it.second }?.first
        }
        
        // Last resort: find all 4-8 digit numbers and prefer the longest one near OTP keywords
        val allNumbersPattern = Pattern.compile("\\b([0-9]{4,8})\\b")
        val allNumbers = mutableListOf<Pair<String, Int>>()
        val matcher = allNumbersPattern.matcher(messageBody)
        
        while (matcher.find()) {
            val number = matcher.group(1) ?: ""
            if (number.length in 4..8 && number.all { it.isDigit() }) {
                // Check if this number is near OTP keywords (within 20 characters)
                val startPos = matcher.start()
                val contextStart = (startPos - 20).coerceAtLeast(0)
                val contextEnd = (startPos + number.length + 20).coerceAtMost(messageBody.length)
                val context = messageBody.substring(contextStart, contextEnd).lowercase()
                
                if (context.contains("otp") || 
                    context.contains("code") || 
                    context.contains("verification") ||
                    context.contains("one time password") ||
                    context.contains("one-time password")) {
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

