/*
OBJETIVO: Gestión de Firebase, Transmisión de Video en Vivo y Dashboard de usuario.
INTEGRANTES: Jorge Ivan Muñiz Samano, Hazziel Enrique Ramirez Vilches
PROYECTO: VibraSight
*/

package com.example.vibrasight

import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// ==============================================================================
// 1. MODELOS DE DATOS
// ==============================================================================
data class SensorData(
    val sensor_pir: Boolean = false,
    val luz_detectada: Boolean = false,
    val distancia_cm: Double = 0.0
)

data class Alerta(
    val tipo: String = "",
    val descripcion: String = ""
)

// ==============================================================================
// 2. VIEWMODEL (Conexión en tiempo real con Firestore)
// ==============================================================================
class VibraSightViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()

    private val _sensores = MutableStateFlow(SensorData())
    val sensores: StateFlow<SensorData> = _sensores

    private val _alertas = MutableStateFlow<List<Alerta>>(emptyList())
    val alertas: StateFlow<List<Alerta>> = _alertas

    private val _actuadorActivo = MutableStateFlow(false)
    val actuadorActivo: StateFlow<Boolean> = _actuadorActivo

    init {
        escucharSensores()
        escucharAlertas()
        escucharActuador()
    }

    private fun escucharSensores() {
        db.collection("sensores").document("lecturas_actuales")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    snapshot.toObject(SensorData::class.java)?.let {
                        _sensores.value = it
                    }
                }
            }
    }

    private fun escucharAlertas() {
        db.collection("alertas")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(5)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    _alertas.value = snapshot.documents.mapNotNull { it.toObject(Alerta::class.java) }
                }
            }
    }

    private fun escucharActuador() {
        db.collection("actuadores").document("control")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    _actuadorActivo.value = snapshot.getBoolean("rele_principal") ?: false
                }
            }
    }

    fun toggleActuador(estado: Boolean) {
        db.collection("actuadores").document("control")
            .set(mapOf("rele_principal" to estado))
    }
}

// ==============================================================================
// 3. ACTIVIDAD PRINCIPAL (Suscripción a Notificaciones)
// ==============================================================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Vinculación al Topic de FCM para los avisos reflejados en el Smartwatch
        FirebaseMessaging.getInstance().subscribeToTopic("alertas_vibrasight")
            .addOnCompleteListener { task ->
                val msg = if (task.isSuccessful) "Suscrito a alertas de VibraSight" else "Fallo en suscripción de notificaciones"
                Log.d("FCM_Vibrasight", msg)
            }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VibraSightApp()
                }
            }
        }
    }
}

// ==============================================================================
// 4. INTERFAZ GRÁFICA (Jetpack Compose + LazyColumn Estructurado)
// ==============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VibraSightApp(viewModel: VibraSightViewModel = viewModel()) {
    val sensores by viewModel.sensores.collectAsState()
    val alertas by viewModel.alertas.collectAsState()
    val actuador by viewModel.actuadorActivo.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("VibraSight Dashboard") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- SECCIÓN 1: REPRODUCTOR DE VIDEO EN VIVO (IP WEBCAM) ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Cámara de Seguridad en Vivo",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Componente nativo WebView embebido dentro del flujo de Compose
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    webViewClient = WebViewClient()
                                    settings.javaScriptEnabled = true
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = true

                                    // ⚠️ REEMPLAZA ESTA URL con la IP actual que muestre tu app IP Webcam (mantén el /video al final)
                                    loadUrl("http://192.168.100.131:8080/video")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        )
                    }
                }
            }

            // --- SECCIÓN 2: CONTROL REMOTO DEL ACTUADOR ---
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Relé Principal", style = MaterialTheme.typography.titleMedium)
                            Text(text = "Cerradura magnética / Acceso", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = actuador,
                            onCheckedChange = { viewModel.toggleActuador(it) }
                        )
                    }
                }
            }

            // --- SECCIÓN 3: TELEMETRÍA DE HARDWARE EN TIEMPO REAL ---
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Monitoreo de Sensores", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("PIR (Presencia):")
                            Text(
                                text = if (sensores.sensor_pir) "🚨 Detectado" else "✅ Despejado",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Sensor LDR (Ambiente):")
                            Text(
                                text = if (sensores.luz_detectada) "☀️ Iluminado" else "🌙 Oscuridad total",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Proximidad Estática:")
                            Text(
                                text = "${sensores.distancia_cm} cm",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }

            // --- SECCIÓN 4: ENCABEZADO DE ALERTAS ---
            item {
                Text(
                    text = "Alertas Recientes (Inferencia Lógica)",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // --- SECCIÓN 5: RENDERS DEL HISTORIAL DE ALERTAS ---
            items(alertas) { alerta ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = alerta.tipo,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = alerta.descripcion,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}