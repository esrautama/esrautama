    suspend fun syncWithGoogleSheets(url: String): Pair<Boolean, String> {
        if (url.isBlank()) return Pair(false, "URL Apps Script kosong")
        if (!acquireUploadLock()) return Pair(false, "Sinkronisasi sedang berjalan")
        
        return withContext(Dispatchers.IO) {
            try {
                uploadMutex.withLock {
                    val service = com.example.data.RetrofitClient.createService(url)
                    
                    // Push transactions
                    val transactions = repository.getAllTransactionsDirect()
                    if (transactions.isNotEmpty()) {
                        val request = com.example.data.SyncRequest(transactions = transactions)
                        val response = service.syncTransactions(request)
                        if (response.status != "success") {
                            return@withLock Pair(false, "Gagal push transaksi: ${response.message}")
                        }
                    }
                    
                    // Pull Users
                    val usersData = service.getSheetData("getUsers")
                    if (usersData.isNotEmpty()) {
                        repository.deleteAllUsers()
                        for (row in usersData) {
                            val id = row["ID"]?.toString() ?: continue
                            val username = row["Username"]?.toString() ?: ""
                            val password = row["Password"]?.toString() ?: ""
                            val role = row["Role"]?.toString() ?: "Sales"
                            repository.insertUser(UserEntity(id, username, password, role))
                        }
                    }

                    // Pull Products
                    val productsData = service.getSheetData("getProducts")
                    if (productsData.isNotEmpty()) {
                        repository.deleteAllProducts()
                        for (row in productsData) {
                            val id = row["ID"]?.toString() ?: continue
                            val name = row["Name"]?.toString() ?: ""
                            val prStr = row["PriceRetail"]?.toString() ?: "0"
                            val pwStr = row["PriceWholesale"]?.toString() ?: "0"
                            repository.insertProduct(ProductEntity(id, name, prStr.toDoubleOrNull() ?: 0.0, pwStr.toDoubleOrNull() ?: 0.0, 0))
                        }
                    }

                    // Pull Outlets
                    val outletsData = service.getSheetData("getOutlets")
                    if (outletsData.isNotEmpty()) {
                        repository.deleteAllOutlets()
                        for (row in outletsData) {
                            val id = row["ID"]?.toString() ?: continue
                            val name = row["Name"]?.toString() ?: ""
                            val type = row["Type"]?.toString() ?: ""
                            val category = row["Category"]?.toString() ?: ""
                            val address = row["Address"]?.toString() ?: ""
                            repository.insertOutlet(OutletEntity(id, name, type, category, address, "", "", ""))
                        }
                    }
                    
                    Pair(true, "Sinkronisasi 2-Arah Berhasil!")
                }
            } catch (e: Exception) {
                Pair(false, "Error: ${e.message}")
            } finally {
                releaseUploadLock()
            }
        }
    }
}
