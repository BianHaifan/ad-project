package com.adproject.candidate.core.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SessionTokens(val accessToken: String, val refreshToken: String, val onboardingRequired: Boolean = false)

interface TokenStore {
    suspend fun read(): SessionTokens?
    suspend fun write(tokens: SessionTokens)
    suspend fun clear()
}

private val Context.authSessionDataStore by preferencesDataStore(name = "encrypted_auth_session")

class KeystoreTokenStore(
    private val context: Context,
    moshi: Moshi,
) : TokenStore {
    private val adapter = moshi.adapter(SessionTokens::class.java)

    override suspend fun read(): SessionTokens? {
        val encoded = context.authSessionDataStore.data.first()[ENCRYPTED_SESSION] ?: return null
        return runCatching { adapter.fromJson(decrypt(encoded)) }.getOrElse {
            clear()
            null
        }
    }

    override suspend fun write(tokens: SessionTokens) {
        val encrypted = encrypt(adapter.toJson(tokens))
        context.authSessionDataStore.edit { it[ENCRYPTED_SESSION] = encrypted }
    }

    override suspend fun clear() {
        context.authSessionDataStore.edit { it.remove(ENCRYPTED_SESSION) }
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val payload = cipher.iv + cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.size > IV_SIZE)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, payload.copyOfRange(0, IV_SIZE)))
        return cipher.doFinal(payload.copyOfRange(IV_SIZE, payload.size)).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        val ENCRYPTED_SESSION = stringPreferencesKey("encrypted_session")
        const val KEY_ALIAS = "ad_candidate_auth_session_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
