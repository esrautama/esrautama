    fun exportRawDataToCSV(): String {
        val headers = listOf("Tanggal", "Nama Sales", "Nama Outlet", "SKU", "Qty", "Nominal Rupiah")
        val data = mutableListOf<List<String>>()
        
        val moshi = com.squareup.moshi.Moshi.Builder().addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
        val mapType = com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, mapType)
        val jsonAdapter = moshi.adapter<List<Map<String, Any>>>(listType)

        transactionsList.value.forEach { trx ->
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
