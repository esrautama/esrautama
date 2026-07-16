// --- ADMIN RAW DATA SECTION ---
@Composable
fun AdminRawDataSection(
    viewModel: SfaViewModel
) {
    val context = LocalContext.current
    var csvContent by remember { mutableStateOf("") }
    
    // Filter State
    val todayDate = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()) }
    var startDate by remember { mutableStateOf(todayDate) }
    var endDate by remember { mutableStateOf(todayDate) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val createDocumentLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(csvContent.toByteArray())
                }
                Toast.makeText(context, "CSV berhasil disimpan!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal menyimpan CSV: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val transactions by viewModel.transactionsList.collectAsStateWithLifecycle()
    
    // Generate CSV based on filtered transactions
    LaunchedEffect(transactions, startDate, endDate) {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val startD = try { sdf.parse(startDate) } catch(e: Exception) { null }
        val endD = try { sdf.parse(endDate) } catch(e: Exception) { null }
        
        csvContent = viewModel.exportRawDataToCSVFiltered(startD, endD)
    }

    if (showStartPicker) {
        val calendar = java.util.Calendar.getInstance()
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val m = (month + 1).toString().padStart(2, '0')
                val d = dayOfMonth.toString().padStart(2, '0')
                startDate = "$year-$m-$d"
                showStartPicker = false
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        ).apply {
            setOnCancelListener { showStartPicker = false }
        }.show()
    }

    if (showEndPicker) {
        val calendar = java.util.Calendar.getInstance()
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val m = (month + 1).toString().padStart(2, '0')
                val d = dayOfMonth.toString().padStart(2, '0')
                endDate = "$year-$m-$d"
                showEndPicker = false
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        ).apply {
            setOnCancelListener { showEndPicker = false }
        }.show()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Raw Data Penjualan", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val fileName = "RawData_Sales_${startDate}_to_${endDate}.csv"
                        createDocumentLauncher.launch(fileName)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Download CSV", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Download CSV")
                }
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Filter Range:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = { showStartPicker = true }, shape = RoundedCornerShape(8.dp)) {
                Text(startDate)
            }
            Text("sampai", fontSize = 12.sp)
            OutlinedButton(onClick = { showEndPicker = true }, shape = RoundedCornerShape(8.dp)) {
                Text(endDate)
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                Text(
                    text = "Preview CSV (Top 50 baris):",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                val previewLines = csvContent.lines().take(50).joinToString("\n")
                
                OutlinedTextField(
                    value = previewLines,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxSize(),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                )
            }
        }
    }
}
