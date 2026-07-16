package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val username: String,
    val password: String,
    val role: String,
    val isStokisSales: Boolean = false
)

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val priceRetail: Double,
    val priceWholesale: Double,
    val warehouseStock: Int
)

@Entity(
    tableName = "outlets",
    indices = [
        Index(value = ["salesId"]),
        Index(value = ["isNewLocal"])
    ]
)
data class OutletEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val category: String,
    val address: String,
    val geotag: String,
    val salesId: String,
    val salesName: String,
    val kodeHari: String = "",
    val isNewLocal: Boolean = false
)

@Entity(
    tableName = "stock_allocations",
    indices = [
        Index(value = ["salesId"]),
        Index(value = ["productId"])
    ]
)
data class StockAllocationEntity(
    @PrimaryKey(autoGenerate = true) val localId: Int = 0,
    val date: String,
    val salesId: String,
    val salesName: String,
    val productId: String,
    val productName: String,
    val qty: Int
)

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["salesId"]),
        Index(value = ["statusSynced"])
    ]
)
data class TransactionEntity(
    @PrimaryKey val orderId: String,
    val date: String,
    val salesId: String,
    val salesName: String,
    val outletName: String,
    val outletType: String,
    val geotag: String,
    val total: Double,
    val paymentMethod: String,
    val topDays: Int,
    val dueDate: String,
    val itemsJson: String, // format: [{"id":"...","name":"...","price":...,"qty":...}]
    val statusSynced: Boolean
)

@Entity(tableName = "warehouse_logs")
data class WarehouseLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,
    val type: String,
    val productId: String,
    val productName: String,
    val qtyChange: Int
)

@Entity(tableName = "settings")
data class ReceiptSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val header: String = "ESRA UTAMA SFA",
    val footer: String = "Terima Kasih",
    val logoBase64: String = "",
    val logoAlign: String = "Center",
    val headerAlign: String = "Center",
    val footerAlign: String = "Center"
)

@Entity(tableName = "stokis")
data class StokisEntity(
    @PrimaryKey val id: String,
    val name: String,
    val address: String,
    val assignedSalesId: String = "",
    val assignedSalesName: String = ""
)

@Entity(tableName = "stokis_stock", primaryKeys = ["stokisId", "productId"])
data class StokisStockEntity(
    val stokisId: String,
    val stokisName: String,
    val productId: String,
    val productName: String,
    val qty: Int,
    val lastUpdateDate: String = ""
)

@Entity(
    tableName = "pending_uploads",
    indices = [
        Index(value = ["status"]),
        Index(value = ["createdAt"])
    ]
)
data class PendingUploadEntity(
    @PrimaryKey val id: String, // orderId or outletId (unique key)
    val type: String, // "Transaction" or "Outlet"
    val name: String, // Name of outlet or display details
    val status: String, // "Pending", "Retrying", "Failed", "Success"
    val lastAttempt: String, // Timestamp of last attempt
    val retryCount: Int = 0,
    val createdAt: String
)

@Entity(
    tableName = "sync_audit_logs",
    indices = [
        Index(value = ["timestamp"])
    ]
)
data class SyncAuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: String,
    val processType: String,
    val details: String,
    val result: String,
    val errorMessage: String? = null
)

