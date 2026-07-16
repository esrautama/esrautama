
// --- ADMIN RAW DATA SECTION ---
@Composable
fun AdminRawDataSection(
    viewModel: SfaViewModel
) {
    val context = LocalContext.current
    var csvContent by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        csvContent = viewModel.exportRawDataToCSV()
    }
    
    val transactions by viewModel.transactionsList.collectAsStateWithLifecycle()
    // Re-generate if transactions change
    LaunchedEffect(transactions) {
        csvContent = viewModel.exportRawDataToCSV()
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
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Raw Data CSV", csvContent)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "CSV disalin ke Clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy CSV", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy CSV")
                }
                
                Button(
                    onClick = {
                        val sendIntent: Intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, csvContent)
                            type = "text/csv"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, null)
                        context.startActivity(shareIntent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share CSV", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share CSV")
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                Text(
                    text = "Preview CSV (Top 100 baris):",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Show a preview of the CSV
                val previewLines = csvContent.lines().take(100).joinToString("\n")
                
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
