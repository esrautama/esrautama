        Text("Inject Stock Gudang ke Stokis", fontWeight = FontWeight.Bold, fontSize = 14.sp)

        var stokisExpanded by remember { mutableStateOf(false) }
        var productsExpanded by remember { mutableStateOf(true) }
        val qtysToInject = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }

        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                
                val selectedStokisName = allStokis.find { it.id == selectedStokisId }?.name ?: "Pilih Stokis"
                
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { stokisExpanded = !stokisExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("1. Pilih Stokis", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Text(selectedStokisName, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Icon(
                        imageVector = if (stokisExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand Stokis"
                    )
                }

                if (stokisExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        allStokis.forEach { s ->
                            val isSelected = selectedStokisId == s.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { 
                                        selectedStokisId = s.id 
                                        stokisExpanded = false
                                    }
                                    .padding(10.dp)
                            ) {
                                Text(s.name, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
                
                HorizontalDivider()
                
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { productsExpanded = !productsExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("2. Pilih Produk Gudang", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
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
                                    Text("Gudang: ${p.warehouseStock} PCS", fontSize = 10.sp, color = Color.Gray)
                                }
                                OutlinedTextField(
                                    value = qtysToInject[p.id] ?: "",
                                    onValueChange = { if (it.all { char -> char.isDigit() }) qtysToInject[p.id] = it },
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

                Button(
                    onClick = {
                        val stokis = allStokis.find { it.id == selectedStokisId }
                        if (stokis != null) {
                            qtysToInject.forEach { (pid, qtyStr) ->
                                val qty = qtyStr.toIntOrNull() ?: 0
                                if (qty > 0) {
                                    viewModel.distributeToStokis(stokis.id, pid, qty)
                                }
                            }
                            selectedStokisId = ""
                            qtysToInject.clear()
                            stokisExpanded = false
                            productsExpanded = false
                        }
                    },
                    enabled = selectedStokisId.isNotEmpty() && qtysToInject.values.any { (it.toIntOrNull() ?: 0) > 0 },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Inventory, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Inject ke Stokis")
                }
            }
        }
