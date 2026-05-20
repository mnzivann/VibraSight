/*
OBJETIVO: Gestión de Firebase y Dashboard de usuario (Android Compose).
INTEGRANTES: Jorge Ivan Muñiz Samano, Hazziel Enrique Ramirez Vilches
PROYECTO: VibraSight
*/

package com.tuempresa.vibrasight // Asegúrate de mantener tu package original

import android.os.Bundle
import android.util.Log
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
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Modelos
data class SensorData(val sensor_pir: Boolean = false, val luz_detectada: Boolean = false, val distancia_cm: Double = 0.0)
data class Alerta(val tipo: String = "", val descripcion: String = "")

// ViewModel actualizado a VibraSight
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
        db.collection("sensores").document("lecturas_actuales").addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                snapshot.toObject(SensorData::class.java)?.let { _sensores.value = it }
            }
        }
    }

    private fun escucharAlertas() {
        db.collection("alertas").orderBy("timestamp", Query.Direction.DESCENDING).limit(5)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    _alertas.value = snapshot.documents.mapNotNull { it.toObject(Alerta::class.java) }
                }
            }
    }

    private fun escucharActuador() {
        db.collection("actuadores").document("control").addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                _actuadorActivo.value = snapshot.getBoolean("rele_principal") ?: false
            }
        }
    }

    fun toggleActuador(estado: Boolean) {
        db.collection("actuadores").document("control").set(mapOf("rele_principal" to estado))
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // SUSCRIPCIÓN A NOTIFICACIONES: Esto conecta el teléfono con los avisos de Python
        FirebaseMessaging.getInstance().subscribeToTopic("alertas_vibrasight")
            .addOnCompleteListener { task ->
                var msg = "Suscrito a alertas de VibraSight"
                if (!task.isSuccessful) msg = "Fallo al suscribirse a notificaciones"
                Log.d("FCM", msg)
            }

        setContent {
            MaterialTheme { VibraSightApp() }
        }
    }
}

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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Tarjeta Actuador
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Relé Principal", style = MaterialTheme.typography.titleMedium)
                    Switch(checked = actuador, onCheckedChange = { viewModel.toggleActuador(it) })
                }
            }
            // Tarjeta Sensores
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Sensores en Tiempo Real", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("PIR: ${if (sensores.sensor_pir) "🚨 Movimiento" else "✅ Despejado"}")
                    Text("Luz: ${if (sensores.luz_detectada) "☀️ Detectada" else "🌙 Oscuridad"}")
                    Text("Distancia: ${sensores.distancia_cm} cm")
                }
            }
            // Tarjeta Alertas
            Text("Últimas Alertas", style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(alertas) { alerta ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(alerta.tipo, style = MaterialTheme.typography.titleSmall)
                            Text(alerta.descripcion, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}