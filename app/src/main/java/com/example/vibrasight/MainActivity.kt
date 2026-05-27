/*
OBJETIVO: Gestión de Firebase, Transmisión de Video en Vivo de PC y Alerta de Timbre.
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
// 1. MODELOS DE DATOS VIBRASIGHT (Simplificado)
// ==============================================================================
data class SensorData(
    val luz_detectada: Boolean = false,
    val timbre_sonando: Boolean = false
)

data class Alerta(
    val tipo: String = "",
    val descripcion: String = ""
)

// ==============================================================================
// 2. VIEWMODEL (Arquitectura de datos reactivos)
// ==============================================================================
class VibraSightViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()

    private val _sensores = MutableStateFlow(SensorData())
    val sensores: StateFlow<SensorData> = _sensores

    private val _alertas = MutableStateFlow<List<Alerta>>(emptyList())
    val alertas: StateFlow<List<Alerta>> = _alertas

    init {
        escucharSensores()
        escucharAlertas()
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
}

// ==============================================================================
// 3. CLASE PRINCIPAL DE LA ACTIVIDAD
// ==============================================================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Suscripción al canal de notificaciones en la nube para el Smartwatch/Teléfono
        FirebaseMessaging.getInstance().subscribeToTopic("alertas_vibrasight")
            .addOnCompleteListener { task ->
                val msg = if (task.isSuccessful) "Suscrito a alertas de VibraSight" else "Fallo en suscripción de notificaciones"
                Log.d("FCM_Vibrasight", msg)
            }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    VibraSightApp()
                }
            }
        }
    }
}

// ==============================================================================
// 4. INTERFAZ GRÁFICA (Jetpack Compose)
// ==============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VibraSightApp(viewModel: VibraSightViewModel = viewModel()) {
    val sensores by viewModel.sensores.collectAsState()
    val alertas by viewModel.alertas.collectAsState()

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

            // --- BANNER DE ALERTA: TIMBRE ACTIVO ---
            // Solo aparece en pantalla cuando Firebase detecta que timbre_sonando es True
            if (sensores.timbre_sonando) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = " ¡ALGUIEN TOCANDO EL TIMBRE!",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }

            // --- SECCIÓN 1: REPRODUCTOR DE VIDEO (WEBCAM SERVIDA DESDE PC) ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Cámara USB en Vivo (Servidor Local)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    webViewClient = WebViewClient()
                                    settings.javaScriptEnabled = true
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = true

                                    //  REEMPLAZAR CON LA IP LOCAL(Mantén el puerto 5000)
                                    loadUrl("http://192.168.1.20:5000/video")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                    }
                }
            }

            // --- SECCIÓN 2: MONITOREO DE SENSORES EN TIEMPO REAL ---
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Monitoreo de Sensores", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Detector LDR:")
                            Text(text = if (sensores.luz_detectada) "☀️ Día (Luz detectada)" else "🌙 Noche (Oscuridad)")
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Timbre Frontal:")
                            Text(text = if (sensores.timbre_sonando) "🔔 Sonando" else "🔕 Inactivo")
                        }
                    }
                }
            }

            // --- SECCIÓN 3: HISTORIAL DE ALERTAS ---
            item { Text(text = "Historial de Alertas", style = MaterialTheme.typography.titleMedium) }
            items(alertas) { alerta ->
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = alerta.tipo, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(text = alerta.descripcion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
    }
}