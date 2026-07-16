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
    suspend fun getInitialData(
        @Query("action") action: String = "getInitialData", 
        @Query("lastSync") lastSync: String? = null, 
        @Query("clientVersion") clientVersion: String? = null,
        @Query("productsVersion") productsVersion: String? = null,
        @Query("outletsVersion") outletsVersion: String? = null,
        @Query("routesVersion") routesVersion: String? = null,
        @Query("stokisVersion") stokisVersion: String? = null
    ): InitialDataResponse
    
    @GET("exec")
    suspend fun getTransactions(
        @Query("action") action: String = "getTransactions",
        @Query("salesId") salesId: String?,
        @Query("lastSync") lastSync: String?
    ): List<Map<String, Any>>

    @POST("exec")
    suspend fun syncTransactions(@Body request: SyncRequest): SyncResponse
    // PERBAIKAN BUG: endpoint baru supaya Android bisa memicu penarikan stock
    // stokis->sales langsung ke server (lihat penjelasan lengkap di AppsScript.gs,
    // action "pullStockFromStokis", dan di SfaViewModel.injectStockFromStokisToSales).
    @POST("exec")
    suspend fun pullStockFromStokis(@Body request: PullStokisStockRequest): PullStokisStockResponse
    @GET("exec")
    suspend fun checkHealth(@Query("action") action: String = "healthCheck"): HealthCheckWrapper
}




data class HealthCheckResponse(
    val status: String,
    val timestamp: String?,
    val lastUpdate: String?,
    val databaseHealth: Map<String, DbHealth>?,
    val systemMetrics: Map<String, Any>?,
    val cacheHealth: Map<String, Any>?,
    val todayTransactions: Int?,
    val syncErrors: Int?,
    val triggerHealth: Map<String, Any>?
)

data class DbHealth(
    val status: String,
    val name: String,
    val sizeBytes: Long?
)

data class HealthCheckWrapper(
    val status: String,
    val data: HealthCheckResponse?
)
data class InitialDataResponse(
    val users: List<Map<String, Any>> = emptyList(),
    val products: List<Map<String, Any>> = emptyList(),
    val outlets: List<Map<String, Any>> = emptyList(),
    val stokis: List<Map<String, Any>> = emptyList(),
    val stokisStock: List<Map<String, Any>> = emptyList(),
    val stockAllocations: List<Map<String, Any>> = emptyList(),
    val injectStock: List<Map<String, Any>>? = null,
    val version: String? = null,
    val printerSettings: ReceiptSettingsEntity? = null,
    val productsVersion: String? = null,
    val outletsVersion: String? = null,
    val routesVersion: String? = null,
    val stokisVersion: String? = null,
    val stokisStockVersion: String? = null
) {
    val finalStockAllocations: List<Map<String, Any>>
        get() = if (stockAllocations.isNotEmpty()) stockAllocations else (injectStock ?: emptyList())
}
data class SyncRequest(
    val action: String = "syncTransactions",
    val transactions: List<TransactionEntity>,
    val newOutlets: List<OutletEntity> = emptyList(),
    val syncUsers: List<UserEntity> = emptyList(),
    val syncProducts: List<ProductEntity> = emptyList(),
    val syncStokis: List<StokisEntity> = emptyList(),
    val syncOutlets: List<OutletEntity> = emptyList(),
    val stockAllocations: List<StockAllocationEntity> = emptyList(),
    val isUserAdmin: Boolean = false,
    val deviceId: String? = null,
    val appVersion: String? = null,
    val salesId: String? = null,
    val printerSettings: ReceiptSettingsEntity? = null
)

data class SyncResponse(
    val status: String,
    val message: String
)

// PERBAIKAN BUG (lihat catatan lengkap di atas endpoint pullStockFromStokis):
// model request/response khusus untuk fitur "Ambil Stock Mandiri dari Stokis"
// supaya penarikan stock benar-benar tercatat di server (ledger StokisStock
// berkurang + StockAllocations sales bertambah), bukan cuma di database lokal HP.
data class PullStokisStockItem(
    val productId: String,
    val qty: Int
)
data class PullStokisStockRequest(
    val action: String = "pullStockFromStokis",
    val stokisId: String,
    val salesName: String,
    val payload: List<PullStokisStockItem>
)
data class PullStokisStockResponse(
    val success: Boolean = false,
    val message: String = ""
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

    // PERBAIKAN BUG (gagal upload/timeout di jaringan sales yang lambat): sebelumnya
    // hanya connectTimeout & readTimeout yang di-set eksplisit (30 detik), sedangkan
    // writeTimeout TIDAK di-set sehingga OkHttp memakai default bawaan (10 detik).
    // Saat sales mengunggah banyak transaksi sekaligus (mis. setelah offline seharian
    // lalu sync di jaringan 3G/EDGE yang lambat), proses MENGIRIM (write) body request
    // yang besar bisa memakan waktu lebih dari 10 detik dan gagal dengan SocketTimeoutException
    // padahal koneksi & server sebenarnya baik-baik saja -- muncul sebagai "Sinkronisasi
    // gagal" acak yang sulit direproduksi karena tergantung kecepatan jaringan device.
    // Disamakan menjadi 30 detik seperti connect/read timeout supaya konsisten.
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(GzipRequestInterceptor()) // Compress request payloads
        .addInterceptor(AppsScriptInterceptor()) // Check for HTML response
        .build()

    fun createService(url: String): GoogleSheetsApiService {
        val baseUrl = if (url.endsWith("/")) url else "$url/"
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
            .build()
        return retrofit.create(GoogleSheetsApiService::class.java)
    }
}
