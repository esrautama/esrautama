package com.example.data

import kotlinx.coroutines.flow.Flow
import androidx.room.withTransaction

class AppRepository(private val database: AppDatabase) {
    private val dao = database.appDao()

    suspend fun <R> runInTransaction(block: suspend () -> R): R {
        return database.withTransaction {
            block()
        }
    }

    // --- USERS ---
    val allUsers: Flow<List<UserEntity>> = dao.getAllUsers()
    suspend fun getAllUsersDirect(): List<UserEntity> = dao.getAllUsersDirect()
    suspend fun insertUser(user: UserEntity) = dao.insertUser(user)
    suspend fun insertUsers(users: List<UserEntity>) = dao.insertUsers(users)
    suspend fun deleteUserById(id: String) = dao.deleteUserById(id)
    suspend fun deleteAllUsers() = dao.deleteAllUsers()
    suspend fun updateUserPassword(id: String, newPass: String) = dao.updateUserPassword(id, newPass)


    // --- PRODUCTS ---
    val allProducts: Flow<List<ProductEntity>> = dao.getAllProducts()
    suspend fun getAllProductsDirect(): List<ProductEntity> = dao.getAllProductsDirect()
    suspend fun insertProduct(product: ProductEntity) = dao.insertProduct(product)
    suspend fun insertProducts(products: List<ProductEntity>) = dao.insertProducts(products)
    suspend fun deleteProductById(id: String) = dao.deleteProductById(id)
    suspend fun deleteAllProducts() = dao.deleteAllProducts()
    suspend fun updateWarehouseStock(id: String, newStock: Int) = dao.updateWarehouseStock(id, newStock)


    // --- OUTLETS ---
    val allOutlets: Flow<List<OutletEntity>> = dao.getAllOutlets()
    suspend fun getAllOutletsDirect(): List<OutletEntity> = dao.getAllOutletsDirect()
    suspend fun insertOutlet(outlet: OutletEntity) = dao.insertOutlet(outlet)
    suspend fun insertOutlets(outlets: List<OutletEntity>) = dao.insertOutlets(outlets)
    suspend fun deleteOutletById(id: String) = dao.deleteOutletById(id)
    suspend fun deleteAllOutlets() = dao.deleteAllOutlets()
    suspend fun deleteAllSyncedOutlets() = dao.deleteAllSyncedOutlets()
    suspend fun assignOutletToSales(id: String, salesId: String, salesName: String, kodeHari: String) = dao.assignOutletToSales(id, salesId, salesName, kodeHari)


    // --- STOCK ALLOCATIONS ---
    val allStockAllocations: Flow<List<StockAllocationEntity>> = dao.getAllStockAllocations()
    suspend fun getAllStockAllocationsDirect(): List<StockAllocationEntity> = dao.getAllStockAllocationsDirect()
    suspend fun deleteStockAllocationById(id: Int) = dao.deleteStockAllocationById(id)
    suspend fun insertStockAllocation(allocation: StockAllocationEntity) = dao.insertStockAllocation(allocation)
    suspend fun deleteAllStockAllocations() = dao.deleteAllStockAllocations()


    // --- TRANSACTIONS ---
    val allTransactions: Flow<List<TransactionEntity>> = dao.getAllTransactions()
    suspend fun getAllTransactionsDirect(): List<TransactionEntity> = dao.getAllTransactionsDirect()
    suspend fun insertTransaction(transaction: TransactionEntity) = dao.insertTransaction(transaction)
    suspend fun insertTransactions(transactions: List<TransactionEntity>) = dao.insertTransactions(transactions)
    suspend fun deleteAllTransactions() = dao.deleteAllTransactions()
    suspend fun deleteTransactionById(orderId: String) = dao.deleteTransactionById(orderId)
    suspend fun markTransactionSynced(orderId: String) = dao.markTransactionSynced(orderId)


    // --- WAREHOUSE LOGS ---
    val allWarehouseLogs: Flow<List<WarehouseLogEntity>> = dao.getAllWarehouseLogs()
    suspend fun getAllWarehouseLogsDirect(): List<WarehouseLogEntity> = dao.getAllWarehouseLogsDirect()
    suspend fun insertWarehouseLog(log: WarehouseLogEntity) = dao.insertWarehouseLog(log)
    suspend fun deleteAllWarehouseLogs() = dao.deleteAllWarehouseLogs()


    // --- SETTINGS ---
    val receiptSettings: Flow<ReceiptSettingsEntity?> = dao.getSettingsFlow()
    suspend fun getSettingsDirect(): ReceiptSettingsEntity? = dao.getSettingsDirect()
    suspend fun saveSettings(settings: ReceiptSettingsEntity) = dao.insertSettings(settings)

    // --- STOKIS STOCK ---
    val allStokisStock: Flow<List<StokisStockEntity>> = dao.getAllStokisStock()
    suspend fun getAllStokisStockDirect(): List<StokisStockEntity> = dao.getAllStokisStockDirect()
    suspend fun insertStokisStock(stock: StokisStockEntity) = dao.insertStokisStock(stock)
    
    val allStokis: kotlinx.coroutines.flow.Flow<List<StokisEntity>> = dao.getAllStokis()
    suspend fun getAllStokisDirect(): List<StokisEntity> = dao.getAllStokisDirect()
    suspend fun insertStokis(stokis: StokisEntity) = dao.insertStokis(stokis)
    suspend fun deleteStokisById(id: String) = dao.deleteStokisById(id)
    suspend fun deleteAllStokis() = dao.deleteAllStokis()

    suspend fun getStokisStockByProduct(stokisId: String, productId: String): StokisStockEntity? = dao.getStokisStockByProduct(stokisId, productId)
    suspend fun deleteAllStokisStock() = dao.deleteAllStokisStock()

    // --- PENDING UPLOADS ---
    val allPendingUploads: Flow<List<PendingUploadEntity>> = dao.getAllPendingUploads()
    suspend fun getAllPendingUploadsDirect(): List<PendingUploadEntity> = dao.getAllPendingUploadsDirect()
    suspend fun insertPendingUpload(upload: PendingUploadEntity) = dao.insertPendingUpload(upload)
    suspend fun deletePendingUploadById(id: String) = dao.deletePendingUploadById(id)
    suspend fun deleteAllPendingUploads() = dao.deleteAllPendingUploads()

    suspend fun updateMasterData(
        users: List<UserEntity>?,
        products: List<ProductEntity>?,
        outlets: List<OutletEntity>?,
        stokis: List<StokisEntity>?,
        stokisStock: List<StokisStockEntity>?,
        stockAllocations: List<StockAllocationEntity>?,
        keepCurrentUser: UserEntity?
    ) {
        database.withTransaction {
            dao.updateMasterData(users, products, outlets, stokis, stokisStock, stockAllocations, keepCurrentUser)
        }
    }

    // --- SYNC AUDIT LOGS ---
    val allSyncAuditLogs: Flow<List<SyncAuditLogEntity>> = dao.getAllSyncAuditLogs()
    suspend fun insertSyncAuditLog(log: SyncAuditLogEntity) {
        dao.insertSyncAuditLog(log)
        dao.pruneSyncAuditLogs()
    }
    suspend fun deleteAllSyncAuditLogs() = dao.deleteAllSyncAuditLogs()
}
