import sys

file_path = 'app/src/main/java/com/example/ui/SfaApp.kt'
print("Reading file...")
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

target = """        Spacer(modifier = Modifier.height(16.dp))

        // Synchronization Audit Logs Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Log Audit Sinkronisasi",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (auditLogs.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearSyncAuditLogs() }) {
                            Text("Hapus Log", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                }

                HorizontalDivider()

                if (auditLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Belum ada log sinkronisasi.",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        auditLogs.forEach { log ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (log.result == "Success") Color(0xFFF0FDF4) else Color(0xFFFDF2F2)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (log.result == "Success") Color(0xFFDCFCE7) else Color(0xFFFDE8E8)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = log.timestamp,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (log.result == "Success") Color(0xFFBBF7D0) else Color(0xFFFECACA)
                                            ),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = if (log.result == "Success") "SUKSES" else "GAGAL",
                                                color = if (log.result == "Success") Color(0xFF166534) else Color(0xFF991B1B),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = log.processType,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Text(
                                        text = log.details,
                                        fontSize = 12.sp,
                                        color = Color.DarkGray
                                    )

                                    if (!log.errorMessage.isNullOrEmpty()) {
                                        Text(
                                            text = "Error: ${log.errorMessage}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF991B1B),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }"""

replacement = """        if (currentUser?.role != "Sales") {
            Spacer(modifier = Modifier.height(16.dp))

            // Synchronization Audit Logs Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Log Audit Sinkronisasi",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (auditLogs.isNotEmpty()) {
                            TextButton(onClick = { viewModel.clearSyncAuditLogs() }) {
                                Text("Hapus Log", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            }
                        }
                    }

                    HorizontalDivider()

                    if (auditLogs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Belum ada log sinkronisasi.",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            auditLogs.forEach { log ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (log.result == "Success") Color(0xFFF0FDF4) else Color(0xFFFDF2F2)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (log.result == "Success") Color(0xFFDCFCE7) else Color(0xFFFDE8E8)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = log.timestamp,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (log.result == "Success") Color(0xFFBBF7D0) else Color(0xFFFECACA)
                                                ),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = if (log.result == "Success") "SUKSES" else "GAGAL",
                                                    color = if (log.result == "Success") Color(0xFF166534) else Color(0xFF991B1B),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = log.processType,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        Text(
                                            text = log.details,
                                            fontSize = 12.sp,
                                            color = Color.DarkGray
                                        )

                                        if (!log.errorMessage.isNullOrEmpty()) {
                                            Text(
                                                text = "Error: ${log.errorMessage}",
                                                fontSize = 11.sp,
                                                color = Color(0xFF991B1B),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }"""

if target in content:
    print("Found exact block! Replacing...")
    new_content = content.replace(target, replacement)
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(new_content)
    print("Replacement successful!")
else:
    print("Exact block NOT found! Let's check with standard normalizing...")
    # Normalize line endings just in case
    content_normalized = content.replace('\r\n', '\n')
    target_normalized = target.replace('\r\n', '\n')
    if target_normalized in content_normalized:
        print("Found normalized block! Replacing...")
        new_content = content_normalized.replace(target_normalized, replacement.replace('\r\n', '\n'))
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(new_content)
        print("Replacement successful (normalized)!")
    else:
        print("Block not found at all! Exiting with error.")
        sys.exit(1)
