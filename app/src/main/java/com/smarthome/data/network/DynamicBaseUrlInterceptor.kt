package com.smarthome.data.network

import com.smarthome.BuildConfig
import com.smarthome.data.AuthPreferences
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

class DynamicBaseUrlInterceptor(
    private val authPreferences: AuthPreferences
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()

        if (authPreferences.cachedIsCustomServerEnabled) {
            val customUrlStr = authPreferences.cachedCustomServerUrl
            if (!customUrlStr.isNullOrBlank()) {
                val customHttpUrl = formatUrl(customUrlStr).toHttpUrlOrNull()
                val defaultHttpUrl = BuildConfig.API_BASE_URL.toHttpUrlOrNull()

                if (customHttpUrl != null && defaultHttpUrl != null) {
                    val originalUrl = request.url

                    // Find remaining path segments relative to default base URL
                    val defaultSegments = defaultHttpUrl.pathSegments.filter { it.isNotEmpty() }
                    val originalSegments = originalUrl.pathSegments.filter { it.isNotEmpty() }

                    val relativeSegments = if (originalSegments.take(defaultSegments.size) == defaultSegments) {
                        originalSegments.drop(defaultSegments.size)
                    } else {
                        originalSegments
                    }

                    val customSegments = customHttpUrl.pathSegments.filter { it.isNotEmpty() }
                    val fullPathSegments = customSegments + relativeSegments

                    val builder = originalUrl.newBuilder()
                        .scheme(customHttpUrl.scheme)
                        .host(customHttpUrl.host)
                        .port(customHttpUrl.port)

                    // Remove old path segments
                    for (i in 0 until originalUrl.pathSize) {
                        builder.removePathSegment(0)
                    }

                    // Add new path segments
                    for (segment in fullPathSegments) {
                        builder.addPathSegment(segment)
                    }

                    request = request.newBuilder()
                        .url(builder.build())
                        .build()
                }
            }
        }

        return chain.proceed(request)
    }

    private fun formatUrl(url: String): String {
        var formatted = url.trim()
        if (!formatted.startsWith("http://") && !formatted.startsWith("https://")) {
            formatted = "http://$formatted"
        }
        if (!formatted.endsWith("/")) {
            formatted = "$formatted/"
        }
        return formatted
    }
}
