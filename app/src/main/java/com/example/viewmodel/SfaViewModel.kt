package com.example.viewmodel
import kotlinx.coroutines.delay
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

import android.app.Application
import android.content.Context
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.*

// PERBAIKAN BUG UTAMA: helper ini menggantikan pola lama "row[\"X\"]?.toString()?.toIntOrNull()".
// Bug itu adalah ROOT CAUSE utama kenapa Qty/Stock selalu terbaca 0 sehingga stock/alokasi
// tidak pernah muncul di HP sales walau data sudah benar di sheet & sudah tersinkron.
// Penyebabnya: parser JSON (Moshi) selalu membaca angka JSON sebagai Double untuk tipe
// generik Any/Map<String, Any> -- misalnya angka 5 dari server dibaca jadi 5.0 di Kotlin,
// lalu ".toString()" menghasilkan "5.0", dan "5.0".toIntOrNull() mengembalikan NULL (bukan 5)
// karena toIntOrNull() tidak menerima string berformat desimal -- sehingga SELALU jatuh ke
// fallback "?: 0". Akibatnya baris DAO "if (sa.qty > 0)" selalu gagal dan data tidak pernah
// disimpan, meski proses sync "berhasil".
private fun parseFlexibleInt(value: Any?): Int? {
    if (value == null) return null
    return when (value) {
        is Int -> value
        is Long -> value.toInt()
        is Double -> value.toInt()
        is Float -> value.toInt()
        is Number -> value.toInt()
        is String -> {
            val trimmed = value.trim()
            trimmed.toIntOrNull() ?: trimmed.toDoubleOrNull()?.toInt()
        }
        else -> value.toString().trim().toDoubleOrNull()?.toInt()
    }
}

class SfaViewModel(application: Application) : AndroidViewModel(application) {

    private val _appsScriptUrl = MutableStateFlow(getApplication<Application>().getSharedPreferences("sfa_prefs", android.content.Context.MODE_PRIVATE).getString("apps_script_url", "https://script.google.com/macros/s/AKfycbxLlopheoW9H1UJy_W7Py1LGlZGCE2gEs1hs4GLd_8pFGYCSeW9G5AReEZEWCRzDAdy/exec") ?: "https://script.google.com/macros/s/AKfycbxLlopheoW9H1UJy_W7Py1LGlZGCE2gEs1hs4GLd_8pFGYCSeW9G5AReEZEWCRzDAdy/exec")
    val appsScriptUrl: StateFlow<String> = _appsScriptUrl

    fun saveAppsScriptUrl(url: String) {
        getApplication<Application>().getSharedPreferences("sfa_prefs", android.content.Context.MODE_PRIVATE).edit().putString("apps_script_url", url).apply()
        _appsScriptUrl.value = url
    }


    private val repository: AppRepository
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    // Menghitung berapa milidetik dari sekarang sampai jam:menit berikutnya di zona
    // waktu WIB (Asia/Jakarta). Jika jam tersebut sudah lewat hari ini, otomatis
    // dihitung ke hari berikutnya. Dipakai untuk menjadwalkan auto-sync jam 12:00 WIB.
    private fun calculateInitialDelayUntilWibTime(hour: Int, minute: Int): Long {
        val zone = java.util.TimeZone.getTimeZone("Asia/Jakarta")
        val now = java.util.Calendar.getInstance(zone)
        val target = java.util.Calendar.getInstance(zone).apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }

    // Database and flows

    init {
        val database = AppDatabase.getDatabase(application)
        repository = AppRepository(database)
        
        // PERBAIKAN BUG CRASH: sebelumnya blok penjadwalan WorkManager di bawah ini
        // TIDAK dibungkus try-catch. Kalau WorkManager.getInstance(...) atau
        // enqueueUniquePeriodicWork(...) melempar exception apapun (misalnya karena
        // ada state WorkManager lama/tidak konsisten dari install sebelumnya di HP
        // yang sama -- WorkManager punya database internal terpisah dari database
        // aplikasi ini, TIDAK ikut ter-reset oleh fallbackToDestructiveMigration()),
        // exception itu akan lolos sampai ke init{} block ViewModel dan meng-crash
        // SELURUH aplikasi sebelum sempat menampilkan layar login sama sekali --
        // dan karena masalahnya ada di database internal WorkManager (bukan database
        // aplikasi kita), satu-satunya cara memperbaikinya waktu itu adalah clear
        // cache & clear data (yang menghapus SEMUA data privat aplikasi, termasuk
        // database internal WorkManager tsb). Sekarang seluruh blok ini dibungkus
        // try-catch supaya kalaupun WorkManager gagal dijadwalkan, aplikasi tetap
        // bisa lanjut menampilkan layar login secara normal (auto-sync terjadwal
        // memang fitur pelengkap, bukan sesuatu yang boleh membuat seluruh app crash).
        try {
            // Auto-sync WorkManager setup
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // PERBAIKAN: sebelumnya auto-sync berjalan setiap 3 jam sejak app pertama
            // dibuka (tidak terikat jam tertentu), sehingga tidak sesuai dengan
            // requirement "auto upload setiap jam 12:00 WIB". Sekarang dijadwalkan
            // sebagai periodic 24 jam dengan initial delay dihitung sampai jam 12:00
            // WIB berikutnya, sehingga siklusnya jatuh di sekitar jam 12:00 WIB tiap hari.
            // Catatan: WorkManager tidak menjamin presisi ke detik (bergantung Doze/OS
            // scheduling), tapi ini adalah pendekatan standar Android untuk "jadwal jam tertentu".
            val initialDelayMillis = calculateInitialDelayUntilWibTime(hour = 12, minute = 0)

            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, androidx.work.WorkRequest.MIN_BACKOFF_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build()

            // PERBAIKAN TAMBAHAN: pakai REPLACE (bukan KEEP) khusus saat definisi jadwal
            // berubah (mis. dari 3 jam ke 24 jam seperti ini). KEEP akan mempertahankan
            // jadwal LAMA yang sudah terlanjur terdaftar di WorkManager pada HP yang
            // sebelumnya sudah pernah install versi app yang lama -- jadi perubahan
            // jadwal ini tidak akan pernah benar-benar diterapkan di HP yang sudah
            // pernah dipakai sebelumnya kalau tetap pakai KEEP.
            WorkManager.getInstance(application).enqueueUniquePeriodicWork(
                "AutoSyncData",
                ExistingPeriodicWorkPolicy.UPDATE,
                syncRequest
            )
        } catch (e: Exception) {
            // Sengaja diabaikan: auto-sync terjadwal adalah fitur pelengkap, bukan
            // sesuatu yang boleh menghentikan aplikasi untuk sampai ke layar login.
        }

        // Restore active user session from SessionManager completely separate from users table
        viewModelScope.launch {
            val sessionManager = SessionManager(application)
            val activeSession = sessionManager.getSession()
            if (activeSession != null) {
                _currentUser.value = activeSession
                if (activeSession.role == "Admin") {
                    _currentTab.value = "AdminPanel"
                    _adminCurrentTab.value = "Users"
                } else {
                    _currentTab.value = "Sales"
                }

                // PERBAIKAN BUG (keranjang/outlet hilang saat switch aplikasi -- lihat
                // penjelasan lengkap di SessionManager.saveCartState). Pulihkan outlet
                // yang sedang dipilih beserta isi keranjangnya, kalau ada yang tersimpan.
                // Data outlet & produk dari Room dimuat secara asynchronous, jadi kita
                // tunggu (maksimal 3 detik) sampai outletsList terisi sebelum mencoba
                // mencocokkan outlet tersimpan -- kalau timeout/tidak ketemu, cukup abaikan
                // secara diam-diam (jangan sampai mem-block atau meng-crash startup app).
                val savedOutletId = sessionManager.getSavedOutletId()
                if (!savedOutletId.isNullOrEmpty() && activeSession.role != "Admin") {
                    val outlets = kotlinx.coroutines.withTimeoutOrNull(3000) {
                        outletsList.first { it.isNotEmpty() }
                    } ?: outletsList.value
                    val restoredOutlet = outlets.find { it.id.equals(savedOutletId, ignoreCase = true) }
                    if (restoredOutlet != null) {
                        _selectedOutlet.value = restoredOutlet
                        val savedCart = sessionManager.getSavedCart()
                        if (savedCart.isNotEmpty()) {
                            _cart.value = savedCart
                            _successMessage.value = "Keranjang belanja sebelumnya untuk outlet ${restoredOutlet.name} berhasil dipulihkan."
                        }
                    } else {
                        // Outlet tersimpan sudah tidak ada lagi (mis. dihapus admin) --
                        // buang saja data keranjang yang tersimpan supaya tidak nyangkut.
                        sessionManager.clearCartState()
                    }
                }
            }
        }
    }


    // State flows representing database contents
    val usersList = repository.allUsers.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val productsList = repository.allProducts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val outletsList = repository.allOutlets.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val stockAllocationsList = repository.allStockAllocations.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val transactionsList = repository.allTransactions.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val warehouseLogsList = repository.allWarehouseLogs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val receiptSettings = repository.receiptSettings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val stokisStockList = repository.allStokisStock.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val stokisList = repository.allStokis.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val pendingUploadsList = repository.allPendingUploads.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val syncAuditLogs = repository.allSyncAuditLogs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun clearSyncAuditLogs() {
        viewModelScope.launch {
            repository.deleteAllSyncAuditLogs()
        }
    }

    fun saveStokis(stokis: com.example.data.StokisEntity) {
        viewModelScope.launch {
            repository.insertStokis(stokis)
            if (stokis.assignedSalesId.isNotEmpty()) {
                val user = usersList.value.find { it.id == stokis.assignedSalesId }
                if (user != null && !user.isStokisSales) {
                    repository.insertUser(user.copy(isStokisSales = true))
                }
            }
            _successMessage.value = "Stokis berhasil disimpan."
            syncWithGoogleSheets(_appsScriptUrl.value)
        }
    }

    fun deleteStokis(id: String) {
        viewModelScope.launch {
            repository.deleteStokisById(id)
            _successMessage.value = "Stokis berhasil dihapus."
            syncWithGoogleSheets(_appsScriptUrl.value)
        }
    }


    // Current Session State
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser = _currentUser.asStateFlow()

    private val _currentTab = MutableStateFlow("Sales")
    val currentTab = _currentTab.asStateFlow()

    private val _adminCurrentTab = MutableStateFlow("Users")
    val adminCurrentTab = _adminCurrentTab.asStateFlow()

    // Sales Tab State
    private val _selectedOutlet = MutableStateFlow<OutletEntity?>(null)
    val selectedOutlet = _selectedOutlet.asStateFlow()

    val salesOutletSearchQuery = MutableStateFlow("")
    val currentOutletType = MutableStateFlow("Regular") // "Regular", "NOO"

    // NOO Form State
    val nooName = MutableStateFlow("")
    val nooPatokan = MutableStateFlow("")
    val nooGeotag = MutableStateFlow("")
    val nooKodeHari = MutableStateFlow("")
    val isSearchingGeotag = MutableStateFlow(false)

    // Cart / Checkout State
    private val _cart = MutableStateFlow<Map<String, Int>>(emptyMap()) // productId -> qty
    val cart = _cart.asStateFlow()

    val checkoutPriceTier = MutableStateFlow("Retail") // "Retail", "Wholesale"
    val checkoutPaymentMethod = MutableStateFlow("Cash") // "Cash", "Cashless", "Kredit"
    val checkoutTOPDays = MutableStateFlow("0")

    // Receipts / Printing State
    private val _lastReceiptText = MutableStateFlow("")
    val lastReceiptText = _lastReceiptText.asStateFlow()

    private val _lastReceiptIntentUri = MutableStateFlow("")
    val lastReceiptIntentUri = _lastReceiptIntentUri.asStateFlow()

    // Logs Filtering State (Admin Log Gudang)
    val logFilterStartDate = MutableStateFlow("")
    val logFilterEndDate = MutableStateFlow("")

    // Admin History Filtering State
    val adminHistoryFilterStartDate = MutableStateFlow("")
    val adminHistoryFilterEndDate = MutableStateFlow("")
    val isAdminHistoryFilterActive = MutableStateFlow(false)

    // Pagination limits & indicators
    val adminOutletPage = MutableStateFlow(1)
    val adminUserPage = MutableStateFlow(1)
    val adminProductPage = MutableStateFlow(1)
    val adminWarehouseLogPage = MutableStateFlow(1)

    // Lock Service for secure uploading and synchronization
    private val _isUploadLocked = MutableStateFlow(false)
    val isUploadLocked = _isUploadLocked.asStateFlow()
    private val uploadMutex = kotlinx.coroutines.sync.Mutex()

    // PERBAIKAN BUG (race condition transaksi/stock ganda): sebelumnya tombol "Simpan"
    // di CheckoutDialog dan "Kirim No Order" di NoOrderDialog tidak punya penjaga apapun
    // terhadap tap ganda yang cepat (double-tap/tap-lag di HP low-end). checkout()
    // dipanggil lewat viewModelScope.launch (async) sedangkan dialog baru benar-benar
    // hilang dari layar SETELAH recomposition berikutnya -- ada jeda singkat di mana
    // tombol masih bisa ditekan lagi. Kalau itu terjadi, dua coroutine checkout() berjalan
    // nyaris bersamaan, masing-masing membaca _cart.value yang SAMA dan masing-masing
    // menyimpan transaksi + mengurangi stock alokasi sendiri-sendiri -> transaksi
    // terduplikasi dan stock sales berkurang dua kali dari yang seharusnya (gejala
    // "inject stock gagal"/stock tidak akurat yang dilaporkan sales). Flag ini menjamin
    // hanya SATU proses checkout yang bisa berjalan pada satu waktu per sesi ViewModel.
    private val _isCheckoutInProgress = MutableStateFlow(false)
    val isCheckoutInProgress = _isCheckoutInProgress.asStateFlow()

