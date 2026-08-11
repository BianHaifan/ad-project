package com.adproject.candidate.core.network

import android.content.Context
import com.adproject.candidate.BuildConfig
import com.adproject.candidate.core.auth.KeystoreTokenStore
import com.adproject.candidate.core.auth.SessionManager
import com.adproject.candidate.data.api.AuthHttpApi
import com.adproject.candidate.data.api.CandidateJobHttpApi
import com.adproject.candidate.data.api.RealAuthRepository
import com.adproject.candidate.data.api.RealCandidateJobRepository
import com.adproject.candidate.data.api.CandidateProfileHttpApi
import com.adproject.candidate.data.api.CandidateResumeHttpApi
import com.adproject.candidate.data.api.RealCandidateProfileRepository
import com.adproject.candidate.data.api.RealCandidateResumeRepository
import com.adproject.candidate.data.api.CandidateApplicationHttpApi
import com.adproject.candidate.data.api.RealCandidateApplicationRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class CandidateAppContainer(context: Context) {
    val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val converter = MoshiConverterFactory.create(moshi)
    private val publicRetrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .addConverterFactory(converter)
        .client(OkHttpClient.Builder().build())
        .build()
    private val publicAuthApi = publicRetrofit.create(AuthHttpApi::class.java)
    val sessionManager = SessionManager(KeystoreTokenStore(context.applicationContext, moshi), publicAuthApi)
    private val authenticatedClient = OkHttpClient.Builder()
        .addInterceptor(AccessTokenInterceptor(sessionManager))
        .authenticator(RefreshAuthenticator(sessionManager))
        .build()
    private val authenticatedRetrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .addConverterFactory(converter)
        .client(authenticatedClient)
        .build()
    val authRepository = RealAuthRepository(
        publicAuthApi,
        authenticatedRetrofit.create(AuthHttpApi::class.java),
        sessionManager,
        moshi,
    )
    val candidateJobRepository = RealCandidateJobRepository(
        authenticatedRetrofit.create(CandidateJobHttpApi::class.java),
        moshi,
    )
    val candidateProfileRepository = RealCandidateProfileRepository(authenticatedRetrofit.create(CandidateProfileHttpApi::class.java), moshi)
    val candidateResumeRepository = RealCandidateResumeRepository(authenticatedRetrofit.create(CandidateResumeHttpApi::class.java), moshi)
    val candidateApplicationRepository = RealCandidateApplicationRepository(
        authenticatedRetrofit.create(CandidateApplicationHttpApi::class.java), moshi,
    )
}
