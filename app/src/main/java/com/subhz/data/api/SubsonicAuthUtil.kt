package com.subhz.data.api

import java.security.MessageDigest

/**
 * Utility to generate Subsonic API authentication parameters.
 */
object SubsonicAuthUtil {

    /**
     * Generates a random salt and an MD5 token.
     * Formula: token = md5(password + salt)
     */
    fun generateTokenAndSalt(password: String): AuthParams {
        val allowedChars = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        val salt = (1..6).map { allowedChars.random() }.joinToString("")

        val input = password + salt
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        val token = digest.joinToString("") { "%02x".format(it) }

        return AuthParams(token, salt)
    }
}

/**
 * Data class representing the result of the auth generation.
 */
data class AuthParams(
    val token: String,
    val salt: String
)