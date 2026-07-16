# Panduan Sinkronisasi dengan Google Apps Script (Spreadsheet)

Aplikasi ini sudah dilengkapi dengan kerangka integrasi ke Google Sheets menggunakan file `AppsScript.gs` dan `SyncService.kt` (Retrofit). Agar aplikasi benar-benar terhubung ke Google Sheets Anda, ikuti langkah-langkah berikut:

## Langkah 1: Persiapkan Google Sheets
1. Buka Google Drive, lalu buat file **Google Sheets** baru (misalnya dengan nama "Database SFA").
2. Salin **ID Spreadsheet** Anda. Anda bisa menemukannya di URL browser, misalnya:
   `https://docs.google.com/spreadsheets/d/1yOoncZUDh8qnrXn6mN3ttYhJunmfpKfQMjfIn_vg-ew/edit`
   Maka ID-nya adalah `1yOoncZUDh8qnrXn6mN3ttYhJunmfpKfQMjfIn_vg-ew`.
3. Buat Sheet (Tab) di bagian bawah dengan nama persis seperti berikut (perhatikan huruf besar/kecil):
   - **Users**: (Isi baris 1 dengan: `id`, `username`, `password`, `role`)
   - **Products**: (Isi baris 1 dengan: `id`, `name`, `price`, `warehouseStock`, `uom`)
   - **Outlets**: (Isi baris 1 dengan: `id`, `name`, `address`, `kodeHari`, `salesId`, `geotag`)

## Langkah 2: Deploy Google Apps Script
1. Di Google Sheets Anda, klik menu **Extensions (Ekstensi) > Apps Script**.
2. Hapus semua kode bawaan (`myFunction`), lalu buka file `AppsScript.gs` yang ada di *root* proyek Android Anda, salin semua kodenya, dan paste ke editor Apps Script.
3. Di dalam kode tersebut (baris ke-3 dan baris ke-23), cari ID spreadsheet contoh ini:
   `var ss = SpreadsheetApp.openById("1yOoncZUDh8qnrXn6mN3ttYhJunmfpKfQMjfIn_vg-ew");`
   Ganti ID tersebut dengan **ID Spreadsheet Anda sendiri** yang Anda dapatkan di Langkah 1.
4. Klik tombol **Deploy (Penerapan) > New deployment (Penerapan baru)** di kanan atas.
5. Pada logo roda gigi (Select type), pilih **Web app**.
6. Konfigurasi:
   - Execute as: **Me (Anda)**
   - Who has access: **Anyone (Siapa saja)**
7. Klik **Deploy**. (Jika diminta otorisasi akses akun Google, klik *Advanced > Go to (unsafe) > Allow*).
8. Setelah berhasil, Anda akan mendapatkan **Web app URL** (berawalan `https://script.google.com/macros/...`). Salin URL tersebut!

## Langkah 3: Integrasi URL ke Aplikasi Android
1. Di dalam proyek Android, buka file `app/src/main/java/com/example/data/SyncService.kt`.
2. Cari variabel `BASE_URL` di dalam `object RetrofitClient`:
   ```kotlin
   private const val BASE_URL = "https://script.google.com/macros/s/AKfycbz_YOUR_WEB_APP_ID_HERE/"
   ```
3. Ganti nilainya dengan **Web app URL** yang Anda salin pada Langkah 2. (Pastikan URL berakhiran `/` atau `exec` bergantung konfigurasi Retrofit, disarankan tetap membiarkan ada garis miring `/` di ujung url folder makro).
4. Selesai!

## Catatan Tambahan (Pengembangan Lanjutan)
Saat ini simulasi tombol Tarik Data & Sinkronisasi (`downloadMasterSync` dan `endDaySync` di `SfaViewModel.kt`) masih menggunakan penundaan lokal (`delay`). Jika Anda ingin benar-benar menarik data dari API yang baru saja Anda buat:
- Panggil `val service = RetrofitClient.createService(RetrofitClient.BASE_URL)`
- Gunakan `service.getSheetData("getUsers")` atau `service.syncTransactions(syncRequest)` di dalam rutin Coroutine Anda pada kelas ViewModel.

### Apakah Modifikasi ViewModel Mempengaruhi Auto-Sync 3 Jam?
**Tidak.** 
Tombol manual (`downloadMasterSync` dan `endDaySync` di `SfaViewModel.kt`) dan sinkronisasi otomatis per 3 jam (`SyncWorker.kt`) menggunakan fungsi yang terpisah. 

Jika Anda mengubah kode di `SfaViewModel.kt` untuk menggunakan Retrofit (API asli), **sinkronisasi otomatis Anda akan tetap berjalan secara simulasi** karena ia diatur secara independen. 

Agar **auto-sync per 3 jam** juga benar-benar mengirim data ke Google Sheets, Anda juga harus mengubah bagian `doWork()` di dalam file `app/src/main/java/com/example/viewmodel/SyncWorker.kt` menjadi seperti ini:

```kotlin
if (unsynced.isNotEmpty()) {
    // 1. Panggil Retrofit
    val service = RetrofitClient.createService("https://script.google.com/macros/s/AKfycbz_YOUR_WEB_APP_ID_HERE/")
    val request = SyncRequest(transactions = unsynced)
    val response = service.syncTransactions(request)
    
    // 2. Jika sukses, tandai lokal sebagai tersinkronisasi
    if (response.status == "success") {
        val updated = unsynced.map { it.copy(statusSynced = true) }
        repository.insertTransactions(updated)
    }
}
```
