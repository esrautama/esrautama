package com.example.ui

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material.icons.filled.HealthAndSafety

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.data.*
import com.example.viewmodel.SfaViewModel
import java.text.SimpleDateFormat
import java.util.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SfaApp(
    viewModel: SfaViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()

    val successMessage by viewModel.successMessage.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val selectedOutlet by viewModel.selectedOutlet.collectAsStateWithLifecycle()

    // Dialog state
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showCheckoutDialog by remember { mutableStateOf(false) }
    var showNoOrderDialog by remember { mutableStateOf(false) }
    var showReceiptPreviewDialog by remember { mutableStateOf(false) }
    var showAddDataDialog by remember { mutableStateOf(false) }
    var showCsvImportDialog by remember { mutableStateOf(false) }

    // Forms states
    var addDataType by remember { mutableStateOf("Users") } // Users, Products, Outlets
    var editIndexInList by remember { mutableStateOf<Int?>(null) } // null if adding new

    // Handle Toast messages
    LaunchedEffect(successMessage, errorMessage) {
        successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessages()
        }
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessages()
        }
    }

    val isUploadLocked by viewModel.showSyncOverlay.collectAsStateWithLifecycle()
    val checkoutInProgressTopLevel by viewModel.isCheckoutInProgress.collectAsStateWithLifecycle()
    var showSplashScreen by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(2000)
        showSplashScreen = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showSplashScreen) {
            SplashScreen()
        } else if (currentUser == null) {
            LoginScreen(
                viewModel = viewModel,
                onLoginClick = { u, p, onFinish ->
                    viewModel.login(u, p) { success ->
                        onFinish()
                        if (!success) {
                            Toast.makeText(context, "Username atau Password salah atau Server tidak dapat dihubungi!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        } else {
            Scaffold(
                topBar = {
                    SfaHeader(
                        user = currentUser!!,
                        onSettingsClick = { showSettingsDialog = true },
                        onLogoutClick = { viewModel.logout() }
                    )
                },
                bottomBar = {
                    SfaBottomNavigation(
                        role = currentUser!!.role,
                        currentTab = currentTab,
                        onTabSelected = { viewModel.setTab(it) }
                    )
                },
                modifier = modifier.fillMaxSize(),
                contentWindowInsets = WindowInsets.safeDrawing
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    // Tab Selection Pane
                    AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "tab_animation"
                    ) { targetTab ->
                        when (targetTab) {
                            "Sales" -> SalesTab(
                                viewModel = viewModel,
                                onCheckoutClick = { showCheckoutDialog = true },
                                onNoOrderClick = { showNoOrderDialog = true }
                            )
                            "Stock" -> StockTab(viewModel = viewModel, onPrintClick = { showReceiptPreviewDialog = true })
                            "History" -> HistoryTab(
                                viewModel = viewModel,
                                onPrintClick = {
                                    viewModel.preparePrintFromHistory(it)
                                    showReceiptPreviewDialog = true
                                }
                            )
                            "Sync" -> SyncTab(viewModel = viewModel)
                            "Reports" -> ReportsTab(
                                viewModel = viewModel,
                                onPrintFullClick = {
                                    viewModel.preparePrintFullReport()
                                    showReceiptPreviewDialog = true
                                }
                            )
                            "OutletMain" -> OutletMainTab(
                                viewModel = viewModel,
                                onAddOutletClick = {
                                    addDataType = "OutletMain"
                                    editIndexInList = null
                                    showAddDataDialog = true
                                },
                                onEditOutletClick = { index ->
                                    addDataType = "OutletMain"
                                    editIndexInList = index
                                    showAddDataDialog = true
                                },
                                onImportCsvClick = {
                                    addDataType = "OutletMain"
                                    showCsvImportDialog = true
                                }
                            )
                            "AdminPanel" -> AdminPanelTab(
                                viewModel = viewModel,
                                onAddDataClick = { tab ->
                                    addDataType = tab
                                    editIndexInList = null
                                    showAddDataDialog = true
                                },
                                onEditDataClick = { tab, index ->
                                    addDataType = tab
                                    editIndexInList = index
                                    showAddDataDialog = true
                                },
                                onImportCsvClick = { tab ->
                                    addDataType = tab
                                    showCsvImportDialog = true
                                },
                                onPreviewReceiptClick = {
                                    showReceiptPreviewDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }

        if (isUploadLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(enabled = false) {}, // Entirely captures all touch events and blocks clicks
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier.padding(32.dp).widthIn(max = 320.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF0284C7),
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "Sync & Upload Terkunci",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF0F172A),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Sistem sedang mengunggah dan mengamankan database transaksi Anda. Harap tunggu sebentar...",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }

    // --- DIALOGS REGISTRATION ---
    if (showSettingsDialog) {
        SettingsAndPassDialog(
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showCheckoutDialog) {
        CheckoutDialog(
            viewModel = viewModel,
            onDismiss = { showCheckoutDialog = false },
            onConfirmCheckout = {
                viewModel.checkout(isNoOrder = false)
                showCheckoutDialog = false
                showReceiptPreviewDialog = true
            }
        )
    }

    if (showNoOrderDialog) {
        NoOrderDialog(
            isNoo = selectedOutlet?.isNewLocal == true,
            checkoutInProgress = checkoutInProgressTopLevel,
            onDismiss = { showNoOrderDialog = false },
            onConfirm = { reason ->
                viewModel.checkout(isNoOrder = true, noOrderReason = reason)
                showNoOrderDialog = false
            }
        )
    }

    if (showReceiptPreviewDialog) {
        ReceiptPreviewDialog(
            viewModel = viewModel,
            onDismiss = { showReceiptPreviewDialog = false }
        )
    }

    if (showAddDataDialog) {
        AddEditDataDialog(
            viewModel = viewModel,
            dataType = addDataType,
            editIndex = editIndexInList,
            onDismiss = { showAddDataDialog = false }
        )
    }

    if (showCsvImportDialog) {
        CsvImportDialog(
            viewModel = viewModel,
            dataType = addDataType,
            onDismiss = { showCsvImportDialog = false }
        )
    }
}

// --- HEADER COMPONENT ---
@Composable
fun SfaHeader(
    user: UserEntity,
    onSettingsClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isOnline by remember { mutableStateOf(true) }
    
    DisposableEffect(context) {
        val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                isOnline = true
            }
            override fun onLost(network: android.net.Network) {
                isOnline = false
            }
        }
        val request = android.net.NetworkRequest.Builder().addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        try {
            connectivityManager.registerNetworkCallback(request, callback)
            val activeNetwork = connectivityManager.activeNetwork
            val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
            isOnline = caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } catch (e: Exception) {
        }
        
        onDispose {
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (e: Exception) {}
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Selamat Datang,",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = user.username,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .border(
                            width = 0.5.dp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = user.role,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Online/Offline network badge
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isOnline) Color(0xFF22C55E) else Color(0xFFEF4444),
                            shape = RoundedCornerShape(50)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color.White, CircleShape)
                        )
                        Text(
                            text = if (isOnline) "Online" else "Offline",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Header custom settings and logout buttons
                // PERBAIKAN: gear icon "Pengaturan Akun & Printer" sekarang hanya
                // ditampilkan untuk role Admin, supaya sales tidak bisa tidak sengaja
                // mengubah pengaturan. Password sales tetap bisa direset oleh Admin
                // lewat menu Users di SFA Console.
                if (user.role == "Admin") {
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.1f),
                                shape = CircleShape
                            )
                            .testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Pengaturan",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onLogoutClick,
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.1f),
                            shape = CircleShape
                        )
                        .testTag("logout_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = "Keluar",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// --- NAVIGATION COMPONENT ---
@Composable
fun SfaBottomNavigation(
    role: String,
    currentTab: String,
    onTabSelected: (String) -> Unit
) {
    // Generate tabs based on the user's role
    val tabs = remember(role) {
        if (role == "Admin") {
            listOf(
                TabItem("AdminPanel", "Admin", Icons.Default.AdminPanelSettings),
                TabItem("OutletMain", "Outlet", Icons.Default.Storefront),
                TabItem("Stock", "Stock", Icons.Default.Inventory2),
                TabItem("History", "History", Icons.Default.History),
                TabItem("Reports", "Laporan", Icons.Default.PieChart)
            )
        } else {
            listOf(
                TabItem("Sales", "Sales", Icons.Default.Storefront),
                TabItem("Stock", "Stock", Icons.Default.Inventory2),
                TabItem("History", "History", Icons.Default.History),
                TabItem("Sync", "Sync", Icons.Default.Sync),
                TabItem("Reports", "Laporan", Icons.Default.PieChart)
            )
        }
    }

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        modifier = Modifier.testTag("bottom_nav")
    ) {
        tabs.forEach { tab ->
            val selected = currentTab == tab.id
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab.id) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    unselectedIconColor = Color.Gray,
                    unselectedTextColor = Color.Gray
                )
            )
        }
    }
}

data class TabItem(val id: String, val label: String, val icon: ImageVector)

@Composable
fun LoginUrlSettingsDialog(
    viewModel: SfaViewModel,
    onDismiss: () -> Unit
) {
    val appsScriptUrl by viewModel.appsScriptUrl.collectAsStateWithLifecycle()
    var currentUrl by remember { mutableStateOf(appsScriptUrl) }
    val context = LocalContext.current
    var isSyncing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Pengaturan URL Server", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF0F172A))
                    IconButton(onClick = onDismiss, enabled = !isSyncing) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Text(
                    text = "Gunakan menu ini untuk menghubungkan aplikasi ke Google Spreadsheet Anda dengan memasukkan URL Google Apps Script Web App.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                OutlinedTextField(
                    value = currentUrl,
                    onValueChange = { currentUrl = it },
                    label = { Text("URL Google Apps Script") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSyncing,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF3B82F6),
                        focusedLabelColor = Color(0xFF3B82F6)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Test connection / sync button
                    Button(
                        onClick = {
                            if (currentUrl.isBlank()) {
                                Toast.makeText(context, "URL tidak boleh kosong", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isSyncing = true
                            scope.launch {
                                // Save the URL first so sync uses the correct one
                                viewModel.saveAppsScriptUrl(currentUrl.trim())
                                val result = viewModel.syncWithGoogleSheets(currentUrl.trim())
                                Toast.makeText(context, result.second, Toast.LENGTH_LONG).show()
                                isSyncing = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        enabled = !isSyncing,
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 1.5.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tarik Data", fontSize = 11.sp, maxLines = 1)
                        }
                    }

                    // Save and Close button
                    Button(
                        onClick = {
                            if (currentUrl.isBlank()) {
                                Toast.makeText(context, "URL tidak boleh kosong", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            viewModel.saveAppsScriptUrl(currentUrl.trim())
                            Toast.makeText(context, "URL berhasil disimpan.", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A)),
                        enabled = !isSyncing,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Simpan", fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

// --- LOGIN SCREEN ---
@Composable
fun LoginScreen(
    viewModel: SfaViewModel,
    onLoginClick: (String, String, () -> Unit) -> Unit = { _, _, _ -> }
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoggingIn by remember { mutableStateOf(false) }
    var showUrlSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        val isLandscape = maxWidth > maxHeight || maxWidth > 600.dp

        if (isLandscape) {
            // Web/Tablet Landscape Split View
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF8FAFC)) // Light gray background
            ) {
                // Left side branding panel
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF1E3A8A), Color(0xFF1D4ED8))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(32.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                            modifier = Modifier
                                .size(160.dp)
                                .padding(bottom = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = "https://res.cloudinary.com/donww7xep/image/upload/v1782804261/ChatGPT_Image_28_Jun_2026_21.47.19_nupxu2.png",
                                    contentDescription = "Logo ESRA UTAMA",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp)
                                        .clip(RoundedCornerShape(16.dp)),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                        Text(
                            text = "ESRA UTAMA SFA",
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Sales Force Automation System",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Right side login form
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .padding(vertical = 24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Text(
                                text = "Silakan Masuk",
                                color = Color(0xFF0F172A),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )

                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text("Username") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("username_input"),
                                singleLine = true,
                                enabled = !isLoggingIn,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF3B82F6),
                                    focusedLabelColor = Color(0xFF3B82F6)
                                )
                            )

                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Password") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { 
                                    if (username.isNotBlank() && password.isNotBlank()) {
                                        isLoggingIn = true
                                        onLoginClick(username, password) { isLoggingIn = false }
                                    }
                                }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("password_input"),
                                singleLine = true,
                                enabled = !isLoggingIn,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF3B82F6),
                                    focusedLabelColor = Color(0xFF3B82F6)
                                )
                            )

                            Button(
                                onClick = { 
                                    if (username.isBlank() || password.isBlank()) {
                                        Toast.makeText(context, "Username dan Password harus diisi", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isLoggingIn = true
                                    onLoginClick(username, password) { isLoggingIn = false }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A)),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isLoggingIn,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("login_button")
                            ) {
                                if (isLoggingIn) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = "MASUK",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "v${com.example.BuildConfig.VERSION_NAME} (Hybrid Offline Support)",
                                    color = Color.Gray.copy(alpha = 0.5f),
                                    fontSize = 10.sp,
                                    // PERBAIKAN: gear icon pengaturan URL server dihapus dari tampilan
                                    // supaya tidak terlihat/terpencet sales awam. Akses hanya lewat
                                    // long-press (tahan) teks versi ini, khusus untuk admin/teknisi.
                                    modifier = Modifier.pointerInput(Unit) {
                                        detectTapGestures(onLongPress = { showUrlSettings = true })
                                    }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Portrait layout (original)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF1E3A8A), Color(0xFF1D4ED8))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Animated/Polished logo card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(32.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                        modifier = Modifier
                            .size(140.dp)
                            .padding(bottom = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = "https://res.cloudinary.com/donww7xep/image/upload/v1782804261/ChatGPT_Image_28_Jun_2026_21.47.19_nupxu2.png",
                                contentDescription = "Logo ESRA UTAMA",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    Text(
                        text = "ESRA UTAMA SFA",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Sales Force Automation System",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Input credentials card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Silakan Masuk",
                                color = Color(0xFF0F172A),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )

                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text("Username") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("username_input"),
                                singleLine = true,
                                enabled = !isLoggingIn,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF3B82F6),
                                    focusedLabelColor = Color(0xFF3B82F6)
                                )
                            )

                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Password") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { 
                                    if (username.isNotBlank() && password.isNotBlank()) {
                                        isLoggingIn = true
                                        onLoginClick(username, password) { isLoggingIn = false }
                                    }
                                }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("password_input"),
                                singleLine = true,
                                enabled = !isLoggingIn,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF3B82F6),
                                    focusedLabelColor = Color(0xFF3B82F6)
                                )
                            )

                            Button(
                                onClick = { 
                                    if (username.isBlank() || password.isBlank()) {
                                        Toast.makeText(context, "Username dan Password harus diisi", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isLoggingIn = true
                                    onLoginClick(username, password) { isLoggingIn = false }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A)),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isLoggingIn,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("login_button")
                            ) {
                                if (isLoggingIn) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = "MASUK",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "v${com.example.BuildConfig.VERSION_NAME} (Hybrid Offline Support)",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal,
                        // PERBAIKAN: gear icon pengaturan URL server dihapus dari tampilan
                        // supaya tidak terlihat/terpencet sales awam. Akses hanya lewat
                        // long-press (tahan) teks versi ini, khusus untuk admin/teknisi.
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(onLongPress = { showUrlSettings = true })
                            }
                    )
                }
            }
        }

        if (showUrlSettings) {
            LoginUrlSettingsDialog(
                viewModel = viewModel,
                onDismiss = { showUrlSettings = false }
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
// --- SALES TAB COMPONENT ---
@Composable
fun SalesTab(
    viewModel: SfaViewModel,
    onCheckoutClick: () -> Unit,
    onNoOrderClick: () -> Unit
) {
    val selectedOutlet by viewModel.selectedOutlet.collectAsStateWithLifecycle()
    val currentOutletType by viewModel.currentOutletType.collectAsStateWithLifecycle()
    val salesOutletSearchQuery by viewModel.salesOutletSearchQuery.collectAsStateWithLifecycle()
    val outletsList by viewModel.outletsList.collectAsStateWithLifecycle()
    val productsList by viewModel.productsList.collectAsStateWithLifecycle()
    val stockAllocationsList by viewModel.stockAllocationsList.collectAsStateWithLifecycle()
    val cart by viewModel.cart.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val transactionsList by viewModel.transactionsList.collectAsStateWithLifecycle()

    val context = androidx.compose.ui.platform.LocalContext.current
    val locationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            viewModel.mockGetGeotag()
        } else {
            viewModel.mockGetGeotag()
        }
    }

    val todayStr = remember { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date()) }
    val todayIndoDayName = remember {
        when (java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.MONDAY -> "SENIN"
            java.util.Calendar.TUESDAY -> "SELASA"
            java.util.Calendar.WEDNESDAY -> "RABU"
            java.util.Calendar.THURSDAY -> "KAMIS"
            java.util.Calendar.FRIDAY -> "JUMAT"
            java.util.Calendar.SATURDAY -> "SABTU"
            java.util.Calendar.SUNDAY -> "MINGGU"
            else -> ""
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (selectedOutlet == null) {
            // OUTLET SELECTION CONTAINER
            Text(
                text = "Pilih Outlet untuk Memulai",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Segmented selector for Regular vs NOO
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(Color.LightGray.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("Regular", "NOO").forEach { type ->
                    val isSelected = currentOutletType == type
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable { viewModel.currentOutletType.value = type },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = type,
                            color = if (isSelected) Color.White else Color.Gray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (currentOutletType == "Regular") {
                // ACTIVE ROUTE STATUS BANNER
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Rute Ter-Inject Hari Ini: $todayIndoDayName",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (salesOutletSearchQuery.isNotBlank()) {
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "Mencari Semua...",
                            fontSize = 10.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // REGULAR OUTLETS LIST WITH SEARCH
                OutlinedTextField(
                    value = salesOutletSearchQuery,
                    onValueChange = { viewModel.salesOutletSearchQuery.value = it },
                    placeholder = { Text("Cari nama/alamat outlet...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Filtered outlets
                val filteredOutlets = remember(salesOutletSearchQuery, outletsList, currentUser, todayIndoDayName) {
                    outletsList.filter { out ->
                        val isMyOutlet = out.salesId.equals(currentUser?.id, ignoreCase = true) || out.salesName.equals(currentUser?.username, ignoreCase = true)
                        if (isMyOutlet) {
                            if (salesOutletSearchQuery.isBlank()) {
                                // Default / Empty search query -> show only today's injected route
                                out.kodeHari.startsWith(todayIndoDayName, ignoreCase = true)
                            } else {
                                // Search query present -> search through all outlets assigned to this sales
                                out.name.contains(salesOutletSearchQuery, ignoreCase = true) ||
                                        out.address.contains(salesOutletSearchQuery, ignoreCase = true) ||
                                        out.kodeHari.contains(salesOutletSearchQuery, ignoreCase = true)
                            }
                        } else {
                            false
                        }
                    }
                }

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (filteredOutlets.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (salesOutletSearchQuery.isBlank()) {
                                        "Tidak ada outlet regular di rute hari ini ($todayIndoDayName).\nGunakan kolom pencarian di atas untuk memanggil outlet lain."
                                    } else {
                                        "Tidak ada outlet yang cocok dengan pencarian '$salesOutletSearchQuery'."
                                    },
                                    color = Color.Gray,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        items(filteredOutlets) { out ->
                            val todayTrxs = remember(transactionsList, out.name, todayStr) {
                                transactionsList.filter { it.outletName == out.name && it.date.startsWith(todayStr) }
                            }
                            val hasTransactionToday = todayTrxs.isNotEmpty()
                            val isNoOrderOnly = todayTrxs.isNotEmpty() && todayTrxs.all { it.paymentMethod == "No Order" }

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (hasTransactionToday) Color(0xFFF0FDF4) else Color.White
                                ),
                                border = if (hasTransactionToday) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDCFCE7)) else null,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.selectOutlet(out) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = out.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (out.isNewLocal) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                                                    contentColor = Color(0xFFD97706),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.CloudQueue,
                                                            contentDescription = "Offline",
                                                            tint = Color(0xFFD97706),
                                                            modifier = Modifier.size(10.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(2.dp))
                                                        Text(
                                                            text = "NOO Baru (Offline)",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                            val isTodayRoute = out.kodeHari.startsWith(todayIndoDayName, ignoreCase = true)
                                            if (!isTodayRoute) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                                                    contentColor = Color(0xFFD97706),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "Luar Rute (${out.kodeHari})",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            if (hasTransactionToday) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    color = if (isNoOrderOnly) Color(0xFFF59E0B).copy(alpha = 0.15f) else Color(0xFF10B981).copy(alpha = 0.15f),
                                                    contentColor = if (isNoOrderOnly) Color(0xFFD97706) else Color(0xFF10B981),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.CheckCircle,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(10.dp),
                                                            tint = if (isNoOrderOnly) Color(0xFFD97706) else Color(0xFF10B981)
                                                        )
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text(
                                                            text = if (isNoOrderOnly) "Telah Dikunjungi (No Order)" else "Telah Dikunjungi (Transaksi)",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = out.address,
                                            color = Color.Gray,
                                            fontSize = 11.sp,
                                            maxLines = 1
                                        )
                                    }
                                    if (hasTransactionToday) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Sudah dikunjungi",
                                            tint = if (isNoOrderOnly) Color(0xFFF59E0B) else Color(0xFF4CAF50)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // NOO ENROLLMENT FORM
                val nooNameVal by viewModel.nooName.collectAsStateWithLifecycle()
                val nooPatokanVal by viewModel.nooPatokan.collectAsStateWithLifecycle()
                val nooKodeHariVal by viewModel.nooKodeHari.collectAsStateWithLifecycle()
                val nooGeotagVal by viewModel.nooGeotag.collectAsStateWithLifecycle()
                val isSearchingGeotagVal by viewModel.isSearchingGeotag.collectAsStateWithLifecycle()
                
                var expandedKodeHari by remember { mutableStateOf(false) }
                val listHari = listOf("SENIN1", "SENIN2", "SELASA1", "SELASA2", "RABU1", "RABU2", "KAMIS1", "KAMIS2", "JUMAT1", "JUMAT2", "SABTU1", "SABTU2", "MINGGU1", "MINGGU2")

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Daftarkan Outlet NOO Baru",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        OutlinedTextField(
                            value = nooNameVal,
                            onValueChange = { viewModel.nooName.value = it },
                            label = { Text("Nama Outlet Baru") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = nooPatokanVal,
                            onValueChange = { viewModel.nooPatokan.value = it },
                            label = { Text("Patokan Jalan (Opsional)") },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Cth: Sebelah Masjid Raya") }
                        )
                        
                        ExposedDropdownMenuBox(
                            expanded = expandedKodeHari,
                            onExpandedChange = { expandedKodeHari = it }
                        ) {
                            OutlinedTextField(
                                value = nooKodeHariVal,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Kode Hari") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedKodeHari) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedKodeHari,
                                onDismissRequest = { expandedKodeHari = false }
                            ) {
                                listHari.forEach { hari ->
                                    DropdownMenuItem(
                                        text = { Text(hari) },
                                        onClick = {
                                            viewModel.nooKodeHari.value = hari
                                            expandedKodeHari = false
                                        }
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.ACCESS_FINE_LOCATION
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                
                                if (hasFine || hasCoarse) {
                                    viewModel.mockGetGeotag()
                                } else {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9), contentColor = Color.DarkGray),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isSearchingGeotagVal) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Mencari Lokasi...", fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Red)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (nooGeotagVal.isEmpty()) "Ambil Geotag Location" else "Lokasi Berhasil Diambil!", fontSize = 12.sp)
                            }
                        }

                        if (nooGeotagVal.isNotEmpty()) {
                            Text(
                                text = "Koordinat: $nooGeotagVal",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Button(
                            onClick = { viewModel.confirmNOOSelection() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Konfirmasi NOO")
                        }
                    }
                }
            }
        } else {
            // OUTLET SELECTED - SHOW PRODUCTS & CART
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedOutlet!!.name,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = selectedOutlet!!.address,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }

                TextButton(
                    onClick = { viewModel.deselectOutlet() },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Batal", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ganti", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // =====================================================================
            // FITUR BARU: Riwayat Kunjungan Terakhir
            // Menampilkan brand/produk apa saja beserta qty yang dibeli outlet ini
            // pada kunjungan terakhir, SEBELUM sales memilih produk. Data diambil
            // murni dari database lokal (transactionsList) sehingga tetap berfungsi
            // 100% offline dan tidak butuh koneksi internet maupun perubahan skema
            // database (dicocokkan berdasarkan nama outlet, case-insensitive).
            // =====================================================================
            val lastVisitSummary = remember(transactionsList, selectedOutlet) {
                val outletNameSel = selectedOutlet?.name ?: return@remember null
                val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

                val lastRealTrx = transactionsList
                    .filter { it.outletName.equals(outletNameSel, ignoreCase = true) && it.paymentMethod != "No Order" }
                    .maxByOrNull { trx ->
                        try { dateFormat.parse(trx.date)?.time ?: 0L } catch (e: Exception) { 0L }
                    }

                if (lastRealTrx == null) {
                    null
                } else {
                    val items = mutableListOf<Pair<String, Int>>()
                    try {
                        val arr = org.json.JSONArray(lastRealTrx.itemsJson)
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            if (obj.has("name") && obj.has("qty")) {
                                items.add(obj.getString("name") to obj.getInt("qty"))
                            }
                        }
                    } catch (e: Exception) { /* biarkan kosong kalau gagal parse */ }
                    Triple(lastRealTrx.date, items, lastRealTrx.total)
                }
            }

            if (lastVisitSummary != null) {
                val (lastDate, lastItems, lastTotal) = lastVisitSummary
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                tint = Color(0xFFC2410C),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Riwayat Kunjungan Terakhir ($lastDate)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFFC2410C)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (lastItems.isEmpty()) {
                            Text(
                                text = "Detail produk tidak tersedia.",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        } else {
                            lastItems.forEach { (name, qty) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = name, fontSize = 12.sp, color = Color(0xFF7C2D12), modifier = Modifier.weight(1f))
                                    Text(text = "x$qty", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7C2D12))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Divider(color = Color(0xFFFDBA74))
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Total Kunjungan Lalu", fontSize = 11.sp, color = Color.Gray)
                            Text(
                                text = "Rp ${String.format(Locale.getDefault(), "%,.0f", lastTotal)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC2410C)
                            )
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Belum ada riwayat pembelian sebelumnya untuk outlet ini (kunjungan pertama, atau riwayat lama sudah tidak ada di perangkat ini).",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            Text(
                text = "Pilih Produk",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )

            // Dynamic Products catalog items listing
                val activeSales = currentUser?.id ?: ""
                val allocMap = remember(stockAllocationsList, activeSales) {
                    stockAllocationsList
                        .filter { it.salesId.equals(activeSales, ignoreCase = true) || it.salesName.equals(currentUser?.username, ignoreCase = true) }
                        .groupBy { it.productId }
                        .mapValues { (_, list) -> list.sumOf { it.qty } }
                }
                val allocatedProducts = remember(productsList, allocMap) {
                    productsList.filter { p -> (allocMap[p.id] ?: 0) > 0 }
                }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 8.dp)
            ) {


                if (allocatedProducts.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Tidak ada stock di mobil Anda.\nHarap hubungi Admin untuk inject stock.",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(allocatedProducts) { p ->
                        val qtyInCart = cart[p.id] ?: 0
                        val allocated = allocMap[p.id] ?: 0

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(0.5.dp, Color.LightGray.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(p.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Row(
                                        modifier = Modifier.padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFFEFF6FF), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "R: Rp ${String.format(Locale.getDefault(), "%,.0f", p.priceRetail)}",
                                                color = Color(0xFF1D4ED8),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFFECFDF5), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "W: Rp ${String.format(Locale.getDefault(), "%,.0f", p.priceWholesale)}",
                                                color = Color(0xFF047857),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Sisa Stock Anda: $allocated",
                                        color = Color.Gray,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                                        .padding(4.dp)
                                ) {
                                    IconButton(
                                        onClick = { viewModel.updateCart(p.id, -1) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Kurang", modifier = Modifier.size(14.dp))
                                    }
                                    Text(
                                        text = qtyInCart.toString(),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                    IconButton(
                                        onClick = { viewModel.updateCart(p.id, 1) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Tambah", modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Floating Cart Bar
            val totalInCart = cart.values.sum()
            val finalPriceTier by viewModel.checkoutPriceTier.collectAsStateWithLifecycle()

            val estimatedTotal = remember(cart, productsList, finalPriceTier) {
                val pMap = productsList.associateBy { it.id }
                cart.entries.sumOf { (pId, qty) ->
                    val prod = pMap[pId] ?: return@sumOf 0.0
                    val price = if (finalPriceTier == "Wholesale") prod.priceWholesale else prod.priceRetail
                    price * qty
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .background(Color(0xFF0F172A), RoundedCornerShape(24.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Keranjang: $totalInCart Item",
                            color = Color.LightGray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Est. Rp ${String.format(Locale.getDefault(), "%,.0f", estimatedTotal)}",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (totalInCart == 0) {
                            Button(
                                onClick = onNoOrderClick,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF97316)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("No Order", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = onCheckoutClick,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Checkout", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NoOrderDialog(
    isNoo: Boolean = false,
    checkoutInProgress: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val reasons = if (isNoo) {
        listOf("Registrasi NOO", "Toko Tutup", "Bos Tidak Ada", "Stok Masih Banyak", "Uang Tidak Cukup", "Lainnya")
    } else {
        listOf("Toko Tutup", "Bos Tidak Ada", "Stok Masih Banyak", "Uang Tidak Cukup", "Registrasi NOO", "Lainnya")
    }
    
    var selectedReason by remember { mutableStateOf(if (isNoo) "Registrasi NOO" else "Toko Tutup") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Konfirmasi No Order") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pilih alasan kunjungan tanpa order:")
                reasons.forEach { reason ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReason = reason }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = (selectedReason == reason),
                            onClick = { selectedReason = reason }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(reason)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Keterangan Tambahan / Registrasi") },
                    placeholder = { Text("Masukkan keterangan pendaftaran/alasan...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    val finalReason = if (notes.isNotBlank()) {
                        "$selectedReason - Keterangan: ${notes.trim()}"
                    } else {
                        selectedReason
                    }
                    onConfirm(finalReason) 
                },
                enabled = !checkoutInProgress,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF97316))
            ) {
                if (checkoutInProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text("Kirim No Order")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !checkoutInProgress) {
                Text("Batal")
            }
        }
    )
}

// --- CHECKOUT OVERLAY DIALOG ---
@Composable
fun CheckoutDialog(
    viewModel: SfaViewModel,
    onDismiss: () -> Unit,
    onConfirmCheckout: () -> Unit
) {
    val tier by viewModel.checkoutPriceTier.collectAsStateWithLifecycle()
    val method by viewModel.checkoutPaymentMethod.collectAsStateWithLifecycle()
    val topDays by viewModel.checkoutTOPDays.collectAsStateWithLifecycle()
    val cart by viewModel.cart.collectAsStateWithLifecycle()
    val productsList by viewModel.productsList.collectAsStateWithLifecycle()
    // PERBAIKAN: cegah tombol "Simpan" ditekan berkali-kali dengan cepat (double-tap)
    // sebelum dialog sempat tertutup, yang sebelumnya bisa memicu transaksi & pengurangan
    // stock ganda. checkoutInProgress juga dijaga di sisi ViewModel (checkout()), ini
    // adalah lapisan tambahan di UI supaya tombol langsung terlihat nonaktif.
    val checkoutInProgress by viewModel.isCheckoutInProgress.collectAsStateWithLifecycle()

    val total = remember(cart, productsList, tier) {
        val pMap = productsList.associateBy { it.id }
        cart.entries.sumOf { (pId, qty) ->
            val p = pMap[pId] ?: return@sumOf 0.0
            val price = if (tier == "Wholesale") p.priceWholesale else p.priceRetail
            price * qty
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().imePadding().padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Checkout Transaksi",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                // Select price tier
                Column {
                    Text("Pilih Tipe Harga", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .background(Color(0xFFEFF6FF), RoundedCornerShape(8.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("Retail", "Wholesale").forEach { t ->
                            val isSelected = tier == t
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable { viewModel.checkoutPriceTier.value = t }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(t, color = if (isSelected) Color.White else Color.DarkGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Select payment method
                Column {
                    Text("Metode Pembayaran", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("Cash", "Cashless", "Kredit").forEach { m ->
                            val isSelected = method == m
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable { viewModel.checkoutPaymentMethod.value = m }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(m, color = if (isSelected) Color.White else Color.DarkGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (method == "Kredit") {
                    Column {
                        Text("Jatuh Tempo (T.O.P Days)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        OutlinedTextField(
                            value = topDays,
                            onValueChange = { viewModel.checkoutTOPDays.value = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            singleLine = true
                        )
                    }
                }

                Divider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Total Tagihan:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Rp ${String.format(Locale.getDefault(), "%,.0f", total)}",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), enabled = !checkoutInProgress) {
                        Text("Batal")
                    }
                    Button(onClick = onConfirmCheckout, modifier = Modifier.weight(1f), enabled = !checkoutInProgress) {
                        if (checkoutInProgress) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text("Simpan")
                        }
                    }
                }
            }
        }
    }
}

// --- RECEIPT PREVIEW CONSOLE DIALOG ---
@Composable
fun ReceiptPreviewDialog(
    viewModel: SfaViewModel,
    onDismiss: () -> Unit
) {
    val text by viewModel.lastReceiptText.collectAsStateWithLifecycle()
    val intentUri by viewModel.lastReceiptIntentUri.collectAsStateWithLifecycle()
    val settings by viewModel.receiptSettings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().imePadding().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Preview Struk", fontWeight = FontWeight.Bold, fontSize = 16.dp.value.sp)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                // Typewriter/Console style scrollable container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp)
                        .background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        settings?.let { s ->
                            if (s.logoBase64.isNotEmpty()) {
                                val decodedBmp = try {
                                    val imageBytes = android.util.Base64.decode(s.logoBase64, android.util.Base64.DEFAULT)
                                    android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                } catch(e: Exception) { null }
                                
                                if (decodedBmp != null) {
                                    val alignment = when(s.logoAlign) {
                                        "Left" -> Alignment.Start
                                        "Right" -> Alignment.End
                                        else -> Alignment.CenterHorizontally
                                    }
                                    
                                    androidx.compose.foundation.Image(
                                        bitmap = decodedBmp.asImageBitmap(),
                                        contentDescription = "Logo",
                                        modifier = Modifier.align(alignment).padding(bottom = 8.dp).sizeIn(maxWidth = 150.dp, maxHeight = 100.dp),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                    )
                                }
                            }
                        }
                        
                        Text(
                            text = text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFF334155),
                            lineHeight = 16.sp
                        )
                    }
                }

                Button(
                    onClick = {
                        try {
                            if (intentUri.isNotEmpty()) {
                                val intent = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME)
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } else {
                                Toast.makeText(context, "Data struk tidak tersedia", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Aplikasi RawBT tidak ditemukan", Toast.LENGTH_LONG).show()
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=ru.a402d.rawbtprinter")))
                            } catch (e2: Exception) {}
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Print via RawBT", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun StockTab(
    viewModel: SfaViewModel,
    onPrintClick: () -> Unit = {}
) {
    val productsList by viewModel.productsList.collectAsStateWithLifecycle()
    val stockAllocationsList by viewModel.stockAllocationsList.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    var showPreview by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Stock Mobil Anda",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(
                    onClick = {
                        viewModel.preparePrintStockReport()
                        onPrintClick()
                    }
                ) {
                    Icon(Icons.Default.Print, contentDescription = "Cetak Stock", tint = Color.Gray)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

            val activeSales = currentUser?.id ?: ""
            val salesAllocs = remember(stockAllocationsList, activeSales) {
                stockAllocationsList.filter { it.salesId.equals(activeSales, ignoreCase = true) || it.salesName.equals(currentUser?.username, ignoreCase = true) }
            }
            val allocMap = remember(salesAllocs) {
                salesAllocs.groupBy { it.productId }
            }
            val allocatedProducts = remember(productsList, allocMap) {
                productsList.filter { p -> (allocMap[p.id]?.sumOf { it.qty } ?: 0) > 0 }
            }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {

            if (allocatedProducts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Tidak ada stock mobil ter-inject.",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(allocatedProducts) { p ->
                    val allocations = allocMap[p.id] ?: emptyList()
                    val awal = allocations.filter { it.qty > 0 }.sumOf { it.qty }
                    val jual = allocations.filter { it.qty < 0 }.sumOf { kotlin.math.abs(it.qty) }
                    val sisa = awal - jual

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Row(
                                    modifier = Modifier.padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text("Awal: $awal", fontSize = 11.sp, color = Color.Gray)
                                    Text("Jual: $jual", fontSize = 11.sp, color = Color.Red)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(0xFFEFF6FF), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("SISA", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8))
                                    Text("$sisa", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF1D4ED8))
                                }
                            }
                        }
                    }
                }
            }

            if (currentUser?.isStokisSales == true) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    SalesStokisSection(viewModel = viewModel)
                }
            }
        }
    }

    if (showPreview) {
        ReceiptPreviewDialog(viewModel = viewModel, onDismiss = { showPreview = false })
    }
}

// --- HISTORY TAB COMPONENT ---
@Composable
fun HistoryTab(
    viewModel: SfaViewModel,
    onPrintClick: (TransactionEntity) -> Unit
) {
    val todayTransactions = remember(viewModel.transactionsList.value, viewModel.currentUser.value) {
        viewModel.getSalesTodayTransactions()
    }
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    var showPreviewRecap by remember { mutableStateOf(false) }
    
    var editingTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var deletingTransactionId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Riwayat Hari Ini",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            IconButton(
                onClick = {
                    viewModel.preparePrintHistoryRecap()
                    showPreviewRecap = true
                }
            ) {
                Icon(Icons.Default.Print, contentDescription = "Cetak Rekap", tint = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (todayTransactions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Belum ada transaksi terekam hari ini.",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(todayTransactions) { trx ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(trx.outletName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(top = 2.dp)
                                    ) {
                                        Text(trx.date, color = Color.Gray, fontSize = 11.sp)
                                        Text("•", color = Color.LightGray, fontSize = 11.sp)
                                        if (trx.statusSynced) {
                                            Icon(
                                                imageVector = Icons.Default.CloudDone,
                                                contentDescription = "Synced",
                                                tint = Color(0xFF047857),
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = "Terupload ke Spreadsheet",
                                                color = Color(0xFF047857),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.CloudQueue,
                                                contentDescription = "Unsynced",
                                                tint = Color(0xFFC2410C),
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = "Belum Terupload",
                                                color = Color(0xFFC2410C),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = if (trx.paymentMethod == "Kredit") Color(0xFFFFF7ED) else Color(0xFFECFDF5),
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = trx.paymentMethod,
                                            color = if (trx.paymentMethod == "Kredit") Color(0xFFC2410C) else Color(0xFF047857),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    IconButton(
                                        onClick = { onPrintClick(trx) },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(Color(0xFFEFF6FF), CircleShape)
                                    ) {
                                        Icon(Icons.Default.Print, contentDescription = "Cetak Struk", tint = Color(0xFF1D4ED8), modifier = Modifier.size(14.dp))
                                    }
                                    
                                    if (currentUser?.role == "Admin") {
                                        IconButton(onClick = { editingTransaction = trx }, modifier = Modifier.size(32.dp).background(Color(0xFFEFF6FF), CircleShape)) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Blue, modifier = Modifier.size(14.dp))
                                        }
                                        IconButton(onClick = { deletingTransactionId = trx.orderId }, modifier = Modifier.size(32.dp).background(Color(0xFFFEF2F2), CircleShape)) {
                                            Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color.Red, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }

                            Divider(modifier = Modifier.padding(vertical = 10.dp))

                            if (trx.paymentMethod == "No Order") {
                                val reason = try {
                                    val arr = org.json.JSONArray(trx.itemsJson)
                                    if (arr.length() > 0 && arr.getJSONObject(0).has("reason")) {
                                        arr.getJSONObject(0).getString("reason")
                                    } else ""
                                } catch (e: Exception) { "" }
                                
                                Text("Alasan: $reason", fontSize = 12.sp, color = Color.Red, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Total Tagihan", fontSize = 11.sp, color = Color.Gray)
                                Text(
                                    "Rp ${String.format(Locale.getDefault(), "%,.0f", trx.total)}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPreviewRecap) {
        ReceiptPreviewDialog(viewModel = viewModel, onDismiss = { showPreviewRecap = false })
    }
    
    // Edit Transaction Dialog
    if (editingTransaction != null) {
        val trx = editingTransaction!!
        var outletNameVal by remember { mutableStateOf(trx.outletName) }
        var paymentMethodVal by remember { mutableStateOf(trx.paymentMethod) }
        var totalVal by remember { mutableStateOf(trx.total.toString()) }
        var topDaysVal by remember { mutableStateOf(trx.topDays.toString()) }
        var dueDateVal by remember { mutableStateOf(trx.dueDate) }

        androidx.compose.ui.window.Dialog(onDismissRequest = { editingTransaction = null }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().imePadding().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Revisi Transaksi - ${trx.orderId}", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                    OutlinedTextField(
                        value = outletNameVal,
                        onValueChange = { outletNameVal = it },
                        label = { Text("Nama Outlet") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Column {
                        Text("Metode Pembayaran", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Cash", "Cashless", "Kredit").forEach { m ->
                                val sel = paymentMethodVal == m
                                AssistChip(
                                    onClick = { paymentMethodVal = m },
                                    label = { Text(m) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = if (sel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                    )
                                )
                            }
                        }
                    }

                    if (paymentMethodVal == "Kredit") {
                        OutlinedTextField(
                            value = topDaysVal,
                            onValueChange = { topDaysVal = it },
                            label = { Text("T.O.P Days") },
                            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = dueDateVal,
                            onValueChange = { dueDateVal = it },
                            label = { Text("Jatuh Tempo") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = totalVal,
                        onValueChange = { totalVal = it },
                        label = { Text("Total Tagihan (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = { editingTransaction = null }, modifier = Modifier.weight(1f)) {
                            Text("Batal")
                        }
                        Button(
                            onClick = {
                                val updated = trx.copy(
                                    outletName = outletNameVal,
                                    paymentMethod = paymentMethodVal,
                                    total = totalVal.toDoubleOrNull() ?: trx.total,
                                    topDays = topDaysVal.toIntOrNull() ?: trx.topDays,
                                    dueDate = dueDateVal
                                )
                                viewModel.updateTransaction(updated)
                                editingTransaction = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Simpan")
                        }
                    }
                }
            }
        }
    }

    // Delete Transaction Dialog
    if (deletingTransactionId != null) {
        val oid = deletingTransactionId!!
        AlertDialog(
            onDismissRequest = { deletingTransactionId = null },
            title = { Text("Hapus Transaksi", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = { Text("Apakah Anda yakin ingin menghapus transaksi $oid dari database? Tindakan ini tidak dapat dibatalkan.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTransaction(oid)
                        deletingTransactionId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Hapus", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTransactionId = null }) {
                    Text("Batal")
                }
            }
        )
    }
}

// --- SYNC TAB COMPONENT ---
@Composable
fun SyncTab(viewModel: SfaViewModel) {
    var isDownloading by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val pendingUploads by viewModel.pendingUploadsList.collectAsStateWithLifecycle()
    val auditLogs by viewModel.syncAuditLogs.collectAsStateWithLifecycle()
    
    val totalPendingCount = pendingUploads.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SyncAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sinkronisasi Data",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                HorizontalDivider()

                // Download Section
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Tarik data Master secara Manual (Mode Hybrid).",
                            color = Color(0xFF1E3A8A),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = {
                                isDownloading = true
                                scope.launch {
                                    val result = viewModel.downloadMasterSync()
                                    isDownloading = false
                                    Toast.makeText(context, result.second, Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isDownloading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                            } else {
                                Icon(Icons.Default.CloudDownload, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Download Data Master")
                            }
                        }
                    }
                }
                
                // Upload Section
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Kirim ",
                                color = Color(0xFF14532D),
                                fontSize = 13.sp
                            )
                            Text(
                                text = "$totalPendingCount",
                                color = Color.Red,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = " antrean data (transaksi/NOO) offline.",
                                color = Color(0xFF14532D),
                                fontSize = 13.sp
                            )
                        }
                        
                        Button(
                            onClick = {
                                isUploading = true
                                scope.launch {
                                    val result = viewModel.endDaySync()
                                    isUploading = false
                                    Toast.makeText(context, result.second, Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isUploading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                            } else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Upload Antrean Offline")
                            }
                        }
                    }
                }

                // Upload Queue Visual List
                if (pendingUploads.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Antrean Pending (${pendingUploads.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF374151)
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pendingUploads.forEach { upload ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (upload.type == "Transaction") Icons.Default.ShoppingCart else Icons.Default.Store,
                                        contentDescription = null,
                                        tint = if (upload.type == "Transaction") Color(0xFF10B981) else Color(0xFF3B82F6),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = upload.name,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF1F2937)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "ID: ${upload.id}",
                                            fontSize = 10.sp,
                                            color = Color(0xFF6B7280)
                                        )
                                        if (upload.lastAttempt != "-") {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Terakhir dicoba: ${upload.lastAttempt} (Gagal, Retries: ${upload.retryCount})",
                                                fontSize = 10.sp,
                                                color = Color(0xFFEF4444)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    // Status Badge
                                    val isFailed = upload.status == "Failed"
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isFailed) Color(0xFFFEE2E2) else Color(0xFFEFF6FF)
                                        ),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.wrapContentSize()
                                    ) {
                                        Text(
                                            text = if (isFailed) "Gagal / Offline" else "Menunggu",
                                            color = if (isFailed) Color(0xFF991B1B) else Color(0xFF1E40AF),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFDCFCE7)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Semua antrean data tuntas ter-upload!",
                                color = Color(0xFF15803D),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // PERBAIKAN: Kartu "Log Audit Sinkronisasi" (termasuk tombol "Hapus Log")
        // sekarang HANYA ditampilkan untuk role Admin. Sebelumnya kartu ini selalu
        // tampil untuk semua role termasuk Sales, sehingga sales bisa melihat DAN
        // menghapus log audit sinkronisasi sendiri (tanpa gating apapun) - berisiko
        // log penting terhapus tanpa sepengetahuan Admin.
        if (currentUser?.role == "Admin") {
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
        }
    }
}
// --- REPORTS TAB COMPONENT ---
@Composable
fun ReportsTab(
    viewModel: SfaViewModel,
    onPrintFullClick: () -> Unit
) {
    val transactions by viewModel.transactionsList.collectAsStateWithLifecycle()
    val productsList by viewModel.productsList.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    val todayTransactions = remember(transactions, currentUser) {
        viewModel.getSalesTodayTransactions()
    }

    var totalSales = 0.0
    val totalOutlet = todayTransactions.size
    var totalCreditOutlet = 0
    var totalPiutang = 0.0

    todayTransactions.forEach {
        totalSales += it.total
        if (it.paymentMethod == "Kredit" && it.total > 0) {
            totalCreditOutlet++
            totalPiutang += it.total
        }
    }

    // Top Selling SKU calculations
    val skuMap = remember(todayTransactions) {
        val map = mutableMapOf<String, Int>()
        val moshiObj = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val listType = Types.newParameterizedType(List::class.java, mapType)
        val jsonAdapter = moshiObj.adapter<List<Map<String, Any>>>(listType)

        todayTransactions.forEach { t ->
            val items = try {
                jsonAdapter.fromJson(t.itemsJson) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            items.forEach { it ->
                val name = it["name"] as? String ?: ""
                val qty = (it["qty"] as? Double)?.toInt() ?: 0
                map[name] = (map[name] ?: 0) + qty
            }
        }
        map.toList().sortedByDescending { it.second }.take(10)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Laporan Kunjungan", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                IconButton(onClick = onPrintFullClick) {
                    Icon(Icons.Default.Print, contentDescription = "Cetak Laporan", tint = Color.Gray)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("TOTAL RUPIAH", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8))
                        Text("Rp ${String.format(Locale.getDefault(), "%,.0f", totalSales)}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF1E3A8A))
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("TOTAL OUTLET", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                        Text("$totalOutlet Visited", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF065F46))
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().imePadding().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("TOTAL PIUTANG ($totalCreditOutlet Outlet)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC2410C))
                        Text("Rp ${String.format(Locale.getDefault(), "%,.0f", totalPiutang)}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color(0xFF9A3412))
                    }
                    Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = Color(0xFFC2410C))
                }
            }
        }

        // Top 10 SKU
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFFEAB308), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Top 10 SKU Terjual", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    if (skuMap.isEmpty()) {
                        Text("Belum ada produk terjual.", color = Color.Gray, fontSize = 11.sp)
                    } else {
                        skuMap.forEach { (name, qty) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(name, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFEFF6FF), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("$qty PCS", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF1D4ED8))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Credit outlets listing
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(Icons.Default.AssignmentLate, contentDescription = null, tint = Color.Red, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Daftar Outlet Kredit", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    val creditOutletsTrx = todayTransactions.filter { it.paymentMethod == "Kredit" && it.total > 0 }
                    if (creditOutletsTrx.isEmpty()) {
                        Text("Tidak ada tagihan/piutang.", color = Color.Gray, fontSize = 11.sp)
                    } else {
                        creditOutletsTrx.forEach { t ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(t.outletName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("Jatuh Tempo: ${t.dueDate}", color = Color.Gray, fontSize = 9.sp)
                                }
                                Text(
                                    "Rp ${String.format(Locale.getDefault(), "%,.0f", t.total)}",
                                    color = Color.Red,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- ADMIN OUTLETS MAIN TABLE TAB ---
@Composable
fun OutletMainTab(
    viewModel: SfaViewModel,
    onAddOutletClick: () -> Unit,
    onEditOutletClick: (Int) -> Unit,
    onImportCsvClick: () -> Unit
) {
    val outlets by viewModel.outletsList.collectAsStateWithLifecycle()
    val allStokis by viewModel.stokisList.collectAsStateWithLifecycle()
    val page by viewModel.adminOutletPage.collectAsStateWithLifecycle()
    val limit = 10

    val totalPages = remember(outlets) {
        val size = outlets.size
        if (size == 0) 1 else Math.ceil(size.toDouble() / limit).toInt()
    }

    val pagedOutlets = remember(outlets, page) {
        val start = (page - 1) * limit
        outlets.drop(start).take(limit)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Manajemen Outlet", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = onImportCsvClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("CSV", fontSize = 10.sp)
                }
                Button(
                    onClick = onAddOutletClick,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tambah", fontSize = 10.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Table List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (pagedOutlets.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Belum ada outlet terdaftar.", color = Color.Gray, fontSize = 13.sp)
                    }
                }
            } else {
                items(pagedOutlets.size) { i ->
                    val out = pagedOutlets[i]
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("[${out.id}] ${out.name}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Alamat: ${out.address}", color = Color.Gray, fontSize = 10.sp)
                                Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(modifier = Modifier.background(Color(0xFFF1F5F9), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                        Text(out.type, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Box(modifier = Modifier.background(Color(0xFFEFF6FF), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                        Text(out.category, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8))
                                    }
                                    if (out.salesName.isNotEmpty()) {
                                        Text("Sales: ${out.salesName}", fontSize = 9.sp, color = Color.DarkGray, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = { onEditOutletClick(outlets.indexOf(out)) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Blue, modifier = Modifier.size(14.dp))
                                }
                                IconButton(onClick = { viewModel.deleteOutlet(out.id) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color.Red, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Pagination row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Hal $page / $totalPages", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = { if (page > 1) viewModel.adminOutletPage.value = page - 1 },
                    enabled = page > 1,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Prev", fontSize = 10.sp)
                }
                Button(
                    onClick = { if (page < totalPages) viewModel.adminOutletPage.value = page + 1 },
                    enabled = page < totalPages,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Next", fontSize = 10.sp)
                }
            }
        }
    }
}

// --- ADMIN PANEL CONTAINER & COMPONENT ---
@Composable
fun AdminPanelTab(
    viewModel: SfaViewModel,
    onAddDataClick: (String) -> Unit,
    onEditDataClick: (String, Int) -> Unit,
    onImportCsvClick: (String) -> Unit,
    onPreviewReceiptClick: () -> Unit
) {
    val adminCurrentTab by viewModel.adminCurrentTab.collectAsStateWithLifecycle()
    val users by viewModel.usersList.collectAsStateWithLifecycle()
    val products by viewModel.productsList.collectAsStateWithLifecycle()
    val logs by viewModel.warehouseLogsList.collectAsStateWithLifecycle()
    val outlets by viewModel.outletsList.collectAsStateWithLifecycle()
    val allStokis by viewModel.stokisList.collectAsStateWithLifecycle()

    val stockAllocations by viewModel.stockAllocationsList.collectAsStateWithLifecycle()
    val logFilterStart by viewModel.logFilterStartDate.collectAsStateWithLifecycle()
    val logFilterEnd by viewModel.logFilterEndDate.collectAsStateWithLifecycle()

    var showDatePickerFor by remember { mutableStateOf<String?>(null) } // null, "Start", "End"

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val rightPanelWidth = maxOf(600.dp, maxWidth - 180.dp)

        Row(
            modifier = Modifier
                .fillMaxHeight()
                .horizontalScroll(rememberScrollState())
        ) {
            // Web Console Sidebar (Left Panel)
            Column(
                modifier = Modifier
                    .width(180.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF1E293B)) // Web dark slate sidebar
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp, horizontal = 10.dp)
            ) {
                // Console Header
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = "Admin",
                        tint = Color(0xFF38BDF8), // Radiant light blue accent
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "SFA CONSOLE",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Sidebar Menu Tabs
                val tabsList = listOf(
                    "Users" to Icons.Default.Person,
                    "Products" to Icons.Default.Inventory2,
                    "History Transaksi" to Icons.Default.Receipt,
                    "Raw Data" to Icons.Default.List,
                    "Stock Gudang" to Icons.Default.History,
                    "Stock Stokis" to Icons.Default.Layers,
                    "Inject Outlets" to Icons.Default.Storefront,
                    "Inject Stock" to Icons.Default.Bolt,
                    "Log Gudang" to Icons.Default.AssignmentLate,
                    "Settings" to Icons.Default.Settings,
                    "System Health" to Icons.Default.HealthAndSafety
                )

                tabsList.forEach { (tab, icon) ->
                    val isSelected = adminCurrentTab == tab
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(
                                color = if (isSelected) Color(0xFF334155) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { viewModel.setAdminTab(tab) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = tab,
                            tint = if (isSelected) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = tab,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp,
                            color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                        )
                    }
                }
            }

            // Web Console Content (Right Panel)
            Column(
                modifier = Modifier
                    .width(rightPanelWidth)
                    .fillMaxHeight()
                    .background(Color(0xFFF1F5F9)) // soft grey backdrop
                    .padding(16.dp)
            ) {
            // Web Dashboard Stat widgets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Widget 1: Users
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.5.dp, Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("TOTAL USER", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text("${users.size}", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFF0F172A))
                    }
                }
                // Widget 2: Products
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.5.dp, Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("KATALOG PRODUK", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text("${products.size}", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFF0D9488))
                    }
                }
                // Widget 3: Outlets
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.5.dp, Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("TOTAL OUTLET", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text("${outlets.size}", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFFD97706))
                    }
                }
            }

            // Chosen Workspace View Section inside a responsive paper sheet
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.5.dp, Color(0xFFE2E8F0))
            ) {
                Box(
                    modifier = Modifier
                        .padding(14.dp)
                        .fillMaxSize()
                ) {
                    when (adminCurrentTab) {

                        "System Health" -> {
                            AdminSystemHealthSection(viewModel)
                        }
                        "Raw Data" -> {
                            AdminRawDataSection(
                                viewModel = viewModel
                            )
                        }
                        "Users" -> {
                            AdminUsersSection(
                                users = users,
                                onAddUser = { onAddDataClick("Users") },
                                onEditUser = { onEditDataClick("Users", it) },
                                onDeleteUser = { viewModel.deleteUser(it) },
                                onDeleteAllUsers = { viewModel.deleteAllUsers() },
                                onImportCsv = { onImportCsvClick("Users") }
                            )
                        }
                        "Products" -> {
                            AdminProductsSection(
                                products = products,
                                onAddProduct = { onAddDataClick("Products") },
                                onEditProduct = { onEditDataClick("Products", it) },
                                onDeleteProduct = { viewModel.deleteProduct(it) },
                                onDeleteAllProducts = { viewModel.deleteAllProducts() },
                                onImportCsv = { onImportCsvClick("Products") }
                            )
                        }
                        "History Transaksi" -> {
                            AdminHistorySection(
                                viewModel = viewModel,
                                onSelectStartDate = { showDatePickerFor = "HistoryStart" },
                                onSelectEndDate = { showDatePickerFor = "HistoryEnd" },
                                onPrintOverall = {
                                    viewModel.preparePrintFullReport()
                                    onPreviewReceiptClick()
                                },
                                onPrintDetail = { trx ->
                                    viewModel.preparePrintFromHistory(trx)
                                    onPreviewReceiptClick()
                                }
                            )
                        }
                        "Stock Gudang" -> {
                            AdminWarehouseStockSection(
                                products = products,
                                onAddStock = { pid, q -> viewModel.addWarehouseStock(pid, q) },
                                onAddProduct = { onAddDataClick("Products") }
                            )
                        }
                        "Stock Stokis" -> {
                            AdminStokisSection(
                                viewModel = viewModel,
                                onAddStokis = { onAddDataClick("Stokis") },
                                onEditStokis = { onEditDataClick("Stokis", allStokis.indexOf(it)) }
                            )
                        }
                        "Inject Outlets" -> {
                            AdminInjectOutletsSection(
                                outlets = outlets,
                                users = users,
                                onUpdateRoute = { sid, sname, kh, selectedIds -> viewModel.updateRouteForSales(sid, sname, kh, selectedIds) }
                            )
                        }
                        "Inject Stock" -> {
                            AdminInjectStockSection(
                                stockAllocations = stockAllocations,
                                products = products,
                                users = users,
                                onInjectStock = { sid, sname, pid, q -> viewModel.injectStockToSales(sid, sname, pid, q) }
                            )
                        }
                        "Log Gudang" -> {
                            AdminWarehouseLogsSection(
                                logs = logs,
                                logFilterStart = logFilterStart,
                                logFilterEnd = logFilterEnd,
                                onSelectStartDate = { showDatePickerFor = "Start" },
                                onSelectEndDate = { showDatePickerFor = "End" }
                            )
                        }
                        "Settings" -> {
                            AdminReceiptSettingsSection(
                                viewModel = viewModel,
                                onPreviewReceiptClick = onPreviewReceiptClick
                            )
                        }
                    }
                }
            }
        }
    }

    // Date Pickers mockup dialogs
    if (showDatePickerFor != null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showDatePickerFor = null }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Pilih Tanggal (Format: YYYY-MM-DD)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    var dateVal by remember { 
                        mutableStateOf(
                            when (showDatePickerFor) {
                                "Start" -> logFilterStart
                                "End" -> logFilterEnd
                                "HistoryStart" -> viewModel.adminHistoryFilterStartDate.value
                                else -> viewModel.adminHistoryFilterEndDate.value
                            }
                        )
                    }

                    OutlinedTextField(
                        value = dateVal,
                        onValueChange = { dateVal = it },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            when (showDatePickerFor) {
                                "Start" -> viewModel.logFilterStartDate.value = dateVal
                                "End" -> viewModel.logFilterEndDate.value = dateVal
                                "HistoryStart" -> viewModel.adminHistoryFilterStartDate.value = dateVal
                                "HistoryEnd" -> viewModel.adminHistoryFilterEndDate.value = dateVal
                            }
                            showDatePickerFor = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Simpan")
                    }
                }
            }
        }
    }
}
}

// --- ADMIN USERS PANEL ---
@Composable
fun AdminUsersSection(
    users: List<UserEntity>,
    onAddUser: () -> Unit,
    onEditUser: (Int) -> Unit,
    onDeleteUser: (String) -> Unit,
    onDeleteAllUsers: () -> Unit,
    onImportCsv: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Pengguna SFA", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onImportCsv, modifier = Modifier.size(32.dp).background(Color(0xFFF1F5F9), CircleShape)) {
                    Icon(Icons.Default.UploadFile, contentDescription = "Import CSV", modifier = Modifier.size(14.dp))
                }
                IconButton(onClick = onAddUser, modifier = Modifier.size(32.dp).background(Color(0xFFEFF6FF), CircleShape)) {
                    Icon(Icons.Default.Add, contentDescription = "Tambah User", tint = Color(0xFF1D4ED8), modifier = Modifier.size(14.dp))
                }
                IconButton(onClick = onDeleteAllUsers, modifier = Modifier.size(32.dp).background(Color(0xFFFEF2F2), CircleShape)) {
                    Icon(Icons.Default.DeleteForever, contentDescription = "Hapus Semua", tint = Color.Red, modifier = Modifier.size(14.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(users.size) { index ->
                val user = users[index]
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(10.dp)) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(user.username, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (user.role == "Admin" || user.role == "Supervisor" || user.role == "Sales") {
                                    Icon(
                                        if (user.role == "Admin") Icons.Default.AdminPanelSettings else if (user.role == "Supervisor") Icons.Default.Security else Icons.Default.VerifiedUser,
                                        contentDescription = "Otoritas",
                                        tint = if (user.role == "Admin") Color.Red else if (user.role == "Supervisor") Color(0xFFD97706) else Color(0xFF059669),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text("Role: ${user.role} | Password: ${user.password}", color = Color.Gray, fontSize = 10.sp)
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = { onEditUser(index) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Blue, modifier = Modifier.size(14.dp))
                            }
                            IconButton(onClick = { onDeleteUser(user.id) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color.Red, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- ADMIN PRODUCTS PANEL ---
@Composable
fun AdminProductsSection(
    products: List<ProductEntity>,
    onAddProduct: () -> Unit,
    onEditProduct: (Int) -> Unit,
    onDeleteProduct: (String) -> Unit,
    onDeleteAllProducts: () -> Unit,
    onImportCsv: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Katalog Produk", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onImportCsv, modifier = Modifier.size(32.dp).background(Color(0xFFF1F5F9), CircleShape)) {
                    Icon(Icons.Default.UploadFile, contentDescription = "Import CSV", modifier = Modifier.size(14.dp))
                }
                IconButton(onClick = onAddProduct, modifier = Modifier.size(32.dp).background(Color(0xFFEFF6FF), CircleShape)) {
                    Icon(Icons.Default.Add, contentDescription = "Tambah Produk", tint = Color(0xFF1D4ED8), modifier = Modifier.size(14.dp))
                }
                IconButton(onClick = onDeleteAllProducts, modifier = Modifier.size(32.dp).background(Color(0xFFFEF2F2), CircleShape)) {
                    Icon(Icons.Default.DeleteForever, contentDescription = "Hapus Semua", tint = Color.Red, modifier = Modifier.size(14.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(products.size) { index ->
                val p = products[index]
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(10.dp)) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("R: Rp ${String.format(Locale.getDefault(), "%,.0f", p.priceRetail)} | W: Rp ${String.format(Locale.getDefault(), "%,.0f", p.priceWholesale)}", color = Color.Gray, fontSize = 10.sp)
                            Text("Stock Gudang: ${p.warehouseStock}", color = MaterialTheme.colorScheme.primary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = { onEditProduct(index) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Blue, modifier = Modifier.size(14.dp))
                            }
                            IconButton(onClick = { onDeleteProduct(p.id) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color.Red, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- ADMIN STOCK ADDITION PANEL ---
@Composable
fun AdminWarehouseStockSection(
    products: List<ProductEntity>,
    onAddStock: (String, Int) -> Unit,
    onAddProduct: () -> Unit
) {
    var selectedProductId by remember { mutableStateOf("") }
    var qtyToAdd by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Tambah Master Stock ke Gudang", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            IconButton(onClick = onAddProduct, modifier = Modifier.size(32.dp).background(Color(0xFFEFF6FF), CircleShape)) {
                Icon(Icons.Default.Add, contentDescription = "Tambah Produk Baru", tint = Color(0xFF1D4ED8), modifier = Modifier.size(14.dp))
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                var isExpanded by remember { mutableStateOf(false) }
                val selectedProduct = products.find { it.id == selectedProductId }
                
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Pilih Produk", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Text(selectedProduct?.name ?: "Pilih Produk...", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand Products"
                    )
                }

                if (isExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        products.forEach { p ->
                            val isSelected = selectedProductId == p.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { 
                                        selectedProductId = p.id
                                        isExpanded = false
                                    }
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(p.name, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                                Text("Gudang: ${p.warehouseStock}", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = qtyToAdd,
                    onValueChange = { qtyToAdd = it },
                    label = { Text("Qty Penambahan") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        val qty = qtyToAdd.toIntOrNull() ?: 0
                        if (selectedProductId.isNotEmpty() && qty > 0) {
                            onAddStock(selectedProductId, qty)
                            qtyToAdd = ""
                            selectedProductId = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AddCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tambah ke Gudang")
                }
            }
        }
    }
}

// --- ADMIN INJECT OUTLETS PANEL ---

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AdminInjectOutletsSection(
    outlets: List<OutletEntity>,
    users: List<UserEntity>,
    onUpdateRoute: (String, String, String, Set<String>) -> Unit
) {
    var selectedSalesId by remember { mutableStateOf("") }
    var selectedKodeHari by remember { mutableStateOf("") }
    
    var expandedSales by remember { mutableStateOf(false) }
    var expandedKodeHari by remember { mutableStateOf(false) }

    val salesUsers = users.filter { it.role == "Sales" }
    val listHari = listOf("SENIN1", "SENIN2", "SELASA1", "SELASA2", "RABU1", "RABU2", "KAMIS1", "KAMIS2", "JUMAT1", "JUMAT2", "SABTU1", "SABTU2", "MINGGU1", "MINGGU2")
    
    val selectedSalesUser = salesUsers.find { it.id.equals(selectedSalesId, ignoreCase = true) }
    
    val selectedOutletIds = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    
    LaunchedEffect(selectedSalesId, selectedKodeHari, outlets) {
        if (selectedSalesId.isNotEmpty() && selectedKodeHari.isNotEmpty()) {
            val currentAssigned = outlets.filter { it.salesId.equals(selectedSalesId, ignoreCase = true) && it.kodeHari == selectedKodeHari }
            selectedOutletIds.clear()
            currentAssigned.forEach { selectedOutletIds[it.id] = true }
        } else {
            selectedOutletIds.clear()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Inject Outlet ke Rute Sales", fontWeight = FontWeight.Bold, fontSize = 14.sp)

        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("1. Pilih Salesman Target", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                ExposedDropdownMenuBox(
                    expanded = expandedSales,
                    onExpandedChange = { expandedSales = it }
                ) {
                    OutlinedTextField(
                        value = selectedSalesUser?.username ?: "Pilih Sales...",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSales) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedSales,
                        onDismissRequest = { expandedSales = false }
                    ) {
                        salesUsers.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.username) },
                                onClick = {
                                    selectedSalesId = s.id
                                    expandedSales = false
                                }
                            )
                        }
                    }
                }
                
                Text("2. Pilih Kode Hari", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                ExposedDropdownMenuBox(
                    expanded = expandedKodeHari,
                    onExpandedChange = { expandedKodeHari = it }
                ) {
                    OutlinedTextField(
                        value = if (selectedKodeHari.isEmpty()) "Pilih Hari..." else selectedKodeHari,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedKodeHari) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedKodeHari,
                        onDismissRequest = { expandedKodeHari = false }
                    ) {
                        listHari.forEach { hari ->
                            DropdownMenuItem(
                                text = { Text(hari) },
                                onClick = {
                                    selectedKodeHari = hari
                                    expandedKodeHari = false
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                if (selectedSalesId.isNotEmpty() && selectedKodeHari.isNotEmpty()) {
                    Text("3. Pilih Outlet untuk Rute Ini", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    
                    val totalSelected = selectedOutletIds.values.count { it }
                    Text("$totalSelected Outlet Terpilih", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

                    outlets.forEach { out ->
                        val isChecked = selectedOutletIds[out.id] == true
                        val isAssignedToOther = out.salesId.isNotEmpty() && (!out.salesId.equals(selectedSalesId, ignoreCase = true) || out.kodeHari != selectedKodeHari)
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = if (isChecked) MaterialTheme.colorScheme.primaryContainer else Color(0xFFF8FAFC),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedOutletIds[out.id] = !isChecked }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(out.name, fontSize = 12.sp, fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal)
                                if (isAssignedToOther) {
                                    Text("Sudah di rute: ${out.salesName} (${out.kodeHari})", fontSize = 10.sp, color = Color.Red)
                                } else if (isChecked) {
                                    Text("Di rute saat ini", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                } else {
                                    Text("Belum ter-inject", fontSize = 10.sp, color = Color.Gray)
                                }
                            }
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { selectedOutletIds[out.id] = it }
                            )
                        }
                    }

                    Button(
                        onClick = {
                            val sales = salesUsers.find { it.id == selectedSalesId }
                            if (sales != null) {
                                val selectedSet = selectedOutletIds.filterValues { it }.keys.toSet()
                                onUpdateRoute(sales.id, sales.username, selectedKodeHari, selectedSet)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Simpan Rute")
                    }
                } else {
                    Text("Pilih Salesman dan Kode Hari terlebih dahulu untuk menampilkan list outlet.", fontSize = 12.sp, color = Color.Gray, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                }
            }
        }
    }
}


// --- ADMIN INJECT STOCK PANEL ---
@Composable
fun AdminInjectStockSection(
    products: List<ProductEntity>,
    users: List<UserEntity>,
    stockAllocations: List<com.example.data.StockAllocationEntity>,
    onInjectStock: (String, String, String, Int) -> Unit
) {
    var selectedSalesId by remember { mutableStateOf("") }
    val qtysToInject = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }
    
    var salesExpanded by remember { mutableStateOf(false) }
    var productsExpanded by remember { mutableStateOf(false) }

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

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

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
        
        HorizontalDivider()

        Text("Daftar Sales yang Terinject Stock", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        

        val stockMap = remember(stockAllocations, products) {
            val pMap = products.associateBy { it.id }
            val map = mutableMapOf<Pair<String, String>, MutableMap<String, Int>>()
            stockAllocations.forEach { alloc ->
                val pName = pMap[alloc.productId]?.name ?: "Unknown"
                val sName = alloc.salesName
                val dateStr = alloc.date
                val key = Pair(sName, dateStr)
                if (!map.containsKey(key)) {
                    map[key] = mutableMapOf()
                }
                val current = map[key]!![pName] ?: 0
                map[key]!![pName] = current + alloc.qty
            }
            map
        }

        if (stockMap.isEmpty()) {
            Text("Belum ada stock yang di-inject ke sales.", fontSize = 12.sp, color = Color.Gray, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
        } else {
            val expandedSales = remember { androidx.compose.runtime.mutableStateMapOf<Pair<String, String>, Boolean>() }
            
            stockMap.forEach { (key, productsMap) ->
                val hasStock = productsMap.values.any { it > 0 }
                if (hasStock) {
                    val isExpanded = expandedSales[key] ?: false
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedSales[key] = !isExpanded }
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${key.first} - Tgl: ${key.second}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Expand",
                                    tint = Color.Gray
                                )
                            }
                            if (isExpanded) {
                                HorizontalDivider(color = Color(0xFFF1F5F9))
                                Column(modifier = Modifier.padding(16.dp)) {
                                    productsMap.filter { it.value > 0 }.forEach { (pName, qty) ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(pName, fontSize = 12.sp)
                                            Text("$qty PCS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- ADMIN WAREHOUSE LOGS PANEL ---
@Composable
fun AdminWarehouseLogsSection(
    logs: List<WarehouseLogEntity>,
    logFilterStart: String,
    logFilterEnd: String,
    onSelectStartDate: () -> Unit,
    onSelectEndDate: () -> Unit
) {
    val filteredLogs = remember(logs, logFilterStart, logFilterEnd) {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val inputSdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        try {
            val start = if (logFilterStart.isNotEmpty()) inputSdf.parse(logFilterStart) else null
            val end = if (logFilterEnd.isNotEmpty()) inputSdf.parse(logFilterEnd) else null

            logs.filter { log ->
                val datePart = log.date.split(" ")[0]
                val logDate = sdf.parse(datePart)
                if (logDate != null) {
                    val afterStart = start == null || logDate.time >= start.time
                    val beforeEnd = end == null || logDate.time <= end.time
                    afterStart && beforeEnd
                } else {
                    true
                }
            }
        } catch (e: Exception) {
            logs
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Log Mutasi Gudang & Sales", fontWeight = FontWeight.Bold, fontSize = 14.sp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onSelectStartDate, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9), contentColor = Color.DarkGray), modifier = Modifier.weight(1f)) {
                Text(logFilterStart.ifEmpty { "Mulai" }, fontSize = 11.sp)
            }
            Text("s/d", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            Button(onClick = onSelectEndDate, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9), contentColor = Color.DarkGray), modifier = Modifier.weight(1f)) {
                Text(logFilterEnd.ifEmpty { "Akhir" }, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            if (filteredLogs.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Tidak ada log dalam range tanggal tersebut.", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            } else {
                items(filteredLogs) { log ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(10.dp)) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(log.productName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Tipe: ${log.type}", color = Color.Gray, fontSize = 10.sp)
                                Text("Waktu: ${log.date}", color = Color.Gray, fontSize = 9.sp)
                            }

                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (log.qtyChange > 0) Color(0xFFECFDF5) else Color(0xFFFEF2F2),
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (log.qtyChange > 0) "+${log.qtyChange}" else "${log.qtyChange}",
                                    color = if (log.qtyChange > 0) Color(0xFF047857) else Color.Red,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- ADMIN RECEIPT SETTINGS PANEL ---
@Composable
fun AdminReceiptSettingsSection(
    viewModel: SfaViewModel,
    onPreviewReceiptClick: () -> Unit
) {
    val settings by viewModel.receiptSettings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var header by remember { mutableStateOf("") }
    var footer by remember { mutableStateOf("") }
    var headerAlign by remember { mutableStateOf("Center") }
    var footerAlign by remember { mutableStateOf("Center") }
    var logoBase64 by remember { mutableStateOf("") }
    var logoAlign by remember { mutableStateOf("Center") }

    LaunchedEffect(settings) {
        settings?.let {
            header = it.header
            footer = it.footer
            headerAlign = it.headerAlign
            footerAlign = it.footerAlign
            logoBase64 = it.logoBase64
            logoAlign = it.logoAlign
        }
    }

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                // Resize to max width 384 for 58mm printer
                val scaled = if (bitmap.width > 384) {
                    val ratio = 384.0f / bitmap.width
                    android.graphics.Bitmap.createScaledBitmap(bitmap, 384, (bitmap.height * ratio).toInt(), true)
                } else bitmap
                
                val outputStream = java.io.ByteArrayOutputStream()
                scaled.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outputStream)
                val byteArray = outputStream.toByteArray()
                logoBase64 = android.util.Base64.encodeToString(byteArray, android.util.Base64.DEFAULT)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Pengaturan Struk (Thermal 58mm)", fontWeight = FontWeight.Bold, fontSize = 14.sp)

        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                
                Text("Logo Struk", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (logoBase64.isNotEmpty()) {
                        val decodedBmp = try {
                            val imageBytes = android.util.Base64.decode(logoBase64, android.util.Base64.DEFAULT)
                            android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        } catch(e: Exception) { null }
                        
                        if (decodedBmp != null) {
                            androidx.compose.foundation.Image(
                                bitmap = decodedBmp.asImageBitmap(),
                                contentDescription = "Logo",
                                modifier = Modifier.size(80.dp)
                            )
                        } else {
                            Text("Invalid Image", color = Color.Red)
                        }
                    } else {
                        Box(modifier = Modifier.size(80.dp).background(Color.LightGray, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                            Text("No Logo", fontSize = 10.sp)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { launcher.launch("image/*") }) {
                            Text("Upload Logo")
                        }
                        if (logoBase64.isNotEmpty()) {
                            OutlinedButton(onClick = { logoBase64 = "" }) {
                                Text("Hapus Logo", color = Color.Red)
                            }
                        }
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Posisi Logo: ", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    listOf("Left", "Center", "Right").forEach { a ->
                        val isSelected = logoAlign == a
                        AssistChip(
                            onClick = { logoAlign = a },
                            label = { Text(a, fontSize = 10.sp) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            )
                        )
                    }
                }
                
                Divider()
                
                OutlinedTextField(
                    value = header,
                    onValueChange = { header = it },
                    label = { Text("Header Struk") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Posisi Header: ", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    listOf("Left", "Center", "Right").forEach { a ->
                        val isSelected = headerAlign == a
                        AssistChip(
                            onClick = { headerAlign = a },
                            label = { Text(a, fontSize = 10.sp) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            )
                        )
                    }
                }

                Divider()

                OutlinedTextField(
                    value = footer,
                    onValueChange = { footer = it },
                    label = { Text("Footer Struk") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Posisi Footer: ", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    listOf("Left", "Center", "Right").forEach { a ->
                        val isSelected = footerAlign == a
                        AssistChip(
                            onClick = { footerAlign = a },
                            label = { Text(a, fontSize = 10.sp) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val demoTrx = TransactionEntity(
                                orderId = "TRX-DEMO-123",
                                date = "02/07/2026 10:30",
                                salesId = "SLS-DEMO",
                                salesName = "Sales Demo",
                                outletName = "Toko Bangunan Demo",
                                outletType = "Regular",
                                geotag = "-",
                                total = 150000.0,
                                paymentMethod = "Cash",
                                topDays = 0,
                                dueDate = "-",
                                itemsJson = "[{\"id\":\"PRD-01\",\"name\":\"Semen Padang 50kg\",\"price\":65000.0,\"qty\":2}]",
                                statusSynced = true
                            )
                            val demoSettings = ReceiptSettingsEntity(
                                header = header,
                                footer = footer,
                                headerAlign = headerAlign,
                                footerAlign = footerAlign,
                                logoBase64 = logoBase64,
                                logoAlign = logoAlign
                            )
                            viewModel.saveSettings(demoSettings)
                            viewModel.preparePrintFromHistory(demoTrx)
                            onPreviewReceiptClick()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Preview")
                    }

                    Button(
                        onClick = {
                            viewModel.saveSettings(
                                ReceiptSettingsEntity(
                                    header = header,
                                    footer = footer,
                                    headerAlign = headerAlign,
                                    footerAlign = footerAlign,
                                    logoBase64 = logoBase64,
                                    logoAlign = logoAlign
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Simpan Settings")
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AddEditDataDialog(
    viewModel: SfaViewModel,
    dataType: String, // Users, Products, OutletMain
    editIndex: Int?, // index in relevant list if edit, null if add
    onDismiss: () -> Unit
) {
    val users by viewModel.usersList.collectAsStateWithLifecycle()
    val products by viewModel.productsList.collectAsStateWithLifecycle()
    val outlets by viewModel.outletsList.collectAsStateWithLifecycle()
    val allStokis by viewModel.stokisList.collectAsStateWithLifecycle()

    var fId by remember { mutableStateOf("") }
    var f1 by remember { mutableStateOf("") }
    var f2 by remember { mutableStateOf("") }
    var f3 by remember { mutableStateOf("") }
    var f4 by remember { mutableStateOf("") }
    var f5 by remember { mutableStateOf("") }
    var isStokisSales by remember { mutableStateOf(false) }

    val isEdit = editIndex != null

    LaunchedEffect(Unit) {
        if (isEdit) {
            when (dataType) {
                "Stokis" -> {
                    val st = allStokis.getOrNull(editIndex!!)
                    if (st != null) {
                        fId = st.id
                        f1 = st.name
                        f2 = st.address
                        f3 = st.assignedSalesId
                        f4 = st.assignedSalesName
                    }
                }
                "Users" -> {
                    val u = users[editIndex!!]
                    fId = u.id
                    f1 = u.username
                    f2 = u.password
                    f3 = u.role
                    isStokisSales = u.isStokisSales
                }
                "Products" -> {
                    val p = products[editIndex!!]
                    fId = p.id
                    f1 = p.name
                    f2 = p.priceRetail.toInt().toString()
                    f3 = p.priceWholesale.toInt().toString()
                }
                "OutletMain" -> {
                    val o = outlets[editIndex!!]
                    fId = o.id
                    f1 = o.name
                    f2 = o.type
                    f3 = o.category
                    f4 = o.address
                    f5 = o.salesId
                }
            }
        } else {
            // Setup defaults for Add
            if (dataType == "OutletMain") {
                fId = String.format(Locale.getDefault(), "%06d", outlets.size + 1)
                f2 = "Regular"
                f3 = "Retail"
            } else if (dataType == "Stokis") {
                fId = "STK" + String.format(Locale.getDefault(), "%03d", allStokis.size + 1)
            } else if (dataType == "Products") {
                fId = "PRD-" + (10000..99999).random().toString()
            }
        }
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().imePadding().padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (isEdit) "Edit Data $dataType" else "Tambah Data $dataType",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                // ID input
                if (dataType != "OutletMain") {
                    OutlinedTextField(
                        value = fId,
                        onValueChange = { fId = it },
                        label = { Text("ID") },
                        enabled = !isEdit,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = fId,
                        onValueChange = { },
                        label = { Text("ID (Otomatis)") },
                        enabled = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                when (dataType) {
                    "Users" -> {
                        OutlinedTextField(
                            value = f1,
                            onValueChange = { f1 = it },
                            label = { Text("Username") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = f2,
                            onValueChange = { f2 = it },
                            label = { Text("Password") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        // Simple dropdown for roles
                        Column {
                            Text("Pilih Role", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("Sales", "Supervisor", "Admin").forEach { role ->
                                    val selected = f3 == role
                                    AssistChip(
                                        onClick = { f3 = role },
                                        label = { Text(role) },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                        )
                                    )
                                }
                            }
                        }
                        
                        if (f3 == "Sales") {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                Text("Otoritas Ambil Stokis", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(
                                            selected = isStokisSales,
                                            onClick = { isStokisSales = true }
                                        )
                                        Text("Ya", fontSize = 13.sp)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(
                                            selected = !isStokisSales,
                                            onClick = { isStokisSales = false }
                                        )
                                        Text("Tidak", fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                    "Products" -> {
                        OutlinedTextField(
                            value = f1,
                            onValueChange = { f1 = it },
                            label = { Text("Nama Produk") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = f2,
                            onValueChange = { f2 = it },
                            label = { Text("Harga Retail") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = f3,
                            onValueChange = { f3 = it },
                            label = { Text("Harga Wholesale") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    "Stokis" -> {
                        OutlinedTextField(value = fId, onValueChange = { fId = it }, label = { Text("ID Stokis") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = f1, onValueChange = { f1 = it }, label = { Text("Nama Stokis") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = f2, onValueChange = { f2 = it }, label = { Text("Alamat") }, modifier = Modifier.fillMaxWidth())
                        
                        // Select Sales
                        val salesUsers = users.filter { it.role == "Sales" }
                        var expandedSales by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expandedSales,
                            onExpandedChange = { expandedSales = it }
                        ) {
                            OutlinedTextField(
                                value = if (f4.isNotEmpty()) f4 else "Pilih Sales (Otoritas Stokis)",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSales) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedSales,
                                onDismissRequest = { expandedSales = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Tidak ada / Hapus") },
                                    onClick = {
                                        f3 = ""
                                        f4 = ""
                                        expandedSales = false
                                    }
                                )
                                salesUsers.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text("${s.username} (${s.id})") },
                                        onClick = {
                                            f3 = s.id
                                            f4 = s.username
                                            expandedSales = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    "OutletMain" -> {
                        OutlinedTextField(
                            value = f1,
                            onValueChange = { f1 = it },
                            label = { Text("Nama Outlet") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = f4,
                            onValueChange = { f4 = it },
                            label = { Text("Alamat Lengkap") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        // Select regular/NOO
                        Column {
                            Text("Tipe Outlet", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("Regular", "NOO").forEach { t ->
                                    val selected = f2 == t
                                    AssistChip(
                                        onClick = { f2 = t },
                                        label = { Text(t) },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                        )
                                    )
                                }
                            }
                        }
                        // Price tiers default
                        Column {
                            Text("Harga Bawaan", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("Retail", "Wholesale").forEach { cat ->
                                    val selected = f3 == cat
                                    AssistChip(
                                        onClick = { f3 = cat },
                                        label = { Text(cat) },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Batal")
                    }
                    Button(
                        onClick = {
                            if (fId.isEmpty() && dataType != "OutletMain") return@Button
                            when (dataType) {
                                "Users" -> {
                                    viewModel.saveUser(UserEntity(fId, f1, f2, f3, isStokisSales))
                                }
                                "Products" -> {
                                    val existingProduct = products.find { it.id == fId }
                                    val currentStock = existingProduct?.warehouseStock ?: 0
                                    viewModel.saveProduct(ProductEntity(fId, f1, f2.toDoubleOrNull() ?: 0.0, f3.toDoubleOrNull() ?: 0.0, currentStock))
                                }
                                "OutletMain" -> {
                                    val matchingSales = users.find { it.id == f5 }
                                    viewModel.saveOutlet(
                                        OutletEntity(
                                            id = fId,
                                            name = f1,
                                            type = f2,
                                            category = f3,
                                            address = f4,
                                            geotag = "",
                                            salesId = f5,
                                            salesName = matchingSales?.username ?: "",
                                            isNewLocal = true
                                        )
                                    )
                                }
                                "Stokis" -> {
                                    viewModel.saveStokis(
                                        com.example.data.StokisEntity(
                                            id = fId,
                                            name = f1,
                                            address = f2,
                                            assignedSalesId = f3,
                                            assignedSalesName = f4
                                        )
                                    )
                                }
                            }
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Simpan")
                    }
                }
            }
        }
    }
}

// --- CSV CODES PASTE OR IMPORT OVERLAY DIALOG ---
@Composable
fun CsvImportDialog(
    viewModel: SfaViewModel,
    dataType: String, // Users, Products, OutletMain
    onDismiss: () -> Unit
) {
    var pastedText by remember { mutableStateOf("") }
    var isUploading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().imePadding().padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Mass Upload CSV - $dataType",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                Text(
                    text = "Tempel teks berformat CSV Anda ke kotak teks di bawah ini lalu klik upload.",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )

                OutlinedTextField(
                    value = pastedText,
                    onValueChange = { pastedText = it },
                    placeholder = {
                        val format = when (dataType) {
                            "Users" -> "ID,Username,Password,Role\nSLS-02,sales2,sales123,Sales"
                            "Products" -> "ID,Name,PriceRetail,PriceWholesale\nPRD-06,Paku Beton,15000,12000"
                            else -> "ID,Name,Type,Category,Address\n000004,Toko Baru,Regular,Retail,Jl. Padang Baru No. 4"
                        }
                        Text("Cth Format:\n$format")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    maxLines = 15
                )

                // Fill template helper button
                TextButton(
                    onClick = {
                        pastedText = when (dataType) {
                            "Users" -> "ID,Username,Password,Role\nSLS-02,budi,budi123,Sales\nSLS-03,siti,siti123,Sales"
                            "Products" -> "ID,Name,PriceRetail,PriceWholesale,WarehouseStock\nPRD-06,Pipa Paralon 3 Inch,45000,41000,150\nPRD-07,Cat Tembok 5kg,120000,112000,80"
                            else -> "ID,Name,Type,Category,Address\n000004,Toko Sinar Jaya,Regular,Retail,Jl. Jenderal Sudirman No. 25\n000005,CV Karya Mandiri,Regular,Wholesale,Kawasan Ganting No. 10"
                        }
                    }
                ) {
                    Text("Isi Prefilled Mock Data Valid", fontSize = 11.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Batal")
                    }
                    Button(
                        onClick = {
                            if (pastedText.trim().isEmpty()) return@Button
                            isUploading = true
                            scope.launch {
                                val success = when (dataType) {
                                    "Users" -> viewModel.importUsersFromCSV(pastedText)
                                    "Products" -> viewModel.importProductsFromCSV(pastedText)
                                    else -> viewModel.importOutletsFromCSV(pastedText)
                                }
                                isUploading = false
                                if (success) {
                                    Toast.makeText(context, "Data berhasil diunggah secara massal!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                } else {
                                    Toast.makeText(context, "Gagal mengimpor. Periksa format CSV Anda.", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isUploading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                        } else {
                            Text("Upload")
                        }
                    }
                }
            }
        }
    }
}

// --- PREPRINT LOG IN PASSWORD SETTINGS DIALOG ---
@Composable
fun SettingsAndPassDialog(
    viewModel: SfaViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val users by viewModel.usersList.collectAsStateWithLifecycle()

    var oldPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }

    // Admin reset other user password state
    var selectedUserToReset by remember { mutableStateOf("") }
    var adminResetPassVal by remember { mutableStateOf("") }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().imePadding().padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Pengaturan Akun & Printer", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                // TEST PRINTER SECTION
                Text("Printer Thermal 58mm", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                Text(
                    text = "Sistem terintegrasi dengan aplikasi RawBT. Pastikan RawBT terpasang dan printer Bluetooth sudah dipasangkan di HP ini.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                Button(
                    onClick = {
                        val testString = PrintingHelper.generateReceiptText(
                            title = "TEST KONEKSI",
                            subtitle = "SFA System",
                            contentLines = listOf("Koneksi Thermal Printer OK", "line"),
                            settings = viewModel.receiptSettings.value ?: ReceiptSettingsEntity()
                        )
                        val intentUri = PrintingHelper.getRawBTIntentUri(testString, "", "Center")
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(intentUri))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Aplikasi RawBT tidak terpasang atau printer belum terhubung.", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9), contentColor = Color.DarkGray),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test Koneksi Printer", fontSize = 11.sp)
                }

                Divider()

                // CHANGE OWN PASSWORD SECTION
                Text("Ubah Password Saya", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                OutlinedTextField(
                    value = oldPass,
                    onValueChange = { oldPass = it },
                    label = { Text("Password Lama") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPass,
                    onValueChange = { newPass = it },
                    label = { Text("Password Baru") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        if (currentUser != null && oldPass.isNotEmpty() && newPass.isNotEmpty()) {
                            viewModel.changePassword(
                                username = currentUser!!.username,
                                role = currentUser!!.role,
                                targetUser = currentUser!!.username,
                                oldPass = oldPass,
                                newPass = newPass
                            )
                            oldPass = ""
                            newPass = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Simpan Password Baru")
                }

                // ADMIN FORCED RESET SECTION
                if (currentUser?.role == "Admin") {
                    Divider()
                    Text("Superadmin: Reset Password Lain", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Red)
                    Text(
                        text = "Reset password untuk user lain tanpa memerlukan password lama mereka.",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )

                    // Mock dropdown representation
                    users.filter { it.id != currentUser?.id }.forEach { u ->
                        val isSelected = selectedUserToReset == u.username
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedUserToReset = u.username }
                                .padding(8.dp)
                        ) {
                            Text(text = "${u.username} (${u.role})", fontSize = 11.sp)
                        }
                    }

                    OutlinedTextField(
                        value = adminResetPassVal,
                        onValueChange = { adminResetPassVal = it },
                        label = { Text("Password Baru Target") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            if (selectedUserToReset.isNotEmpty() && adminResetPassVal.isNotEmpty()) {
                                viewModel.changePassword(
                                    username = currentUser!!.username,
                                    role = currentUser!!.role,
                                    targetUser = selectedUserToReset,
                                    oldPass = "",
                                    newPass = adminResetPassVal
                                )
                                selectedUserToReset = ""
                                adminResetPassVal = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Paksa Reset Password")
                    }
                }
            }
        }
    }
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White), // Pure white background to match and blend seamlessly with the image
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            AsyncImage(
                model = "https://res.cloudinary.com/donww7xep/image/upload/v1782804261/ChatGPT_Image_28_Jun_2026_21.47.19_nupxu2.png",
                contentDescription = "ESRA UTAMA Splash Screen Logo",
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(24.dp)),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.height(32.dp))
            CircularProgressIndicator(
                color = Color(0xFF0D47A1), // Elegant deep blue theme color
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "ESRA UTAMA SFA",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0D47A1),
                letterSpacing = 0.5.sp
            )
            Text(
                text = "Sales Force Automation System",
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = Color.Gray,
                letterSpacing = 0.25.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

// --- ADMIN HISTORY COMPONENT ---
@Composable
fun AdminHistorySection(
    viewModel: SfaViewModel,
    onSelectStartDate: () -> Unit,
    onSelectEndDate: () -> Unit,
    onPrintOverall: () -> Unit = {},
    onPrintDetail: (TransactionEntity) -> Unit = {}
) {
    val transactions by viewModel.transactionsList.collectAsStateWithLifecycle()
    val filterStart by viewModel.adminHistoryFilterStartDate.collectAsStateWithLifecycle()
    val filterEnd by viewModel.adminHistoryFilterEndDate.collectAsStateWithLifecycle()
    val filterActive by viewModel.isAdminHistoryFilterActive.collectAsStateWithLifecycle()

    var editingTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var deletingTransactionId by remember { mutableStateOf<String?>(null) }

    val filteredList = remember(transactions, filterStart, filterEnd, filterActive) {
        if (!filterActive) {
            transactions
        } else {
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val inputSdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            try {
                val start = if (filterStart.isNotEmpty()) inputSdf.parse(filterStart) else null
                val end = if (filterEnd.isNotEmpty()) inputSdf.parse(filterEnd) else null

                transactions.filter { trx ->
                    val datePart = trx.date.split(" ")[0]
                    val trxDate = sdf.parse(datePart)
                    if (trxDate != null) {
                        val afterStart = start == null || trxDate.time >= start.time
                        val beforeEnd = end == null || trxDate.time <= end.time
                        afterStart && beforeEnd
                    } else {
                        true
                    }
                }
            } catch (e: Exception) {
                transactions
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("History Transaksi Sales", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            
            // Toggle Filter Active
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onPrintOverall, modifier = Modifier.size(28.dp).background(Color(0xFFEFF6FF), CircleShape)) {
                    Icon(Icons.Default.Print, contentDescription = "Cetak Semua", tint = Color.Blue, modifier = Modifier.size(14.dp))
                }
                Text("Aktifkan Filter", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Switch(
                    checked = filterActive,
                    onCheckedChange = { viewModel.isAdminHistoryFilterActive.value = it }
                )
            }
        }

        if (filterActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onSelectStartDate,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9), contentColor = Color.DarkGray),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(filterStart.ifEmpty { "Mulai" }, fontSize = 11.sp)
                }
                Text("s/d", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                Button(
                    onClick = onSelectEndDate,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9), contentColor = Color.DarkGray),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(filterEnd.ifEmpty { "Akhir" }, fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            if (filteredList.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Tidak ada transaksi ditemukan.", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            } else {
                items(filteredList) { trx ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(10.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(trx.outletName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Sales: ${trx.salesName} (${trx.salesId})", color = Color.Gray, fontSize = 11.sp)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(top = 1.dp)
                                    ) {
                                        Text("Waktu: ${trx.date}", color = Color.Gray, fontSize = 9.sp)
                                        Text("•", color = Color.LightGray, fontSize = 9.sp)
                                        if (trx.statusSynced) {
                                            Icon(
                                                imageVector = Icons.Default.CloudDone,
                                                contentDescription = "Synced",
                                                tint = Color(0xFF047857),
                                                modifier = Modifier.size(10.dp)
                                            )
                                            Text(
                                                text = "Terupload ke Spreadsheet",
                                                color = Color(0xFF047857),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.CloudQueue,
                                                contentDescription = "Unsynced",
                                                tint = Color(0xFFC2410C),
                                                modifier = Modifier.size(10.dp)
                                            )
                                            Text(
                                                text = "Belum Terupload",
                                                color = Color(0xFFC2410C),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(onClick = { onPrintDetail(trx) }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Print, contentDescription = "Cetak", tint = Color(0xFF047857), modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(onClick = { editingTransaction = trx }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Blue, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(onClick = { deletingTransactionId = trx.orderId }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color.Red, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            Divider(modifier = Modifier.padding(vertical = 8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = if (trx.paymentMethod == "Kredit") Color(0xFFFFF7ED) else Color(0xFFECFDF5),
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = trx.paymentMethod,
                                        color = if (trx.paymentMethod == "Kredit") Color(0xFFC2410C) else Color(0xFF047857),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    "Rp ${String.format(Locale.getDefault(), "%,.0f", trx.total)}",
                                    color = Color(0xFF0F172A),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit Transaction Dialog
    if (editingTransaction != null) {
        val trx = editingTransaction!!
        var outletNameVal by remember { mutableStateOf(trx.outletName) }
        var paymentMethodVal by remember { mutableStateOf(trx.paymentMethod) }
        var totalVal by remember { mutableStateOf(trx.total.toString()) }
        var topDaysVal by remember { mutableStateOf(trx.topDays.toString()) }
        var dueDateVal by remember { mutableStateOf(trx.dueDate) }

        androidx.compose.ui.window.Dialog(onDismissRequest = { editingTransaction = null }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().imePadding().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Revisi Transaksi - ${trx.orderId}", fontWeight = FontWeight.Bold, fontSize = 15.sp)

                    OutlinedTextField(
                        value = outletNameVal,
                        onValueChange = { outletNameVal = it },
                        label = { Text("Nama Outlet") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Column {
                        Text("Metode Pembayaran", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Cash", "Cashless", "Kredit").forEach { m ->
                                val sel = paymentMethodVal == m
                                AssistChip(
                                    onClick = { paymentMethodVal = m },
                                    label = { Text(m) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = if (sel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                    )
                                )
                            }
                        }
                    }

                    if (paymentMethodVal == "Kredit") {
                        OutlinedTextField(
                            value = topDaysVal,
                            onValueChange = { topDaysVal = it },
                            label = { Text("T.O.P Days") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = dueDateVal,
                            onValueChange = { dueDateVal = it },
                            label = { Text("Jatuh Tempo") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = totalVal,
                        onValueChange = { totalVal = it },
                        label = { Text("Total Tagihan (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = { editingTransaction = null }, modifier = Modifier.weight(1f)) {
                            Text("Batal")
                        }
                        Button(
                            onClick = {
                                val updated = trx.copy(
                                    outletName = outletNameVal,
                                    paymentMethod = paymentMethodVal,
                                    total = totalVal.toDoubleOrNull() ?: trx.total,
                                    topDays = topDaysVal.toIntOrNull() ?: trx.topDays,
                                    dueDate = dueDateVal
                                )
                                viewModel.updateTransaction(updated)
                                editingTransaction = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Simpan")
                        }
                    }
                }
            }
        }
    }

    // Delete Transaction Dialog
    if (deletingTransactionId != null) {
        val oid = deletingTransactionId!!
        AlertDialog(
            onDismissRequest = { deletingTransactionId = null },
            title = { Text("Hapus Transaksi", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = { Text("Apakah Anda yakin ingin menghapus transaksi $oid dari database? Tindakan ini tidak dapat dibatalkan.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTransaction(oid)
                        deletingTransactionId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Hapus", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTransactionId = null }) {
                    Text("Batal")
                }
            }
        )
    }
}

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
                    val groupedStokis = remember(stokisList) { stokisList.groupBy { it.stokisName } }
                    val expandedGroups = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
                    
                    groupedStokis.forEach { (stokisName, items) ->
                        val isExpanded = expandedGroups[stokisName] ?: false
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { expandedGroups[stokisName] = !isExpanded }
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val dateStr = items.find { it.lastUpdateDate.isNotEmpty() }?.lastUpdateDate ?: ""
                                    val title = if (dateStr.isNotEmpty()) "$stokisName - Tgl: $dateStr" else stokisName
                                    Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = "Expand",
                                        tint = Color.Gray
                                    )
                                }
                                if (isExpanded) {
                                    HorizontalDivider(color = Color(0xFFE2E8F0))
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items.forEach { s ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(s.productName, fontSize = 12.sp)
                                                Text("${s.qty} PCS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider()

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
                                    viewModel.injectStockToStokis(stokis.id, pid, qty)
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
    }
}
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

@Composable
fun AdminSystemHealthSection(viewModel: SfaViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("System Health & Archive", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
        HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))

        // Apps Script URL Configuration
        val appsScriptUrl by viewModel.appsScriptUrl.collectAsStateWithLifecycle()
        var currentUrl by remember { mutableStateOf(appsScriptUrl) }
        val context = LocalContext.current
        
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Google Apps Script URL (2-Ways Sync)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                OutlinedTextField(
                    value = currentUrl,
                    onValueChange = { currentUrl = it },
                    label = { Text("URL Web App") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var isSyncing by remember { mutableStateOf(false) }
                    val scope = rememberCoroutineScope()
                    
                    Button(
                        onClick = {
                            if (currentUrl.isBlank()) {
                                Toast.makeText(context, "URL tidak boleh kosong", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isSyncing = true
                            scope.launch {
                                val result = viewModel.syncWithGoogleSheets(currentUrl)
                                Toast.makeText(context, result.second, Toast.LENGTH_LONG).show()
                                isSyncing = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        enabled = !isSyncing,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Sync Sekarang")
                    }
                    
                    Button(
                        onClick = {
                            viewModel.saveAppsScriptUrl(currentUrl)
                            Toast.makeText(context, "URL berhasil disimpan", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Simpan URL")
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Database Status
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                border = BorderStroke(1.dp, Color(0xFFDCFCE7))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("DATABASE STATUS", color = Color(0xFF16A34A), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("HEALTHY", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }
            }

            // Archive Status
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                border = BorderStroke(1.dp, Color(0xFFDBEAFE))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ARCHIVE STATUS", color = Color(0xFF2563EB), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("IDLE", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        // Performance Metrics Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Performance Metrics", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))
                Text("Last Archive Duration: -", fontSize = 13.sp, color = Color(0xFF64748B))
                Spacer(modifier = Modifier.height(4.dp))
                Text("Largest Month Trx: -", fontSize = 13.sp, color = Color(0xFF64748B))
            }
        }

        // Jalankan Archive Button
        Button(
            onClick = { Toast.makeText(context, "Archive functionality not yet implemented", Toast.LENGTH_SHORT).show() },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Jalankan Archive Sekarang", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun HealthCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = Color.Gray, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
    }
}
