package com.smarthome.data.network

import android.content.Context
import com.smarthome.BuildConfig
import com.smarthome.data.AuthPreferences
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkClient {
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    @Volatile
    private var apiService: ApiService? = null

    fun getApiService(context: Context, authPreferences: AuthPreferences): ApiService {
        return apiService ?: synchronized(this) {
            apiService ?: buildApiService(context, authPreferences).also { apiService = it }
        }
    }

    private fun buildApiService(context: Context, authPreferences: AuthPreferences): ApiService {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(DynamicBaseUrlInterceptor(authPreferences))
            .addInterceptor(AuthInterceptor(context, authPreferences))
            .addInterceptor(loggingInterceptor)
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
