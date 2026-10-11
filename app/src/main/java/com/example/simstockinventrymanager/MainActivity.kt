package com.example.simstockinventrymanager

import android.content.Intent
import android.os.Bundle
import android.app.Activity
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private val Ink = Color(0xFFF3F6FC)
private val Muted = Color(0xFFA0AEC0)
private val Teal = Color(0xFF5AA8FF)
private val Aqua = Color(0xFF83C2FF)
private val Canvas = Color(0xFF070A0F)
private val Border = Color(0xFF273447)
private val Danger = Color(0xFFFF6673)
private val Amber = Color(0xFFFFA64D)
private val Success = Color(0xFF41D6A2)
private val SurfaceDeep = Color(0xFF121A25)

data class Dealer(
    val id: String = "",
    val name: String = "",
    val city: String = "",
    val phone: String = "",
    val discount: Double = 0.0
)

data class Product(
    val id: String = "",
    val name: String = "",
    val mrp: Double = 0.0,
    val weight: Double = 0.0,
    val weightUnit: String = "g",
    val box: Int = 0,
    val min: Int = 5
)

data class StockEntry(
    val id: String = "",
    val type: String = "",
    val dealerId: String = "",
    val productId: String = "",
    val quantity: Int = 0,
    val amount: Double = 0.0,
    val date: String = "",
    val unitPrice: Double = 0.0,
    val expiry: String = "",
    val toDealerId: String = "",
    val groupId: String = "",
    val delta: Int = 0
)

private enum class Page(val label: String) {
    Stock("Stock"), Entry("Entry"), Dealers("Dealers"), Products("Products"), History("History")
}

class MainActivity : ComponentActivity() {
    private var firebaseConfigured = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firebaseConfigured = FirebaseApp.initializeApp(this) != null
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Teal,
                    onPrimary = Color.White,
                    secondary = Aqua,
                    background = Canvas,
                    surface = SurfaceDeep,
                    onSurface = Ink,
                    error = Danger,
                    onBackground = Ink,
                    surfaceVariant = Color(0xFF1C2735),
                    onSurfaceVariant = Muted
                )
            ) {
                CompositionLocalProvider(
                    LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.SansSerif
                    )
                ) {
                    DealerStockApp(firebaseConfigured)
                }
            }
        }
    }
}

