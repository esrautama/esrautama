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
