package com.xiaohan.xhsnotegen.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // 10.0.2.2 = Android emulator → host localhost. Change for physical device.
    // 10.0.2.2 = emulator → host. For physical device, use PC's LAN IP.
    private const val DEFAULT_BASE_URL = "http://10.0.0.44:8000/"

    private var baseUrl: String = DEFAULT_BASE_URL
    private var retrofit: Retrofit? = null
    private var api: AiGenerationApi? = null

    fun setBaseUrl(url: String) {
        if (url != baseUrl) {
            baseUrl = url
            retrofit = null
            api = null
        }
    }

    fun getApi(): AiGenerationApi {
        if (api == null) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            api = retrofit!!.create(AiGenerationApi::class.java)
        }
        return api!!
    }
}