@Composable
private fun DealerStockApp(firebaseConfigured: Boolean) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val clipboardManager = LocalClipboardManager.current

    val auth = remember(firebaseConfigured) {
        if (firebaseConfigured) FirebaseAuth.getInstance() else null
    }
    val db = remember(firebaseConfigured) {
        if (firebaseConfigured) FirebaseFirestore.getInstance() else null
    }
    val credentialManager = remember { CredentialManager.create(context) }
    val coroutineScope = rememberCoroutineScope()

    var user by remember { mutableStateOf(auth?.currentUser) }
    var signInPending by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var userId by remember { mutableStateOf(auth?.currentUser?.uid.orEmpty()) }

    var dealers by remember { mutableStateOf(emptyList<Dealer>()) }
    var products by remember { mutableStateOf(emptyList<Product>()) }
    var entries by remember { mutableStateOf(emptyList<StockEntry>()) }
    var error by remember { mutableStateOf("") }
    var activePage by remember { mutableStateOf(Page.Stock) }
    var openedDealer by remember { mutableStateOf("") }
    var historyType by remember { mutableStateOf("tout") }
    var historyDealer by remember { mutableStateOf("") }
    var stockSheet by remember { mutableStateOf(false) }
    var editDealer by remember { mutableStateOf<Dealer?>(null) }
    var editProduct by remember { mutableStateOf<Product?>(null) }
    var confirmDelete by remember { mutableStateOf<Pair<String, String>?>(null) }

    val webClientResource = remember {
        context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
    }
    val webClientId = remember(webClientResource) {
        if (webClientResource == 0) "859375987923-9ibsk0j3n7csrmnel469cfiudpfklrqr.apps.googleusercontent.com" else context.getString(webClientResource)
    }
    val signInReady = firebaseConfigured && auth != null && webClientId.isNotBlank()

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            user = firebaseAuth.currentUser
            userId = firebaseAuth.currentUser?.uid.orEmpty()
            dealers = emptyList()
            products = emptyList()
            entries = emptyList()
            openedDealer = ""
            activePage = Page.Stock
            error = ""
        }
        auth?.addAuthStateListener(listener)
        onDispose { auth?.removeAuthStateListener(listener) }
    }

    DisposableEffect(db, userId) {
        var dealerListener: ListenerRegistration? = null
        var productListener: ListenerRegistration? = null
        var entryListener: ListenerRegistration? = null
        if (db != null && userId.isNotBlank()) {
            val root = db.collection("users").document(userId)
            dealerListener = root.collection("dealers").addSnapshotListener { snapshot, e ->
                if (e != null) error = e.localizedMessage ?: "Could not load dealers."
                else dealers = snapshot?.toObjects(Dealer::class.java) ?: emptyList()
            }
            productListener = root.collection("products").addSnapshotListener { snapshot, e ->
                if (e != null) error = e.localizedMessage ?: "Could not load products."
                else products = snapshot?.toObjects(Product::class.java) ?: emptyList()
            }
            entryListener = root.collection("transactions").addSnapshotListener { snapshot, e ->
                if (e != null) error = e.localizedMessage ?: "Could not load stock history."
                else entries = snapshot?.toObjects(StockEntry::class.java) ?: emptyList()
            }
        }
        onDispose {
            dealerListener?.remove()
            productListener?.remove()
            entryListener?.remove()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            try {
                activity.contentResolver.openOutputStream(uri)?.use {
                    it.write(exportCsv(dealers, products, entries).toByteArray())
                } ?: throw IllegalStateException("Could not open the selected file.")
                error = "Backup saved."
            } catch (e: Exception) {
                error = e.localizedMessage ?: "Backup could not be saved."
            }
        }
    }

    var pendingExcelData by remember { mutableStateOf<Triple<List<String>, List<List<String>>, List<String>>?>(null) }

    val excelExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        val excelData = pendingExcelData
        if (result.resultCode == Activity.RESULT_OK && uri != null && excelData != null) {
            try {
                activity.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    generateXlsxReport(excelData.first, excelData.second, excelData.third, outputStream)
                } ?: throw IllegalStateException("Could not open output stream for Excel file.")
                error = "Dealer_Stock_Report.xlsx saved successfully."
            } catch (e: Exception) {
                error = e.localizedMessage ?: "Excel report could not be saved."
            }
        }
    }

    fun saveDealer(dealer: Dealer) {
        if (db != null && userId.isNotBlank()) {
            db.collection("users").document(userId).collection("dealers").document(dealer.id).set(dealer)
                .addOnFailureListener { error = it.localizedMessage ?: "Dealer could not be saved." }
        }
    }

    fun saveProduct(product: Product) {
        if (db != null && userId.isNotBlank()) {
            db.collection("users").document(userId).collection("products").document(product.id).set(product)
                .addOnFailureListener { error = it.localizedMessage ?: "Product could not be saved." }
        }
    }

    fun saveEntry(entry: StockEntry) {
        if (db != null && userId.isNotBlank()) {
            db.collection("users").document(userId).collection("transactions").document(entry.id).set(entry)
                .addOnFailureListener { error = it.localizedMessage ?: "Stock entry could not be saved." }
        }
    }

    fun saveEntries(entriesToSave: List<StockEntry>) {
        if (db != null && userId.isNotBlank()) {
            val batch = db.batch()
            val root = db.collection("users").document(userId)
            entriesToSave.forEach { batch.set(root.collection("transactions").document(it.id), it) }
            batch.commit().addOnFailureListener { error = it.localizedMessage ?: "Stock entries could not be saved." }
        }
    }

    fun deleteEntry(entry: StockEntry) {
        if (db != null && userId.isNotBlank()) {
            val root = db.collection("users").document(userId)
            if (entry.groupId.isNotBlank()) {
                root.collection("transactions").whereEqualTo("groupId", entry.groupId).get()
                    .addOnSuccessListener { snapshot ->
                        val batch = db.batch()
                        snapshot.documents.forEach { batch.delete(it.reference) }
                        batch.commit().addOnFailureListener { error = it.localizedMessage ?: "Entry could not be deleted." }
                    }
                    .addOnFailureListener { error = it.localizedMessage ?: "Group entries could not be loaded." }
            } else {
                root.collection("transactions").document(entry.id).delete()
                    .addOnFailureListener { error = it.localizedMessage ?: "Entry could not be deleted." }
            }
        }
    }

    fun deleteDealer(dealer: Dealer) {
        if (db != null && userId.isNotBlank()) {
            val root = db.collection("users").document(userId)
            root.collection("transactions").whereEqualTo("dealerId", dealer.id).get()
                .addOnSuccessListener { snapshot ->
                    val batch = db.batch()
                    snapshot.documents.forEach { batch.delete(it.reference) }
                    batch.delete(root.collection("dealers").document(dealer.id))
                    batch.commit().addOnFailureListener { error = it.localizedMessage ?: "Dealer could not be removed." }
                }
                .addOnFailureListener { error = it.localizedMessage ?: "Dealer entries could not be loaded." }
        }
    }

    fun deleteProduct(product: Product) {
        if (db != null && userId.isNotBlank()) {
            val root = db.collection("users").document(userId)
            root.collection("transactions").whereEqualTo("productId", product.id).get()
                .addOnSuccessListener { snapshot ->
                    val batch = db.batch()
                    snapshot.documents.forEach { batch.delete(it.reference) }
                    batch.delete(root.collection("products").document(product.id))
                    batch.commit().addOnFailureListener { error = it.localizedMessage ?: "Product could not be removed." }
                }
                .addOnFailureListener { error = it.localizedMessage ?: "Product entries could not be loaded." }
        }
    }

    val stock = { dealerId: String, productId: String ->
        entries.filter { it.dealerId == dealerId && it.productId == productId }.sumOf { entry ->
            when (entry.type) {
                "dispatch", "tin" -> entry.quantity
                "sold", "return", "tout" -> -entry.quantity
                "manual" -> entry.delta
                else -> 0
            }
        }
    }
    val price = { dealerId: String, productId: String ->
        val p = products.find { it.id == productId }
        p?.let { (it.mrp * (100.0 - (dealers.find { d -> d.id == dealerId }?.discount ?: 0.0)) / 100.0).roundToInt().toDouble() }
            ?: 0.0
    }
    val entryPrice = { entry: StockEntry ->
        if (entry.unitPrice > 0.0) entry.unitPrice else price(entry.dealerId, entry.productId)
    }
    val due = { dealerId: String ->
        entries.filter { it.dealerId == dealerId }.sumOf { entry ->
            when (entry.type) {
                "dispatch", "tin" -> entry.quantity * entryPrice(entry)
                "return", "tout" -> -entry.quantity * entryPrice(entry)
                "pay" -> -entry.amount
                else -> 0.0
            }
        }
    }
    val batchList = { dealerId: String, productId: String ->
        val batches = entries.filter {
            it.dealerId == dealerId && it.productId == productId &&
                (it.type == "dispatch" || it.type == "tin")
        }.sortedBy { it.expiry.ifBlank { "9999-12-31" } }
            .map { it.quantity to it.expiry }
            .toMutableList()
        var outgoing = entries.filter {
            it.dealerId == dealerId && it.productId == productId &&
                (it.type == "sold" || it.type == "return" || it.type == "tout" || it.type == "manual")
        }.sumOf { if (it.type == "manual") maxOf(0, -it.delta) else it.quantity }
        batches.indices.forEach { i ->
            val used = minOf(batches[i].first, outgoing)
            batches[i] = (batches[i].first - used) to batches[i].second
            outgoing -= used
        }
        batches.filter { it.first > 0 }
    }

    if (user == null) {
        SignInScreen(
            signInReady = signInReady,
            error = error,
            loading = signInPending,
            onSignIn = {
                if (!signInReady) {
                    error = "Google sign-in requires a valid Web OAuth client ID in google-services.json."
                } else {
                    signInPending = true
                    error = ""
                    coroutineScope.launch {
                        try {
                            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId = webClientId).build()
                            val googleOption = GetGoogleIdOption.Builder()
                                .setFilterByAuthorizedAccounts(false)
                                .setServerClientId(webClientId)
                                .setAutoSelectEnabled(false)
                                .build()
                            val request = GetCredentialRequest.Builder()
                                .addCredentialOption(signInWithGoogleOption)
                                .addCredentialOption(googleOption)
                                .build()
                            val result = credentialManager.getCredential(activity, request)
                            val credential = result.credential
                            if (credential !is CustomCredential ||
                                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                            ) {
                                throw IllegalStateException("Google did not return a valid sign-in credential.")
                            }
                            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                            val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
                            val firebaseAuth = auth
                                ?: throw IllegalStateException("Firebase is not configured for this app.")
                            firebaseAuth.signInWithCredential(firebaseCredential)
                                .addOnSuccessListener { authResult ->
                                    signInPending = false
                                    user = authResult.user
                                    error = ""
                                }
                                .addOnFailureListener { exception ->
                                    signInPending = false
                                    error = exception.localizedMessage ?: "Firebase sign-in failed."
                                }
                        } catch (_: GetCredentialCancellationException) {
                            signInPending = false
                            error = "Google sign-in was cancelled."
                        } catch (exception: Exception) {
                            signInPending = false
                            error = exception.localizedMessage ?: "Google sign-in failed."
                        }
                    }
                }
            }
        )
        return
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Canvas) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(Modifier.size(38.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Dealer Stock", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Ink, letterSpacing = (-0.25).sp)
                        Text("Inventory overview", fontSize = 12.sp, color = Muted, letterSpacing = 0.15.sp)
                    }
                }
                user?.let { u ->
                    val initial = (u.displayName?.firstOrNull() ?: u.email?.firstOrNull() ?: 'U').uppercaseChar()
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Teal, Aqua)))
                            .clickable { showProfile = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(initial.toString(), color = Canvas, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
            error.takeIf { it.isNotBlank() }?.let {
                Text(it, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), color = Danger)
            }
            if (openedDealer.isNotBlank() && activePage == Page.Stock) {
                DealerReportScreen(
                    dealer = dealers.find { it.id == openedDealer },
                    dealers = dealers,
                    products = products,
                    entries = entries,
                    stock = stock,
                    due = due,
                    price = price,
                    batchList = batchList,
                    onBack = { openedDealer = "" },
                    onShare = { report ->
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, report)
                        }, "Share stock report"))
                    }
                )
            } else {
                when (activePage) {
                    Page.Stock -> StockScreen(
                        dealers, products, entries, stock, due, batchList, stockSheet,
                        entries.filter { it.type != "tin" }.sortedByDescending { it.date }.take(5),
                        onSheetChange = { stockSheet = it },
                        onOpenDealer = { openedDealer = it },
                        onGoTo = { activePage = it },
                        onDownload = {
                            exportLauncher.launch(
                                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_TITLE, "dealer-stock.csv")
                                }
                            )
                        },
                        onDownloadExcelReport = { headers, rows, totals ->
                            pendingExcelData = Triple(headers, rows, totals)
                            excelExportLauncher.launch(
                                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                    putExtra(Intent.EXTRA_TITLE, "Dealer_Stock_Report.xlsx")
                                }
                            )
                        }
                    )
                    Page.Entry -> EntryScreen(
                        dealers, products, entries, stock, price, batchList,
                        onSave = ::saveEntry, onSaveMany = ::saveEntries,
                        onDelete = { confirmDelete = "entry" to it.id }
                    )
                    Page.Dealers -> DealersScreen(
                        dealers, due,
                        editing = editDealer,
                        onEdit = { editDealer = it },
                        onCancelEdit = { editDealer = null },
                        onSave = { saveDealer(it); editDealer = null },
                        onRemove = { confirmDelete = "dealer" to it.id }
                    )
                    Page.Products -> ProductsScreen(
                        products,
                        editing = editProduct,
                        onEdit = { editProduct = it },
                        onCancelEdit = { editProduct = null },
                        onSave = { saveProduct(it); editProduct = null },
                        onRemove = { confirmDelete = "product" to it.id },
                        onMinChange = { p, min -> saveProduct(p.copy(min = min)) }
                    )
                    Page.History -> HistoryScreen(
                        entries, dealers, products, historyType, historyDealer, entryPrice,
                        onTypeChange = { historyType = it },
                        onDealerChange = { historyDealer = it },
                        onDelete = { confirmDelete = "entry" to it.id }
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth()
                    .background(Color(0xF2121924))
                    .border(1.dp, Border, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Page.entries.forEach { page ->
                    val selected = activePage == page
                    val tint by animateColorAsState(
                        if (selected) Teal else Muted,
                        label = "navigation tint"
                    )
                    TextButton(
                        onClick = { activePage = page; openedDealer = ""; error = "" },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = tint,
                            containerColor = if (selected) Color(0xFF1A2C43) else Color.Transparent
                        ),
                        contentPadding = PaddingValues(vertical = 5.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(if (selected) 34.dp else 30.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (selected) Brush.linearGradient(listOf(Teal, Color(0xFF326EB6)))
                                        else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                                    )
                                    .then(
                                        if (selected) Modifier.shadow(
                                            8.dp,
                                            RoundedCornerShape(12.dp),
                                            ambientColor = Teal.copy(alpha = 0.28f),
                                            spotColor = Teal.copy(alpha = 0.38f)
                                        ) else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    pageIcon(page),
                                    color = if (selected) Color.White else Muted,
                                    fontSize = if (selected) 19.sp else 17.sp,
                                    lineHeight = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                page.label,
                                color = tint,
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }

    if (showProfile && user != null) {
        ProfileDialog(
            user = user!!,
            onDismiss = { showProfile = false },
            onSignOut = {
                showProfile = false
                auth?.signOut()
            }
        )
    }

    confirmDelete?.let { (kind, id) ->
        val label = when (kind) {
            "dealer" -> "dealer and all associated stock entries"
            "product" -> "product and all associated stock entries"
            else -> "entry"
        }
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Remove $label?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    when (kind) {
                        "dealer" -> dealers.find { it.id == id }?.let(::deleteDealer)
                        "product" -> products.find { it.id == id }?.let(::deleteProduct)
                        else -> entries.find { it.id == id }?.let(::deleteEntry)
                    }
                    confirmDelete = null
                }) { Text("Remove", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ProfileDialog(
    user: FirebaseUser,
    onDismiss: () -> Unit,
    onSignOut: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Google Account Profile", color = Ink, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (!user.displayName.isNullOrBlank()) {
                    Text("Name: ${user.displayName}", color = Ink, fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                }
                if (!user.email.isNullOrBlank()) {
                    Text("Email: ${user.email}", color = Muted, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                }
                Text("UID: ${user.uid}", color = Muted, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = onSignOut,
                colors = ButtonDefaults.buttonColors(containerColor = Danger)
            ) {
                Text("Sign Out", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Teal)
            }
        },
        containerColor = SurfaceDeep
    )
}

@Composable
private fun SignInScreen(
    signInReady: Boolean,
    error: String,
    loading: Boolean,
    onSignIn: () -> Unit
) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF0F1722), Canvas, Color(0xFF090D14)))
        ),
        contentAlignment = Alignment.Center
    ) {
        GlassCard(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BrandMark(Modifier.size(72.dp))
                Spacer(Modifier.height(18.dp))
                Text("Dealer Stock Tracker", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Inventory, expiry and payments — synced to Gmail / Google.",
                    color = Muted,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onSignIn,
                    enabled = signInReady && !loading,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Teal)
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(if (loading) "Signing in…" else "Continue with Gmail / Google", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                if (error.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(error, color = Danger, fontSize = 13.sp)
                }
                Spacer(Modifier.height(16.dp))
                Text("Your stock data is securely stored under your Google account.", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun BrandMark(modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(14.dp)).background(
            Brush.linearGradient(listOf(Color(0xFF59A8FF), Color(0xFF154A93), Color(0xFF101B2D)))
        ),
        contentAlignment = Alignment.Center
    ) {
        Text("◇", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
        Text(
            "↗",
            Modifier.align(Alignment.TopEnd).padding(end = 6.dp, top = 2.dp),
            color = Color(0xFFBCE0FF),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ColumnScope.PageContent(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(title, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
        Spacer(Modifier.height(8.dp))
        content()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    val cardBorder = accent?.copy(alpha = 0.72f) ?: Border
    val gradient = if (accent == null) {
        listOf(Color(0xE61C2735), Color(0xE610151E), Color(0xE6192533))
    } else {
        listOf(accent.copy(alpha = 0.18f), Color(0xE610151E), Color(0xE6192533))
    }
    Card(
        modifier = modifier.fillMaxWidth().animateContentSize()
            .then(
                if (accent != null) Modifier.shadow(
                    10.dp,
                    shape,
                    ambientColor = accent.copy(alpha = 0.20f),
                    spotColor = accent.copy(alpha = 0.30f)
                ) else Modifier
            )
            .clip(shape)
            .background(Brush.linearGradient(gradient))
            .border(if (accent != null) 1.25.dp else 1.dp, cardBorder, shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 7.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
    }
}

@Composable
private fun ActionButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: String? = null,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Teal, Color(0xFF3989DC), Success))),
        shape = shape,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) Text(icon, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(text, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1)
        }
    }
}

@Composable
private fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (icon != null) Text(icon, color = Teal, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(text, maxLines = 1)
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Teal,
            unfocusedBorderColor = Border,
            focusedTextColor = Ink,
            unfocusedTextColor = Ink,
            focusedLabelColor = Teal,
            unfocusedLabelColor = Muted,
            cursorColor = Teal
        )
    )
}

