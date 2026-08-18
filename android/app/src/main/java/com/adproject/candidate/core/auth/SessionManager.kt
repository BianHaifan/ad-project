package com.adproject.candidate.core.auth

import com.adproject.candidate.data.contract.RefreshTokenRequest
import com.adproject.candidate.data.contract.TokenData
import com.adproject.candidate.data.api.AuthHttpApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SessionManager(
    private val tokenStore: TokenStore,
    private val refreshApi: AuthHttpApi,
) {
    private val refreshMutex = Mutex()
    private val mutableSessionActive = MutableStateFlow<Boolean?>(null)
    val sessionActive: StateFlow<Boolean?> = mutableSessionActive
    private val mutableOnboardingRequired = MutableStateFlow<Boolean?>(null)
    val onboardingRequired: StateFlow<Boolean?> = mutableOnboardingRequired

    suspend fun load() {
        val stored = tokenStore.read()
        mutableSessionActive.value = stored != null
        mutableOnboardingRequired.value = stored?.onboardingRequired ?: false
    }

    suspend fun tokens(): SessionTokens? = tokenStore.read()

    suspend fun save(accessToken: String, refreshToken: String, onboardingRequired: Boolean? = null) {
        val required = onboardingRequired ?: tokenStore.read()?.onboardingRequired ?: false
        tokenStore.write(SessionTokens(accessToken, refreshToken, required))
        mutableSessionActive.value = true
        mutableOnboardingRequired.value = required
    }

    suspend fun markOnboardingComplete() {
        val current = tokenStore.read() ?: return
        tokenStore.write(current.copy(onboardingRequired = false))
        mutableOnboardingRequired.value = false
    }

    suspend fun clear() {
        tokenStore.clear()
        mutableSessionActive.value = false
        mutableOnboardingRequired.value = false
    }

    suspend fun refreshAfterUnauthorized(failedAccessToken: String): String? = refreshMutex.withLock {
        val current = tokenStore.read() ?: return@withLock null
        if (current.accessToken != failedAccessToken) return@withLock current.accessToken
        val replacement: TokenData = try {
            val response = refreshApi.refresh(RefreshTokenRequest(current.refreshToken))
            if (!response.isSuccessful) {
                clear()
                return@withLock null
            }
            response.body()?.data ?: run {
                clear()
                return@withLock null
            }
        } catch (_: Exception) {
            clear()
            return@withLock null
        }
        save(replacement.accessToken, replacement.refreshToken)
        replacement.accessToken
    }
}
