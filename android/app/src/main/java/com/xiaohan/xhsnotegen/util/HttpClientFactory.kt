package com.xiaohan.xhsnotegen.util

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * One shared OkHttpClient for Gemini and XHS APIs: identical timeouts,
 * a single connection pool, and one place to tune client-wide behavior.
 */
object HttpClientFactory {

    val shared: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}