@Composable
private fun <T> Picker(
    label: String,
    selected: T?,
    options: List<T>,
    text: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(if (selected == null) label else text(selected), maxLines = 1)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(SurfaceDeep)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text(option), color = Ink) },
                    onClick = { onSelected(option); expanded = false }
                )
            }
        }
    }
}

private fun pageIcon(page: Page): String = when (page) {
    Page.Stock -> "▦"
    Page.Entry -> "+"
    Page.Dealers -> "⌂"
    Page.Products -> "◇"
    Page.History -> "↻"
}

@Composable
private fun ColumnScope.StockScreen(
    dealers: List<Dealer>,
    products: List<Product>,
    entries: List<StockEntry>,
    stock: (String, String) -> Int,
    due: (String) -> Double,
    batchList: (String, String) -> List<Pair<Int, String>>,
    stockSheet: Boolean,
    recentEntries: List<StockEntry>,
    onSheetChange: (Boolean) -> Unit,
    onOpenDealer: (String) -> Unit,
    onGoTo: (Page) -> Unit,
    onDownload: () -> Unit,
    onDownloadExcelReport: (List<String>, List<List<String>>, List<String>) -> Unit
) {
    PageContent(if (stockSheet) "All Dealers Stock" else "Stock at Dealers") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (stockSheet) QuietButton("Dealer cards", { onSheetChange(false) }, Modifier.weight(1f))
            else ActionButton("Dealer cards", Modifier.weight(1f)) { onSheetChange(false) }
            if (stockSheet) ActionButton("All-dealers sheet", Modifier.weight(1f)) { onSheetChange(true) }
            else QuietButton("All-dealers sheet", { onSheetChange(true) }, Modifier.weight(1f))
        }
        if (stockSheet) {
            if (dealers.isEmpty() || products.isEmpty()) {
                GlassCard { Text("Add dealers and products first.", color = Muted) }
            } else {
                val headers = listOf("Dealer") + products.map { it.name } + listOf("Total pcs", "Amount due (Rs)")
                val rows = dealers.map { dealer ->
                    val quantities = products.map { stock(dealer.id, it.id) }
                    listOf(dealer.name) + quantities.map(Int::toString) +
                        listOf(quantities.sum().toString(), due(dealer.id).toInt().toString())
                }
                val totals = listOf("TOTAL") + products.indices.map { i ->
                    dealers.sumOf { stock(it.id, products[i].id) }.toString()
                } + listOf(
                    dealers.sumOf { dealer -> products.sumOf { stock(dealer.id, it.id) } }.toString(),
                    dealers.sumOf { due(it.id) }.toInt().toString()
                )
                GlassCard {
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        Column {
                            SheetRow(headers, bold = true)
                            rows.forEachIndexed { index, row ->
                                TextButton(onClick = { onOpenDealer(dealers[index].id) }) {
                                    SheetRow(row)
                                }
                            }
                            SheetRow(totals, bold = true)
                        }
                    }
                }
                ActionButton("Download Excel Report", onClick = {
                    onDownloadExcelReport(headers, rows, totals)
                }, icon = "↓")
            }
        } else {
            val allDue = dealers.sumOf { due(it.id) }
            val allUnits = dealers.sumOf { d -> products.sumOf { stock(d.id, it.id) } }
            val trackedProducts = dealers.flatMap { dealer ->
                products.filter { product ->
                    entries.any { it.dealerId == dealer.id && it.productId == product.id && it.type in listOf("dispatch", "tin") }
                }.map { product -> product to stock(dealer.id, product.id) }
            }
            val lowStockCount = trackedProducts.count { (product, quantity) -> quantity in 1..product.min }
            val outOfStockCount = trackedProducts.count { (_, quantity) -> quantity <= 0 }
            val expiringSoonCount = dealers.sumOf { dealer ->
                products.sumOf { product ->
                    batchList(dealer.id, product.id).count { (_, expiry) ->
                        expiry.isNotBlank() && runCatching {
                            ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(expiry)).toInt() in 0..30
                        }.getOrDefault(false)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Total stock", allUnits.toString(), Modifier.weight(1f), Success, "▦ units across all dealers")
                MetricCard("Low stock", lowStockCount.toString(), Modifier.weight(1f), Amber, "$outOfStockCount out of stock")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Expiring soon", expiringSoonCount.toString(), Modifier.weight(1f), Danger, "Within 30 days")
                MetricCard("Total due", money(allDue), Modifier.weight(1f), Teal, "Outstanding balance")
            }
            GlassCard {
                SectionHeading("Quick actions", "Get things moving")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("+ Entry", Modifier.weight(1f), icon = "↗") { onGoTo(Page.Entry) }
                    QuietButton("+ Shop", { onGoTo(Page.Dealers) }, Modifier.weight(1f), icon = "⌂")
                    QuietButton("+ Product", { onGoTo(Page.Products) }, Modifier.weight(1f), icon = "◇")
                }
            }
            ExpiryAlerts(dealers, products, entries, batchList)
            if (dealers.isEmpty()) {
                GlassCard {
                    SectionHeading("Your stockroom is ready", "Start with a shop and a product.")
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton("＋  Add shop", Modifier.weight(1f)) { onGoTo(Page.Dealers) }
                        QuietButton("＋  Add product", { onGoTo(Page.Products) }, Modifier.weight(1f))
                    }
                }
            }
            dealers.forEach { dealer ->
                val lines = products.map { it to stock(dealer.id, it.id) }.filter { (product, quantity) ->
                    quantity != 0 || entries.any {
                        it.dealerId == dealer.id && it.productId == product.id && it.type in listOf("dispatch", "tin")
                    }
                }
                GlassCard(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { onOpenDealer(dealer.id) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                            IconBadge("⌂", Teal)
                            Column(Modifier.weight(1f)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${dealer.name}  ›", fontWeight = FontWeight.Bold, color = Ink, fontSize = 16.sp)
                                    Text("Due ${money(due(dealer.id))}", color = if (due(dealer.id) > 0) Danger else Muted, fontWeight = FontWeight.SemiBold)
                                }
                                Text("${dealer.city}  ${dealer.phone}", color = Muted, fontSize = 12.sp)
                                if (lines.isEmpty()) Text("No stock yet", color = Muted, fontSize = 12.sp)
                                lines.forEach { (product, quantity) ->
                                    val stateColor = when {
                                        quantity <= 0 -> Danger
                                        quantity <= product.min -> Amber
                                        else -> Success
                                    }
                                    Text(
                                        "●  ${product.name}: ${quantityLabel(product, quantity)}${if (quantity <= 0) "  out of stock" else if (quantity <= product.min) "  low" else ""}",
                                        color = stateColor,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (dealers.isNotEmpty()) {
                GlassCard {
                    SectionHeading("Recent activity", "Your latest stock movements")
                    if (recentEntries.isEmpty()) {
                        EmptyState("No activity yet", "Your saved entries will show here.")
                    } else {
                        recentEntries.forEach { entry ->
                            RecentActivityRow(entry, dealers, products)
                        }
                    }
                }
            }
            QuietButton("↓  Download backup", onDownload, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SheetRow(items: List<String>, bold: Boolean = false) {
    Row {
        items.forEachIndexed { index, item ->
            Text(
                item,
                modifier = Modifier.width(if (index == 0) 132.dp else 108.dp).padding(8.dp),
                color = Ink,
                fontSize = 12.sp,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = Teal,
    supporting: String = ""
) {
    GlassCard(modifier, accent) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(accent))
            Text(label, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(7.dp))
        Text(value, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Ink, lineHeight = 27.sp)
        if (supporting.isNotBlank()) Text(supporting, fontSize = 10.sp, color = Muted, lineHeight = 13.sp)
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String = "") {
    Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    if (subtitle.isNotBlank()) Text(subtitle, color = Muted, fontSize = 12.sp)
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("✦", color = Teal, fontSize = 26.sp)
        Spacer(Modifier.height(6.dp))
        Text(title, color = Ink, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun IconBadge(symbol: String, accent: Color) {
    Box(
        Modifier.size(42.dp).clip(RoundedCornerShape(15.dp))
            .background(accent.copy(alpha = 0.13f))
            .border(1.dp, accent.copy(alpha = 0.24f), RoundedCornerShape(15.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, color = accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RecentActivityRow(entry: StockEntry, dealers: List<Dealer>, products: List<Product>) {
    val accent = when (entry.type) {
        "pay", "return" -> Success
        "manual" -> Amber
        else -> Teal
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                when (entry.type) {
                    "pay" -> "$"
                    "return" -> "↩"
                    "manual" -> "±"
                    "tout" -> "⇄"
                    else -> "↗"
                },
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                when (entry.type) {
                    "pay" -> "Payment received"
                    "manual" -> "Manual stock update"
                    "return" -> "Stock returned"
                    "tout" -> "Stock transferred"
                    else -> "Stock dispatched"
                },
                color = Ink,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Text(
                "${dealers.find { it.id == entry.dealerId }?.name ?: "Shop"} · " +
                    (products.find { it.id == entry.productId }?.name ?: entry.date),
                color = Muted,
                fontSize = 11.sp
            )
        }
        Text(
            if (entry.type == "pay") money(entry.amount) else "${if (entry.type == "tout") "−" else "+"}${entry.quantity}",
            color = accent,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun ExpiryAlerts(
    dealers: List<Dealer>,
    products: List<Product>,
    entries: List<StockEntry>,
    batchList: (String, String) -> List<Pair<Int, String>>
) {
    val today = LocalDate.now()
    val alerts = dealers.flatMap { dealer ->
        products.flatMap { product ->
            batchList(dealer.id, product.id).mapNotNull { (quantity, expiry) ->
                if (expiry.isBlank()) null else {
                    val days = runCatching { ChronoUnit.DAYS.between(today, LocalDate.parse(expiry)).toInt() }.getOrNull()
                    if (days != null && days <= 180) Triple(dealer, product, quantity to days) else null
                }
            }
        }
    }.sortedBy { it.third.second }
    if (alerts.isNotEmpty()) GlassCard {
        Text("⏰ Expiry alert (next 6 months)", fontWeight = FontWeight.Bold)
        alerts.forEach { (dealer, product, pair) ->
            val expiryColor = when {
                pair.second < 0 -> Danger
                pair.second <= 30 -> Amber
                else -> Success
            }
            Text(
                "${product.name} × ${quantityLabel(product, pair.first)} · ${dealer.name} · " +
                    if (pair.second < 0) "Expired ${-pair.second} days ago" else "Expires in ${pair.second} days",
                color = expiryColor,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
    val low = dealers.flatMap { dealer ->
        products.mapNotNull { product ->
            val quantity = entries.filter { it.dealerId == dealer.id && it.productId == product.id }.sumOf {
                when (it.type) {
                    "dispatch", "tin" -> it.quantity
                    "sold", "return", "tout" -> -it.quantity
                    "manual" -> it.delta
                    else -> 0
                }
            }
            if (entries.any { it.dealerId == dealer.id && it.productId == product.id && it.type in listOf("dispatch", "tin") } &&
                quantity <= product.min
            ) "${dealer.name} · ${product.name}: ${quantityLabel(product, quantity)} left (alert at ≤${product.min})" else null
        }
    }
    if (low.isNotEmpty()) GlassCard {
        Text("⚠ Low stock alert", fontWeight = FontWeight.Bold)
        low.forEach { Text(it, color = Amber, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
    }
}

@Composable
private fun ColumnScope.DealerReportScreen(
    dealer: Dealer?,
    dealers: List<Dealer>,
    products: List<Product>,
    entries: List<StockEntry>,
    stock: (String, String) -> Int,
    due: (String) -> Double,
    price: (String, String) -> Double,
    batchList: (String, String) -> List<Pair<Int, String>>,
    onBack: () -> Unit,
    onShare: (String) -> Unit
) {
    PageContent(dealer?.name ?: "Dealer report") {
        QuietButton("‹ All dealers", onBack)
        if (dealer == null) return@PageContent
        Text("${dealer.city}   ${dealer.phone}", color = Muted)
        val totalUnits = products.sumOf { stock(dealer.id, it.id) }
        val stockValue = products.sumOf { stock(dealer.id, it.id) * price(dealer.id, it.id) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Stock value", money(stockValue), Modifier.weight(1f))
            MetricCard("Amount due", money(due(dealer.id)), Modifier.weight(1f))
        }
        GlassCard {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Column {
                    SheetRow(listOf("Product", "Sent", "Sold", "Ret.", "Xfer", "In stock"), true)
                    products.forEach { product ->
                        val matching = entries.filter { it.dealerId == dealer.id && it.productId == product.id }
                        val sent = matching.filter { it.type == "dispatch" }.sumOf { it.quantity }
                        val sold = matching.filter { it.type == "sold" }.sumOf { it.quantity }
                        val returned = matching.filter { it.type == "return" }.sumOf { it.quantity }
                        val transfers = matching.filter { it.type == "tin" }.sumOf { it.quantity } -
                            matching.filter { it.type == "tout" }.sumOf { it.quantity }
                        SheetRow(listOf(product.name, sent.toString(), sold.toString(), returned.toString(),
                            transfers.toString(), stock(dealer.id, product.id).toString()))
                    }
                    SheetRow(listOf("Total", "", "", "", "", totalUnits.toString()), true)
                }
            }
        }
        ExpiryAlerts(listOf(dealer), products, entries, batchList)
        val paid = entries.filter { it.dealerId == dealer.id && it.type == "pay" }.sumOf { it.amount }
        GlassCard { Text("Payments received: ${money(paid)}", color = Muted) }
        ActionButton("Share report", onClick = {
            val report = buildString {
                append("*Stock report - ${dealer.name}* (${LocalDate.now()})\n")
                products.forEach { p ->
                    val sent = entries.filter { it.dealerId == dealer.id && it.productId == p.id && it.type == "dispatch" }
                        .sumOf { it.quantity }
                    val current = stock(dealer.id, p.id)
                    if (sent > 0 || current != 0) append("${p.name}: ${quantityLabel(p, current)} in stock (sent $sent)\n")
                }
                append("\nDue: ${money(due(dealer.id))}")
            }
            onShare(report)
        })
    }
}

@Composable
private fun ColumnScope.EntryScreen(
    dealers: List<Dealer>,
    products: List<Product>,
    entries: List<StockEntry>,
    stock: (String, String) -> Int,
    price: (String, String) -> Double,
    batchList: (String, String) -> List<Pair<Int, String>>,
    onSave: (StockEntry) -> Unit,
    onSaveMany: (List<StockEntry>) -> Unit,
    onDelete: (StockEntry) -> Unit
) {
    var type by remember { mutableStateOf("dispatch") }
    var dealerId by remember { mutableStateOf("") }
    var toDealerId by remember { mutableStateOf("") }
    var productId by remember { mutableStateOf("") }
    var quantityText by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("Pieces") }
    var amountText by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var message by remember { mutableStateOf("") }
    val dealer = dealers.find { it.id == dealerId }
    val product = products.find { it.id == productId }
    val isPayment = type == "pay"
    val isTransfer = type == "transfer"
    val isManual = type == "manual"
    val inputQuantity = quantityText.toIntOrNull()
    val quantity = if (unit == "Boxes" && product != null) inputQuantity?.times(product.box) else inputQuantity
    PageContent("New Entry") {
        GlassCard {
            Picker("Entry type", type, listOf("dispatch", "manual", "return", "transfer", "pay"), {
                when (it) {
                    "dispatch" -> "Dispatch to dealer"
                    "manual" -> "Manual Stock Update"
                    "return" -> "Dealer returned"
                    "transfer" -> "Transfer to another shop"
                    else -> "Payment received"
                }
            }, { type = it; message = "" })
            Picker("Dealer", dealer, dealers, { it.name }, { dealerId = it.id })
            if (isTransfer) Picker("Transfer to shop", dealers.find { it.id == toDealerId },
                dealers.filter { it.id != dealerId }, { it.name }, { toDealerId = it.id })
            if (!isPayment) Picker("Product", product, products, { productName(it) }, {
                productId = it.id
                unit = "Pieces"
            })
            if (isPayment) {
                LabeledField("Amount received (₹)", amountText, { amountText = it }, keyboardType = KeyboardType.Decimal)
            } else {
                LabeledField(
                    if (isManual) "Current counted stock" else "Quantity",
                    quantityText,
                    { quantityText = it },
                    keyboardType = KeyboardType.Number
                )
                if (product?.box ?: 0 > 0 && !isManual) {
                    Picker("Quantity unit", unit, listOf("Pieces", "Boxes"), { it }, { unit = it })
                    Text("1 box = ${product?.box} pieces", color = Muted, fontSize = 12.sp)
                }
                if (isManual) Text(
                    "Set the dealer's actual on-hand quantity. Current stock: ${product?.let { stock(dealerId, it.id) } ?: 0}",
                    color = Muted,
                    fontSize = 12.sp
                )
                if (type == "dispatch") {
                    LabeledField("Expiry date (YYYY-MM-DD, optional)", expiry, { expiry = it })
                }
                if (dealer != null && product != null && !isManual) {
                    Text(
                        "Dealer price: ${money(price(dealer.id, product.id))} per unit (MRP ${money(product.mrp)}, ${dealer.discount}% off)",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
            }
            LabeledField("Entry date (YYYY-MM-DD)", date, { date = it })
            Spacer(Modifier.height(8.dp))
            ActionButton("Save entry", onClick = {
                message = when {
                    dealer == null -> "Add a dealer first."
                    !isPayment && product == null -> "Add a product first."
                    runCatching { LocalDate.parse(date) }.isFailure -> "Enter a valid entry date."
                    isPayment && (amountText.toDoubleOrNull() ?: 0.0) <= 0.0 -> "Enter an amount greater than zero."
                    !isPayment && isManual && inputQuantity == null -> "Enter the counted stock (zero is allowed)."
                    !isPayment && !isManual && (quantity == null || quantity <= 0) -> "Enter a quantity greater than zero."
                    !isPayment && unit == "Boxes" && (product?.box ?: 0) <= 0 -> "Set pieces per box in Products first."
                    expiry.isNotBlank() && type == "dispatch" && runCatching { LocalDate.parse(expiry) }.isFailure ->
                        "Enter a valid expiry date."
                    isTransfer && (dealers.find { it.id == toDealerId } == null || toDealerId == dealerId) ->
                        "Choose two different dealers."
                    isTransfer && (quantity ?: 0) > stock(dealerId, productId) ->
                        "Source dealer only has ${stock(dealerId, productId)} of this product."
                    !isPayment && !isManual && type != "dispatch" && (quantity ?: 0) > stock(dealerId, productId) ->
                        "Dealer only has ${stock(dealerId, productId)} of this product."
                    else -> ""
                }
                if (message.isBlank()) {
                    val stamp = System.currentTimeMillis().toString(36)
                    when {
                        isPayment -> onSave(
                            StockEntry(stamp, "pay", dealerId, amount = amountText.toDouble(), date = date)
                        )
                        isManual -> {
                            val current = stock(dealerId, productId)
                            onSave(StockEntry(stamp, "manual", dealerId, productId, inputQuantity!!,
                                date = date, unitPrice = price(dealerId, productId), delta = inputQuantity - current))
                        }
                        isTransfer -> {
                            val count = quantity!!
                            val outBatches = mutableListOf<Pair<Int, String>>()
                            var remaining = count
                            batchList(dealerId, productId).forEach { batch ->
                                if (remaining > 0) {
                                    val used = minOf(remaining, batch.first)
                                    if (used > 0) outBatches.add(used to batch.second)
                                    remaining -= used
                                }
                            }
                            if (remaining > 0) outBatches.add(remaining to "")
                            val group = stamp
                            val transferEntries = outBatches.mapIndexed { index, batch ->
                                StockEntry("${stamp}i$index", "tin", toDealerId, productId, batch.first,
                                    date = date, unitPrice = price(toDealerId, productId), expiry = batch.second,
                                    groupId = group)
                            }.toMutableList()
                            transferEntries.add(StockEntry("${stamp}o", "tout", dealerId, productId, count, date = date,
                                unitPrice = price(dealerId, productId), toDealerId = toDealerId, groupId = group))
                            onSaveMany(transferEntries)
                        }
                        else -> onSave(StockEntry(stamp, type, dealerId, productId, quantity!!, date = date,
                            unitPrice = price(dealerId, productId), expiry = if (type == "dispatch") expiry else ""))
                    }
                    quantityText = ""
                    amountText = ""
                    expiry = ""
                    message = "Entry saved."
                }
            })
            if (message.isNotBlank()) Text(message, color = if (message.startsWith("Entry saved")) Teal else Danger)
        }
        Text("Recent", fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
        val visibleEntries = entries.filter { it.type != "tin" }.sortedByDescending { it.date }.take(15)
        if (visibleEntries.isEmpty()) {
            GlassCard {
                EmptyState("No entries yet", "Saved dispatches, returns and payments will appear here.")
            }
        }
        visibleEntries.forEach { entry ->
            EntryCard(entry, dealers, products, onDelete)
        }
    }
}

@Composable
private fun EntryCard(
    entry: StockEntry,
    dealers: List<Dealer>,
    products: List<Product>,
    onDelete: (StockEntry) -> Unit
) {
    val label = when (entry.type) {
        "pay" -> "Payment ${money(entry.amount)}"
        "manual" -> "Manual Stock Update · ${if (entry.delta >= 0) "+" else ""}${entry.delta} pcs"
        "dispatch" -> "Dispatched"
        "sold" -> "Sold"
        "return" -> "Returned"
        else -> "Transferred"
    }
    GlassCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (entry.type == "pay" || entry.type == "manual") label
                    else "$label ${entry.quantity} × ${products.find { it.id == entry.productId }?.let(::productName) ?: "?"}",
                    fontWeight = FontWeight.SemiBold
                )
                val who = dealers.find { it.id == entry.dealerId }?.name ?: "?"
                val to = dealers.find { it.id == entry.toDealerId }?.name
                Text("$who${to?.let { " → $it" } ?: ""} · ${entry.date}${entry.expiry.takeIf { it.isNotBlank() }?.let { " · exp $it" } ?: ""}",
                    color = Muted, fontSize = 12.sp)
            }
            TextButton(onClick = { onDelete(entry) }) { Text("Remove", color = Danger) }
        }
    }
}

@Composable
private fun ColumnScope.DealersScreen(
    dealers: List<Dealer>,
    due: (String) -> Double,
    editing: Dealer?,
    onEdit: (Dealer) -> Unit,
    onCancelEdit: () -> Unit,
    onSave: (Dealer) -> Unit,
    onRemove: (Dealer) -> Unit
) {
    var name by remember(editing?.id) { mutableStateOf(editing?.name.orEmpty()) }
    var city by remember(editing?.id) { mutableStateOf(editing?.city.orEmpty()) }
    var phone by remember(editing?.id) { mutableStateOf(editing?.phone.orEmpty()) }
    var discount by remember(editing?.id) { mutableStateOf(editing?.discount?.toString().orEmpty()) }
    var message by remember { mutableStateOf("") }
    PageContent("Dealers") {
        GlassCard {
            Text(if (editing == null) "Add dealer" else "Edit dealer", fontWeight = FontWeight.Bold)
            LabeledField("Dealer name", name, { name = it })
            LabeledField("City / area", city, { city = it })
            LabeledField("Phone", phone, { phone = it }, keyboardType = KeyboardType.Phone)
            LabeledField("Discount % from MRP", discount, { discount = it }, keyboardType = KeyboardType.Decimal)
            ActionButton(if (editing == null) "Add dealer" else "Save dealer", onClick = {
                val rate = discount.toDoubleOrNull() ?: 0.0
                if (name.isBlank()) message = "Enter a dealer name."
                else if (rate !in 0.0..100.0) message = "Discount must be between 0 and 100."
                else {
                    onSave(Dealer(editing?.id ?: System.currentTimeMillis().toString(36),
                        name.trim(), city.trim(), phone.trim(), rate))
                    name = ""; city = ""; phone = ""; discount = ""; message = ""
                }
            })
            if (editing != null) QuietButton("Cancel", onCancelEdit)
            if (message.isNotBlank()) Text(message, color = Danger)
        }
        dealers.forEach { dealer ->
            GlassCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBadge("⌂", Teal)
                        Column(Modifier.weight(1f)) {
                            Text(dealer.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("${dealer.city}  ${dealer.phone}", color = Muted, fontSize = 12.sp)
                            Text("Discount ${dealer.discount}% off MRP · Due ${money(due(dealer.id))}",
                                color = if (due(dealer.id) > 0) Danger else Muted, fontSize = 12.sp)
                        }
                    }
                    Column {
                        TextButton(onClick = { onEdit(dealer) }) { Text("Edit") }
                        TextButton(onClick = { onRemove(dealer) }) { Text("Remove", color = Danger) }
                    }
                }
            }
            if (dealers.isEmpty()) {
                GlassCard {
                    EmptyState("No shops added yet", "Add your first shop to start tracking stock.")
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ProductsScreen(
    products: List<Product>,
    editing: Product?,
    onEdit: (Product) -> Unit,
    onCancelEdit: () -> Unit,
    onSave: (Product) -> Unit,
    onRemove: (Product) -> Unit,
    onMinChange: (Product, Int) -> Unit
) {
    var name by remember(editing?.id) { mutableStateOf(editing?.name.orEmpty()) }
    var mrp by remember(editing?.id) { mutableStateOf(editing?.mrp?.toString().orEmpty()) }
    var weight by remember(editing?.id) { mutableStateOf(editing?.weight?.takeIf { it > 0 }?.toString().orEmpty()) }
    var weightUnit by remember(editing?.id) { mutableStateOf(editing?.weightUnit ?: "g") }
    var box by remember(editing?.id) { mutableStateOf(editing?.box?.takeIf { it > 0 }?.toString().orEmpty()) }
    var min by remember(editing?.id) { mutableStateOf(editing?.min?.toString() ?: "5") }
    var message by remember { mutableStateOf("") }
    PageContent("Products") {
        GlassCard {
            Text(if (editing == null) "Add product" else "Edit product", fontWeight = FontWeight.Bold)
            LabeledField("Product name", name, { name = it })
            LabeledField("MRP (₹ per unit)", mrp, { mrp = it }, keyboardType = KeyboardType.Decimal)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                LabeledField("Weight per unit", weight, { weight = it }, Modifier.weight(1f), KeyboardType.Decimal)
                Picker("Unit", weightUnit, listOf("g", "kg"), { it }, { weightUnit = it }, Modifier.width(112.dp))
            }
            LabeledField("Pieces per box (optional)", box, { box = it }, keyboardType = KeyboardType.Number)
            LabeledField("Low stock alert at", min, { min = it }, keyboardType = KeyboardType.Number)
            ActionButton(if (editing == null) "Add product" else "Save product", onClick = {
                val amount = mrp.toDoubleOrNull()
                val minCount = min.toIntOrNull() ?: 5
                if (name.isBlank() || amount == null || amount <= 0) message = "Enter a name and a valid MRP."
                else if (minCount < 0 || (box.isNotBlank() && (box.toIntOrNull() ?: 0) <= 0)) {
                    message = "Low stock and box counts must be valid non-negative values."
                } else {
                    onSave(Product(editing?.id ?: System.currentTimeMillis().toString(36), name.trim(), amount,
                        weight.toDoubleOrNull() ?: 0.0, weightUnit, box.toIntOrNull() ?: 0, minCount))
                    name = ""; mrp = ""; weight = ""; box = ""; min = "5"; message = ""
                }
            })
            if (editing != null) QuietButton("Cancel", onCancelEdit)
            if (message.isNotBlank()) Text(message, color = Danger)
        }
        products.forEach { product ->
            GlassCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBadge("◇", Teal)
                        Column(Modifier.weight(1f)) {
                            Text(productName(product), fontWeight = FontWeight.Bold)
                            Text(
                                "MRP ${money(product.mrp)} per unit${if (product.box > 0) " · ${product.box} pcs/box" else ""}",
                                color = Muted,
                                fontSize = 12.sp
                            )
                            Text("Low stock alert at ≤ ${product.min}", color = Amber, fontSize = 12.sp)
                        }
                    }
                    Column {
                        TextButton(onClick = { onEdit(product) }) { Text("Edit") }
                        TextButton(onClick = { onRemove(product) }) { Text("Remove", color = Danger) }
                    }
                }
                if (products.isEmpty()) {
                    GlassCard {
                        EmptyState("No products added yet", "Create a product to add stock and record movements.")
                    }
                }
                var minText by remember(product.id, product.min) { mutableStateOf(product.min.toString()) }
                LabeledField("Low stock alert", minText, { minText = it }, keyboardType = KeyboardType.Number)
                TextButton(onClick = {
                    minText.toIntOrNull()?.takeIf { it >= 0 }?.let { onMinChange(product, it) }
                }) { Text("Update alert level") }
            }
        }
    }
}

@Composable
private fun ColumnScope.HistoryScreen(
    entries: List<StockEntry>,
    dealers: List<Dealer>,
    products: List<Product>,
    type: String,
    dealerId: String,
    entryPrice: (StockEntry) -> Double,
    onTypeChange: (String) -> Unit,
    onDealerChange: (String) -> Unit,
    onDelete: (StockEntry) -> Unit
) {
    val filters = listOf("tout", "all", "dispatch", "manual", "return", "pay")
    val typeLabel = { value: String ->
        when (value) {
            "tout" -> "Stock transfers (adjustments)"
            "all" -> "All entries"
            "dispatch" -> "Dispatches"
            "manual" -> "Manual Stock Update"
            "return" -> "Returns"
            else -> "Payments"
        }
    }
    val filtered = entries.filter { it.type != "tin" }
        .filter { type == "all" || it.type == type }
        .filter { dealerId.isBlank() || it.dealerId == dealerId || it.toDealerId == dealerId }
        .sortedByDescending { it.date }
    PageContent("History") {
        GlassCard {
            Picker("Entry type", type, filters, typeLabel, onTypeChange)
            Picker("All shops / dealers", dealerId, listOf("") + dealers.map { it.id },
                { id -> dealers.find { it.id == id }?.name ?: "All shops / dealers" }, onDealerChange)
        }
        Text("${filtered.size} ${if (filtered.size == 1) "entry" else "entries"}", color = Muted, modifier = Modifier.padding(4.dp))
        if (filtered.isEmpty()) GlassCard {
            EmptyState("Nothing to show yet", "Your stock movements and payments will appear here.")
        }
        filtered.forEach { entry ->
            val dealerName = dealers.find { it.id == entry.dealerId }?.name ?: "?"
            val product = products.find { it.id == entry.productId }
            val title = when (entry.type) {
                "pay" -> "Payment received · ${money(entry.amount)}"
                "manual" -> "Manual Stock Update · ${if (entry.delta >= 0) "+" else ""}${entry.delta} pcs"
                "dispatch" -> "Dispatched ${quantityLabel(product, entry.quantity)} × ${product?.let(::productName) ?: "?"}"
                "return" -> "Returned ${quantityLabel(product, entry.quantity)} × ${product?.let(::productName) ?: "?"}"
                "sold" -> "Sold ${quantityLabel(product, entry.quantity)} × ${product?.let(::productName) ?: "?"}"
                else -> "Transferred ${quantityLabel(product, entry.quantity)} × ${product?.let(::productName) ?: "?"}"
            }
            GlassCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, fontWeight = FontWeight.SemiBold)
                        Text("$dealerName${dealers.find { it.id == entry.toDealerId }?.let { " → ${it.name}" } ?: ""} · ${entry.date}" +
                            (if (entry.expiry.isNotBlank()) " · exp ${entry.expiry}" else "") +
                            if (entry.type == "pay") "" else " · ${money(entry.quantity * entryPrice(entry))}",
                            color = Muted, fontSize = 12.sp)
                    }
                    TextButton(onClick = { onDelete(entry) }) { Text("Remove", color = Danger) }
                }
            }
        }
    }
}

private fun productName(product: Product): String =
    product.name + if (product.weight > 0) " (${product.weight} ${product.weightUnit})" else ""

private fun quantityLabel(product: Product?, quantity: Int): String {
    if (product == null || product.box <= 0 || quantity < product.box) return quantity.toString()
    val boxes = quantity / product.box
    val remainder = quantity % product.box
    return "$boxes box${if (boxes == 1) "" else "es"}${if (remainder > 0) " + $remainder" else ""} ($quantity)"
}

private fun money(value: Double): String =
    "₹" + String.format(Locale("en", "IN"), "%,.0f", value)

private fun exportCsv(dealers: List<Dealer>, products: List<Product>, entries: List<StockEntry>): String {
    fun stock(dealerId: String, productId: String) = entries.filter {
        it.dealerId == dealerId && it.productId == productId
    }.sumOf {
        when (it.type) {
            "dispatch", "tin" -> it.quantity
            "sold", "return", "tout" -> -it.quantity
            "manual" -> it.delta
            else -> 0
        }
    }
    fun csv(value: String) = "\"${value.replace("\"", "\"\"")}\""
    return buildString {
        appendLine(listOf("Dealer", "Product", "Stock").joinToString(","))
        dealers.forEach { dealer ->
            products.forEach { product ->
                val quantity = stock(dealer.id, product.id)
                if (quantity != 0) appendLine(
                    listOf(dealer.name, product.name, quantity.toString()).joinToString(",") { csv(it) }
                )
            }
        }
    }
}

private fun escapeXml(str: String): String {
    return str.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}

private fun getColumnName(index: Int): String {
    var num = index
    val result = StringBuilder()
    while (num >= 0) {
        result.insert(0, ('A' + (num % 26)))
        num = (num / 26) - 1
    }
    return result.toString()
}

private fun generateXlsxReport(
    headers: List<String>,
    rows: List<List<String>>,
    totals: List<String>,
    outputStream: OutputStream
) {
    val sharedStrings = mutableListOf<String>()
    val stringToIdMap = mutableMapOf<String, Int>()

    fun getStringId(s: String): Int {
        return stringToIdMap.getOrPut(s) {
            val id = sharedStrings.size
            sharedStrings.add(s)
            id
        }
    }

    val sheetXml = StringBuilder()
    sheetXml.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
    sheetXml.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
    sheetXml.append("<sheetData>")

    var rowIndex = 1

    // Header row
    sheetXml.append("""<row r="$rowIndex">""")
    headers.forEachIndexed { colIdx, header ->
        val cellRef = "${getColumnName(colIdx)}$rowIndex"
        val strId = getStringId(header)
        sheetXml.append("""<c r="$cellRef" t="s" s="1"><v>$strId</v></c>""")
    }
    sheetXml.append("</row>")
    rowIndex++

    // Data rows
    rows.forEach { row ->
        sheetXml.append("""<row r="$rowIndex">""")
        row.forEachIndexed { colIdx, cellValue ->
            val cellRef = "${getColumnName(colIdx)}$rowIndex"
            val numValue = cellValue.toDoubleOrNull()
            if (colIdx > 0 && numValue != null) {
                sheetXml.append("""<c r="$cellRef" s="0"><v>$numValue</v></c>""")
            } else {
                val strId = getStringId(cellValue)
                sheetXml.append("""<c r="$cellRef" t="s" s="0"><v>$strId</v></c>""")
            }
        }
        sheetXml.append("</row>")
        rowIndex++
    }

    // TOTAL row
    sheetXml.append("""<row r="$rowIndex">""")
    totals.forEachIndexed { colIdx, cellValue ->
        val cellRef = "${getColumnName(colIdx)}$rowIndex"
        val numValue = cellValue.toDoubleOrNull()
        if (colIdx > 0 && numValue != null) {
            sheetXml.append("""<c r="$cellRef" s="2"><v>$numValue</v></c>""")
        } else {
            val strId = getStringId(cellValue)
            sheetXml.append("""<c r="$cellRef" t="s" s="2"><v>$strId</v></c>""")
        }
    }
    sheetXml.append("</row>")

    sheetXml.append("</sheetData>")
    sheetXml.append("</worksheet>")

    val sharedStringsXml = StringBuilder()
    sharedStringsXml.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
    sharedStringsXml.append("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${sharedStrings.size}" uniqueCount="${sharedStrings.size}">""")
    sharedStrings.forEach { s ->
        sharedStringsXml.append("<si><t xml:space=\"preserve\">${escapeXml(s)}</t></si>")
    }
    sharedStringsXml.append("</sst>")

    val stylesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><name val="Calibri"/></font>
  </fonts>
  <fills count="2">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
  </fills>
  <borders count="1">
    <border><left/><right/><top/><bottom/><diagonal/></border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="3">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
    <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
  </cellXfs>
</styleSheet>"""

    val contentTypesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
</Types>"""

    val rootRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    val workbookXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Dealer Stock" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>"""

    val workbookRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
</Relationships>"""

    val zip = ZipOutputStream(outputStream)

    fun addZipEntry(path: String, content: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    addZipEntry("[Content_Types].xml", contentTypesXml)
    addZipEntry("_rels/.rels", rootRelsXml)
    addZipEntry("xl/workbook.xml", workbookXml)
    addZipEntry("xl/_rels/workbook.xml.rels", workbookRelsXml)
    addZipEntry("xl/styles.xml", stylesXml)
    addZipEntry("xl/sharedStrings.xml", sharedStringsXml.toString())
    addZipEntry("xl/worksheets/sheet1.xml", sheetXml.toString())

    zip.flush()
    zip.finish()
}
