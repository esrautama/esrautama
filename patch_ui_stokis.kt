// --- ADMIN STOKIS MANAGEMENT COMPONENT ---
@Composable
fun AdminStokisSection(
    viewModel: SfaViewModel,
    onAddStokis: () -> Unit,
    onEditStokis: (com.example.data.StokisEntity) -> Unit
) {
    val products by viewModel.productsList.collectAsStateWithLifecycle()
    val allStokis by viewModel.stokisList.collectAsStateWithLifecycle()
    val stokisList by viewModel.stokisStockList.collectAsStateWithLifecycle()

    var selectedStokisId by remember { mutableStateOf("") }
    var selectedProductId by remember { mutableStateOf("") }
    var qtyToInject by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Master Stokis
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Master Gudang Stokis", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            IconButton(onClick = onAddStokis, modifier = Modifier.size(32.dp).background(Color(0xFFEFF6FF), CircleShape)) {
                Icon(Icons.Default.Add, contentDescription = "Tambah Stokis", tint = Color(0xFF1D4ED8), modifier = Modifier.size(14.dp))
            }
        }
        
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (allStokis.isEmpty()) {
                    Text("Belum ada Master Stokis. Silakan tambahkan.", color = Color.Gray, fontSize = 12.sp)
                } else {
                    allStokis.forEach { s ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(s.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(s.address, fontSize = 10.sp, color = Color.Gray)
                            }
                            Row {
                                IconButton(onClick = { onEditStokis(s) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray, modifier = Modifier.size(14.dp))
                                }
                                IconButton(onClick = { viewModel.deleteStokis(s.id) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        
        HorizontalDivider()

        Text("Daftar Stok per Stokis", fontWeight = FontWeight.Bold, fontSize = 14.sp)

        // Show Stokis list table
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (stokisList.isEmpty()) {
                    Text("Belum ada stok di Stokis. Silakan inject dari gudang di bawah ini.", color = Color.Gray, fontSize = 12.sp)
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Stokis & Produk", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
                        Text("Stock", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
                    }
                    stokisList.forEach { s ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(s.productName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("Di: ${s.stokisName}", fontSize = 10.sp, color = Color.Gray)
                            }
                            Text("${s.qty} PCS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        Text("Inject Stock Gudang ke Stokis", fontWeight = FontWeight.Bold, fontSize = 14.sp)

        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("1. Pilih Stokis", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                allStokis.forEach { s ->
                    val isSelected = selectedStokisId == s.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedStokisId = s.id }
                            .padding(10.dp)
                    ) {
                        Text(s.name, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                
                HorizontalDivider()
                
                Text("2. Pilih Produk Gudang", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                products.forEach { p ->
                    val isSelected = selectedProductId == p.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedProductId = p.id }
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(p.name, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        Text("Gudang: ${p.warehouseStock} PCS", fontSize = 12.sp, color = Color.Gray)
                    }
                }
                
                HorizontalDivider()

                OutlinedTextField(
                    value = qtyToInject,
                    onValueChange = { if (it.all { char -> char.isDigit() }) qtyToInject = it },
                    label = { Text("Kuantitas (PCS)", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )

                Button(
                    onClick = {
                        val qty = qtyToInject.toIntOrNull() ?: 0
                        if (selectedStokisId.isNotEmpty() && selectedProductId.isNotEmpty() && qty > 0) {
                            viewModel.injectStockToStokis(selectedStokisId, selectedProductId, qty)
                            selectedProductId = ""
                            qtyToInject = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedStokisId.isNotEmpty() && selectedProductId.isNotEmpty() && qtyToInject.isNotEmpty()
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Kirim ke Stokis")
                }
            }
        }
    }
}
