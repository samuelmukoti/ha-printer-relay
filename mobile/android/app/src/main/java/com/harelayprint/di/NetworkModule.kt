package com.harelayprint.di

import android.content.Context
import com.harelayprint.data.api.RelayPrintApi
import com.harelayprint.data.local.SettingsDataStore
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context
    ): SettingsDataStore = SettingsDataStore(context)

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideApiClientFactory(
        okHttpClient: OkHttpClient
    ): ApiClientFactory = ApiClientFactory(okHttpClient, json)
}

/**
 * Factory for creating API clients with dynamic base URLs.
 * This is needed because the HA URL is configured by the user and can change.
 */
class ApiClientFactory(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    private var cachedApi: RelayPrintApi? = null
    private var cachedBaseUrl: String? = null

    fun createApi(baseUrl: String, token: String): RelayPrintApi {
        // Return cached API if base URL hasn't changed
        if (cachedApi != null && cachedBaseUrl == baseUrl) {
            return cachedApi!!
        }

        val authClient = okHttpClient.newBuilder()
            .addInterceptor { chain ->
                val request = if (token.isNotEmpty()) {
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                } else {
                    chain.request()
                }
                chain.proceed(request)
            }
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(normalizeUrl(baseUrl))
            .client(authClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        cachedApi = retrofit.create(RelayPrintApi::class.java)
        cachedBaseUrl = baseUrl
        return cachedApi!!
    }

    fun clearCache() {
        cachedApi = null
        cachedBaseUrl = null
    }

    private fun normalizeUrl(url: String): String {
        var normalized = url.trim()
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://$normalized"
        }
        if (!normalized.endsWith("/")) {
            normalized = "$normalized/"
        }
        return normalized
    }
}
