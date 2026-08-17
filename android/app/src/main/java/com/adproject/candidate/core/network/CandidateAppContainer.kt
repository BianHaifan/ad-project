package com.adproject.candidate.core.network

import android.content.Context
import com.adproject.candidate.BuildConfig
import com.adproject.candidate.R
import com.adproject.candidate.core.auth.KeystoreTokenStore
import com.adproject.candidate.core.auth.SessionManager
import com.adproject.candidate.data.api.AuthHttpApi
import com.adproject.candidate.data.api.CandidateJobHttpApi
import com.adproject.candidate.data.api.RealAuthRepository
import com.adproject.candidate.data.api.RealCandidateJobRepository
import com.adproject.candidate.data.api.CandidateProfileHttpApi
import com.adproject.candidate.data.api.CandidateResumeHttpApi
import com.adproject.candidate.data.api.RealCandidateProfileRepository
import com.adproject.candidate.data.api.RealCandidateAvatarRepository
import com.adproject.candidate.data.api.RealCandidateResumeRepository
import com.adproject.candidate.data.api.CandidateApplicationHttpApi
import com.adproject.candidate.data.api.RealCandidateApplicationRepository
import com.adproject.candidate.data.api.CandidateConversationHttpApi
import com.adproject.candidate.data.api.RealCandidateConversationRepository
import com.adproject.candidate.data.api.CandidateRecommendationHttpApi
import com.adproject.candidate.data.api.RealCandidateRecommendationRepository
import com.adproject.candidate.data.api.CandidatePublicProfileHttpApi
import com.adproject.candidate.data.api.RealCandidatePublicProfileRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class CandidateAppContainer(context: Context) {
    val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val converter = MoshiConverterFactory.create(moshi)

    private val pinner = CertificatePinner.Builder()
        .add(BuildConfig.API_HOST, SERVER_PUBLIC_KEY_PIN)
        .build()

    private val sslContext = buildPinnedSslContext(context)
    private val trustManager = sslContext.first
    private val sslSocketFactory = sslContext.second

    private val publicRetrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .addConverterFactory(converter)
        .client(
            OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, trustManager)
                .certificatePinner(pinner)
                .build(),
        )
        .build()
    private val publicAuthApi = publicRetrofit.create(AuthHttpApi::class.java)
    val sessionManager = SessionManager(KeystoreTokenStore(context.applicationContext, moshi), publicAuthApi)
    private val authenticatedClient = OkHttpClient.Builder()
        .sslSocketFactory(sslSocketFactory, trustManager)
        .certificatePinner(pinner)
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
    val candidateAvatarRepository = RealCandidateAvatarRepository(authenticatedRetrofit.create(CandidateProfileHttpApi::class.java), moshi)
    val candidateResumeRepository = RealCandidateResumeRepository(authenticatedRetrofit.create(CandidateResumeHttpApi::class.java), moshi)
    val candidateApplicationRepository = RealCandidateApplicationRepository(
        authenticatedRetrofit.create(CandidateApplicationHttpApi::class.java), moshi,
    )
    val candidateConversationRepository = RealCandidateConversationRepository(
        authenticatedRetrofit.create(CandidateConversationHttpApi::class.java), moshi,
    )
    val candidateRecommendationRepository = RealCandidateRecommendationRepository(
        authenticatedRetrofit.create(CandidateRecommendationHttpApi::class.java), moshi,
    )
    val candidatePublicProfileRepository = RealCandidatePublicProfileRepository(
        authenticatedRetrofit.create(CandidatePublicProfileHttpApi::class.java), moshi,
    )

    private fun buildPinnedSslContext(context: Context): Pair<X509TrustManager, SSLSocketFactory> {
        val cert = context.resources.openRawResource(R.raw.ad_b_server_cert)
            .use { stream -> CertificateFactory.getInstance("X.509").generateCertificate(stream) }
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("ad_b_server", cert)
        }
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore)
        }
        val trustManager = tmf.trustManagers.filterIsInstance<X509TrustManager>().first()
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, tmf.trustManagers, null)
        }
        return trustManager to sslContext.socketFactory
    }

    private companion object {
        const val SERVER_PUBLIC_KEY_PIN =
            "sha256/+kyvNPg1eXyxlmr6gVIr3L909mGgL8Ny4BOP40R5nRk="
    }
}