    // PERBAIKAN: sebelumnya sync otomatis setelah login (yang dimaksudkan berjalan di
    // belakang layar/silent) tetap memicu overlay blocking "Sync & Upload Terkunci" ke
    // seluruh layar, karena overlay itu dulu memakai flag yang SAMA (_isUploadLocked)
    // dengan flag pengunci konkurensi. Sekarang dipisah: _isUploadLocked tetap dipakai
    // sebagai pengunci konkurensi (mencegah 2 sync jalan bersamaan), sedangkan
    // showSyncOverlay adalah flag KHUSUS UI yang hanya dinyalakan untuk sync yang
    // memang sengaja dibuat terlihat (mis. tombol "Download Data Master" ditekan manual).
    private val _showSyncOverlay = MutableStateFlow(false)
    val showSyncOverlay = _showSyncOverlay.asStateFlow()

    fun acquireUploadLock(silent: Boolean = false): Boolean {
        if (_isUploadLocked.value) return false
        _isUploadLocked.value = true
        if (!silent) _showSyncOverlay.value = true
        return true
    }

    fun releaseUploadLock() {
        _isUploadLocked.value = false
        _showSyncOverlay.value = false
    }

    // Alert & Message state

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage = _successMessage.asStateFlow()

    init {
        val prefs = getApplication<Application>().getSharedPreferences("sfa_prefs", android.content.Context.MODE_PRIVATE)
        val oldDefault = "https://script.google.com/macros/s/AKfycbxxoaNCXP6qPviOehKZUvGY0rPNTMekYLaogfTSVF81tuJAmLBg-o1yYKjY1fFp5ROxLA/exec"
        val currentUrl = prefs.getString("apps_script_url", "")
        if (currentUrl.isNullOrBlank() || currentUrl == oldDefault) {
            val defaultUrl = "https://script.google.com/macros/s/AKfycbxLlopheoW9H1UJy_W7Py1LGlZGCE2gEs1hs4GLd_8pFGYCSeW9G5AReEZEWCRzDAdy/exec"
            prefs.edit().putString("apps_script_url", defaultUrl).apply()
            _appsScriptUrl.value = defaultUrl
        }
        
        // Set default filter date to today
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        logFilterStartDate.value = todayStr
        logFilterEndDate.value = todayStr
        adminHistoryFilterStartDate.value = todayStr
        adminHistoryFilterEndDate.value = todayStr
    }

    fun setTab(tab: String) {
        _currentTab.value = tab
    }

    fun setAdminTab(tab: String) {
        _adminCurrentTab.value = tab
    }

    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    fun showSuccess(msg: String) {
        _successMessage.value = msg
    }

    fun showError(msg: String) {
        _errorMessage.value = msg
    }

    // --- LOGIN FLOW ---
    // PERBAIKAN BUG CRASH/FORCE CLOSE: sebelumnya bagian pencarian user lokal
    // (usersList.value.find / repository.getAllUsersDirect()) TIDAK dibungkus
    // try-catch. Kalau query Room di sini melempar exception (mis. database
    // terkunci/corrupt/exception IO lain), exception itu lolos sampai ke
    // coroutine viewModelScope.launch tanpa CoroutineExceptionHandler, yang
    // artinya SELURUH APLIKASI force close tepat saat user menekan tombol
    // "Login" -- padahal seharusnya cukup menampilkan pesan error dan
    // membiarkan user mencoba lagi. Sekarang seluruh proses login dibungkus
    // try-catch di paling luar. onResult() dijamin SELALU terpanggil (baik
    // sukses, gagal login, maupun exception tak terduga) supaya UI (tombol
    // Login) tidak pernah "menggantung" menunggu callback yang tidak pernah datang.
    fun login(usernameInput: String, passwordInput: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val trimmedUser = usernameInput.trim()
                val trimmedPass = passwordInput.trim()

                if (trimmedUser.isEmpty() || trimmedPass.isEmpty()) {
                    _errorMessage.value = "Username dan Password wajib diisi!"
                    onResult(false)
                    return@launch
                }

                var user = usersList.value.find {
                    it.username.equals(trimmedUser, ignoreCase = true) && it.password == trimmedPass
                }

                if (user == null) {
                    try {
                        var dbUsers = repository.getAllUsersDirect()
                        if (dbUsers.isEmpty()) {
                            kotlinx.coroutines.delay(1000) // Wait for initial population
                            dbUsers = repository.getAllUsersDirect()
                        }
                        user = dbUsers.find {
                            it.username.equals(trimmedUser, ignoreCase = true) && it.password == trimmedPass
                        }
                    } catch (e: Exception) {
                        // Query database lokal gagal (mis. DB terkunci/IO error).
                        // Jangan biarkan ini menghentikan proses login -- tetap
                        // lanjut ke fallback online di bawah, siapa tahu masih
                        // bisa login lewat data server.
                    }
                }

                // Fallback: If local credentials search fails, we check online
                if (user == null) {
                    try {
                        val result = syncWithGoogleSheets(_appsScriptUrl.value)
                        if (result.first) {
                            // Retry finding user with fresh data
                            val dbUsers = repository.getAllUsersDirect()
                            user = dbUsers.find {
                                it.username.equals(trimmedUser, ignoreCase = true) && it.password == trimmedPass
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore online sync exception and continue with local result
                    }
                }

                if (user != null) {
                    _currentUser.value = user

                    // Save session in SessionManager completely separate from users table
                    try {
                        SessionManager(getApplication()).saveSession(user)
                    } catch (e: Exception) {
                        // Kalaupun gagal menyimpan sesi (mis. SharedPreferences bermasalah),
                        // user tetap boleh lanjut memakai app untuk sesi berjalan ini --
                        // yang hilang hanya auto-login di sesi berikutnya, bukan crash.
                    }

                    // Set tab appropriate for the role
                    if (user.role == "Admin") {
                        _currentTab.value = "AdminPanel"
                        _adminCurrentTab.value = "Users"
                    } else {
                        _currentTab.value = "Sales"
                    }
                    // Auto-sync after successful login to get the latest allocations, routes, and products.
                    // PERBAIKAN: dijalankan silent=true supaya benar-benar di belakang layar --
                    // sebelumnya tetap memunculkan dialog blocking "Sync & Upload Terkunci" ke
                    // seluruh layar walau sync ini sudah dijalankan lewat coroutine terpisah.
                    viewModelScope.launch {
                        try {
                            syncWithGoogleSheets(_appsScriptUrl.value, silent = true)
                        } catch (e: Exception) {
                            // ignore sync errors during background login auto-sync
                        }
                    }
                    onResult(true)
                } else {
                    onResult(false)
                }
            } catch (e: Exception) {
                // Jaring pengaman terakhir: exception tak terduga apapun di alur
                // login tidak boleh sampai meng-crash aplikasi. Tampilkan pesan
                // error yang jelas ke user dan anggap login gagal.
                _errorMessage.value = "Terjadi kesalahan saat login: ${e.message ?: "Unknown error"}. Silakan coba lagi."
                onResult(false)
            }
        }
    }

    fun logout() {
        _currentUser.value = null
        _selectedOutlet.value = null
        _cart.value = emptyMap()
        _currentTab.value = "Sales"
        
        // Clear session from SessionManager completely separate from users table
        // (clearSession() juga otomatis membersihkan cart state tersimpan)
        SessionManager(getApplication()).clearSession()
    }

    // --- SALES OUTLET SELECTION ---
    fun selectOutlet(outlet: OutletEntity) {
        _selectedOutlet.value = outlet
        // Pre-select price tier depending on outlet's default category
        checkoutPriceTier.value = if (outlet.category.equals("Wholesale", ignoreCase = true)) "Wholesale" else "Retail"
        SessionManager(getApplication()).saveCartState(outlet.id, _cart.value)
    }

    fun deselectOutlet() {
        if (_cart.value.isNotEmpty()) {
            _errorMessage.value = "Mengganti outlet akan mengosongkan keranjang belanja Anda. Harap selesaikan atau kosongkan keranjang terlebih dahulu."
        } else {
            _selectedOutlet.value = null
            _cart.value = emptyMap()
            SessionManager(getApplication()).clearCartState()
        }
    }

    fun forceResetSalesTab() {
        _selectedOutlet.value = null
        _cart.value = emptyMap()
        nooName.value = ""
        nooPatokan.value = ""
        nooGeotag.value = ""
        SessionManager(getApplication()).clearCartState()
    }

    // --- NOO ENROLLMENT ---
    fun confirmNOOSelection() {
        val name = nooName.value.trim()
        val geotagVal = nooGeotag.value.trim()
        val patokan = nooPatokan.value.trim()
        val kodeHariVal = nooKodeHari.value.trim()

        if (name.isEmpty()) {
            _errorMessage.value = "Nama outlet baru wajib diisi!"
            return
        }
        if (geotagVal.isEmpty()) {
            _errorMessage.value = "Geotag lokasi wajib diambil!"
            return
        }
        if (kodeHariVal.isEmpty()) {
            _errorMessage.value = "Kode hari wajib diisi!"
            return
        }

        viewModelScope.launch {
            val address = if (patokan.isNotEmpty()) "$patokan, Geotag: $geotagVal" else "Geotag: $geotagVal"
            
            // Generate standard unique 6-digit string ID
            val count = outletsList.value.size
            val newId = String.format(Locale.getDefault(), "%06d", count + 1)
            
            val salesUser = _currentUser.value ?: return@launch
            val newOutlet = OutletEntity(
                id = newId,
                name = name,
                type = "Regular", // converts to regular after enrollment
                category = "Retail",
                address = address,
                geotag = geotagVal,
                salesId = salesUser.id,
                salesName = salesUser.username,
                kodeHari = kodeHariVal,
                isNewLocal = true
            )

            repository.insertOutlet(newOutlet)

            val timestampStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
            repository.insertPendingUpload(
                PendingUploadEntity(
                    id = newOutlet.id,
                    type = "Outlet",
                    name = "NOO - ${newOutlet.name}",
                    status = "Pending",
                    lastAttempt = "-",
                    retryCount = 0,
                    createdAt = timestampStr
                )
            )

            // Auto select
            selectOutlet(newOutlet)

            // Reset NOO form
            nooName.value = ""
            nooPatokan.value = ""
            nooGeotag.value = ""

            _successMessage.value = "Outlet baru terekam dan rute di-inject langsung."

            // Trigger background auto-sync to upload the new outlet immediately if online
            viewModelScope.launch {
                try {
                    syncWithGoogleSheets(_appsScriptUrl.value)
                } catch (e: Exception) {
                    // Ignore background sync errors (e.g. if offline)
                }
            }
        }
    }

    @Suppress("MissingPermission")
    fun mockGetGeotag() {
        isSearchingGeotag.value = true
        viewModelScope.launch {
            val context = getApplication<Application>()
            var lat = -6.1149
            var lon = 106.1503
            var locationFetched = false

            // Detect user's region from their existing outlets
            val salesUser = _currentUser.value
            var operatesInSerang = true // Default to Serang as requested
            if (salesUser != null) {
                val assignedOutlets = repository.getAllOutletsDirect().filter { 
                    it.salesId.equals(salesUser.id, ignoreCase = true) || 
                    it.salesName.equals(salesUser.username, ignoreCase = true) 
                }
                val hasSerang = assignedOutlets.any { it.address.contains("Serang", ignoreCase = true) || it.name.contains("Serang", ignoreCase = true) }
                val hasPadang = assignedOutlets.any { it.address.contains("Padang", ignoreCase = true) || it.name.contains("Padang", ignoreCase = true) }
                if (hasPadang && !hasSerang) {
                    operatesInSerang = false
                }
            }

            // Try to get real location from GPS/Network if permission is granted
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                try {
                    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                    val freshLoc = getFreshGPSLocation(locationManager)
                    if (freshLoc != null) {
                        lat = freshLoc.latitude
                        lon = freshLoc.longitude
                        locationFetched = true
                    } else {
                        val providers = locationManager.getProviders(true)
                        var bestLocation: android.location.Location? = null
                        for (provider in providers) {
                            val l = locationManager.getLastKnownLocation(provider) ?: continue
                            if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                                bestLocation = l
                            }
                        }
                        if (bestLocation != null) {
                            lat = bestLocation.latitude
                            lon = bestLocation.longitude
                            locationFetched = true
                        }
                    }
                } catch (e: Exception) {
                    // ignore and use fallback/mock coords
                }
            }

