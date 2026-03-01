package com.example.georeport

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.georeport.data.AppDatabase
import com.example.georeport.domain.GeoReportRepository
import com.example.georeport.ui.ReportViewModel
import com.example.georeport.ui.ReportViewModelFactory
import com.google.android.gms.location.LocationServices
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = GeoReportRepository(AppDatabase.getInstance(this).appDao())

        setContent {
            MaterialTheme {
                val vm: ReportViewModel = viewModel(factory = ReportViewModelFactory(repository))
                ReportScreen(vm)
            }
        }
    }
}

@Composable
private fun ReportScreen(viewModel: ReportViewModel) {
    val context = LocalContext.current
    val reportId = remember { UUID.randomUUID().toString() }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var currentPhotoPath by remember { mutableStateOf<String?>(null) }
    var locationInfo by remember { mutableStateOf("Sem localização") }

    val selectedAnswers = remember { mutableStateMapOf<String, String>() }
    val photos by viewModel.photos.collectAsState()

    fun refreshLocation() {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocation) {
            locationInfo = "Permissão de localização não concedida"
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            latitude = location?.latitude
            longitude = location?.longitude
            locationInfo = if (latitude != null && longitude != null) {
                "Lat: $latitude, Lon: $longitude"
            } else {
                "GPS indisponível"
            }
        }
    }

    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshLocation()
    }

    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoPath != null) {
            viewModel.savePhoto(reportId, currentPhotoPath!!, latitude, longitude)
        }
    }

    LaunchedEffect(Unit) {
        requestPermissionsLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
        refreshLocation()
        viewModel.loadPhotos(reportId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Relatório georreferenciado", style = MaterialTheme.typography.titleLarge)
        Text(locationInfo)

        Button(onClick = { refreshLocation() }) {
            Text("Atualizar coordenadas")
        }

        viewModel.questions.forEach { question ->
            var value by remember(question.id) { mutableStateOf("") }
            OutlinedTextField(
                value = value,
                onValueChange = {
                    value = it
                    selectedAnswers[question.id] = it
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("${question.title} (digite ID da opção)") },
                supportingText = {
                    Text(question.options.joinToString { opt -> "${opt.id}:${opt.label}" })
                }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val photoFile = createImageFile(context.filesDir)
                currentPhotoPath = photoFile.absolutePath
                val photoUri = FileProvider.getUriForFile(
                    context,
                    "com.example.georeport.fileprovider",
                    photoFile
                )
                takePhotoLauncher.launch(photoUri)
            }) {
                Text("Tirar foto")
            }

            Button(onClick = {
                viewModel.saveReport(reportId, latitude, longitude, selectedAnswers.toMap())
            }) {
                Text("Salvar relatório")
            }
        }

        Text("Fotos capturadas: ${photos.size}")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(photos) { photo ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Arquivo: ${photo.filePath}")
                        Text("Lat: ${photo.latitude} / Lon: ${photo.longitude}")
                    }
                }
            }
        }
    }
}

private fun createImageFile(baseDir: File): File {
    val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    val fileName = "IMG_${formatter.format(Date())}.jpg"
    val picturesDir = File(baseDir, "Pictures").apply { mkdirs() }
    return File(picturesDir, fileName)
}
