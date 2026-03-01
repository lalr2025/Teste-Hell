package com.example.georeport

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.georeport.data.ReportEntity
import com.example.georeport.data.ReportWithPhotos
import com.example.georeport.domain.GeoReportRepository
import com.example.georeport.ui.ReportFormState
import com.example.georeport.ui.ReportViewModel
import com.example.georeport.ui.ReportViewModelFactory
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
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
                GeoReportApp(vm)
            }
        }
    }
}

private enum class Screen { MAP, FORM }

@Composable
private fun GeoReportApp(viewModel: ReportViewModel) {
    val context = LocalContext.current
    val reports by viewModel.reports.collectAsState()
    var screen by remember { mutableStateOf(Screen.MAP) }
    var selectedReport by remember { mutableStateOf<ReportWithPhotos?>(null) }

    LaunchedEffect(Unit) { viewModel.refreshReports() }

    if (screen == Screen.MAP) {
        MapScreen(
            reports = reports,
            onNewReport = { screen = Screen.FORM },
            onMarkerClick = { selectedReport = it },
            onExportCsv = {
                val csv = viewModel.csvContent()
                val file = File(context.cacheDir, "relatorios_georeferenciados.csv")
                file.writeText(csv)
                val uri = FileProvider.getUriForFile(context, "com.example.georeport.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Exportar CSV"))
            }
        )

        selectedReport?.let { report ->
            ReportDetailDialog(report = report, onDismiss = { selectedReport = null })
        }
    } else {
        FormScreen(
            viewModel = viewModel,
            onFinish = {
                viewModel.refreshReports()
                screen = Screen.MAP
            }
        )
    }
}