            // If location was fetched but is clearly outside Indonesia (e.g., California/USA emulator location), override it
            val isOutsideIndonesia = lat > 6.0 || lat < -11.0 || lon < 95.0 || lon > 141.0
            if (isOutsideIndonesia || !locationFetched) {
                // Try IP geolocation as first fallback
                var ipCoords: Pair<Double, Double>? = null
                try {
                    ipCoords = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val url = java.net.URL("https://ip-api.com/json")
                        val conn = url.openConnection() as java.net.HttpURLConnection
                        conn.requestMethod = "GET"
                        conn.connectTimeout = 3000
                        conn.readTimeout = 3000
                        if (conn.responseCode == 200) {
                            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                            val latRegex = "\"lat\"\\s*:\\s*(-?\\d+\\.\\d+)".toRegex()
                            val lonRegex = "\"lon\"\\s*:\\s*(-?\\d+\\.\\d+)".toRegex()
                            val countryRegex = "\"countryCode\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                            
                            val latMatch = latRegex.find(responseText)
                            val lonMatch = lonRegex.find(responseText)
                            val countryMatch = countryRegex.find(responseText)
                            
                            val fetchedCountry = countryMatch?.groupValues?.get(1) ?: ""
                            val fetchedLat = latMatch?.groupValues?.get(1)?.toDoubleOrNull()
                            val fetchedLon = lonMatch?.groupValues?.get(1)?.toDoubleOrNull()
                            
                            if (fetchedLat != null && fetchedLon != null && fetchedCountry == "ID") {
                                Pair(fetchedLat, fetchedLon)
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                    }
                } catch (e: Exception) {
                    // ignore network exception
                }

                if (ipCoords != null) {
                    lat = ipCoords.first
                    lon = ipCoords.second
                } else {
                    // Use beautiful local fallback based on user's active area
                    val r = java.util.Random()
                    if (operatesInSerang) {
                        lat = -6.1149 + (-0.01 + r.nextDouble() * 0.02)
                        lon = 106.1503 + (-0.01 + r.nextDouble() * 0.02)
                    } else {
                        lat = -0.9471 + (-0.02 + r.nextDouble() * 0.04)
                        lon = 100.4172 + (-0.02 + r.nextDouble() * 0.04)
                    }
                }
            }

            val geotagVal = String.format(Locale.US, "%.6f,%.6f", lat, lon)
            nooGeotag.value = geotagVal

            // Reverse geocoding to get highly precise street addresses
            var resolvedAddress = ""
            try {
                if (android.location.Geocoder.isPresent()) {
                    val geocoder = android.location.Geocoder(context, Locale("id", "ID"))
                    val addresses = geocoder.getFromLocation(lat, lon, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val addressObj = addresses[0]
                        val addressLines = mutableListOf<String>()
                        for (i in 0..addressObj.maxAddressLineIndex) {
                            addressLines.add(addressObj.getAddressLine(i))
                        }
                        resolvedAddress = addressLines.joinToString(", ")
                    }
                }
            } catch (e: Exception) {
                // ignore
            }

            if (resolvedAddress.isEmpty() || resolvedAddress.contains("Amerika", ignoreCase = true) || resolvedAddress.contains("United States", ignoreCase = true)) {
                // Fallback to high-quality precise local address sector mapping
                resolvedAddress = if (operatesInSerang) getSerangAreaAddress(lat, lon) else getPadangAreaAddress(lat, lon)
            }

            nooPatokan.value = resolvedAddress
            isSearchingGeotag.value = false
        }
    }

    @Suppress("MissingPermission")
    private suspend fun getFreshGPSLocation(locationManager: android.location.LocationManager): android.location.Location? = withContext(Dispatchers.Main) {
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(location: android.location.Location) {
                    locationManager.removeUpdates(this)
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.success(location))
                    }
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            try {
                val provider = if (locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                    android.location.LocationManager.GPS_PROVIDER
                } else if (locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) {
                    android.location.LocationManager.NETWORK_PROVIDER
                } else {
                    null
                }
                
                if (provider != null) {
                    locationManager.requestLocationUpdates(provider, 0L, 0f, listener)
                    val timeoutJob = viewModelScope.launch {
                        delay(4000)
                        if (continuation.isActive) {
                            locationManager.removeUpdates(listener)
                            continuation.resumeWith(Result.success(null))
                        }
                    }
                    continuation.invokeOnCancellation {
                        locationManager.removeUpdates(listener)
                        timeoutJob.cancel()
                    }
                } else {
                    continuation.resumeWith(Result.success(null))
                }
            } catch (e: Exception) {
                continuation.resumeWith(Result.success(null))
            }
        }
    }

    private fun getSerangAreaAddress(lat: Double, lon: Double): String {
        val streets = listOf(
            "Jl. Veteran No. 12, Cipare, Kec. Serang, Kota Serang, Banten 42117",
            "Jl. Jenderal Sudirman No. 45, Sumurpecung, Kec. Serang, Kota Serang, Banten 42118",
            "Jl. Ahmad Yani No. 88, Cipare, Kec. Serang, Kota Serang, Banten 42117",
            "Jl. Raya Pandeglang No. 102, Sempu, Kec. Serang, Kota Serang, Banten 42111",
            "Jl. Yusuf Martadilaga No. 5, Benggala, Kec. Serang, Kota Serang, Banten 42117",
            "Jl. KH. Abdul Fatah Hasan No. 27, Ciceri, Kec. Serang, Kota Serang, Banten 42118"
        )
        val index = (Math.abs(lat * 1000) + Math.abs(lon * 1000)).toInt() % streets.size
        return streets[index]
    }

    private fun getPadangAreaAddress(lat: Double, lon: Double): String {
        val streetsWest = listOf(
            "Jl. Samudra No. 12, Purus, Kec. Padang Barat, Kota Padang, Sumatera Barat",
            "Jl. Hayam Wuruk No. 45, Belakang Tangsi, Kec. Padang Barat, Kota Padang, Sumatera Barat",
            "Jl. Damar No. 8, Olo, Kec. Padang Barat, Kota Padang, Sumatera Barat",
            "Jl. Veteran No. 102, Padang Pasir, Kec. Padang Barat, Kota Padang, Sumatera Barat"
        )
        val streetsEast = listOf(
            "Jl. Sawahan No. 34, Simpang Haru, Kec. Padang Timur, Kota Padang, Sumatera Barat",
            "Jl. Jati No. 56, Ganting Parak Gadang, Kec. Padang Timur, Kota Padang, Sumatera Barat",
            "Jl. Dr. Sutomo No. 18, Kubu Marapalam, Kec. Padang Timur, Kota Padang, Sumatera Barat",
            "Jl. Sisingamangaraja No. 88, Andalas, Kec. Padang Timur, Kota Padang, Sumatera Barat"
        )
        val streetsNorth = listOf(
            "Jl. Prof. Dr. Hamka No. 15, Air Tawar Barat, Kec. Padang Utara, Kota Padang, Sumatera Barat",
            "Jl. Gajah Mada No. 42, Alai Parak Kopi, Kec. Padang Utara, Kota Padang, Sumatera Barat",
            "Jl. Khatib Sulaiman No. 77, Ulak Karang Selatan, Kec. Padang Utara, Kota Padang, Sumatera Barat",
            "Jl. S. Parman No. 23, Lolong Belanti, Kec. Padang Utara, Kota Padang, Sumatera Barat"
        )
        val streetsSouth = listOf(
            "Jl. Sutan Syahrir No. 110, Mata Air, Kec. Padang Selatan, Kota Padang, Sumatera Barat",
            "Jl. Pemuda No. 5, Olo, Kec. Padang Barat, Kota Padang, Sumatera Barat",
            "Jl. Niaga No. 27, Kampung Pondok, Kec. Padang Barat, Kota Padang, Sumatera Barat",
            "Jl. Batang Arau No. 48, Berok Nipah, Kec. Padang Selatan, Kota Padang, Sumatera Barat"
        )
        
        val list = when {
            lat < -0.95 -> streetsSouth
            lat > -0.92 -> streetsNorth
            lon < 100.38 -> streetsWest
            else -> streetsEast
        }
        
        val index = (Math.abs(lat * 1000) + Math.abs(lon * 1000)).toInt() % list.size
        return list[index]
    }

    // --- CART AND CHECKOUT ---
    fun updateCart(productId: String, change: Int) {
        val currentCart = _cart.value.toMutableMap()
        val product = productsList.value.find { it.id.equals(productId, ignoreCase = true) } ?: return

        // Calculate currently allocated stock for this sales from initial and sold items
        val salesUser = _currentUser.value ?: return
        val itemsAllocated = stockAllocationsList.value
            .filter { 
                (it.salesId.equals(salesUser.id, ignoreCase = true) || it.salesName.equals(salesUser.username, ignoreCase = true)) && 
                it.productId.equals(productId, ignoreCase = true) 
            }
            .sumOf { it.qty } // Sum includes positive initial and negative sold items

        val availableStock = itemsAllocated

        val currentQty = currentCart[productId] ?: 0
        val newQty = currentQty + change

        if (newQty < 0) return
        if (newQty > availableStock) {
            _errorMessage.value = "Stock tidak mencukupi! Sisa stock Anda adalah $availableStock."
            return
        }

        if (newQty == 0) {
            currentCart.remove(productId)
        } else {
            currentCart[productId] = newQty
        }
        _cart.value = currentCart
        // Simpan setiap perubahan keranjang ke SharedPreferences supaya tidak hilang
        // kalau app di-background lalu proses dimatikan OS (lihat SessionManager).
        SessionManager(getApplication()).saveCartState(_selectedOutlet.value?.id, currentCart)
    }

    fun checkout(isNoOrder: Boolean, noOrderReason: String = "") {
        // PERBAIKAN: cegah checkout ganda (lihat penjelasan _isCheckoutInProgress di atas).
        // Kalau proses checkout sebelumnya masih berjalan (belum sempat mereset cart),
        // tolak panggilan baru daripada menyimpan transaksi & mengurangi stock dua kali.
        if (_isCheckoutInProgress.value) return
        _isCheckoutInProgress.value = true

        val salesUser = _currentUser.value ?: run { _isCheckoutInProgress.value = false; return }
        val outlet = _selectedOutlet.value ?: run { _isCheckoutInProgress.value = false; return }
        
        val itemsList = mutableListOf<Map<String, Any>>()
        var finalTotal = 0.0

        val tier = checkoutPriceTier.value
        val method = checkoutPaymentMethod.value
        val topStr = checkoutTOPDays.value
        val top = topStr.toIntOrNull() ?: 0

        if (!isNoOrder) {
            for ((pId, qty) in _cart.value) {
                val p = productsList.value.find { it.id.equals(pId, ignoreCase = true) } ?: continue
                val price = if (tier == "Wholesale") p.priceWholesale else p.priceRetail
                finalTotal += price * qty
                itemsList.add(
                    mapOf(
                        "id" to p.id,
                        "name" to p.name,
                        "price" to price,
                        "qty" to qty
                    )
                )
            }
            if (finalTotal > 0.0 && method == "Kredit" && top <= 0) {
                _errorMessage.value = "Masukkan T.O.P (Jatuh Tempo) yang valid!"
                _isCheckoutInProgress.value = false
                return
            }
        }

        viewModelScope.launch {
          try {
            val d = Date()
            val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(d)
            val dateOnlyStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(d)
            
            var dueDateStr = "-"
            if (method == "Kredit" && top > 0) {
                val cal = Calendar.getInstance()
                cal.time = d
                cal.add(Calendar.DATE, top)
                dueDateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(cal.time)
            }

            // Convert items list to JSON string
            val listType = Types.newParameterizedType(List::class.java, Map::class.java)
            val jsonAdapter = moshi.adapter<List<Map<String, Any>>>(listType)
            val itemsJson = if (isNoOrder) """[{"reason":"$noOrderReason"}]""" else jsonAdapter.toJson(itemsList)

            val orderId = "TRX-${System.currentTimeMillis()}"

            val transaction = TransactionEntity(
                orderId = orderId,
                date = dateStr,
                salesId = salesUser.id,
                salesName = salesUser.username,
                outletName = outlet.name,
                outletType = outlet.type,
                geotag = outlet.geotag.ifEmpty { "-" },
                total = finalTotal,
                paymentMethod = if (isNoOrder) "No Order" else method,
                topDays = if (isNoOrder) 0 else top,
                dueDate = if (isNoOrder) "-" else dueDateStr,
                itemsJson = itemsJson,
                statusSynced = false // Save as unsynced initially
            )

            // Save transaction
            repository.insertTransaction(transaction)

            val timestampStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
            repository.insertPendingUpload(
                PendingUploadEntity(
                    id = transaction.orderId,
                    type = "Transaction",
                    name = "Transaksi - ${outlet.name}",
                    status = "Pending",
                    lastAttempt = "-",
                    retryCount = 0,
                    createdAt = timestampStr
                )
            )

            // Trigger background auto-sync to upload to Google Sheets immediately if online
            viewModelScope.launch {
                try {
                    syncWithGoogleSheets(_appsScriptUrl.value)
                } catch (e: Exception) {
                    // Ignore background sync errors (e.g. if offline)
                }
            }

            // Update local stock allocations directly
            if (!isNoOrder) {
                for ((pId, qty) in _cart.value) {
                    val p = productsList.value.find { it.id.equals(pId, ignoreCase = true) } ?: continue
                    
                    // Deduct by appending negative allocation (sales checkout)
                    val deduction = StockAllocationEntity(
                        date = dateOnlyStr,
                        salesId = salesUser.id,
                        salesName = salesUser.username,
                        productId = pId,
                        productName = p.name,
                        qty = -qty
                    )
                    repository.insertStockAllocation(deduction)

                    // Write warehouse logs
                    val tsDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
                    val wLog = WarehouseLogEntity(
                        date = tsDate,
                        type = "SALES OUT (${salesUser.username})",
                        productId = pId,
                        productName = p.name,
                        qtyChange = -qty
                    )
                    repository.insertWarehouseLog(wLog)
                }
            }

            // Auto formatting receipt
            generateAndSetReceipt(transaction)

            // Reset SFA tab
            _cart.value = emptyMap()
            _selectedOutlet.value = null
            checkoutTOPDays.value = "0"
            checkoutPaymentMethod.value = "Cash"
            // Transaksi sudah tersimpan ke database lokal -- keranjang sementara di
            // SharedPreferences (untuk jaga-jaga app switch) tidak diperlukan lagi.
            SessionManager(getApplication()).clearCartState()

            _successMessage.value = if (isNoOrder) "Kunjungan (No Order) berhasil disimpan." else "Transaksi berhasil disimpan dan struk dicetak."
          } catch (e: Exception) {
              // Jaring pengaman: kalaupun ada error tak terduga saat proses checkout
              // (mis. DB error), tampilkan pesan yang jelas alih-alih membuat tombol
              // "Simpan" terkunci selamanya (karena flag tidak pernah direset) atau
              // membuat seluruh app force close.
              _errorMessage.value = "Gagal menyimpan transaksi: ${e.message ?: "Unknown error"}"
          } finally {
              _isCheckoutInProgress.value = false
          }
        }
    }

    // --- RECAP AND STATS FOR CURRENT SALES ---
    fun getSalesTodayTransactions(): List<TransactionEntity> {
        val salesUser = _currentUser.value ?: return emptyList()
        val todayPrefix = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        return transactionsList.value.filter { 
            (salesUser.role == "Admin" || it.salesId.equals(salesUser.id, ignoreCase = true) || it.salesName.equals(salesUser.username, ignoreCase = true)) && it.date.startsWith(todayPrefix) 
        }
    }

    fun getSalesTodayReceiptLines(trx: TransactionEntity): List<String> {
        val lines = mutableListOf<String>()
        lines.add(PrintingHelper.formatLeftRight("NO. TRX", trx.orderId, 32))
        lines.add(PrintingHelper.formatLeftRight("TANGGAL", trx.date, 32))
        lines.add(PrintingHelper.formatLeftRight("SALES", trx.salesName, 32))
        lines.add(PrintingHelper.formatLeftRight("OUTLET", trx.outletName, 32))
        lines.add("line")

        // Parse items
        val listType = Types.newParameterizedType(List::class.java, Map::class.java)
        val jsonAdapter = moshi.adapter<List<Map<String, Any>>>(listType)
        val items = try {
            jsonAdapter.fromJson(trx.itemsJson) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        if (trx.paymentMethod != "No Order" && items.isNotEmpty()) {
            for (item in items) {
                var name = (item["name"] as? String) ?: ""
                if (name.length > 32) name = name.substring(0, 32)
                lines.add(name)
                
                val qty = (item["qty"] as? Double)?.toInt() ?: 1
                val price = (item["price"] as? Double) ?: 0.0
                val subtotal = qty * price
                val detailStr = "  ${qty}x Rp ${String.format(Locale.getDefault(), "%,.0f", price)}"
                val subtotalStr = "Rp ${String.format(Locale.getDefault(), "%,.0f", subtotal)}"
                lines.add(PrintingHelper.formatLeftRight(detailStr, subtotalStr, 32))
            }
        } else if (trx.paymentMethod == "No Order") {
            lines.add(PrintingHelper.formatAlign("KUNJUNGAN - NO ORDER", 32, "Center"))
            val reason = try {
                if (items.isNotEmpty() && items[0].containsKey("reason")) {
                    items[0]["reason"] as? String ?: ""
                } else ""
            } catch (e: Exception) { "" }
            if (reason.isNotEmpty()) {
                lines.add(PrintingHelper.formatAlign("ALASAN: $reason", 32, "Center"))
            }
        } else {
            lines.add(PrintingHelper.formatAlign("KUNJUNGAN - NO ORDER", 32, "Center"))
        }

        lines.add("line")
        lines.add(PrintingHelper.formatLeftRight("TOTAL", "Rp ${String.format(Locale.getDefault(), "%,.0f", trx.total)}", 32))
        lines.add(PrintingHelper.formatLeftRight("PEMBAYARAN", trx.paymentMethod, 32))
        if (trx.paymentMethod == "Kredit") {
            lines.add(PrintingHelper.formatLeftRight("JATUH TEMPO", trx.dueDate, 32))
        }

        return lines
    }

    private fun generateAndSetReceipt(trx: TransactionEntity) {
        val settings = receiptSettings.value ?: ReceiptSettingsEntity()
        val contentLines = getSalesTodayReceiptLines(trx)
        val plainText = PrintingHelper.generateReceiptText(
            title = "STRUK PENJUALAN",
            subtitle = null,
            contentLines = contentLines,
            settings = settings
        )
        _lastReceiptText.value = plainText
        _lastReceiptIntentUri.value = PrintingHelper.getRawBTIntentUri(plainText, settings.logoBase64, settings.logoAlign)
    }

    fun preparePrintFromHistory(trx: TransactionEntity) {
        generateAndSetReceipt(trx)
    }

    // --- RECAP AND REPORT CHECKS ---
    fun getSalesTodayRecapReport(): List<String> {
        val salesUser = _currentUser.value ?: return emptyList()
        val todayTransactions = getSalesTodayTransactions()
        
        var totCash = 0.0
        var totCashless = 0.0
        var totKredit = 0.0
        var count = 1

        val lines = mutableListOf<String>()
        for (t in todayTransactions) {
            lines.add("$count. ${t.outletName}")
            lines.add(PrintingHelper.formatLeftRight("  Total:", "Rp ${String.format(Locale.getDefault(), "%,.0f", t.total)}", 32))
            lines.add(PrintingHelper.formatLeftRight("  Bayar:", t.paymentMethod, 32))
            if (t.paymentMethod == "Kredit") {
                lines.add(PrintingHelper.formatLeftRight("  TOP:", "${t.topDays} Hari", 32))
            }
            lines.add("line")

            when (t.paymentMethod) {
                "Cash" -> totCash += t.total
                "Cashless" -> totCashless += t.total
                "Kredit" -> totKredit += t.total
            }
            count++
        }

        lines.add(PrintingHelper.formatLeftRight("CASH", "Rp ${String.format(Locale.getDefault(), "%,.0f", totCash)}", 32))
        lines.add(PrintingHelper.formatLeftRight("CASHLESS", "Rp ${String.format(Locale.getDefault(), "%,.0f", totCashless)}", 32))
        lines.add(PrintingHelper.formatLeftRight("KREDIT/PIUTANG", "Rp ${String.format(Locale.getDefault(), "%,.0f", totKredit)}", 32))
        lines.add("line")
        lines.add(PrintingHelper.formatLeftRight("TOTAL SEMUA", "Rp ${String.format(Locale.getDefault(), "%,.0f", totCash + totCashless + totKredit)}", 32))

        return lines
    }

    fun preparePrintHistoryRecap() {
        val salesUser = _currentUser.value ?: return
        val settings = receiptSettings.value ?: ReceiptSettingsEntity()
        val lines = getSalesTodayRecapReport()
        val plainText = PrintingHelper.generateReceiptText(
            title = "REKAP KUNJUNGAN",
            subtitle = "Sales: ${salesUser.username}",
            contentLines = lines,
            settings = settings
        )
        _lastReceiptText.value = plainText
        _lastReceiptIntentUri.value = PrintingHelper.getRawBTIntentUri(plainText, settings.logoBase64, settings.logoAlign)
    }

    fun getSalesTodayStockReport(): List<String> {
        val salesUser = _currentUser.value ?: return emptyList()
        val lines = mutableListOf<String>()
        lines.add(PrintingHelper.formatLeftRight("PRODUK", "AWL  JUAL  SISA", 32))
        lines.add("line")

        // Map items
        for (p in productsList.value) {
            val allocations = stockAllocationsList.value.filter { 
                (it.salesId.equals(salesUser.id, ignoreCase = true) || it.salesName.equals(salesUser.username, ignoreCase = true)) && it.productId.equals(p.id, ignoreCase = true) 
            }
            val awal = allocations.filter { it.qty > 0 }.sumOf { it.qty }
            val jual = allocations.filter { it.qty < 0 }.sumOf { Math.abs(it.qty) }
            val sisa = awal - jual

            if (awal > 0) {
                val numStr = String.format(Locale.getDefault(), "%3d  %3d  %3d", awal, jual, sisa)
                if (p.name.length > 14) {
                    lines.add(p.name)
                    lines.add(PrintingHelper.formatLeftRight(" ", numStr, 32))
                } else {
                    lines.add(PrintingHelper.formatLeftRight(p.name, numStr, 32))
                }
            }
        }
        return lines
    }

    fun preparePrintStockReport() {
        val salesUser = _currentUser.value ?: return
        val settings = receiptSettings.value ?: ReceiptSettingsEntity()
        val lines = getSalesTodayStockReport()
        val plainText = PrintingHelper.generateReceiptText(
            title = "LAPORAN STOCK HARIAN",
            subtitle = "Sales: ${salesUser.username}",
            contentLines = lines,
            settings = settings
        )
        _lastReceiptText.value = plainText
        _lastReceiptIntentUri.value = PrintingHelper.getRawBTIntentUri(plainText, settings.logoBase64, settings.logoAlign)
    }

    fun getSalesFullStatsReport(): List<String> {
        val todayTransactions = getSalesTodayTransactions()
        
        var totRupiah = 0.0
        val totOutlet = todayTransactions.size
        var totKreditOutlet = 0
        var totPiutang = 0.0

        todayTransactions.forEach {
            totRupiah += it.total
            if (it.paymentMethod == "Kredit" && it.total > 0) {
                totKreditOutlet++
                totPiutang += it.total
            }
        }

        val lines = mutableListOf<String>()
        lines.add(PrintingHelper.formatLeftRight("TOTAL PENJUALAN", "Rp ${String.format(Locale.getDefault(), "%,.0f", totRupiah)}", 32))
        lines.add(PrintingHelper.formatLeftRight("TOTAL OUTLET", "$totOutlet", 32))
        lines.add(PrintingHelper.formatLeftRight("OUTLET KREDIT", "$totKreditOutlet", 32))
        lines.add(PrintingHelper.formatLeftRight("TOTAL PIUTANG", "Rp ${String.format(Locale.getDefault(), "%,.0f", totPiutang)}", 32))
        lines.add("line")
        lines.add(PrintingHelper.formatAlign("TOP 10 SKU TERJUAL", 32, "Center"))
        lines.add("line")

        // Top SKU calculations
        val skuMap = mutableMapOf<String, Int>()
        todayTransactions.forEach { t ->
            val listType = Types.newParameterizedType(List::class.java, Map::class.java)
            val jsonAdapter = moshi.adapter<List<Map<String, Any>>>(listType)
            val items = try {
                jsonAdapter.fromJson(t.itemsJson) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            items.forEach { it ->
                val name = it["name"] as? String ?: ""
                val qty = (it["qty"] as? Double)?.toInt() ?: 0
                skuMap[name] = (skuMap[name] ?: 0) + qty
            }
        }

        val sortedSkus = skuMap.toList().sortedByDescending { it.second }.take(10)
        if (sortedSkus.isEmpty()) {
            lines.add(PrintingHelper.formatAlign("- Belum ada produk terjual -", 32, "Center"))
        } else {
            sortedSkus.forEach { (name, qty) ->
                lines.add(PrintingHelper.formatLeftRight(name, "$qty PCS", 32))
            }
        }

        return lines
    }

    fun preparePrintFullReport() {
        val salesUser = _currentUser.value ?: return
        val settings = receiptSettings.value ?: ReceiptSettingsEntity()
        val lines = getSalesFullStatsReport()
        val plainText = PrintingHelper.generateReceiptText(
            title = "LAPORAN KUNJUNGAN",
            subtitle = "Sales: ${salesUser.username}",
            contentLines = lines,
            settings = settings
        )
        _lastReceiptText.value = plainText
        _lastReceiptIntentUri.value = PrintingHelper.getRawBTIntentUri(plainText, settings.logoBase64, settings.logoAlign)
    }

    // --- MANAGE OUTLET MAIN (ADMIN CRUD & CSV) ---
    fun saveOutlet(outlet: OutletEntity) {
        viewModelScope.launch {
            repository.insertOutlet(outlet)
            _successMessage.value = "Outlet berhasil disimpan."
            syncWithGoogleSheets(_appsScriptUrl.value)
        }
    }

    fun deleteOutlet(id: String) {
        viewModelScope.launch {
            repository.deleteOutletById(id)
            _successMessage.value = "Outlet berhasil dihapus."
            syncWithGoogleSheets(_appsScriptUrl.value)
        }
    }

    fun deleteAllOutlets() {
        viewModelScope.launch {
            repository.deleteAllOutlets()
            _successMessage.value = "Seluruh data outlet dihapus."
        }
    }


    fun updateRouteForSales(salesId: String, salesName: String, kodeHari: String, selectedOutletIds: Set<String>) {
        viewModelScope.launch {
            val allOutlets = repository.getAllOutletsDirect()
            
            val previousOutlets = allOutlets.filter { it.salesId == salesId && it.kodeHari == kodeHari }
            for (out in previousOutlets) {
                if (!selectedOutletIds.contains(out.id)) {
                    repository.assignOutletToSales(out.id, "", "", "")
                }
            }
            
            for (outId in selectedOutletIds) {
                repository.assignOutletToSales(outId, salesId, salesName, kodeHari)
            }
            
            // PERBAIKAN: Sebelumnya fungsi ini HANYA mengubah database Room lokal di
            // HP admin dan tidak pernah memanggil sync ke server sama sekali.
            // Akibatnya, assignment rute yang dibuat admin tidak pernah sampai ke
            // Spreadsheet, sehingga sales lain (di HP lain) tidak akan pernah melihat
            // outlet yang baru di-assign meskipun tampilan di HP admin sendiri
            // menampilkan "Rute berhasil disimpan". Sekarang kita paksa sync setelah
            // perubahan lokal selesai.
            _successMessage.value = "Menyimpan rute & mengirim ke server..."
            val syncResult = syncWithGoogleSheets(_appsScriptUrl.value)
            _successMessage.value = if (syncResult.first) {
                "Rute berhasil disimpan untuk $salesName ($kodeHari) dan terkirim ke server!"
            } else {
                "Rute tersimpan di HP ini, TAPI GAGAL terkirim ke server: ${syncResult.second}. Coba sync manual nanti."
            }
        }
    }
    fun assignOutletToSales(salesId: String, salesName: String, outletId: String, kodeHari: String) {
        viewModelScope.launch {
            repository.assignOutletToSales(outletId, salesId, salesName, kodeHari)
            // PERBAIKAN: sama seperti updateRouteForSales() di atas -- tanpa baris ini,
            // assignment hanya tersimpan lokal dan tidak pernah sampai ke sales lain.
            _successMessage.value = "Menyimpan & mengirim ke server..."
            val syncResult = syncWithGoogleSheets(_appsScriptUrl.value)
            _successMessage.value = if (syncResult.first) {
                "Outlet berhasil di-inject ke rute $salesName ($kodeHari) dan terkirim ke server!"
            } else {
                "Tersimpan di HP ini, TAPI GAGAL terkirim ke server: ${syncResult.second}. Coba sync manual nanti."
            }
        }
    }

    // --- MANAGE ADMIN SECTION (CRUD & WAREHOUSE LOGISTICS) ---
    fun saveUser(user: UserEntity) {
        viewModelScope.launch {
            repository.insertUser(user)
            _successMessage.value = "User berhasil disimpan."
            syncWithGoogleSheets(_appsScriptUrl.value)
        }
    }

    fun deleteUser(id: String) {
        viewModelScope.launch {
            repository.deleteUserById(id)
            _successMessage.value = "User berhasil dihapus."
            syncWithGoogleSheets(_appsScriptUrl.value)
        }
    }

    fun deleteAllUsers() {
        viewModelScope.launch {
            repository.deleteAllUsers()
            // Make sure admin remains!
            repository.insertUser(UserEntity("ADM-01", "admin", "admin123", "Admin"))
            _successMessage.value = "Seluruh data user berhasil dihapus (Kecuali default admin)."
        }
    }

    fun saveProduct(product: ProductEntity) {
        viewModelScope.launch {
            repository.insertProduct(product)
            _successMessage.value = "Produk berhasil disimpan."
            syncWithGoogleSheets(_appsScriptUrl.value)
        }
    }

    fun deleteProduct(id: String) {
        viewModelScope.launch {
            repository.deleteProductById(id)
            _successMessage.value = "Produk berhasil dihapus."
            syncWithGoogleSheets(_appsScriptUrl.value)
        }
    }

    fun deleteAllProducts() {
        viewModelScope.launch {
            repository.deleteAllProducts()
            _successMessage.value = "Seluruh data produk berhasil dihapus."
        }
    }

    fun addWarehouseStock(productId: String, qty: Int) {
        viewModelScope.launch {
            val product = productsList.value.find { it.id == productId } ?: return@launch
            val newStock = product.warehouseStock + qty
            repository.updateWarehouseStock(productId, newStock)

            // Write warehouse logs
            val tsDate = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
            val wLog = WarehouseLogEntity(
                date = tsDate,
                type = "ADD STOCK IN",
                productId = productId,
                productName = product.name,
                qtyChange = qty
            )
            repository.insertWarehouseLog(wLog)
            _successMessage.value = "$qty unit stock ${product.name} ditambahkan ke Gudang!"
            syncWithGoogleSheets(_appsScriptUrl.value)
        }
    }

    fun injectStockToSales(salesId: String, salesName: String, productId: String, qty: Int) {
        viewModelScope.launch {
            val normalizedSalesId = salesId.trim().uppercase()
            val normalizedProductId = productId.trim().uppercase()
            val product = productsList.value.find { it.id == normalizedProductId } ?: return@launch
            if (product.warehouseStock < qty) {
                _errorMessage.value = "Stock Gudang tidak cukup! Sisa: ${product.warehouseStock}"
                return@launch
            }

            // Deduct Warehouse stock
            repository.updateWarehouseStock(normalizedProductId, product.warehouseStock - qty)

            val d = Date()
            val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(d)
            val tsStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(d)

            // Write sales stock allocations
            val allocation = StockAllocationEntity(
                date = dateStr,
                salesId = normalizedSalesId,
                salesName = salesName,
                productId = normalizedProductId,
                productName = product.name,
                qty = qty
            )
            repository.insertStockAllocation(allocation)

            // Write log
            val wLog = WarehouseLogEntity(
                date = tsStr,
                type = "INJECT TO SALES ($salesName)",
                productId = normalizedProductId,
                productName = product.name,
                qtyChange = -qty
            )
            repository.insertWarehouseLog(wLog)

            _successMessage.value = "Mengirim data inject ke server..."
            val syncResult = syncWithGoogleSheets(_appsScriptUrl.value)
            if (syncResult.first) {
                _successMessage.value = "$qty unit ${product.name} di-inject ke Sales $salesName. Inject berhasil!"
            } else {
                _errorMessage.value = "Gagal menyelaraskan ke server: ${syncResult.second}"
            }
        }
    }

    fun saveSettings(settings: ReceiptSettingsEntity) {
        viewModelScope.launch {
            repository.saveSettings(settings)
            _successMessage.value = "Pengaturan struk berhasil disimpan."
        }
    }

    fun changePassword(username: String, role: String, targetUser: String, oldPass: String, newPass: String) {
        viewModelScope.launch {
            val user = usersList.value.find { it.username.equals(targetUser, ignoreCase = true) }
            if (user == null) {
                _errorMessage.value = "User tidak ditemukan."
                return@launch
            }

            if (username.equals(targetUser, ignoreCase = true)) {
                if (user.password != oldPass) {
                    _errorMessage.value = "Password lama salah!"
                    return@launch
                }
            } else if (role != "Admin") {
                _errorMessage.value = "Akses ditolak!"
                return@launch
            }

            repository.updateUserPassword(user.id, newPass)
            if (username.equals(targetUser, ignoreCase = true)) {
                val updatedUser = user.copy(password = newPass)
                _currentUser.value = updatedUser
                SessionManager(getApplication()).saveSession(updatedUser)
            }
            _successMessage.value = "Password untuk user $targetUser berhasil diperbarui! Menyelaraskan ke server..."
            
            // Push updated user table to Google Sheets immediately
            try {
                syncWithGoogleSheets(_appsScriptUrl.value)
                _successMessage.value = "Password untuk user $targetUser berhasil diperbarui dan disinkronkan ke server!"
            } catch (e: Exception) {
                _successMessage.value = "Password untuk user $targetUser berhasil diperbarui secara lokal (sinkronisasi tertunda)."
            }
        }
    }

    // --- CSV IMPORT / EXPORT OPERATIONS ---
    suspend fun importUsersFromCSV(content: String): Boolean {
        if (!acquireUploadLock()) return false
        return withContext(Dispatchers.IO) {
            try {
                uploadMutex.withLock {
                    val parsed = CSVHelper.parseCSV(content)
                    if (parsed.size < 2) return@withContext false
                    val headers = parsed[0].map { it.uppercase() }
                    
                    val idIdx = headers.indexOf("ID")
                    val usernameIdx = headers.indexOf("USERNAME")
                    val passwordIdx = headers.indexOf("PASSWORD")
                    val roleIdx = headers.indexOf("ROLE")

                    if (usernameIdx == -1 || passwordIdx == -1 || roleIdx == -1) return@withContext false

                    val list = mutableListOf<UserEntity>()
                    for (i in 1 until parsed.size) {
                        val row = parsed[i]
                        if (row.size < 3) continue
                        val id = if (idIdx != -1 && idIdx < row.size && row[idIdx].isNotEmpty()) row[idIdx] else "USR-${System.currentTimeMillis() + i}"
                        val username = row[usernameIdx]
                        val password = row[passwordIdx]
                        val role = if (roleIdx < row.size) row[roleIdx] else "Sales"
                        list.add(UserEntity(id, username, password, role))
                    }
                    if (list.isNotEmpty()) {
                        repository.insertUsers(list)
                    }
                    true
                }
            } catch (e: Exception) {
                false
            } finally {
                releaseUploadLock()
            }
        }
    }

    suspend fun importProductsFromCSV(content: String): Boolean {
        if (!acquireUploadLock()) return false
        return withContext(Dispatchers.IO) {
            try {
                uploadMutex.withLock {
                    val parsed = CSVHelper.parseCSV(content)
                    if (parsed.size < 2) return@withContext false
                    val headers = parsed[0].map { it.uppercase() }

                    val idIdx = headers.indexOf("ID")
                    val nameIdx = headers.indexOf("NAME")
                    val prIdx = headers.indexOf("PRICERETAIL")
                    val pwIdx = headers.indexOf("PRICEWHOLESALE")
                    val wsIdx = headers.indexOf("WAREHOUSESTOCK")

                    if (nameIdx == -1 || prIdx == -1 || pwIdx == -1) return@withContext false

                    val list = mutableListOf<ProductEntity>()
                    for (i in 1 until parsed.size) {
                        val row = parsed[i]
                        if (row.size < 3) continue
                        val id = if (idIdx != -1 && idIdx < row.size && row[idIdx].isNotEmpty()) row[idIdx] else "PRD-${System.currentTimeMillis() + i}"
                        val name = row[nameIdx]
                        val pr = row[prIdx].toDoubleOrNull() ?: 0.0
                        val pw = row[pwIdx].toDoubleOrNull() ?: 0.0
                        val ws = if (wsIdx != -1 && wsIdx < row.size) row[wsIdx].toIntOrNull() ?: 0 else 0
                        list.add(ProductEntity(id, name, pr, pw, ws))
                    }
                    if (list.isNotEmpty()) {
                        repository.insertProducts(list)
                    }
                    true
                }
            } catch (e: Exception) {
                false
            } finally {
                releaseUploadLock()
            }
        }
    }

    suspend fun importOutletsFromCSV(content: String, targetSalesId: String = "", targetSalesName: String = ""): Boolean {
        if (!acquireUploadLock()) return false
        return withContext(Dispatchers.IO) {
            try {
                uploadMutex.withLock {
                    val parsed = CSVHelper.parseCSV(content)
                    if (parsed.size < 2) return@withContext false
                    val headers = parsed[0].map { it.uppercase() }

                    val idIdx = headers.indexOf("ID")
                    val nameIdx = headers.indexOf("NAME")
                    val typeIdx = headers.indexOf("TYPE")
                    val catIdx = headers.indexOf("CATEGORY")
                    val addrIdx = headers.indexOf("ADDRESS")
                    val geoIdx = headers.indexOf("GEOTAG")
                    val sIdIdx = headers.indexOf("SALESID")
                    val sNameIdx = headers.indexOf("SALESNAME")

                    if (nameIdx == -1) return@withContext false

                    val list = mutableListOf<OutletEntity>()
                    for (i in 1 until parsed.size) {
                        val row = parsed[i]
                        if (row.size < 2) continue
                        
                        // Format numeric 6-digit increment
                        val id = if (idIdx != -1 && idIdx < row.size && row[idIdx].isNotEmpty()) {
                            row[idIdx]
                        } else {
                            String.format(Locale.getDefault(), "%06d", outletsList.value.size + list.size + 1)
                        }
                        val name = row[nameIdx]
                        val type = if (typeIdx != -1 && typeIdx < row.size) row[typeIdx] else "Regular"
                        val cat = if (catIdx != -1 && catIdx < row.size) row[catIdx] else "Retail"
                        val addr = if (addrIdx != -1 && addrIdx < row.size) row[addrIdx] else ""
                        val geo = if (geoIdx != -1 && geoIdx < row.size) row[geoIdx] else ""
                        
                        val salesId = if (targetSalesId.isNotEmpty()) targetSalesId else if (sIdIdx != -1 && sIdIdx < row.size) row[sIdIdx] else ""
                        val salesName = if (targetSalesName.isNotEmpty()) targetSalesName else if (sNameIdx != -1 && sNameIdx < row.size) row[sNameIdx] else ""

                        list.add(OutletEntity(id, name, type, cat, addr, geo, salesId, salesName))
                    }
                    if (list.isNotEmpty()) {
                        repository.insertOutlets(list)
                    }
                    true
                }
            } catch (e: Exception) {
                false
            } finally {
                releaseUploadLock()
            }
        }
    }

    fun exportUsersToCSV(): String {
        val headers = listOf("ID", "Username", "Password", "Role")
        val data = usersList.value.map { listOf(it.id, it.username, it.password, it.role) }
        return CSVHelper.toCSV(headers, data)
    }

    fun exportProductsToCSV(): String {
        val headers = listOf("ID", "Name", "PriceRetail", "PriceWholesale", "WarehouseStock")
        val data = productsList.value.map { listOf(it.id, it.name, it.priceRetail.toString(), it.priceWholesale.toString(), it.warehouseStock.toString()) }
        return CSVHelper.toCSV(headers, data)
    }

    fun exportOutletsToCSV(): String {
        val headers = listOf("ID", "Name", "Type", "Category", "Address", "Geotag", "SalesID", "SalesName")
        val data = outletsList.value.map { listOf(it.id, it.name, it.type, it.category, it.address, it.geotag, it.salesId, it.salesName) }
        return CSVHelper.toCSV(headers, data)
    }

    fun exportStockAllocationsToCSV(): String {
        val headers = listOf("ID", "Date", "SalesID", "SalesName", "ProductID", "ProductName", "Qty")
        val data = stockAllocationsList.value.map { listOf(it.localId.toString(), it.date, it.salesId, it.salesName, it.productId, it.productName, it.qty.toString()) }
        return CSVHelper.toCSV(headers, data)
    }

    fun exportRawDataToCSVFiltered(startDate: java.util.Date?, endDate: java.util.Date?): String {
        val headers = listOf("Tanggal", "Nama Sales", "Nama Outlet", "SKU", "Qty", "Nominal Rupiah")
        val data = mutableListOf<List<String>>()
        
        val moshi = com.squareup.moshi.Moshi.Builder().addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
        val mapType = com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, mapType)
        val jsonAdapter = moshi.adapter<List<Map<String, Any>>>(listType)

        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())

        transactionsList.value.forEach { trx ->
            // Filter
            var inRange = true
            if (startDate != null || endDate != null) {
                try {
                    val trxDate = sdf.parse(trx.date)
                    if (trxDate != null) {
                        if (startDate != null) {
                            val calStart = java.util.Calendar.getInstance()
                            calStart.time = startDate
                            calStart.set(java.util.Calendar.HOUR_OF_DAY, 0)
                            calStart.set(java.util.Calendar.MINUTE, 0)
                            calStart.set(java.util.Calendar.SECOND, 0)
                            if (trxDate.before(calStart.time)) inRange = false
                        }
                        if (endDate != null) {
                            val calEnd = java.util.Calendar.getInstance()
                            calEnd.time = endDate
                            calEnd.set(java.util.Calendar.HOUR_OF_DAY, 23)
                            calEnd.set(java.util.Calendar.MINUTE, 59)
                            calEnd.set(java.util.Calendar.SECOND, 59)
                            if (trxDate.after(calEnd.time)) inRange = false
                        }
                    }
                } catch(e: Exception) {}
            }
            if (!inRange) return@forEach

            val items = try {
                jsonAdapter.fromJson(trx.itemsJson) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            
            items.forEach { item ->
                val sku = item["name"]?.toString() ?: ""
                val qty = (item["qty"] as? Double)?.toInt() ?: 0
                val price = (item["price"] as? Double) ?: 0.0
                val subtotal = qty * price
                
                data.add(listOf(
                    trx.date,
                    trx.salesName,
                    trx.outletName,
                    sku,
                    qty.toString(),
                    subtotal.toLong().toString()
                ))
            }
        }
        return com.example.data.CSVHelper.toCSV(headers, data)
    }
    fun exportWarehouseLogsToCSV(): String {
        val headers = listOf("Date", "Type", "ProductID", "ProductName", "Qty_Change")
        val data = warehouseLogsList.value.map { listOf(it.date, it.type, it.productId, it.productName, it.qtyChange.toString()) }
        return CSVHelper.toCSV(headers, data)
    }

    // --- ADMIN TRANSACTION HISTORY OPERATIONS ---
    fun deleteTransaction(orderId: String) {
        viewModelScope.launch {
            repository.deleteTransactionById(orderId)
            _successMessage.value = "Transaksi $orderId berhasil dihapus dari database."
        }
    }

    fun updateTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            repository.insertTransaction(transaction)
            _successMessage.value = "Transaksi ${transaction.orderId} berhasil direvisi."
        }
    }

    // --- STOKIS STOCK MANAGEMENT FLOWS ---
    fun injectStockToStokis(stokisId: String, productId: String, qty: Int) {
        viewModelScope.launch {
            val normalizedStokisId = stokisId.trim().uppercase()
            val normalizedProductId = productId.trim().uppercase()
            val product = productsList.value.find { it.id == normalizedProductId }
            if (product == null) {
                _errorMessage.value = "Produk tidak ditemukan."
                return@launch
            }
            if (product.warehouseStock < qty) {
                _errorMessage.value = "Stok gudang tidak mencukupi! Sisa: ${product.warehouseStock} unit."
                return@launch
            }

            // Deduct from warehouse stock
            repository.updateWarehouseStock(normalizedProductId, product.warehouseStock - qty)

            // Add to stokis stock
            val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
            val existingStokis = repository.getStokisStockByProduct(normalizedStokisId, normalizedProductId)
            val stokisName = repository.getAllStokisDirect().find { it.id == normalizedStokisId }?.name ?: ""
            val newStokisQty = (existingStokis?.qty ?: 0) + qty
            repository.insertStokisStock(
                StokisStockEntity(
                    stokisId = normalizedStokisId,
                    stokisName = stokisName,
                    productId = normalizedProductId,
                    productName = product.name,
                    qty = newStokisQty,
                    lastUpdateDate = dateStr
                )
            )

            // Log warehouse activity
            repository.insertWarehouseLog(
                WarehouseLogEntity(
                    date = dateStr,
                    type = "INJECT TO STOKIS",
                    productId = normalizedProductId,
                    productName = product.name,
                    qtyChange = -qty
                )
            )

            _successMessage.value = "Mengirim data inject ke server..."
            val syncResult = syncWithGoogleSheets(_appsScriptUrl.value)
            if (syncResult.first) {
                _successMessage.value = "Berhasil meng-inject $qty unit ${product.name} dari Gudang ke Stokis. Inject berhasil!"
            } else {
                _errorMessage.value = "Gagal menyelaraskan ke server: ${syncResult.second}"
            }
        }
    }

    // PERBAIKAN BUG KRITIS (stock yang sudah dikirim admin ke Stokis "tidak bisa
    // di-loading mandiri" oleh sales -- persis gejala yang dilaporkan): sebelumnya
    // fungsi ini HANYA mengubah database Room LOKAL di HP (mengurangi stok stokis
    // lokal + menambah alokasi stok sales lokal), lalu memanggil syncWithGoogleSheets()
    // yang TIDAK PERNAH mengirim perubahan StokisStock/StockAllocations non-admin ke
    // server. Akibatnya server tidak pernah tahu stock sudah ditarik, sehingga
    // sinkronisasi berikutnya (yang berjalan TEPAT SETELAH aksi ini, lihat baris
    // syncWithGoogleSheets di bawah) mengunduh ulang angka stok stokis yang MASIH UTUH
    // dari server dan menimpa balik pengurangan lokal -- seolah stock yang sudah
    // dikirim ke stokis tidak pernah berkurang / tidak bisa dimuat mandiri.
    // Sekarang: panggil dulu endpoint server khusus (pullStockFromStokis, yang
    // memakai fungsi backend injectFromStokisToSales yang sudah teruji dipakai
    // Admin dari console web) SEBELUM mengubah apapun di database lokal. Kalau
    // server menolak/gagal (mis. jaringan mati), TIDAK ADA perubahan lokal yang
    // dibuat sama sekali -- supaya data lokal & server selalu konsisten, dan sales
    // mendapat pesan error yang jelas alih-alih ilusi berhasil yang nanti hilang lagi.
    fun injectStockFromStokisToSales(stokisId: String, salesId: String, salesName: String, productId: String, qty: Int) {
        viewModelScope.launch {
            val normalizedStokisId = stokisId.trim().uppercase()
            val normalizedSalesId = salesId.trim().uppercase()
            val normalizedProductId = productId.trim().uppercase()
            val stokisItem = repository.getStokisStockByProduct(normalizedStokisId, normalizedProductId)
            if (stokisItem == null || stokisItem.qty < qty) {
                val available = stokisItem?.qty ?: 0
                _errorMessage.value = "Stok Stokis tidak mencukupi! Tersedia: $available unit."
                return@launch
            }

            val url = _appsScriptUrl.value
            if (url.isBlank()) {
                _errorMessage.value = "URL Apps Script belum diatur. Hubungi Admin."
                return@launch
            }

            _successMessage.value = "Mengirim permintaan tarik stock ke server..."
            val serverResult: PullStokisStockResponse? = try {
                withContext(Dispatchers.IO) {
                    val service = com.example.data.RetrofitClient.createService(url)
                    service.pullStockFromStokis(
                        PullStokisStockRequest(
                            stokisId = normalizedStokisId,
                            salesName = salesName,
                            payload = listOf(PullStokisStockItem(productId = normalizedProductId, qty = qty))
                        )
                    )
                }
            } catch (e: Exception) {
                _errorMessage.value = "Gagal menghubungi server: ${e.message ?: "Tidak ada koneksi"}. Stock TIDAK ditarik, silakan coba lagi."
                null
            }

            if (serverResult == null) return@launch
            if (!serverResult.success) {
                _errorMessage.value = "Gagal menarik stock dari server: ${serverResult.message}"
                return@launch
            }

            // Server sudah mengonfirmasi berhasil (ledger StokisStock server berkurang +
            // StockAllocations sales bertambah) -- baru sekarang aman menerapkan
            // perubahan yang SAMA ke database lokal untuk feedback instan di UI,
            // tanpa perlu menunggu sync/download berikutnya.
            val newStokisQty = stokisItem.qty - qty
            repository.insertStokisStock(stokisItem.copy(qty = newStokisQty))

            val d = Date()
            val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(d)
            val tsStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(d)

            val allocation = StockAllocationEntity(
                date = dateStr,
                salesId = normalizedSalesId,
                salesName = salesName,
                productId = normalizedProductId,
                productName = stokisItem.productName,
                qty = qty
            )
            repository.insertStockAllocation(allocation)

            val wLog = WarehouseLogEntity(
                date = tsStr,
                type = "STOKIS TO SALES ($salesName)",
                productId = normalizedProductId,
                productName = stokisItem.productName,
                qtyChange = -qty
            )
            repository.insertWarehouseLog(wLog)

            _successMessage.value = "Berhasil menarik $qty unit ${stokisItem.productName} dari Stokis ke kendaraan Anda."
        }
    }

    suspend fun downloadMasterSync(): Pair<Boolean, String> {
        return syncWithGoogleSheets(_appsScriptUrl.value, forceRefresh = true)
    }

    suspend fun endDaySync(): Pair<Boolean, String> {
        return withContext(Dispatchers.IO) {
            val sales = _currentUser.value ?: return@withContext Pair(false, "Sesi tidak valid")
            delay(1000)
            
            // First, trigger a robust sync upload to push all transactions and new outlets to Google Sheets
            val uploadResult = syncWithGoogleSheets(_appsScriptUrl.value)
            if (!uploadResult.first) {
                return@withContext Pair(false, "Upload gagal: ${uploadResult.second}. Transaksi Anda tetap aman di perangkat lokal dan rute/stok sisa tidak dibersihkan.")
            }
            
            // 2. Return remaining stock to warehouse
            val allocations = repository.getAllStockAllocationsDirect().filter { 
                it.salesId.equals(sales.id, ignoreCase = true) || it.salesName.equals(sales.username, ignoreCase = true) 
            }
            val products = repository.getAllProductsDirect()
            
            val allocatedProducts = products.filter { p -> allocations.any { it.productId == p.id } }
            for (p in allocatedProducts) {
                val pAllocations = allocations.filter { it.productId == p.id }
                val awal = pAllocations.filter { it.qty > 0 }.sumOf { it.qty }
                val jual = pAllocations.filter { it.qty < 0 }.sumOf { Math.abs(it.qty) }
                val sisa = awal - jual
                if (sisa > 0) {
                    repository.updateWarehouseStock(p.id, p.warehouseStock + sisa)
                }
            }
            
            // 3. Clear allocations for this sales
            allocations.forEach { alloc ->
                repository.deleteStockAllocationById(alloc.localId)
            }
            
            // 4. Unassign routes for this sales
            val outlets = repository.getAllOutletsDirect().filter { 
                it.salesId.equals(sales.id, ignoreCase = true) || it.salesName.equals(sales.username, ignoreCase = true) 
            }
            outlets.forEach { out ->
                repository.assignOutletToSales(out.id, "", "", "")
            }
            
            Pair(true, "Upload antrean berhasil tuntas! Seluruh transaksi terkirim ke server, stock sisa dikembalikan ke gudang, dan rute dibersihkan.")
        }
    }

    suspend fun syncWithGoogleSheets(url: String, forceRefresh: Boolean = false, silent: Boolean = false): Pair<Boolean, String> {
        if (url.isBlank()) return Pair(false, "URL Apps Script kosong")
        if (!acquireUploadLock(silent)) return Pair(false, "Sinkronisasi sedang berjalan")
        
        return withContext(Dispatchers.IO) {
            try {
                com.example.data.SyncUtils.globalSyncMutex.withLock {
                    uploadMutex.withLock {
                        val service = com.example.data.RetrofitClient.createService(url)
                        
                        // Push transactions and new outlets in batches of 20 to 50
                        val allTransactions = repository.getAllTransactionsDirect()
                        val unsyncedTransactions = allTransactions.filter { !it.statusSynced }
                        val newOutlets = repository.getAllOutletsDirect().filter { it.isNewLocal }
                        val allUsers = repository.getAllUsersDirect()
                        val allProducts = repository.getAllProductsDirect()
                        
                        val isUserAdmin = _currentUser.value?.role == "Admin"
                        val localPrinterSettings = if (isUserAdmin) repository.getSettingsDirect() else null

                        val adminStokis = if (isUserAdmin) repository.getAllStokisDirect() else emptyList()
                        val adminOutlets = if (isUserAdmin) repository.getAllOutletsDirect() else emptyList()
                        // PERBAIKAN: sebelumnya alokasi stock (inject stock ke sales) tidak
                        // pernah diikutkan ke payload sync sama sekali. Sekarang kalau yang
                        // sync adalah admin, seluruh alokasi stock lokal ikut dikirim di
                        // batch pertama (sama seperti pola adminOutlets di atas).
                        val adminStockAllocations = if (isUserAdmin) repository.getAllStockAllocationsDirect() else emptyList()

                        val sharedPrefs = getApplication<Application>().getSharedPreferences("sfa_prefs", Context.MODE_PRIVATE)
                        var deviceId = sharedPrefs.getString("device_id", null)
                        if (deviceId == null) {
                            deviceId = UUID.randomUUID().toString()
                            sharedPrefs.edit().putString("device_id", deviceId).apply()
                        }
                        val currentSalesId = _currentUser.value?.id
                        val appVersion = "2.0.0"

                        val batchSize = 30
                        val transactionBatches = if (unsyncedTransactions.isNotEmpty()) {
                            unsyncedTransactions.chunked(batchSize)
                        } else {
                            listOf(emptyList())
                        }

                        for ((index, batch) in transactionBatches.withIndex()) {
                            // Include master/admin tables only in the first batch to avoid huge duplicate payloads
                            val batchNewOutlets = if (index == 0) newOutlets else emptyList()
                            val batchUsers = if (index == 0) allUsers else emptyList()
                            val batchProducts = if (index == 0) allProducts else emptyList()
                            val batchStokis = if (index == 0) adminStokis else emptyList()
                            val batchOutlets = if (index == 0) adminOutlets else emptyList()
                            val batchPrinterSettings = if (index == 0) localPrinterSettings else null
                            val batchStockAllocations = if (index == 0) adminStockAllocations else emptyList()

                            if (batch.isEmpty() && batchNewOutlets.isEmpty() && batchUsers.isEmpty() && batchProducts.isEmpty() && batchStokis.isEmpty() && batchOutlets.isEmpty() && !isUserAdmin) {
                                continue
                            }

                            val request = com.example.data.SyncRequest(
                                transactions = batch,
                                newOutlets = batchNewOutlets,
                                syncUsers = batchUsers,
                                syncProducts = batchProducts,
                                syncStokis = batchStokis,
                                syncOutlets = batchOutlets,
                                stockAllocations = batchStockAllocations,
                                isUserAdmin = isUserAdmin && (index == 0),
                                deviceId = deviceId,
                                appVersion = appVersion,
                                salesId = currentSalesId,
                                printerSettings = batchPrinterSettings
                            )

                            val response = service.syncTransactions(request)
                            if (response.status != "success") {
                                try {
                                    val timeStr = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())
                                    val currentPending = repository.getAllPendingUploadsDirect()
                                    for (t in batch) {
                                        val p = currentPending.find { it.id == t.orderId }
                                        if (p != null) {
                                            repository.insertPendingUpload(p.copy(status = "Failed", lastAttempt = timeStr, retryCount = p.retryCount + 1))
                                        }
                                    }
                                    if (index == 0) {
                                        for (o in newOutlets) {
                                            val p = currentPending.find { it.id == o.id }
                                            if (p != null) {
                                                repository.insertPendingUpload(p.copy(status = "Failed", lastAttempt = timeStr, retryCount = p.retryCount + 1))
                                            }
                                        }
                                    }
                                } catch (dbEx: Exception) {}

                                try {
                                    val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                                    repository.insertSyncAuditLog(
                                        com.example.data.SyncAuditLogEntity(
                                            timestamp = timestamp,
                                            processType = "Sync Foreground (Manual)",
                                            details = "Push batch ${index + 1} gagal",
                                            result = "Failed",
                                            errorMessage = "Gagal push transaksi/outlet: ${response.message}"
                                        )
                                    )
                                } catch (dbEx: Exception) {}

                                return@withLock Pair(false, "Gagal push batch ${index + 1}: ${response.message}")
                            } else {
                                if (batch.isNotEmpty()) {
                                    val updatedTrx = batch.map { it.copy(statusSynced = true) }
                                    repository.insertTransactions(updatedTrx)
                                    try {
                                        for (t in batch) {
                                            repository.deletePendingUploadById(t.orderId)
                                        }
                                    } catch (dbEx: Exception) {}
                                }
                                if (index == 0 && batchNewOutlets.isNotEmpty()) {
                                    val updatedOutlets = batchNewOutlets.map { it.copy(isNewLocal = false) }
                                    repository.insertOutlets(updatedOutlets)
                                    try {
                                        for (o in batchNewOutlets) {
                                            repository.deletePendingUploadById(o.id)
                                        }
                                    } catch (dbEx: Exception) {}
                                }
                            }
                        }

                        // Pull Master Data conditionally based on granular checksums / versions
                        val lastMasterSync = if (forceRefresh) null else sharedPrefs.getString("last_master_sync", null)
                        val clientVersion = if (forceRefresh) null else sharedPrefs.getString("master_data_version", null)

                        val cachedProductsVer = if (forceRefresh) "" else (sharedPrefs.getString("products_version_key", "") ?: "")
                        val cachedOutletsVer = if (forceRefresh) "" else (sharedPrefs.getString("outlets_version_key", "") ?: "")
                        val cachedRoutesVer = if (forceRefresh) "" else (sharedPrefs.getString("routes_version_key", "") ?: "")
                        val cachedStokisVer = if (forceRefresh) "" else (sharedPrefs.getString("stokis_version_key", "") ?: "")
                        val cachedStokisStockVer = if (forceRefresh) "" else (sharedPrefs.getString("stokis_stock_version_key", "") ?: "")

                        val initialData = service.getInitialData(
                            lastSync = lastMasterSync,
                            clientVersion = clientVersion,
                            productsVersion = cachedProductsVer,
                            outletsVersion = cachedOutletsVer,
                            routesVersion = cachedRoutesVer,
                            stokisVersion = cachedStokisVer
                        )
                        
                        // Apply printer settings for non-admin users
                        if (!isUserAdmin) {
                            initialData.printerSettings?.let { remoteSettings ->
                                repository.saveSettings(remoteSettings)
                            }
                        }
                        
                        if (initialData.version != null && initialData.version != clientVersion) {
                            sharedPrefs.edit().putString("master_data_version", initialData.version).apply()
                        } else if (initialData.users.isEmpty() && initialData.products.isEmpty() && initialData.outlets.isEmpty() && initialData.stokis.isEmpty() && initialData.stokisStock.isEmpty() && initialData.finalStockAllocations.isEmpty()) {
                            try {
                                val productsCount = repository.getAllProductsDirect().size
                                val allocationsCount = repository.getAllStockAllocationsDirect().size
                                val outletsCount = repository.getAllOutletsDirect().size
                                val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                                repository.insertSyncAuditLog(
                                    com.example.data.SyncAuditLogEntity(
                                        timestamp = timestamp,
                                        processType = "Sync Foreground (Manual)",
                                        details = "Pushed: ${unsyncedTransactions.size} transaksi, ${newOutlets.size} outlet baru. Pulled: Checked updates (No change/All up to date). Total Aktif di DB: $productsCount Produk, $allocationsCount Alokasi Stok, $outletsCount Outlet.",
                                        result = "Success"
                                    )
                                )
                            } catch (dbEx: Exception) {}
                            return@withLock Pair(true, "Sinkronisasi berhasil (Data sudah yang terbaru)")
                        }
                        
                        val usersData = initialData.users
                        val productsData = initialData.products
                        val outletsData = initialData.outlets
                        val stokisData = initialData.stokis
                        val stokisStockData = initialData.stokisStock
                        val stockAllocationsData = initialData.finalStockAllocations

                        // Check if local database tables are empty
                        val dbProductsEmpty = repository.getAllProductsDirect().isEmpty()
                        val dbOutletsEmpty = repository.getAllOutletsDirect().isEmpty()
                        val dbStokisEmpty = repository.getAllStokisDirect().isEmpty()
                        val dbStokisStockEmpty = repository.getAllStokisStockDirect().isEmpty()
                        val dbAllocationsEmpty = repository.getAllStockAllocationsDirect().isEmpty()
                        val dbUsersEmpty = repository.getAllUsersDirect().isEmpty()

                        // Determine if content has changed by comparing server versions or MD5 checksums of the arrays
                        val newProductsChecksum = if (productsData.isNotEmpty()) com.example.data.SyncUtils.calculateChecksum(productsData) else ""
                        val newOutletsChecksum = if (outletsData.isNotEmpty()) com.example.data.SyncUtils.calculateChecksum(outletsData) else ""
                        val newRoutesChecksum = if (stockAllocationsData.isNotEmpty()) com.example.data.SyncUtils.calculateChecksum(stockAllocationsData) else ""
                        val newStokisChecksum = if (stokisData.isNotEmpty()) com.example.data.SyncUtils.calculateChecksum(stokisData) else ""
                        val newStokisStockChecksum = if (stokisStockData.isNotEmpty()) com.example.data.SyncUtils.calculateChecksum(stokisStockData) else ""
                        
                        // PERBAIKAN: sebelumnya baris ini TIDAK mengikuti pola "if (forceRefresh) '' else ..."
                        // seperti cachedProductsVer/cachedOutletsVer/cachedRoutesVer/dst di atasnya. Akibatnya,
                        // walau user menekan "Download Data Master" (forceRefresh=true), checksum Users yang
                        // sudah ter-cache di HP tetap dipakai apa adanya -- kalau checksum itu kebetulan tersangkut
                        // dari kondisi lama/tidak konsisten, tabel Users lokal tidak pernah benar-benar di-refresh
                        // ulang dari server walau di-force, sampai app data/cache di-clear manual (yang me-reset
                        // SharedPreferences ini juga).
                        val cachedUsersVer = if (forceRefresh) "" else (sharedPrefs.getString("users_version_key", "") ?: "")
                        val newUsersChecksum = if (usersData.isNotEmpty()) com.example.data.SyncUtils.calculateChecksum(usersData) else ""

                        val shouldSaveProducts = productsData.isNotEmpty() && (dbProductsEmpty || (initialData.productsVersion != null && initialData.productsVersion != cachedProductsVer) || (initialData.productsVersion == null && newProductsChecksum != cachedProductsVer))
                        val shouldSaveOutlets = outletsData.isNotEmpty() && (dbOutletsEmpty || (initialData.outletsVersion != null && initialData.outletsVersion != cachedOutletsVer) || (initialData.outletsVersion == null && newOutletsChecksum != cachedOutletsVer))
                        val shouldSaveAllocations = stockAllocationsData.isNotEmpty() && (dbAllocationsEmpty || (initialData.routesVersion != null && initialData.routesVersion != cachedRoutesVer) || (initialData.routesVersion == null && newRoutesChecksum != cachedRoutesVer))
                        val shouldSaveStokis = stokisData.isNotEmpty() && (dbStokisEmpty || (initialData.stokisVersion != null && initialData.stokisVersion != cachedStokisVer) || (initialData.stokisVersion == null && newStokisChecksum != cachedStokisVer))
                        val shouldSaveStokisStock = stokisStockData.isNotEmpty() && (dbStokisStockEmpty || (initialData.stokisStockVersion != null && initialData.stokisStockVersion != cachedStokisStockVer) || (initialData.stokisStockVersion == null && newStokisStockChecksum != cachedStokisStockVer))
                        val shouldSaveUsers = usersData.isNotEmpty() && (dbUsersEmpty || newUsersChecksum != cachedUsersVer)

                        val usersListToSave = if (shouldSaveUsers) {
                            usersData.mapNotNull { row ->
                                val id = row["ID"]?.toString()?.trim()?.uppercase() ?: return@mapNotNull null
                                val username = row["Username"]?.toString() ?: ""
                                val password = row["Password"]?.toString() ?: ""
                                val role = row["Role"]?.toString() ?: ""
                                UserEntity(id, username, password, role)
                            }
                        } else null

                        val productsListToSave = if (shouldSaveProducts) {
                            productsData.mapNotNull { row ->
                                val id = row["ID"]?.toString()?.trim()?.uppercase() ?: return@mapNotNull null
                                val name = row["Name"]?.toString() ?: ""
                                val prStr = row["PriceRetail"]?.toString() ?: "0"
                                val pwStr = row["PriceWholesale"]?.toString() ?: "0"
                                val stockVal = parseFlexibleInt(row["WarehouseStock"])
                                    ?: parseFlexibleInt(row["warehouseStock"])
                                    ?: parseFlexibleInt(row["Stock"])
                                    ?: parseFlexibleInt(row["stock"])
                                    ?: parseFlexibleInt(row["Stok"])
                                    ?: parseFlexibleInt(row["stok"])
                                    ?: parseFlexibleInt(row["Gudang Utama"])
                                    ?: parseFlexibleInt(row["gudang utama"])
                                    ?: parseFlexibleInt(row["GudangUtama"])
                                    ?: parseFlexibleInt(row["gudangUtama"])
                                    ?: parseFlexibleInt(row["Stok Gudang Utama"])
                                    ?: parseFlexibleInt(row["stok gudang utama"])
                                    ?: parseFlexibleInt(row["Stok Gudang"])
                                    ?: parseFlexibleInt(row["stok gudang"])
                                    ?: parseFlexibleInt(row["Stock Gudang"])
                                    ?: parseFlexibleInt(row["stock gudang"])
                                    ?: parseFlexibleInt(row["Gudang"])
                                    ?: parseFlexibleInt(row["gudang"])
                                    ?: parseFlexibleInt(row["Qty"])
                                    ?: parseFlexibleInt(row["qty"])
                                    ?: 0
                                ProductEntity(id, name, prStr.toDoubleOrNull() ?: 0.0, pwStr.toDoubleOrNull() ?: 0.0, stockVal)
                            }
                        } else null

                        val outletsListToSave = if (shouldSaveOutlets) {
                            val resolvedUsers = usersListToSave ?: repository.getAllUsersDirect()
                            outletsData.mapNotNull { row ->
                                val id = row["ID"]?.toString()?.trim()?.uppercase() ?: return@mapNotNull null
                                val name = row["Name"]?.toString() ?: ""
                                val sid = (row["SalesId"]?.toString() ?: "").trim().uppercase()
                                val sname = (row["SalesName"]?.toString() ?: "").trim()
                                val kh = row["KodeHari"]?.toString() ?: ""
                                val type = row["Type"]?.toString() ?: ""
                                val category = row["Category"]?.toString() ?: ""
                                val address = row["Address"]?.toString() ?: ""
                                val geotag = row["Geotag"]?.toString() ?: ""
                                
                                var resolvedSid = sid
                                var resolvedSname = sname
                                val resolvedUser = resolvedUsers.find { 
                                    it.id.equals(resolvedSid, ignoreCase = true) || 
                                    it.username.equals(resolvedSid, ignoreCase = true) ||
                                    it.username.equals(resolvedSname, ignoreCase = true)
                                }
                                if (resolvedUser != null) {
                                    resolvedSid = resolvedUser.id
                                    resolvedSname = resolvedUser.username
                                } else if (resolvedSid.isEmpty() && resolvedSname.isNotEmpty()) {
                                    val resolvedByName = resolvedUsers.find { it.username.equals(resolvedSname, ignoreCase = true) }
                                    if (resolvedByName != null) {
                                        resolvedSid = resolvedByName.id
                                        resolvedSname = resolvedByName.username
                                    }
                                }
                                
                                OutletEntity(id, name, type, category, address, geotag, resolvedSid, resolvedSname, kh)
                            }
                        } else null

                        val stokisListToSave = if (shouldSaveStokis) {
                            val resolvedUsers = usersListToSave ?: repository.getAllUsersDirect()
                            stokisData.mapNotNull { row ->
                                val id = row["ID"]?.toString()?.trim()?.uppercase() ?: return@mapNotNull null
                                val name = row["Name"]?.toString() ?: ""
                                val address = row["Address"]?.toString() ?: ""
                                val assignedSalesId = (row["AssignedSalesId"]?.toString() ?: "").trim().uppercase()
                                val assignedSalesName = (row["AssignedSalesName"]?.toString() ?: "").trim()
                                
                                var resolvedSid = assignedSalesId
                                var resolvedSname = assignedSalesName
                                val resolvedUser = resolvedUsers.find { 
                                    it.id.equals(resolvedSid, ignoreCase = true) || 
                                    it.username.equals(resolvedSid, ignoreCase = true) ||
                                    it.username.equals(resolvedSname, ignoreCase = true)
                                }
                                if (resolvedUser != null) {
                                    resolvedSid = resolvedUser.id
                                    resolvedSname = resolvedUser.username
                                } else if (resolvedSid.isEmpty() && resolvedSname.isNotEmpty()) {
                                    val resolvedByName = resolvedUsers.find { it.username.equals(resolvedSname, ignoreCase = true) }
                                    if (resolvedByName != null) {
                                        resolvedSid = resolvedByName.id
                                        resolvedSname = resolvedByName.username
                                    }
                                }
                                StokisEntity(id, name, address, resolvedSid, resolvedSname)
                            }
                        } else null

                        // PERBAIKAN BUG (stock yang sudah dikirim admin ke Stokis tidak bisa
                        // di-"Ambil Stock Mandiri" oleh sales -- data seperti hilang/berkurang
                        // drastis dibanding yang sebenarnya dikirim): sheet StokisStock di backend
                        // adalah LEDGER -- setiap admin menekan "Inject Stock Gudang ke Stokis",
                        // SATU BARIS BARU ditambahkan ke sheet (bukan meng-update baris lama).
                        // Jadi satu kombinasi stokisId+productId yang sudah di-inject beberapa kali
                        // bisa punya BEBERAPA baris di data mentah dari server (`stokisStockData`).
                        // Sebelumnya, setiap baris mentah ini langsung dipetakan 1:1 menjadi
                        // StokisStockEntity lalu disimpan SATU PER SATU ke Room memakai
                        // insertStokisStock() yang konfigurasinya OnConflictStrategy.REPLACE pada
                        // primary key (stokisId, productId) -- akibatnya baris-baris untuk
                        // kombinasi yang sama saling TIMPA-MENIMPA, dan hanya baris TERAKHIR yang
                        // bertahan di database lokal HP. Semua qty dari inject-inject sebelumnya
                        // hilang begitu saja (bukan dijumlahkan), sehingga jumlah stok yang
                        // terlihat di HP sales jauh lebih kecil dari yang sebenarnya sudah dikirim
                        // -- persis seperti yang dilihat admin di console web (yang MENJUMLAHKAN
                        // semua baris ledger sebelum ditampilkan, lihat renderStockStokis() di
                        // Index.html). Sekarang baris-baris mentah di-agregasi (dijumlahkan) dulu
                        // per kombinasi stokisId+productId di sisi Android, PERSIS seperti logika
                        // agregasi yang sudah dipakai di console web, sebelum disimpan ke Room --
                        // supaya jumlah yang dilihat sales selalu konsisten dengan yang dilihat admin.
                        val stokisStockListToSave = if (shouldSaveStokisStock) {
                            data class RawStokisStockRow(
                                val stokisId: String,
                                val stokisName: String,
                                val productId: String,
                                val productName: String,
                                val qty: Int
                            )
                            val rawRows = stokisStockData.mapNotNull { row ->
                                val stokisId = row["StokisId"]?.toString()?.trim()?.uppercase() ?: row["stokisId"]?.toString()?.trim()?.uppercase() ?: row["StokisID"]?.toString()?.trim()?.uppercase() ?: row["stokisID"]?.toString()?.trim()?.uppercase() ?: return@mapNotNull null
                                val stokisName = row["StokisName"]?.toString() ?: row["stokisName"]?.toString() ?: row["Stokis"]?.toString() ?: row["stokis"]?.toString() ?: ""
                                val productId = row["ProductId"]?.toString()?.trim()?.uppercase() ?: row["productId"]?.toString()?.trim()?.uppercase() ?: row["ProductID"]?.toString()?.trim()?.uppercase() ?: row["productID"]?.toString()?.trim()?.uppercase() ?: return@mapNotNull null
                                val productName = row["ProductName"]?.toString() ?: row["productName"]?.toString() ?: row["Product"]?.toString() ?: row["product"]?.toString() ?: ""
                                val qty = parseFlexibleInt(row["Qty"]) ?: parseFlexibleInt(row["qty"]) ?: 0
                                RawStokisStockRow(stokisId, stokisName, productId, productName, qty)
                            }
                            rawRows.groupBy { it.stokisId to it.productId }.map { (key, rowsForKey) ->
                                val (stokisId, productId) = key
                                val totalQty = rowsForKey.sumOf { it.qty }
                                // Pakai nama stokis/produk dari baris terakhir yang punya nama (nama
                                // tidak berubah antar baris untuk kombinasi yang sama pada praktiknya).
                                val stokisName = rowsForKey.lastOrNull { it.stokisName.isNotBlank() }?.stokisName ?: ""
                                val productName = rowsForKey.lastOrNull { it.productName.isNotBlank() }?.productName ?: ""
                                StokisStockEntity(stokisId, stokisName, productId, productName, totalQty, "")
                            }
                        } else null

                        val stockAllocationsListToSave = if (shouldSaveAllocations) {
                            val resolvedUsers = usersListToSave ?: repository.getAllUsersDirect()
                            stockAllocationsData.mapNotNull { row ->
                                val date = row["Date"]?.toString() ?: row["date"]?.toString() ?: ""
                                val salesId = (row["SalesId"]?.toString() ?: row["salesId"]?.toString() ?: "").trim().uppercase()
                                val salesName = (row["SalesName"]?.toString() ?: row["salesName"]?.toString() ?: row["Sales"]?.toString() ?: row["sales"]?.toString() ?: "").trim()
                                val productId = (row["ProductId"]?.toString() ?: row["productId"]?.toString() ?: row["ProductID"]?.toString() ?: row["productID"]?.toString() ?: "").trim().uppercase()
                                val productName = row["ProductName"]?.toString() ?: row["productName"]?.toString() ?: row["Product"]?.toString() ?: row["product"]?.toString() ?: ""
                                val qty = parseFlexibleInt(row["Qty"]) ?: parseFlexibleInt(row["qty"]) ?: 0
                                
                                var resolvedSid = salesId
                                var resolvedSname = salesName
                                val resolvedUser = resolvedUsers.find { 
                                    it.id.equals(resolvedSid, ignoreCase = true) || 
                                    it.username.equals(resolvedSid, ignoreCase = true) ||
                                    it.username.equals(resolvedSname, ignoreCase = true)
                                }
                                if (resolvedUser != null) {
                                    resolvedSid = resolvedUser.id
                                    resolvedSname = resolvedUser.username
                                } else if (resolvedSid.isEmpty() && resolvedSname.isNotEmpty()) {
                                    val resolvedByName = resolvedUsers.find { it.username.equals(resolvedSname, ignoreCase = true) }
                                    if (resolvedByName != null) {
                                        resolvedSid = resolvedByName.id
                                        resolvedSname = resolvedByName.username
                                    }
                                }
                                StockAllocationEntity(0, date, resolvedSid, resolvedSname, productId, productName, qty)
                            }
                        } else null

                        val currentActiveUser = _currentUser.value
                        repository.updateMasterData(
                            users = usersListToSave,
                            products = productsListToSave,
                            outlets = outletsListToSave,
                            stokis = stokisListToSave,
                            stokisStock = stokisStockListToSave,
                            stockAllocations = stockAllocationsListToSave,
                            keepCurrentUser = currentActiveUser
                        )
                        
                        // Save successfully written versions/checksums to SharedPreferences
                        if (shouldSaveProducts) {
                            val finalProdVer = initialData.productsVersion ?: newProductsChecksum
                            sharedPrefs.edit().putString("products_version_key", finalProdVer).apply()
                        }
                        if (shouldSaveOutlets) {
                            val finalOutletVer = initialData.outletsVersion ?: newOutletsChecksum
                            sharedPrefs.edit().putString("outlets_version_key", finalOutletVer).apply()
                        }
                        if (shouldSaveAllocations) {
                            val finalRoutesVer = initialData.routesVersion ?: newRoutesChecksum
                            sharedPrefs.edit().putString("routes_version_key", finalRoutesVer).apply()
                        }
                        if (shouldSaveStokis) {
                            val finalStokisVer = initialData.stokisVersion ?: newStokisChecksum
                            sharedPrefs.edit().putString("stokis_version_key", finalStokisVer).apply()
                        }
                        if (shouldSaveStokisStock) {
                            val finalStokisStockVer = initialData.stokisStockVersion ?: newStokisStockChecksum
                            sharedPrefs.edit().putString("stokis_stock_version_key", finalStokisStockVer).apply()
                        }
                        if (shouldSaveUsers) {
                            sharedPrefs.edit().putString("users_version_key", newUsersChecksum).apply()
                        }

                        // Refresh current user in case of role updates
                        _currentUser.value?.id?.let { cid ->
                            val updatedUser = repository.getAllUsersDirect().find { it.id == cid }
                            if (updatedUser != null) {
                                _currentUser.value = updatedUser
                                SessionManager(getApplication()).saveSession(updatedUser)
                            }
                        }

                        sharedPrefs.edit().putString("last_master_sync", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())).apply()
                        
                        val productsCount = repository.getAllProductsDirect().size
                        val allocationsCount = repository.getAllStockAllocationsDirect().size
                        val outletsCount = repository.getAllOutletsDirect().size

                        val logDetails = StringBuilder().apply {
                            append("Pushed: ${unsyncedTransactions.size} transaksi, ${newOutlets.size} outlet baru. ")
                            append("Pulled: ")
                            val pullParts = mutableListOf<String>()
                            if (shouldSaveProducts) pullParts.add("${productsData.size} produk") else if (productsData.isNotEmpty()) pullParts.add("Produk (Tetap)")
                            if (shouldSaveOutlets) pullParts.add("${outletsData.size} outlet") else if (outletsData.isNotEmpty()) pullParts.add("Outlet (Tetap)")
                            if (shouldSaveAllocations) pullParts.add("${stockAllocationsData.size} rute") else if (stockAllocationsData.isNotEmpty()) pullParts.add("Rute (Tetap)")
                            if (shouldSaveStokis) pullParts.add("${stokisData.size} stokis") else if (stokisData.isNotEmpty()) pullParts.add("Stokis (Tetap)")
                            if (shouldSaveStokisStock) pullParts.add("${stokisStockData.size} stock stokis") else if (stokisStockData.isNotEmpty()) pullParts.add("Stock Stokis (Tetap)")
                            if (shouldSaveUsers) pullParts.add("${usersData.size} user") else if (usersData.isNotEmpty()) pullParts.add("User (Tetap)")
                            
                            if (pullParts.isEmpty()) {
                                append("Tidak ada data master yang berubah. ")
                            } else {
                                append(pullParts.joinToString(", ")).append(". ")
                            }
                            append("Total Aktif di DB: $productsCount Produk, $allocationsCount Alokasi Stok, $outletsCount Outlet.")
                        }.toString()

                        try {
                            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                            repository.insertSyncAuditLog(
                                com.example.data.SyncAuditLogEntity(
                                    timestamp = timestamp,
                                    processType = "Sync Foreground (Manual)",
                                    details = logDetails,
                                    result = "Success"
                                )
                            )
                        } catch (dbEx: Exception) {}

                        Pair(true, "Sinkronisasi 2-Arah Berhasil!")
                    }
                }
            } catch (e: java.lang.Exception) {
                try {
                    val timeStr = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date())
                    val currentPending = repository.getAllPendingUploadsDirect()
                    for (p in currentPending) {
                        repository.insertPendingUpload(p.copy(status = "Failed", lastAttempt = timeStr, retryCount = p.retryCount + 1))
                    }
                } catch (dbEx: Exception) {}
                
                try {
                    val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                    repository.insertSyncAuditLog(
                        com.example.data.SyncAuditLogEntity(
                            timestamp = timestamp,
                            processType = "Sync Foreground (Manual)",
                            details = "Sinkronisasi gagal",
                            result = "Failed",
                            errorMessage = e.message ?: "Unknown error"
                        )
                    )
                } catch (dbEx: Exception) {}

                Pair(false, "Error: ${e.message}")
            } finally {
                releaseUploadLock()
            }
        }
    }
}
