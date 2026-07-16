// --- SALES STOKIS DIRECT ALLOCATION COMPONENT ---
@Composable
fun SalesStokisSection(
    viewModel: SfaViewModel
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val allStokis by viewModel.stokisList.collectAsStateWithLifecycle()
    val stokisList by viewModel.stokisStockList.collectAsStateWithLifecycle()

    var selectedStokisId by remember { mutableStateOf("") }
    var selectedProductId by remember { mutableStateOf("") }
    var qtyToRequest by remember { mutableStateOf("") }

    val user = currentUser ?: return

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
        border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Ambil Stock Mandiri dari Stokis", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF15803D))
            }
            Text(
                "Sebagai ${user.username} (${user.id}), Anda memiliki otoritas khusus untuk meng-inject stok mobil Anda langsung dari stokis, bukan dari gudang utama.",
                fontSize = 11.sp,
                color = Color.DarkGray
            )

            if (allStokis.isEmpty()) {
                Text("Master Stokis kosong.", color = Color.Gray, fontSize = 11.sp)
            } else {
                Text("1. Pilih Stokis", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                allStokis.forEach { s ->
                    val isSelected = selectedStokisId == s.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isSelected) Color(0xFFDCFCE7) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedStokisId = s.id }
                            .padding(10.dp)
                    ) {
                        Text(s.name, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text("2. Pilih Produk dari Stokis", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                
                val filteredStock = stokisList.filter { it.stokisId == selectedStokisId }
                
                if (selectedStokisId.isEmpty()) {
                    Text("Silakan pilih stokis terlebih dahulu di atas.", color = Color.Gray, fontSize = 11.sp)
                } else if (filteredStock.isEmpty()) {
                    Text("Persediaan stokis ini saat ini kosong.", color = Color.Gray, fontSize = 11.sp)
                } else {
                    filteredStock.forEach { s ->
                        val isSelected = selectedProductId == s.productId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = if (isSelected) Color(0xFFDCFCE7) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedProductId = s.productId }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(s.productName, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            Text("Tersedia: ${s.qty} PCS", fontSize = 12.sp, color = Color(0xFF15803D))
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    OutlinedTextField(
                        value = qtyToRequest,
                        onValueChange = { if (it.all { char -> char.isDigit() }) qtyToRequest = it },
                        label = { Text("Kuantitas Ambil (PCS)", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )

                    Button(
                        onClick = {
                            val qty = qtyToRequest.toIntOrNull() ?: 0
                            if (selectedStokisId.isNotEmpty() && selectedProductId.isNotEmpty() && qty > 0) {
                                viewModel.injectStockFromStokisToSales(selectedStokisId, user.id, user.username, selectedProductId, qty)
                                selectedProductId = ""
                                qtyToRequest = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedStokisId.isNotEmpty() && selectedProductId.isNotEmpty() && qtyToRequest.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Tarik Stock ke Mobil")
                    }
                }
            }
        }
    }
}
