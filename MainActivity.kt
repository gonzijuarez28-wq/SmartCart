package com.example.smartcart

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import com.example.smartcart.ui.theme.SmartCartTheme
import androidx.compose.ui.platform.LocalContext

// ==========================================
// ESTRUCTURAS DE DATOS (MODELOS)
// ==========================================
data class Producto(
    var id: Long = 0,
    var nombre: String = "",
    var categoria: String = "Varios",
    var precio: Double = 0.0,
    var notas: String = "",
    var isChecked: Boolean = false,
    var cantidad: Int = 1
)

data class CompraHistorial(
    val fecha: String = "",
    val productos: List<Producto> = emptyList(),
    val total: Double = 0.0
)

data class ListaCompartida(
    val id: Long = 0,
    val titulo: String = "",
    val emisor: String = "",
    val productos: List<Producto> = emptyList()
)

data class UserProfile(
    val uid: String = "",
    val username: String = "",
    val email: String = "",
    val gender: String = "",
    val age: String = ""
)

// COLORES BASE RESTAURADOS EXACTAMENTE COMO AL INICIO
val AquaBackground = Color(0xFF9DF9EF)
val CardBackground = Color(0xFFE0FFFF)
val ItemBackground = Color(0xFFA3D2CA)
val DarkGrayText = Color(0xFF2C3E50)

enum class Screen { Login, Register, Main, EditDetails, History }

class MainActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        enableEdgeToEdge()
        setContent {
            SmartCartTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        SmartCartMainNavigator(auth, db)
                    }
                }
            }
        }
    }
}

@Composable
fun SmartCartMainNavigator(auth: FirebaseAuth, db: FirebaseFirestore) {
    var currentScreen by remember { mutableStateOf(Screen.Login) }
    var currentUserProfile by remember { mutableStateOf<UserProfile?>(null) }

    val listaProductos = remember { mutableStateListOf<Producto>() }
    val historialCompras = remember { mutableStateListOf<CompraHistorial>() }
    val listasCompartidasRecibidas = remember { mutableStateListOf<ListaCompartida>() }
    var productoAEditarIndex by remember { mutableStateOf(-1) }

    LaunchedEffect(Unit) {
        auth.currentUser?.let { user ->
            db.collection("users").document(user.uid).get().addOnSuccessListener { doc ->
                currentUserProfile = doc.toObject(UserProfile::class.java)
                currentScreen = Screen.Main
            }
        }

        if (listaProductos.isEmpty()) {
            listaProductos.add(Producto(1, "Leche", "Lácteos", 3500.0, "", false, 2))
            listaProductos.add(Producto(2, "Pan medio kilo", "Panadería", 1200.0, "", false, 1))
            listaProductos.add(Producto(3, "Galletas pepas trio", "Snacks", 1500.0, "", false, 1))
            listaProductos.add(Producto(4, "Cereal", "Almacén", 2000.0, "", false, 1))
            listaProductos.add(Producto(5, "Yogurt", "Lácteos", 1000.0, "", false, 1))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (currentScreen) {
            Screen.Login -> LoginScreen(
                onLoginSuccess = { profile ->
                    currentUserProfile = profile
                    currentScreen = Screen.Main
                },
                onNavigateToRegister = { currentScreen = Screen.Register },
                auth = auth, db = db
            )
            Screen.Register -> RegisterScreen(
                onRegisterSuccess = { currentScreen = Screen.Login },
                onBack = { currentScreen = Screen.Login },
                auth = auth, db = db
            )
            Screen.Main -> MainAppDrawerStructure(
                username = currentUserProfile?.username ?: "Usuario",
                productos = listaProductos,
                historial = historialCompras,
                listasCompartidas = listasCompartidasRecibidas,
                onNavigateToEdit = { index ->
                    productoAEditarIndex = index
                    currentScreen = Screen.EditDetails
                },
                onNavigateToHistory = { currentScreen = Screen.History },
                onLogout = {
                    auth.signOut()
                    currentUserProfile = null
                    currentScreen = Screen.Login
                }
            )
            Screen.EditDetails -> {
                if (productoAEditarIndex in listaProductos.indices) {
                    EditProductScreen(
                        producto = listaProductos[productoAEditarIndex],
                        onSave = { updatedProduct ->
                            listaProductos[productoAEditarIndex] = updatedProduct
                            currentScreen = Screen.Main
                        }
                    )
                } else {
                    currentScreen = Screen.Main
                }
            }
            Screen.History -> HistoryScreen(historial = historialCompras, onBack = { currentScreen = Screen.Main })
        }
    }
}

@Composable
fun LoginScreen(onLoginSuccess: (UserProfile) -> Unit, onNavigateToRegister: () -> Unit, auth: FirebaseAuth, db: FirebaseFirestore) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFE0FFFF)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = 40.dp)
                .background(AquaBackground, shape = RoundedCornerShape(30.dp))
                .padding(horizontal = 40.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("SMARTCART", fontSize = 32.sp, fontWeight = FontWeight.Light, color = DarkGrayText)
        }

        TextField(
            value = email, onValueChange = { email = it }, placeholder = { Text("Email") },
            colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFFCBD3D6), unfocusedContainerColor = Color(0xFFCBD3D6)),
            shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth(0.8f).padding(bottom = 8.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = password, onValueChange = { password = it }, placeholder = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFFCBD3D6), unfocusedContainerColor = Color(0xFFCBD3D6)),
            shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth(0.8f).padding(bottom = 8.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))

        if (loading) {
            CircularProgressIndicator()
        } else {
            Row(modifier = Modifier.fillMaxWidth(0.8f), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = {
                    if (email.isNotBlank() && password.isNotBlank()) {
                        loading = true
                        auth.signInWithEmailAndPassword(email, password).addOnSuccessListener { result ->
                            db.collection("users").document(result.user?.uid ?: "").get().addOnSuccessListener { doc ->
                                loading = false
                                val profile = doc.toObject(UserProfile::class.java)
                                if (profile != null) onLoginSuccess(profile)
                            }.addOnFailureListener { loading = false }
                        }.addOnFailureListener { loading = false }
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCBD3D6))) {
                    Text("Iniciar Sesión", color = DarkGrayText)
                }
                Button(onClick = onNavigateToRegister, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCBD3D6))) {
                    Text("Registrarse", color = DarkGrayText)
                }
            }
        }
    }
}

