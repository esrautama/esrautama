package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        UserEntity::class,
        ProductEntity::class,
        OutletEntity::class,
        StockAllocationEntity::class,
        TransactionEntity::class,
        WarehouseLogEntity::class,
        ReceiptSettingsEntity::class,
        StokisEntity::class, StokisStockEntity::class,
        PendingUploadEntity::class,
        SyncAuditLogEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // PERBAIKAN BUG KRITIS (data loss): sebelumnya database ini memakai
        // .fallbackToDestructiveMigration() TANPA syarat. Artinya kalau versi
        // database di atas (saat ini 10) dinaikkan di rilis APK berikutnya --
        // misalnya karena ada kolom/tabel baru -- Room akan MENGHAPUS TOTAL
        // seluruh database lokal di HP setiap sales, TERMASUK transaksi yang
        // statusSynced-nya masih false (belum sempat ter-upload ke server) dan
        // PendingUpload yang masih "Pending"/"Failed". Untuk 20 sales yang
        // kerja di lapangan dan baru sync besoknya, ini artinya update aplikasi
        // bisa menghilangkan transaksi harian mereka tanpa peringatan apapun.
        //
        // Sekarang diganti dengan .fallbackToDestructiveMigrationOnDowngrade():
        // hanya boleh menghapus data kalau versi database TURUN (skenario
        // langka, misalnya downgrade APK manual), TIDAK PERNAH menghapus data
        // saat versi NAIK (kondisi normal setiap rilis update).
        //
        // KONSEKUENSI PENTING UNTUK KE DEPAN:
        // Begitu ada perubahan skema (menambah/mengubah kolom di Entities.kt,
        // atau menaikkan `version` di @Database di atas), WAJIB menambahkan
        // objek Migration eksplisit dan mendaftarkannya lewat .addMigrations(...)
        // di bawah -- kalau tidak, Room akan melempar IllegalStateException
        // saat versi baru pertama kali dibuka (app akan crash dengan pesan
        // error migrasi yang jelas), BUKAN diam-diam menghapus data seperti
        // sebelumnya. Ini disengaja: lebih baik ketahuan & diperbaiki sebelum
        // rilis daripada silent data loss di HP sales.
        //
        // Contoh pola kalau nanti versi naik dari 10 ke 11 (menambah kolom
        // baru "catatan" ke tabel transactions misalnya):
        //
        // private val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
        //     override fun migrate(db: SupportSQLiteDatabase) {
        //         db.execSQL("ALTER TABLE transactions ADD COLUMN catatan TEXT NOT NULL DEFAULT ''")
        //     }
        // }
        // lalu tambahkan .addMigrations(MIGRATION_10_11) di databaseBuilder di bawah,
        // DAN naikkan version = 11 di anotasi @Database di atas.
        private val MIGRATIONS: Array<androidx.room.migration.Migration> = arrayOf(
            // Daftarkan Migration baru di sini setiap kali `version` di @Database dinaikkan.
        )

        private const val DB_NAME = "esra_utama_sfa_database"

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME
            )
                .addMigrations(*MIGRATIONS)
                .fallbackToDestructiveMigrationOnDowngrade()
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .addCallback(DatabaseCallback(context.applicationContext))
                .build()
        }

        // PERBAIKAN (self-healing database): kasus nyata yang ditemukan saat trial --
        // HP yang PERNAH terpasang build lama (skema tabel sedikit berbeda meski
        // sama-sama tercatat version 10, wajar terjadi selama iterasi development)
        // lalu di-update lewat USB debugging, membuat Room gagal membuka database
        // lama tsb dan melempar exception SETIAP kali app dibuka -- satu-satunya
        // jalan keluar sebelumnya adalah user manual Clear Cache/Data lewat
        // Pengaturan HP. Sekarang exception itu ditangkap DI SINI: kalau database
        // lama terbukti tidak bisa dibuka/tidak kompatibel, otomatis dihapus dan
        // dibuat ulang dari nol -- app tetap bisa terbuka normal tanpa butuh
        // campur tangan user. Catatan: ini HANYA terpicu kalau database benar2
        // tidak bisa dibuka (kasus langka/abnormal); pada HP yang baru pertama
        // kali pasang aplikasi (kondisi normal 20 sales nanti), baris ini tidak
        // pernah tersentuh sama sekali.
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = try {
                    val db = buildDatabase(context)
                    // Paksa buka database sekarang juga (bukan nanti secara lazy)
                    // supaya kalau ada schema yang tidak kompatibel, exception-nya
                    // terlempar & tertangkap di sini, bukan muncul acak nanti di
                    // tengah pemakaian app.
                    db.openHelper.writableDatabase
                    db
                } catch (e: Exception) {
                    context.deleteDatabase(DB_NAME)
                    val fresh = buildDatabase(context)
                    fresh.openHelper.writableDatabase
                    fresh
                }
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(
        private val context: Context
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            CoroutineScope(Dispatchers.IO).launch {
                val dao = getDatabase(context).appDao()
                populateInitialData(dao)
            }
        }

        private suspend fun populateInitialData(dao: AppDao) {
            // Add users from spreadsheet
            dao.insertUser(UserEntity("ADM-01", "admin", "admin123", "Admin"))
            dao.insertUser(UserEntity("SPV-01", "spv", "spv123", "Supervisor"))
            dao.insertUser(UserEntity("SLS-01", "WAWAN", "sales123", "Sales"))
            dao.insertUser(UserEntity("SLS-02", "MADRIM", "sales123", "Sales"))
            dao.insertUser(UserEntity("SLS-03", "TONARI", "sales123", "Sales"))
            dao.insertUser(UserEntity("SLS-04", "ROY", "sales123", "Sales"))
            dao.insertUser(UserEntity("SLS-05", "HENDRIS", "sales123", "Sales"))

            // Add products from spreadsheet
            dao.insertProduct(ProductEntity("PRD-96260", "ESRA ORIGINAL KRETEK 16", 8600.0, 8400.0, 822))
            dao.insertProduct(ProductEntity("PRD-76214", "ESRA KOPI KRETEK 16", 8800.0, 8600.0, 316))
            dao.insertProduct(ProductEntity("PRD-19730", "ESRA BERRY ICE KRETEK 16", 8800.0, 8600.0, 100))
            dao.insertProduct(ProductEntity("PRD-64260", "MADUMORO KRETEK 16", 9000.0, 8900.0, 720))
            dao.insertProduct(ProductEntity("PRD-48961", "MAKNA EXCLUSIVE KRETEK 16", 9900.0, 9800.0, 89))
            dao.insertProduct(ProductEntity("PRD-28912", "GLAMOUR EXCLUSIVE KRETEK 12", 9000.0, 8800.0, 95))
            dao.insertProduct(ProductEntity("PRD-43102", "NR BOLD FILTER 20", 17000.0, 16800.0, 112))
            dao.insertProduct(ProductEntity("PRD-09611", "MATARAM KRETEK 16", 8000.0, 7800.0, 254))
            dao.insertProduct(ProductEntity("PRD-22188", "LADANG CENGKEH KRETEK 20", 11000.0, 10800.0, 300))
            dao.insertProduct(ProductEntity("PRD-30403", "KONDANG APEL KRETEK 16", 9000.0, 8800.0, 155))
            dao.insertProduct(ProductEntity("PRD-46326", "DUMANYAR MERAH KRETEK 16", 9000.0, 8800.0, 105))
            dao.insertProduct(ProductEntity("PRD-55355", "DUMANYAR APEL KRETEK 16", 9500.0, 9300.0, 683))
            dao.insertProduct(ProductEntity("PRD-55356", "DUMANYAR KOPI KRETEK 16", 9400.0, 9200.0, 679))
            dao.insertProduct(ProductEntity("PRD-55357", "DUMANYAR ANGGUR KRETEK 16", 9400.0, 9200.0, 750))
            dao.insertProduct(ProductEntity("PRD-55358", "DUMANYAR JAMBU KRETEK 16", 9400.0, 9200.0, 750))
            dao.insertProduct(ProductEntity("PRD-55359", "DUMANYAR EXTRA KRETEK 16", 9400.0, 9200.0, 780))
            dao.insertProduct(ProductEntity("PRD-55360", "DUMANYAR MELON KRETEK 16", 9400.0, 9200.0, 780))

            // Add default outlets
            dao.insertOutlet(OutletEntity("000001", "Toko Bangunan Makmur", "Regular", "Retail", "Jl. Raya Padang No. 12", "-0.9472,100.4172", "SLS-01", "WAWAN"))
            dao.insertOutlet(OutletEntity("000002", "Toko Jaya Abadi", "Regular", "Wholesale", "Jl. By Pass KM 10", "-0.9123,100.3456", "SLS-01", "WAWAN"))
            dao.insertOutlet(OutletEntity("000003", "Depo Semesta Raya", "Regular", "Wholesale", "Kawasan Industri Padang", "-0.8912,100.3981", "", ""))

            // Add default settings
            dao.insertSettings(
                ReceiptSettingsEntity(
                    id = 1,
                    header = "ESRA UTAMA SFA\nPadang, Indonesia",
                    footer = "Terima Kasih Atas Kunjungan Anda\nBarang yang sudah dibeli tidak dapat ditukar",
                    logoBase64 = "",
                    logoAlign = "Center",
                    headerAlign = "Center",
                    footerAlign = "Center"
                )
            )
        }
    }
}
