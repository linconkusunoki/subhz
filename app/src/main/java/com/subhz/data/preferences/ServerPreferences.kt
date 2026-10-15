package com.subhz.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Single source of truth for the active server credentials.
 *
 * The password is stored encrypted with an Android Keystore AES-256-GCM key (hardware
 * backed where available), URL and username in cleartext since neither is a secret.
 * The decrypted config is mirrored in memory as a [StateFlow] so the OkHttp
 * interceptor and the player can read it synchronously on the network thread, and
 * so the UI can gate itself on "is anyone logged in".
 *
 * ponytail: Subsonic's auth is md5(password + salt), so the password itself has to be
 * kept — there is no refreshable token. Swap in a stored Navidrome JWT
 * (`X-ND-Authorization`) if token expiry or remote revocation ever matters.
 */
class ServerPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(read())

    /** The signed-in server, or null when the user is logged out. */
    val config: StateFlow<ServerConfig?> = _config.asStateFlow()

    /**
     * The server the network layer talks to: the login probe target when verifying a
     * new server, otherwise the signed-in one. Kept separate from [config] so a probe
     * never makes the app look signed in before the server has accepted the credentials.
     */
    @Volatile
    private var probeTarget: ServerConfig? = null

    val activeServer: ServerConfig? get() = probeTarget ?: _config.value

    /** Points the network layer at [config] without signing in. Pass null to undo. */
    fun setProbeTarget(config: ServerConfig?) {
        probeTarget = config
    }

    /** Persists [config] and makes it the active server. */
    fun save(config: ServerConfig) {
        val cipherText = SecretBox.encrypt(config.password)
        if (cipherText == null) {
            // We cannot protect the password, so do not pretend we are signed in.
            logout()
            return
        }
        prefs.edit {
            putString(KEY_URL, config.url)
            putString(KEY_USERNAME, config.username)
            putString(KEY_PASSWORD, cipherText)
        }
        probeTarget = null
        _config.value = config
    }

    /** Wipes the stored credentials and returns the app to the logged-out state. */
    fun logout() {
        prefs.edit { clear() }
        probeTarget = null
        _config.value = null
    }

    private fun read(): ServerConfig? {
        val url = prefs.getString(KEY_URL, null) ?: return null
        val username = prefs.getString(KEY_USERNAME, null) ?: return null
        val cipherText = prefs.getString(KEY_PASSWORD, null) ?: return null
        val password = SecretBox.decrypt(cipherText) ?: return null
        return ServerConfig(url, username, password)
    }

    private companion object {
        const val FILE = "substream_server"
        const val KEY_URL = "url"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
    }
}

/**
 * AES-256-GCM encryption backed by a non-exportable Android Keystore key.
 *
 * ponytail: keystore access can fail on broken devices/emulators. We surface that by
 * refusing to store a password we cannot protect (the user simply logs in again next
 * launch) rather than falling back to plaintext.
 */
private object SecretBox {

    private const val KEY_ALIAS = "substream_server_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12
    private const val TAG_SIZE = 128

    /** Returns null when the keystore refused to hand out a key. */
    fun encrypt(plainText: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val payload = cipher.iv + cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(payload, Base64.NO_WRAP)
    }.getOrNull()

    /** Returns null when the payload was tampered with or the key is gone. */
    fun decrypt(encoded: String): String? = runCatching {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_SIZE, payload, 0, IV_SIZE))
        String(cipher.doFinal(payload, IV_SIZE, payload.size - IV_SIZE), Charsets.UTF_8)
    }.getOrNull()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
}