@Composable
fun RegisterScreen(onRegisterSuccess: () -> Unit, onBack: () -> Unit, auth: FirebaseAuth, db: FirebaseFirestore) {
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFE0FFFF)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("CREAR CUENTA", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = DarkGrayText)
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Nombre de Usuario") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Contraseña") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(value = gender, onValueChange = { gender = it }, label = { Text("Género") }, modifier = Modifier.weight(1f).padding(end = 4.dp))
            OutlinedTextField(value = age, onValueChange = { age = it }, label = { Text("Edad") }, modifier = Modifier.weight(1f).padding(start = 4.dp))
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (loading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    if (email.isNotBlank() && password.isNotBlank() && username.isNotBlank()) {
                        loading = true
                        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val uid = auth.currentUser?.uid ?: ""
                                val profile = UserProfile(uid, username, email, gender, age)
                                db.collection("users").document(uid).set(profile).addOnSuccessListener {
                                    loading = false
                                    onRegisterSuccess()
                                }.addOnFailureListener { loading = false }
                            } else { loading = false }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ItemBackground)
            ) { Text("REGISTRARSE", color = Color.White) }

            TextButton(onClick = onBack) { Text("Volver al Login", color = DarkGrayText) }
        }
    }
}

@Composable
fun MainAppDrawerStructure(
    username: String,
    productos: MutableList<Producto>,
    historial: MutableList<CompraHistorial>,
    listasCompartidas: MutableList<ListaCompartida>,
    onNavigateToEdit: (Int) -> Unit,
    onNavigateToHistory: () -> Unit,
    onLogout: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var showCompartirDialog by remember { mutableStateOf(false) }
    var showListasCompartidasDialog by remember { mutableStateOf(false) }
    var linkGenerado by remember { mutableStateOf("") }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.fillMaxHeight().width(280.dp).background(Color(0xFFE8FBF9))) {
                Box(
                    modifier = Modifier.fillMaxWidth().background(AquaBackground).clickable { onLogout() }.padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                            Text("👤", fontSize = 26.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(username, color = DarkGrayText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Cerrar sesión", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                val buttonModifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)

                Button(onClick = { productos.clear(); scope.launch { drawerState.close() } }, colors = ButtonDefaults.buttonColors(containerColor = ItemBackground), modifier = buttonModifier) {
                    Text("Crear lista nueva", color = Color.White)
                }
                Button(onClick = { onNavigateToHistory(); scope.launch { drawerState.close() } }, colors = ButtonDefaults.buttonColors(containerColor = ItemBackground), modifier = buttonModifier) {
                    Text("Historial de compras", color = Color.White)
                }
                Button(onClick = { showCompartirDialog = true; scope.launch { drawerState.close() } }, colors = ButtonDefaults.buttonColors(containerColor = ItemBackground), modifier = buttonModifier) {
                    Text("Compartir lista", color = Color.White)
                }
                Button(onClick = { showListasCompartidasDialog = true; scope.launch { drawerState.close() } }, colors = ButtonDefaults.buttonColors(containerColor = ItemBackground), modifier = buttonModifier) {
                    Text("Listas compartidas", color = Color.White)
                }
            }
        }
    ) {
        SmartCartTabsDashboard(productos, historial, { scope.launch { drawerState.open() } }, onNavigateToEdit)
    }

    if (showCompartirDialog) {
        val context = LocalContext.current
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        AlertDialog(
            onDismissRequest = { showCompartirDialog = false; linkGenerado = "" },
            containerColor = Color(0xFFE8FBF9),
            title = { Text("Compartir Lista Actual", fontWeight = FontWeight.Bold, color = DarkGrayText) },
            text = {
                Column {
                    Text("Genera el enlace para poder copiarlo e invitar a otros usuarios:", color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))

                    if (linkGenerado.isEmpty()) {
                        Button(
                            onClick = { linkGenerado = "https://smartcart-app.web.app/invitacion?listId=\${System.currentTimeMillis()}" },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF76EAD7)),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("🔗 Generar Enlace", color = DarkGrayText) }
                    } else {
                        Text("¡Enlace generado exitosamente!", fontWeight = FontWeight.Bold, color = Color(0xFF00BFFF))
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(modifier = Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(8.dp)).padding(8.dp)) {
                            Text(linkGenerado, fontSize = 11.sp, color = Color.DarkGray)
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                val clipData = android.content.ClipData.newPlainText("Enlace Invitación", linkGenerado)
                                clipboardManager.setPrimaryClip(clipData)
                                listasCompartidas.add(ListaCompartida(System.currentTimeMillis(), "Lista Compartida", "Enlace Copiado", productos.toList()))
                                Toast.makeText(context, "¡Enlace copiado al portapapeles!", Toast.LENGTH_SHORT).show()
                                showCompartirDialog = false
                                linkGenerado = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AquaBackground),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Copiar Enlace", color = DarkGrayText, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCompartirDialog = false; linkGenerado = "" }) { Text("Cancelar", color = Color.Red) } }
        )
    }

    if (showListasCompartidasDialog) {
        AlertDialog(
            onDismissRequest = { showListasCompartidasDialog = false },
            containerColor = CardBackground,
            title = { Text("Historial de Listas Compartidas", fontWeight = FontWeight.Bold, color = DarkGrayText) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().height(350.dp)) {
                    Text("Haz clic en cualquier lista para cargarla:", fontSize = 13.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (listasCompartidas.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Aún no has importado ninguna lista.", color = Color.Gray, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(listasCompartidas) { _, itemCompartido ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable {
                                        productos.clear()
                                        productos.addAll(itemCompartido.productos)
                                        showListasCompartidasDialog = false
                                    },
                                    colors = CardDefaults.cardColors(containerColor = ItemBackground)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(itemCompartido.titulo, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text("Origen: \${itemCompartido.emisor}", fontSize = 12.sp, color = DarkGrayText)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showListasCompartidasDialog = false }) { Text("Cerrar", color = Color.Black) } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartCartTabsDashboard(
    productos: MutableList<Producto>,
    historial: MutableList<CompraHistorial>,
    onMenuClick: () -> Unit,
    onNavigateToEdit: (Int) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var showAddInput by remember { mutableStateOf(false) }
    var nuevoProductoNombre by remember { mutableStateOf("") }
    var showMenuForIndex by remember { mutableStateOf<Int?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(AquaBackground)) {
        TopAppBar(
            title = { Text("SmartCart", color = Color.White) },
            navigationIcon = {
                IconButton(onClick = onMenuClick) { Text("☰", fontSize = 24.sp, color = Color.White) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Gray)
        )

        TabRow(selectedTabIndex = selectedTab, containerColor = AquaBackground, contentColor = Color.Black, indicator = {}) {
            val tabs = listOf("Lista de compra", "Productos restantes", "Total de compra")
            tabs.forEachIndexed { index, tabTitle ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = if (selectedTab == index) Color(0xFFC4FFF7) else Color(0xFFD1FFF9)),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text(tabTitle, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                        }
                    }
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxSize().padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                when (selectedTab) {
                    0 -> {
                        if (!showAddInput) {
                            Button(onClick = { showAddInput = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCBD3D6)), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth(0.8f).padding(bottom = 16.dp)) {
                                Text("Ingresar producto", color = DarkGrayText, fontSize = 16.sp)
                            }
                        } else {
                            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextField(
                                    value = nuevoProductoNombre, onValueChange = { nuevoProductoNombre = it }, placeholder = { Text("Ej. Leche") },
                                    modifier = Modifier.weight(1f), colors = TextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = {
                                    if (nuevoProductoNombre.isNotBlank()) {
                                        productos.add(Producto(id = System.currentTimeMillis(), nombre = nuevoProductoNombre))
                                        nuevoProductoNombre = ""
                                        showAddInput = false
                                    }
                                }, colors = ButtonDefaults.buttonColors(containerColor = ItemBackground)) { Text("Guardar", color = Color.White) }
                            }
                        }

                        LazyColumn {
                            itemsIndexed(productos) { index, prod ->
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = prod.isChecked, onCheckedChange = { isChecked ->
                                        productos[index] = prod.copy(isChecked = isChecked)
                                    })
                                    Box(modifier = Modifier.weight(1f).height(50.dp).background(ItemBackground, shape = RoundedCornerShape(25.dp)).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                                        Text(prod.nombre, color = DarkGrayText, fontSize = 16.sp)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box {
                                        Button(
                                            onClick = { showMenuForIndex = index },
                                            colors = ButtonDefaults.buttonColors(containerColor = ItemBackground),
                                            shape = RoundedCornerShape(50),
                                            modifier = Modifier.size(width = 65.dp, height = 38.dp), // Ajustado el ancho a 65.dp
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text(". . .", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) // Puntos separados
                                        }
                                        DropdownMenu(expanded = showMenuForIndex == index, onDismissRequest = { showMenuForIndex = null }, modifier = Modifier.background(Color(0xFFD7EBE9))) {
                                            DropdownMenuItem(text = { Text("Detalles") }, onClick = { showMenuForIndex = null; onNavigateToEdit(index) })
                                            DropdownMenuItem(text = { Text("Eliminar") }, onClick = { showMenuForIndex = null; productos.removeAt(index) })
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        LazyColumn {
                            itemsIndexed(productos) { index, prod ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).graphicsLayer(alpha = if (prod.isChecked) 0.25f else 1.0f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(checked = prod.isChecked, onCheckedChange = { isChecked ->
                                        productos[index] = prod.copy(isChecked = isChecked)
                                    })
                                    Box(modifier = Modifier.weight(1f).height(50.dp).background(ItemBackground, shape = RoundedCornerShape(25.dp)).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                                        Text(prod.nombre, color = DarkGrayText, fontSize = 16.sp)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Button(onClick = { if (prod.cantidad > 1) productos[index] = prod.copy(cantidad = prod.cantidad - 1) }, modifier = Modifier.size(28.dp), contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF76EAD7))) { Text("-", color = Color.DarkGray) }
                                        Text(" " + prod.cantidad + " ", color = DarkGrayText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Button(onClick = { productos[index] = prod.copy(cantidad = prod.cantidad + 1) }, modifier = Modifier.size(28.dp), contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF76EAD7))) { Text("+", color = Color.DarkGray) }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        val totalCalculado = productos.sumOf { it.precio * it.cantidad }
                        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).background(Color(0xFFCBD3D6), shape = RoundedCornerShape(20.dp)).padding(12.dp), contentAlignment = Alignment.CenterStart) {
                            Text("El total es de:   $" + String.format(java.util.Locale.US, "%,.0f", totalCalculado).replace(",", "."), color = DarkGrayText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }

                        LazyColumn(modifier = Modifier.weight(1f)) {
                            itemsIndexed(productos) { _, prod ->
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.weight(1f).height(45.dp).background(Color(0xFFD6ECEB), shape = RoundedCornerShape(22.dp)).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                                            Text(prod.nombre, color = DarkGrayText)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Cant: " + prod.cantidad, fontWeight = FontWeight.Bold, color = DarkGrayText, modifier = Modifier.padding(horizontal = 8.dp))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(modifier = Modifier.padding(start = 12.dp).background(Color(0xFFCBD3D6), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                                        Text(text = "$" + String.format(java.util.Locale.US, "%,.0f", prod.precio).replace(",", "."), fontSize = 14.sp, color = DarkGrayText)
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (productos.isNotEmpty()) {
                                    historial.add(CompraHistorial("Compra Realizada", productos.toList(), totalCalculado))
                                    productos.clear()
                                    selectedTab = 0
                                }
                            }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF76EAD7)), modifier = Modifier.align(Alignment.End).padding(top = 8.dp)
                        ) {
                            Text("Finalizar compra", color = DarkGrayText, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditProductScreen(producto: Producto, onSave: (Producto) -> Unit) {
    var nombre by remember { mutableStateOf(producto.nombre) }
    var categoria by remember { mutableStateOf(producto.categoria) }
    var precioStr by remember { mutableStateOf(producto.precio.toInt().toString()) }
    var notas by remember { mutableStateOf(producto.notas) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AquaBackground)
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBackground, RoundedCornerShape(16.dp))
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Editar Información de Producto", color = DarkGrayText, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // COLUMNA IZQUIERDA: Nombre, Categoría, Precio
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {

                // Campo Nombre
                Text("Nombre", fontSize = 12.sp, color = DarkGrayText, fontWeight = FontWeight.Bold)
                TextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = CardBackground,
                        unfocusedContainerColor = CardBackground,
                        focusedIndicatorColor = Color.Black,
                        unfocusedIndicatorColor = Color.DarkGray
                    ),
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                )

                // Campo Categoría
                Text("Categoría", fontSize = 12.sp, color = DarkGrayText, fontWeight = FontWeight.Bold)
                TextField(
                    value = categoria,
                    onValueChange = { categoria = it },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = CardBackground,
                        unfocusedContainerColor = CardBackground,
                        focusedIndicatorColor = Color.Black,
                        unfocusedIndicatorColor = Color.DarkGray
                    ),
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                )

                // Campo Precio
                Text("Precio", fontSize = 12.sp, color = DarkGrayText, fontWeight = FontWeight.Bold)
                TextField(
                    value = precioStr,
                    onValueChange = { precioStr = it },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = CardBackground,
                        unfocusedContainerColor = CardBackground,
                        focusedIndicatorColor = Color.Black,
                        unfocusedIndicatorColor = Color.DarkGray
                    ),
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                )
            }

            // COLUMNA DERECHA: Notas adicionales
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text("Notas adicionales", fontSize = 12.sp, color = DarkGrayText, fontWeight = FontWeight.Bold)
                TextField(
                    value = notas,
                    onValueChange = { notas = it },
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = CardBackground,
                        unfocusedContainerColor = CardBackground,
                        focusedIndicatorColor = Color.Black,
                        unfocusedIndicatorColor = Color.DarkGray
                    ),
                    shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val precioDouble = precioStr.toDoubleOrNull() ?: 0.0
                onSave(producto.copy(nombre = nombre, categoria = categoria, precio = precioDouble, notas = notas))
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF76EAD7)),
            modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth(0.6f).height(48.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text("Guardar cambios", color = DarkGrayText, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun HistoryScreen(historial: List<CompraHistorial>, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CardBackground)
            .padding(16.dp)
    ) {
        Text("Historial de Compras", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = DarkGrayText)
        Spacer(modifier = Modifier.height(16.dp))

        if (historial.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No hay compras registradas aún.", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(historial) { _, compra ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = ItemBackground)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(compra.fecha, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Total pagado: $" + String.format(java.util.Locale.US, "%,.0f", compra.total).replace(",", "."), color = Color.White)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = AquaBackground)) {
            Text("Volver al Inicio", color = DarkGrayText)
        }
    }
}