@Composable
private fun MapScreen(
    reports: List<ReportWithPhotos>,
    onNewReport: () -> Unit,
    onMarkerClick: (ReportWithPhotos) -> Unit,
    onExportCsv: () -> Unit
) {
    val defaultLatLng = LatLng(-14.235, -51.925)
    val cameraPositionState = remember {
        CameraPositionState(
            position = CameraPosition.fromLatLngZoom(defaultLatLng, 3.8f)
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onNewReport) { Text("Novo relatório") }
            Button(onClick = onExportCsv) { Text("Baixar CSV") }
        }

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState
        ) {
            reports.forEach { rw ->
                val lat = rw.report.latitude
                val lon = rw.report.longitude
                if (lat != null && lon != null) {
                    Marker(
                        state = MarkerState(position = LatLng(lat, lon)),
                        title = "📍 ${rw.report.cultura}",
                        snippet = "${rw.report.cultivar} • ${formatDate(rw.report.createdAt)}",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
                        onClick = {
                            onMarkerClick(rw)
                            true
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportDetailDialog(report: ReportWithPhotos, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🌱 ${report.report.cultura}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Cultivar: ${report.report.cultivar}")
                Text("Data/Hora: ${formatDate(report.report.createdAt)}")
                Text("Lat/Lon: ${report.report.latitude} / ${report.report.longitude}")
                HorizontalDivider()
                Text("Sanidade geral: ${report.report.sanidadeGeral}")
                Text("Pragas: ${report.report.presencaPragas} • ${report.report.nomesPragas}")
                Text("Doenças: ${report.report.presencaDoencas} • ${report.report.nomesDoencas}")
                Text("Daninhas: ${report.report.presencaDaninhas} • ${report.report.nomesDaninhas}")
                HorizontalDivider()
                Text("Fotos (${report.photos.size}/5)")
                report.photos.take(5).forEachIndexed { index, photo ->
                    Text("${index + 1}. ${photo.filePath.substringAfterLast('/')}")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}

@Composable
private fun FormScreen(viewModel: ReportViewModel, onFinish: () -> Unit) {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val reportId = remember { UUID.randomUUID().toString() }
    var form by remember { mutableStateOf(ReportFormState()) }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var currentPhotoPath by remember { mutableStateOf<String?>(null) }
    var unsaved by remember { mutableStateOf(false) }
    var askLeave by remember { mutableStateOf(false) }
    val photos by viewModel.photos.collectAsState()

    fun refreshLocation() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener {
                latitude = it?.latitude
                longitude = it?.longitude
            }
        }
    }

    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshLocation() }

    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoPath != null) {
            viewModel.savePhoto(reportId, currentPhotoPath!!, latitude, longitude)
            unsaved = true
        }
    }

    BackHandler {
        if (unsaved) askLeave = true else onFinish()
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

    if (askLeave) {
        AlertDialog(
            onDismissRequest = { askLeave = false },
            title = { Text("Atenção") },
            text = { Text("Você possui alterações não salvas. Deseja voltar mesmo assim?") },
            confirmButton = { TextButton(onClick = onFinish) { Text("Voltar") } },
            dismissButton = { TextButton(onClick = { askLeave = false }) { Text("Cancelar") } }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Novo relatório", style = MaterialTheme.typography.titleLarge)
        Text("Data/Hora: ${formatDate(System.currentTimeMillis())}")
        Text("Lat/Lon atual: ${latitude ?: "-"} / ${longitude ?: "-"}")

        DropdownField("1- Cultura", form.cultura, viewModel.culturaOptions) { form = form.copy(cultura = it); unsaved = true }
        TextField("2- Cultivar", form.cultivar) { form = form.copy(cultivar = it); unsaved = true }
        TextField("3- Fase Fenológica", form.faseFenologica) { form = form.copy(faseFenologica = it); unsaved = true }
        NumberField("4- Espaçamento Linha (m)", form.espacamentoLinha) { form = form.copy(espacamentoLinha = it); unsaved = true }
        NumberField("5- Espaçamento Entre Linha (m)", form.espacamentoEntreLinha) { form = form.copy(espacamentoEntreLinha = it); unsaved = true }
        NumberField("6- Altura (m)", form.altura) { form = form.copy(altura = it); unsaved = true }
        NumberField("7- Comprimento do Pivo (Raiz)", form.comprimentoPivoRaiz) { form = form.copy(comprimentoPivoRaiz = it); unsaved = true }
        DropdownField("8- Distribuição Sistema Radicular", form.distribuicaoSistemaRadicular, viewModel.qualidadeOptions) { form = form.copy(distribuicaoSistemaRadicular = it); unsaved = true }
        DropdownField("9- Sanidade Geral", form.sanidadeGeral, viewModel.qualidadeOptions) { form = form.copy(sanidadeGeral = it); unsaved = true }
        DropdownField("10- Presença de Pragas", form.presencaPragas, viewModel.simNaoOptions) { form = form.copy(presencaPragas = it); unsaved = true }
        TextField("11- Nome(s) da(s) praga(s)", form.nomesPragas) { form = form.copy(nomesPragas = it); unsaved = true }
        DropdownField("12- Intensidade dos Danos (pragas)", form.intensidadeDanosPragas, viewModel.intensidadeOptions) { form = form.copy(intensidadeDanosPragas = it); unsaved = true }
        DropdownField("13- Presença de Doenças", form.presencaDoencas, viewModel.simNaoOptions) { form = form.copy(presencaDoencas = it); unsaved = true }
        TextField("14- Nome(s) da(s) Doença(s)", form.nomesDoencas) { form = form.copy(nomesDoencas = it); unsaved = true }
        DropdownField("15- Intensidade dos Danos (doenças)", form.intensidadeDanosDoencas, viewModel.intensidadeOptions) { form = form.copy(intensidadeDanosDoencas = it); unsaved = true }
        DropdownField("16- Presença de Daninhas", form.presencaDaninhas, viewModel.simNaoOptions) { form = form.copy(presencaDaninhas = it); unsaved = true }
        TextField("17- Nome(s) da(s) Daninha(s)", form.nomesDaninhas) { form = form.copy(nomesDaninhas = it); unsaved = true }
        DropdownField("18- Intensidade da Infestação", form.intensidadeInfestacao, viewModel.intensidadeOptions) { form = form.copy(intensidadeInfestacao = it); unsaved = true }
        DropdownField("19- Cobertura de Palha", form.coberturaPalha, viewModel.coberturaOptions) { form = form.copy(coberturaPalha = it); unsaved = true }
        DropdownField("19- Intensidade Erosão", form.intensidadeErosao, viewModel.erosaoOptions) { form = form.copy(intensidadeErosao = it); unsaved = true }
        TextField("20- Cor do Solo", form.corSolo) { form = form.copy(corSolo = it); unsaved = true }
        DropdownField("21- Textura Solo", form.texturaSolo, viewModel.texturaOptions) { form = form.copy(texturaSolo = it); unsaved = true }
        DropdownField("22- Compactação", form.compactacao, viewModel.compactacaoOptions) { form = form.copy(compactacao = it); unsaved = true }

        Button(onClick = {
            if (photos.size >= 5) return@Button
            val photoFile = createImageFile(context.filesDir)
            currentPhotoPath = photoFile.absolutePath
            val photoUri = FileProvider.getUriForFile(context, "com.example.georeport.fileprovider", photoFile)
            takePhotoLauncher.launch(photoUri)
        }) {
            Text("Adicionar foto (${photos.size}/5)")
        }

        Button(onClick = {
            val entity = ReportEntity(
                id = reportId,
                createdAt = System.currentTimeMillis(),
                latitude = latitude,
                longitude = longitude,
                cultura = form.cultura,
                cultivar = form.cultivar,
                faseFenologica = form.faseFenologica,
                espacamentoLinha = form.espacamentoLinha.toDoubleOrNull(),
                espacamentoEntreLinha = form.espacamentoEntreLinha.toDoubleOrNull(),
                altura = form.altura.toDoubleOrNull(),
                comprimentoPivoRaiz = form.comprimentoPivoRaiz.toDoubleOrNull(),
                distribuicaoSistemaRadicular = form.distribuicaoSistemaRadicular,
                sanidadeGeral = form.sanidadeGeral,
                presencaPragas = form.presencaPragas,
                nomesPragas = form.nomesPragas,
                intensidadeDanosPragas = form.intensidadeDanosPragas,
                presencaDoencas = form.presencaDoencas,
                nomesDoencas = form.nomesDoencas,
                intensidadeDanosDoencas = form.intensidadeDanosDoencas,
                presencaDaninhas = form.presencaDaninhas,
                nomesDaninhas = form.nomesDaninhas,
                intensidadeInfestacao = form.intensidadeInfestacao,
                coberturaPalha = form.coberturaPalha,
                intensidadeErosao = form.intensidadeErosao,
                corSolo = form.corSolo,
                texturaSolo = form.texturaSolo,
                compactacao = form.compactacao
            )
            viewModel.saveReport(entity)
            unsaved = false
            onFinish()
        }) {
            Text("Salvar relatório e voltar ao mapa")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(label: String, value: String, options: List<String>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = {
                    onChange(option)
                    expanded = false
                })
            }
        }
    }
}

@Composable
private fun TextField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(), label = { Text(label) })
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(), label = { Text(label) })
}

private fun createImageFile(baseDir: File): File {
    val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    val fileName = "IMG_${formatter.format(Date())}.jpg"
    val picturesDir = File(baseDir, "Pictures").apply { mkdirs() }
    return File(picturesDir, fileName)
}

private fun formatDate(ts: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(ts))
