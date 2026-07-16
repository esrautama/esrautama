package com.example.viewmodel

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.data.RetrofitClient
import com.example.data.SyncRequest
import com.example.data.UserEntity
import com.example.data.ProductEntity
import com.example.data.OutletEntity
import com.example.data.StokisEntity
import com.example.data.StokisStockEntity
import com.example.data.StockAllocationEntity
import com.example.data.SessionManager
import com.example.data.SyncUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat
import com.example.data.PendingUploadEntity

// PERBAIKAN BUG UTAMA (sama seperti di SfaViewModel.kt): lihat penjelasan lengkap di
// SfaViewModel.kt. Intinya, JSON angka dari server selalu dibaca sebagai Double oleh Moshi
// untuk tipe generik Any/Map<String, Any>, sehingga ".toString()?.toIntOrNull()" yang lama
// selalu gagal (mis. "5.0".toIntOrNull() == null) dan diam-diam jatuh ke 0.
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

class SyncWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            val repository = AppRepository(db)
            try {
                com.example.data.SyncUtils.globalSyncMutex.withLock {
                    val prefs = applicationContext.getSharedPreferences("sfa_prefs", Context.MODE_PRIVATE)
                    val url = prefs.getString("apps_script_url", null)
                    if (url.isNullOrBlank()) {
                        return@withLock Result.failure()
                    }

                    val service = RetrofitClient.createService(url)
                    val sessionManager = SessionManager(applicationContext)
                    val activeSession = sessionManager.getSession()
                    
                    // 1. Push Unsynced Transactions and New Outlets in batches of 20 to 50
                    val transactions = repository.getAllTransactionsDirect()
                    val unsynced = transactions.filter { !it.statusSynced }
                    val newOutlets = repository.getAllOutletsDirect().filter { it.isNewLocal }
                    val allUsers = repository.getAllUsersDirect()
                    val allProducts = repository.getAllProductsDirect()
                    
                    var deviceId = prefs.getString("device_id", null)
                    if (deviceId == null) {
                        deviceId = UUID.randomUUID().toString()
                        prefs.edit().putString("device_id", deviceId).apply()
                    }

                    val batchSize = 30
                    val transactionBatches = if (unsynced.isNotEmpty()) {
                        unsynced.chunked(batchSize)
                    } else {
                        listOf(emptyList())
                    }

                    for ((index, batch) in transactionBatches.withIndex()) {
                        val batchNewOutlets = if (index == 0) newOutlets else emptyList()
                        val batchUsers = if (index == 0) allUsers else emptyList()
                        val batchProducts = if (index == 0) allProducts else emptyList()

                        if (batch.isEmpty() && batchNewOutlets.isEmpty() && batchUsers.isEmpty() && batchProducts.isEmpty()) {
                            continue
                        }

                        val request = SyncRequest(
                            transactions = batch,
                            newOutlets = batchNewOutlets,
                            syncUsers = batchUsers,
                            syncProducts = batchProducts,
                            deviceId = deviceId,
                            appVersion = "2.0.0"
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
                                    for (o in batchNewOutlets) {
                                        val p = currentPending.find { it.id == o.id }
                                        if (p != null) {
                                            repository.insertPendingUpload(p.copy(status = "Failed", lastAttempt = timeStr, retryCount = p.retryCount + 1))
                                        }
                                    }
                                }
                            } catch (e: Exception) {}

                            try {
                                val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                                repository.insertSyncAuditLog(
                                    com.example.data.SyncAuditLogEntity(
                                        timestamp = timestamp,
                                        processType = "Sync Background (Auto)",
                                        details = "Push batch ${index + 1} gagal",
                                        result = "Failed",
                                        errorMessage = "Gagal push transaksi/outlet: ${response.message}"
                                    )
                                )
                            } catch (dbEx: Exception) {}

                            return@withLock Result.retry()
                        } else {
                            if (batch.isNotEmpty()) {
                                val updated = batch.map { it.copy(statusSynced = true) }
                                repository.insertTransactions(updated)
                            }
                            if (index == 0 && batchNewOutlets.isNotEmpty()) {
                                val updatedOutlets = batchNewOutlets.map { it.copy(isNewLocal = false) }
                                repository.insertOutlets(updatedOutlets)
                            }

                            try {
                                for (t in batch) {
                                    repository.deletePendingUploadById(t.orderId)
                                }
                                if (index == 0) {
                                    for (o in batchNewOutlets) {
                                        repository.deletePendingUploadById(o.id)
                                    }
                                }
                            } catch (e: Exception) {}
                        }
                    }

                    // 2. Pull Master Data conditionally based on granular checksums / versions
                    val lastMasterSync = prefs.getString("last_master_sync", null)
                    val clientVersion = prefs.getString("master_data_version", null)

                    val cachedProductsVer = prefs.getString("products_version_key", "") ?: ""
                    val cachedOutletsVer = prefs.getString("outlets_version_key", "") ?: ""
                    val cachedRoutesVer = prefs.getString("routes_version_key", "") ?: ""
                    val cachedStokisVer = prefs.getString("stokis_version_key", "") ?: ""
                    val cachedStokisStockVer = prefs.getString("stokis_stock_version_key", "") ?: ""

                    val initialData = service.getInitialData(
                        lastSync = lastMasterSync,
                        clientVersion = clientVersion,
                        productsVersion = cachedProductsVer,
                        outletsVersion = cachedOutletsVer,
                        routesVersion = cachedRoutesVer,
                        stokisVersion = cachedStokisVer
                    )
                    
                    if (initialData.version != null && initialData.version != clientVersion) {
                        prefs.edit().putString("master_data_version", initialData.version).apply()
                    } else if (initialData.users.isEmpty() && initialData.products.isEmpty() && initialData.outlets.isEmpty() && initialData.stokis.isEmpty() && initialData.stokisStock.isEmpty() && initialData.finalStockAllocations.isEmpty()) {
                        try {
                            val productsCount = repository.getAllProductsDirect().size
                            val allocationsCount = repository.getAllStockAllocationsDirect().size
                            val outletsCount = repository.getAllOutletsDirect().size
                            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                            repository.insertSyncAuditLog(
                                com.example.data.SyncAuditLogEntity(
                                    timestamp = timestamp,
                                    processType = "Sync Background (Auto)",
                                    details = "Pushed: ${unsynced.size} transaksi, ${newOutlets.size} outlet baru. Pulled: Checked updates (No change/All up to date). Total Aktif di DB: $productsCount Produk, $allocationsCount Alokasi Stok, $outletsCount Outlet.",
                                    result = "Success"
                                )
                            )
                        } catch (dbEx: Exception) {}
                        return@withLock Result.success()
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
                    val newProductsChecksum = if (productsData.isNotEmpty()) SyncUtils.calculateChecksum(productsData) else ""
                    val newOutletsChecksum = if (outletsData.isNotEmpty()) SyncUtils.calculateChecksum(outletsData) else ""
                    val newRoutesChecksum = if (stockAllocationsData.isNotEmpty()) SyncUtils.calculateChecksum(stockAllocationsData) else ""
                    val newStokisChecksum = if (stokisData.isNotEmpty()) SyncUtils.calculateChecksum(stokisData) else ""
                    val newStokisStockChecksum = if (stokisStockData.isNotEmpty()) SyncUtils.calculateChecksum(stokisStockData) else ""
                    
                    val cachedUsersVer = prefs.getString("users_version_key", "") ?: ""
                    val newUsersChecksum = if (usersData.isNotEmpty()) SyncUtils.calculateChecksum(usersData) else ""

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

                    val stokisStockListToSave = if (shouldSaveStokisStock) {
                        stokisStockData.mapNotNull { row ->
                            val stokisId = row["StokisId"]?.toString()?.trim()?.uppercase() ?: row["stokisId"]?.toString()?.trim()?.uppercase() ?: row["StokisID"]?.toString()?.trim()?.uppercase() ?: row["stokisID"]?.toString()?.trim()?.uppercase() ?: return@mapNotNull null
                            val stokisName = row["StokisName"]?.toString() ?: row["stokisName"]?.toString() ?: row["Stokis"]?.toString() ?: row["stokis"]?.toString() ?: ""
                            val productId = row["ProductId"]?.toString()?.trim()?.uppercase() ?: row["productId"]?.toString()?.trim()?.uppercase() ?: row["ProductID"]?.toString()?.trim()?.uppercase() ?: row["productID"]?.toString()?.trim()?.uppercase() ?: return@mapNotNull null
                            val productName = row["ProductName"]?.toString() ?: row["productName"]?.toString() ?: row["Product"]?.toString() ?: row["product"]?.toString() ?: ""
                            val qty = parseFlexibleInt(row["Qty"]) ?: parseFlexibleInt(row["qty"]) ?: 0
                            val lastUpdateDate = ""
                            StokisStockEntity(stokisId, stokisName, productId, productName, qty, lastUpdateDate)
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

                    val keepCurrentUser = if (activeSession != null) {
                        repository.getAllUsersDirect().find { it.id == activeSession.id }
                    } else null

                    repository.updateMasterData(
                        users = usersListToSave,
                        products = productsListToSave,
                        outlets = outletsListToSave,
                        stokis = stokisListToSave,
                        stokisStock = stokisStockListToSave,
                        stockAllocations = stockAllocationsListToSave,
                        keepCurrentUser = keepCurrentUser
                    )

                    // Save successfully written versions/checksums to SharedPreferences
                    if (shouldSaveProducts) {
                        val finalProdVer = initialData.productsVersion ?: newProductsChecksum
                        prefs.edit().putString("products_version_key", finalProdVer).apply()
                    }
                    if (shouldSaveOutlets) {
                        val finalOutletVer = initialData.outletsVersion ?: newOutletsChecksum
                        prefs.edit().putString("outlets_version_key", finalOutletVer).apply()
                    }
                    if (shouldSaveAllocations) {
                        val finalRoutesVer = initialData.routesVersion ?: newRoutesChecksum
                        prefs.edit().putString("routes_version_key", finalRoutesVer).apply()
                    }
                    if (shouldSaveStokis) {
                        val finalStokisVer = initialData.stokisVersion ?: newStokisChecksum
                        prefs.edit().putString("stokis_version_key", finalStokisVer).apply()
                    }
                    if (shouldSaveStokisStock) {
                        val finalStokisStockVer = initialData.stokisStockVersion ?: newStokisStockChecksum
                        prefs.edit().putString("stokis_stock_version_key", finalStokisStockVer).apply()
                    }
                    if (shouldSaveUsers) {
                        prefs.edit().putString("users_version_key", newUsersChecksum).apply()
                    }

                    // Refresh current active session in SessionManager if it exists in the updated users
                    activeSession?.id?.let { cid ->
                        val updatedUser = repository.getAllUsersDirect().find { it.id == cid }
                        if (updatedUser != null) {
                            sessionManager.saveSession(updatedUser)
                        }
                    }

                    prefs.edit().putString("last_master_sync", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date())).apply()

                    val productsCount = repository.getAllProductsDirect().size
                    val allocationsCount = repository.getAllStockAllocationsDirect().size
                    val outletsCount = repository.getAllOutletsDirect().size

                    val logDetails = StringBuilder().apply {
                        append("Pushed: ${unsynced.size} transaksi, ${newOutlets.size} outlet baru. ")
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
                                processType = "Sync Background (Auto)",
                                details = logDetails,
                                result = "Success"
                            )
                        )
                    } catch (dbEx: Exception) {}

                    Result.success()
                }
            } catch (e: Exception) {
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
                            processType = "Sync Background (Auto)",
                            details = "Sinkronisasi gagal background",
                            result = "Failed",
                            errorMessage = e.message ?: "Unknown background error"
                        )
                    )
                } catch (dbEx: Exception) {}

                Result.retry()
            }
        }
    }
}
