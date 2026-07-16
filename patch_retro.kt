package com.example.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
import okio.BufferedSink
import okio.GzipSink
import okio.buffer
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import java.io.IOException

interface GoogleSheetsApiService {
    @GET("exec")
    suspend fun getSheetData(@Query("action") action: String): List<Map<String, Any>>
    
    @GET("exec")
    suspend fun getTransactions(
        @Query("action") action: String = "getTransactions",
        @Query("salesId") salesId: String?,
        @Query("lastSync") lastSync: String?
    ): List<Map<String, Any>>

    @POST("exec")
    suspend fun syncTransactions(@Body request: SyncRequest): SyncResponse
}

data class SyncRequest(
    val action: String = "syncTransactions",
    val transactions: List<TransactionEntity>,
    val deviceId: String? = null,
    val appVersion: String? = null,
    val salesId: String? = null
)

data class SyncResponse(
    val status: String,
    val message: String
)

class GzipRequestInterceptor : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        if (originalRequest.body == null || originalRequest.header("Content-Encoding") != null) {
            return chain.proceed(originalRequest)
        }

        val compressedRequest = originalRequest.newBuilder()
            .header("Content-Encoding", "gzip")
            .method(originalRequest.method, gzip(originalRequest.body!!))
            .build()
        return chain.proceed(compressedRequest)
    }

    private fun gzip(body: RequestBody): RequestBody {
        return object : RequestBody() {
            override fun contentType() = body.contentType()
            override fun contentLength(): Long = -1 // We don't know the compressed length in advance
            
            @Throws(IOException::class)
            override fun writeTo(sink: BufferedSink) {
                val gzipSink = GzipSink(sink).buffer()
                body.writeTo(gzipSink)
                gzipSink.close()
            }
        }
    }
}

object RetrofitClient {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(GzipRequestInterceptor()) // Compress request payloads
        .build()

    fun createService(url: String): GoogleSheetsApiService {
        val baseUrl = if (url.endsWith("/")) url else "$url/"
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        return retrofit.create(GoogleSheetsApiService::class.java)
    }
}
