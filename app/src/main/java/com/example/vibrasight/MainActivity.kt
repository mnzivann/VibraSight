/*
OBJETIVO: Gestión de Firebase y Dashboard de usuario (Android Compose).
INTEGRANTES: Jorge Ivan Muñiz Samano, Hazziel Enrique Ramirez Vilches
PROYECTO: VibraSight
*/

package com.example.vibrasight

import android.os.Bundle
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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
// 2. VIEWMODEL (La conexión en tiempo real con Firebase)
// ==============================================================================
class TechGuardianViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()

    // Estados observables para la UI
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
                    val data = snapshot.toObject(SensorData::class.java)
                    if (data != null) _sensores.value = data
                }
            }
    }

    private fun escucharAlertas() {
        // Obtenemos solo las últimas 5 alertas, ordenadas por tiempo (Requisito de la rúbrica)
        db.collection("alertas")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(5)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val lista = snapshot.documents.mapNotNull { it.toObject(Alerta::class.java) }
                    _alertas.value = lista
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

    // Función para enviar la orden al servidor Python
    fun toggleActuador(estado: Boolean) {
        db.collection("actuadores").document("control")
            .set(mapOf("rele_principal" to estado))
    }
}

// ==============================================================================
// 3. INTERFAZ GRÁFICA (Jetpack Compose)
// ==============================================================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                TechGuardianApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechGuardianApp(viewModel: TechGuardianViewModel = viewModel()) {
    // Recolectamos los estados del ViewModel
    val sensores by viewModel.sensores.collectAsState()
    val alertas by viewModel.alertas.collectAsState()
    val actuador by viewModel.actuadorActivo.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TechGuardian Dashboard") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- TARJETA DE CONTROL (Actuador) ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Relé Principal (Puerta)", style = MaterialTheme.typography.titleMedium)
                    Switch(
                        checked = actuador,
                        onCheckedChange = { viewModel.toggleActuador(it) }
                    )
                }
            }

            // --- TARJETA DE SENSORES (Telemetría) ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Estado en Tiempo Real", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("PIR (Movimiento): ${if (sensores.sensor_pir) "🚨 Detectado" else "✅ Despejado"}")
                    Text("Iluminación: ${if (sensores.luz_detectada) "☀️ Luz" else "🌙 Oscuridad"}")
                    Text("Distancia: ${sensores.distancia_cm} cm")
                }
            }

            // --- HISTORIAL DE ALERTAS ---
            Text("Últimas 5 Alertas (IA)", style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(alertas) { alerta ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = alerta.tipo, style = MaterialTheme.typography.titleSmall)
                            Text(text = alerta.descripcion, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}