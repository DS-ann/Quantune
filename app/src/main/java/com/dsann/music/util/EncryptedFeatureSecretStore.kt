package com.dsann.music.util

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores optional feature API keys encrypted with a non-exportable Android Keystore key.
 * Android Keystore AES/GCM is available from the app's minimum API level (23).
 */
class EncryptedFeatureSecretStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "dsmusic_feature_secrets",
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun save(name: String, value: String): Boolean {
        if (value.isBlank()) return false
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encrypted = cipher.doFinal(value.trim().toByteArray(Charsets.UTF_8))
            val stored = cipher.iv + encrypted
            preferences.edit().putString(name, Base64.encodeToString(stored, Base64.NO_WRAP)).commit()
        } catch (_: Exception) {
            false
        }
    }

    @Synchronized
    fun read(name: String): String? {
        val stored = preferences.getString(name, null) ?: return null
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            if (bytes.size <= GCM_IV_LENGTH) return null
            val iv = bytes.copyOfRange(0, GCM_IV_LENGTH)
            val encrypted = bytes.copyOfRange(GCM_IV_LENGTH, bytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun clear(name: String) {
        preferences.edit().remove(name).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "com.dsann.music.feature-secrets.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH = 12
    }
}
