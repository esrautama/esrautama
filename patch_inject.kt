@Composable
fun AdminInjectStockSection(
    products: List<ProductEntity>,
    users: List<UserEntity>,
    onInjectStock: (String, String, String, Int) -> Unit
) {
    var selectedSalesId by remember { mutableStateOf("") }
    val qtysToInject = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }
    
    var salesExpanded by remember { mutableStateOf(false) }
    var productsExpanded by remember { mutableStateOf(true) }

    val salesUsers = users.filter { it.role == "Sales" }
    val selectedSalesName = salesUsers.find { it.id == selectedSalesId }?.username ?: "Pilih Salesman"
    val isStokisUser = salesUsers.find { it.id == selectedSalesId }?.isStokisSales == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Inject Stock Mobil Sales (Kurangi Gudang)", fontWeight = FontWeight.Bold, fontSize = 14.sp)

        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // --- Pilih Salesman ---
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { salesExpanded = !salesExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("1. Pilih Salesman", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Text(selectedSalesName, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Icon(
                        imageVector = if (salesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand Salesman"
                    )
                }

                if (salesExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        salesUsers.forEach { s ->
                            val isSelected = selectedSalesId == s.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { 
                                        selectedSalesId = s.id 
                                        salesExpanded = false
                                    }
                                    .padding(10.dp)
                            ) {
                                Text(s.username, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                // --- Pilih Produk ---
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { productsExpanded = !productsExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("2. Pilih Produk Katalog", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        val totalSelected = qtysToInject.values.count { (it.toIntOrNull() ?: 0) > 0 }
                        Text("$totalSelected Produk Dipilih", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Icon(
                        imageVector = if (productsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand Products"
                    )
                }

                if (productsExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        products.forEach { p ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(p.name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("Sisa Gudang: ${p.warehouseStock}", fontSize = 10.sp, color = Color.Gray)
                                }
                                OutlinedTextField(
                                    value = qtysToInject[p.id] ?: "",
                                    onValueChange = { qtysToInject[p.id] = it },
                                    label = { Text("Qty", fontSize = 10.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.width(80.dp),
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                                )
                            }
                        }
                    }
                }

                if (isStokisUser) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFEF2F2), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFFCA5A5), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Salesman dengan Otoritas Stokis hanya diperbolehkan meng-inject stock secara mandiri dari Stock Stokis, bukan langsung dari Stock Gudang. Silakan distribusikan stock ke Stokis terlebih dahulu melalui menu 'Stock Stokis'.",
                            color = Color(0xFFB91C1C),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Button(
                    onClick = {
                        val sales = salesUsers.find { it.id == selectedSalesId }
                        if (sales != null) {
                            qtysToInject.forEach { (pid, qtyStr) ->
                                val qty = qtyStr.toIntOrNull() ?: 0
                                if (qty > 0) {
                                    onInjectStock(sales.id, sales.username, pid, qty)
                                }
                            }
                            selectedSalesId = ""
                            qtysToInject.clear()
                            salesExpanded = false
                            productsExpanded = false
                        }
                    },
                    enabled = !isStokisUser && selectedSalesId.isNotEmpty() && qtysToInject.values.any { (it.toIntOrNull() ?: 0) > 0 },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Inject Stock ke Sales")
                }
            }
        }
    }
}
