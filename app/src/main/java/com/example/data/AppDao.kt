package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    // --- USERS ---
    @Query("SELECT * FROM users ORDER BY id ASC")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users ORDER BY id ASC")
    suspend fun getAllUsersDirect(): List<UserEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteUserById(id: String)

    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()

    @Query("UPDATE users SET password = :password WHERE id = :id")
    suspend fun updateUserPassword(id: String, password: String)


    // --- PRODUCTS ---
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY name ASC")
    suspend fun getAllProductsDirect(): List<ProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProductById(id: String)

    @Query("DELETE FROM products")
    suspend fun deleteAllProducts()

    @Query("UPDATE products SET warehouseStock = :newStock WHERE id = :id")
    suspend fun updateWarehouseStock(id: String, newStock: Int)


    // --- OUTLETS ---
    @Query("SELECT * FROM outlets ORDER BY name ASC")
    fun getAllOutlets(): Flow<List<OutletEntity>>

    @Query("SELECT * FROM outlets ORDER BY name ASC")
    suspend fun getAllOutletsDirect(): List<OutletEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutlet(outlet: OutletEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOutlets(outlets: List<OutletEntity>)

    @Query("DELETE FROM outlets WHERE id = :id")
    suspend fun deleteOutletById(id: String)

    @Query("DELETE FROM outlets")
    suspend fun deleteAllOutlets()

    @Query("DELETE FROM outlets WHERE isNewLocal = 0")
    suspend fun deleteAllSyncedOutlets()

    @Query("UPDATE outlets SET salesId = :salesId, salesName = :salesName, kodeHari = :kodeHari WHERE id = :id")
    suspend fun assignOutletToSales(id: String, salesId: String, salesName: String, kodeHari: String)


    // --- STOCK ALLOCATIONS ---
    @Query("SELECT * FROM stock_allocations ORDER BY localId DESC")
    fun getAllStockAllocations(): Flow<List<StockAllocationEntity>>

    @Query("SELECT * FROM stock_allocations ORDER BY localId DESC")
    suspend fun getAllStockAllocationsDirect(): List<StockAllocationEntity>

    @Query("DELETE FROM stock_allocations WHERE localId = :id")
    suspend fun deleteStockAllocationById(id: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockAllocation(allocation: StockAllocationEntity)

    @Query("DELETE FROM stock_allocations WHERE qty > 0")
    suspend fun deleteAllStockAllocations()


    // --- TRANSACTIONS ---
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    suspend fun getAllTransactionsDirect(): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    @Query("DELETE FROM transactions WHERE orderId = :orderId")
    suspend fun deleteTransactionById(orderId: String)

    @Query("UPDATE transactions SET statusSynced = 1 WHERE orderId = :orderId")
    suspend fun markTransactionSynced(orderId: String)


    // --- WAREHOUSE LOGS ---
    @Query("SELECT * FROM warehouse_logs ORDER BY date DESC")
    fun getAllWarehouseLogs(): Flow<List<WarehouseLogEntity>>

    @Query("SELECT * FROM warehouse_logs ORDER BY date DESC")
    suspend fun getAllWarehouseLogsDirect(): List<WarehouseLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWarehouseLog(log: WarehouseLogEntity)

    @Query("DELETE FROM warehouse_logs")
    suspend fun deleteAllWarehouseLogs()


    // --- SETTINGS ---
    @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<ReceiptSettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsDirect(): ReceiptSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: ReceiptSettingsEntity)

    
    // --- STOKIS ---
    @Query("SELECT * FROM stokis ORDER BY name ASC")
    fun getAllStokis(): kotlinx.coroutines.flow.Flow<List<StokisEntity>>
    @Query("SELECT * FROM stokis ORDER BY name ASC")
    suspend fun getAllStokisDirect(): List<StokisEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStokis(stokis: StokisEntity)
    @Query("DELETE FROM stokis WHERE id = :id")
    suspend fun deleteStokisById(id: String)
    @Query("DELETE FROM stokis")
    suspend fun deleteAllStokis()

    // --- STOKIS STOCK ---
    @Query("SELECT * FROM stokis_stock ORDER BY productName ASC")
    fun getAllStokisStock(): Flow<List<StokisStockEntity>>

    @Query("SELECT * FROM stokis_stock ORDER BY productName ASC")
    suspend fun getAllStokisStockDirect(): List<StokisStockEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStokisStock(stock: StokisStockEntity)

    @Query("SELECT * FROM stokis_stock WHERE stokisId = :stokisId AND productId = :productId LIMIT 1")
    suspend fun getStokisStockByProduct(stokisId: String, productId: String): StokisStockEntity?

    @Query("DELETE FROM stokis_stock WHERE stokisId = :stokisId AND productId = :productId")
    suspend fun deleteStokisStockEntry(stokisId: String, productId: String)

    @Query("DELETE FROM stokis_stock")
    suspend fun deleteAllStokisStock()

    // --- PENDING UPLOADS ---
    @Query("SELECT * FROM pending_uploads ORDER BY createdAt ASC")
    fun getAllPendingUploads(): Flow<List<PendingUploadEntity>>

    @Query("SELECT * FROM pending_uploads ORDER BY createdAt ASC")
    suspend fun getAllPendingUploadsDirect(): List<PendingUploadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingUpload(upload: PendingUploadEntity)

    @Query("DELETE FROM pending_uploads WHERE id = :id")
    suspend fun deletePendingUploadById(id: String)

    @Query("DELETE FROM pending_uploads")
    suspend fun deleteAllPendingUploads()

    @Transaction
    suspend fun updateMasterData(
        users: List<UserEntity>?,
        products: List<ProductEntity>?,
        outlets: List<OutletEntity>?,
        stokis: List<StokisEntity>?,
        stokisStock: List<StokisStockEntity>?,
        stockAllocations: List<StockAllocationEntity>?,
        keepCurrentUser: UserEntity?
    ) {
        if (users != null) {
            val existingUsers = getAllUsersDirect()
            val existingPasswords = existingUsers.associate { it.id to it.password }
            val incomingIds = users.map { it.id }.toSet()
            
            // Delete users that are no longer on the server
            existingUsers.filter { it.id !in incomingIds }.forEach { deleteUserById(it.id) }
            
            val finalUsers = users.map { u ->
                val existingPass = existingPasswords[u.id]
                if (u.password.isEmpty() && existingPass != null && existingPass.isNotEmpty()) {
                    u.copy(password = existingPass)
                } else {
                    u
                }
            }
            insertUsers(finalUsers)
            if (keepCurrentUser != null) {
                val existingPass = existingPasswords[keepCurrentUser.id] ?: keepCurrentUser.password
                val finalKeep = if (existingPass.isNotEmpty()) {
                    keepCurrentUser.copy(password = existingPass)
                } else {
                    keepCurrentUser
                }
                insertUser(finalKeep)
            }
        }
        if (products != null) {
            val existingProducts = getAllProductsDirect()
            val incomingIds = products.map { it.id }.toSet()
            
            // Delete products no longer on server
            existingProducts.filter { it.id !in incomingIds }.forEach { deleteProductById(it.id) }
            
            insertProducts(products)
        }
        if (outlets != null) {
            val existingOutlets = getAllOutletsDirect()
            val incomingIds = outlets.map { it.id }.toSet()
            
            // Delete synced outlets no longer on server (do not delete new local offline outlets)
            existingOutlets.filter { !it.isNewLocal && it.id !in incomingIds }.forEach { deleteOutletById(it.id) }
            
            insertOutlets(outlets)
        }
        if (stokis != null) {
            val existingStokis = getAllStokisDirect()
            val incomingIds = stokis.map { it.id }.toSet()
            
            // Delete stokis no longer on server
            existingStokis.filter { it.id !in incomingIds }.forEach { deleteStokisById(it.id) }
            
            for (s in stokis) {
                insertStokis(s)
                if (s.assignedSalesId.isNotEmpty() && users != null) {
                    val user = users.find { it.id == s.assignedSalesId }
                    if (user != null) {
                        insertUser(user.copy(isStokisSales = true))
                    }
                }
            }
        }
        if (stokisStock != null) {
            val existingStock = getAllStokisStockDirect()
            val incomingKeys = stokisStock.map { it.stokisId to it.productId }.toSet()
            
            // Delete stokis stock no longer on server
            existingStock.filter { (it.stokisId to it.productId) !in incomingKeys }.forEach { 
                deleteStokisStockEntry(it.stokisId, it.productId)
            }
            
            for (st in stokisStock) {
                insertStokisStock(st)
            }
        }
        if (stockAllocations != null) {
            val existingAllocations = getAllStockAllocationsDirect()
            val allocationMap = existingAllocations.associateBy { it.salesId to it.productId }
            val incomingKeys = stockAllocations.map { it.salesId to it.productId }.toSet()
            
            // Delete allocations with qty > 0 that are no longer on server
            existingAllocations.filter { it.qty > 0 && (it.salesId to it.productId) !in incomingKeys }.forEach {
                deleteStockAllocationById(it.localId)
            }
            
            for (sa in stockAllocations) {
                if (sa.qty > 0) {
                    val existing = allocationMap[sa.salesId to sa.productId]
                    if (existing != null) {
                        insertStockAllocation(sa.copy(localId = existing.localId))
                    } else {
                        insertStockAllocation(sa)
                    }
                }
            }
        }
    }

    // --- SYNC AUDIT LOGS ---
    @Query("SELECT * FROM sync_audit_logs ORDER BY id DESC")
    fun getAllSyncAuditLogs(): Flow<List<SyncAuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncAuditLog(log: SyncAuditLogEntity)

    @Query("DELETE FROM sync_audit_logs")
    suspend fun deleteAllSyncAuditLogs()

    @Query("DELETE FROM sync_audit_logs WHERE id NOT IN (SELECT id FROM sync_audit_logs ORDER BY id DESC LIMIT 200)")
    suspend fun pruneSyncAuditLogs()
}
