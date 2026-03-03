package com.example.georeport

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.widget.Toast
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.app.NotificationCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.georeport.data.AppDatabase
import com.example.georeport.data.ReportEntity
import com.example.georeport.data.ReportWithPhotos
import com.example.georeport.domain.GeoReportRepository
import com.example.georeport.ui.ReportFormState
import com.example.georeport.ui.InspectionProject
import com.example.georeport.ui.ReportViewModel
import com.example.georeport.ui.ReportViewModelFactory
import com.google.android.gms.location.LocationServices
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.json.JSONArray
import org.json.JSONObject

private data class ProjectQuestion(
    val id: String,
    val label: String,
    val type: String,
    val options: List<String>,
    val required: Boolean = false
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().userAgentValue = packageName

        val repository = GeoReportRepository(AppDatabase.getInstance(this).appDao())

        setContent {
            MaterialTheme {
                val vm: ReportViewModel = viewModel(factory = ReportViewModelFactory(repository))
                GeoReportApp(vm)
            }
        }
    }
}

private enum class Screen { HOME, MAP, FORM }

private enum class BasemapOption {
    ESTRADAS_ESRI,
    SATELITE_ESRI,
    TOPOGRAFIA_ESRI
}

private val SUPPORTED_QUESTION_TYPES = listOf("text", "number", "dropdown", "date", "boolean", "photo", "audio")

private const val AUDIO_RECORD_CHANNEL_ID = "audio_recording_channel"
private const val AUDIO_RECORD_NOTIFICATION_ID = 10041

@Composable
private fun HomeScreen(
    projects: List<InspectionProject>,
    onOpenProject: (InspectionProject) -> Unit,
    onCreateProject: (name: String, number: String, questionnaireMode: String, questionsJson: String) -> Unit,
    onImportProject: (android.net.Uri) -> Unit,
    onDeleteProject: (InspectionProject) -> Unit
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var number by rememberSaveable { mutableStateOf("") }
    var customQuestionnaire by rememberSaveable { mutableStateOf(false) }
    var newQuestionLabel by rememberSaveable { mutableStateOf("") }
    var newQuestionType by rememberSaveable { mutableStateOf("text") }
    var newQuestionOptions by rememberSaveable { mutableStateOf("") }
    val customQuestions = remember { mutableStateListOf<ProjectQuestion>() }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportProject(uri)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("🌱 Agro Guaxupé", style = MaterialTheme.typography.headlineMedium)
        Text("Selecione uma vistoria existente, crie nova ou importe JSON.")

        OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Nome da vistoria") })
        OutlinedTextField(number, { number = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Número da vistoria") })

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (name.isNotBlank() && number.isNotBlank()) {
                    val questionsJson = if (customQuestionnaire) {
                        JSONArray().apply {
                            customQuestions.forEach { q ->
                                put(
                                    JSONObject()
                                        .put("id", q.id)
                                        .put("label", q.label)
                                        .put("type", q.type)
                                        .put("required", q.required)
                                        .put("options", JSONArray(q.options))
                                )
                            }
                        }.toString()
                    } else defaultProjectQuestionsJson()

                    onCreateProject(
                        name.trim(),
                        number.trim(),
                        if (customQuestionnaire) "CUSTOM" else "DEFAULT",
                        questionsJson
                    )
                    name = ""
                    number = ""
                    customQuestionnaire = false
                    customQuestions.clear()
                }
            }) { Text("Criar nova vistoria") }

            Button(onClick = { importLauncher.launch(arrayOf("application/zip", "application/json", "text/plain")) }) {
                Text("Upload ZIP/JSON")
            }
        }

        HorizontalDivider()
        Button(onClick = { customQuestionnaire = !customQuestionnaire }) {
            Text(if (customQuestionnaire) "Questionário personalizado ativo" else "Usar questionário personalizado")
        }
        if (customQuestionnaire) {
            Text("Monte até 50 perguntas. Após criar o projeto, não será possível adicionar mais perguntas.")
            OutlinedTextField(newQuestionLabel, { newQuestionLabel = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Pergunta") })
            DropdownField("Tipo de resposta", newQuestionType, SUPPORTED_QUESTION_TYPES) { newQuestionType = it }
            if (newQuestionType == "dropdown") {
                OutlinedTextField(
                    newQuestionOptions,
                    { newQuestionOptions = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Opções separadas por vírgula") }
                )
            }
            Button(onClick = {
                if (customQuestions.size >= 50 || newQuestionLabel.isBlank()) return@Button
                val options = if (newQuestionType == "dropdown") newQuestionOptions.split(",").map { it.trim() }.filter { it.isNotBlank() } else emptyList()
                customQuestions.add(ProjectQuestion(UUID.randomUUID().toString(), newQuestionLabel.trim(), newQuestionType, options))
                newQuestionLabel = ""
                newQuestionOptions = ""
                newQuestionType = "text"
            }) {
                Text("Adicionar pergunta (${customQuestions.size}/50)")
            }
            customQuestions.forEachIndexed { idx, q ->
                Text("${idx + 1}. ${q.label} [${q.type}]")
            }
            HorizontalDivider()
        }

        Text("Vistorias existentes", style = MaterialTheme.typography.titleMedium)
        projects.forEach { project ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onOpenProject(project) }, modifier = Modifier.weight(1f)) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(project.name, style = MaterialTheme.typography.titleMedium)
                        Text("Número: ${project.number}")
                        Text("Criado em: ${formatDate(project.createdAt)}")
                    }
                }
                Button(onClick = { onDeleteProject(project) }) {
                    Text("Excluir")
                }
            }
        }
    }
}

@Composable
private fun GeoReportApp(viewModel: ReportViewModel) {
    val context = LocalContext.current
    val reports by viewModel.reports.collectAsState()
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var selectedReport by remember { mutableStateOf<ReportWithPhotos?>(null) }
    var editingReport by remember { mutableStateOf<ReportEntity?>(null) }
    var currentProject by remember { mutableStateOf<InspectionProject?>(null) }
    var projects by remember { mutableStateOf(loadProjects(context)) }
    var exportingZip by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refreshReports() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, currentProject?.id) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshReports(currentProject?.id)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (screen == Screen.HOME) {
        HomeScreen(
            projects = projects,
            onOpenProject = {
                currentProject = it
                viewModel.refreshReports(it.id)
                screen = Screen.MAP
            },
            onCreateProject = { name, number, questionnaireMode, questionsJson ->
                val created = InspectionProject(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    number = number,
                    createdAt = System.currentTimeMillis(),
                    questionnaireMode = questionnaireMode,
                    questionsJson = questionsJson
                )
                projects = (projects + created).sortedByDescending { p -> p.createdAt }
                saveProjects(context, projects)
                currentProject = created
                viewModel.refreshReports(created.id)
                screen = Screen.MAP
            },
            onImportProject = { uri ->
                viewModel.importDocument(context, uri) { importedProjectId ->
                    if (importedProjectId.isNullOrBlank()) {
                        Toast.makeText(context, "Falha ao importar arquivo", Toast.LENGTH_LONG).show()
                        return@importDocument
                    }
                    val imported = runCatching {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { projectFromJson(it.readText()) }
                    }.getOrNull()?.copy(id = importedProjectId)
                        ?: InspectionProject(importedProjectId, "Vistoria importada", "IMPORT", System.currentTimeMillis(), "IMPORT_NO_QUESTIONS", "[]")
                    projects = (projects + imported).distinctBy { it.id }.sortedByDescending { p -> p.createdAt }
                    saveProjects(context, projects)
                    currentProject = imported
                    viewModel.refreshReports(imported.id)
                    screen = Screen.MAP
                }
            },
            onDeleteProject = { project ->
                viewModel.deleteProject(project, File(context.filesDir, "imports/${project.id}")) {
                    projects = projects.filterNot { it.id == project.id }
                    saveProjects(context, projects)
                    if (currentProject?.id == project.id) {
                        currentProject = null
                        screen = Screen.HOME
                    }
                }
            }
        )
    } else if (screen == Screen.MAP) {
        MapScreen(
            projectId = currentProject?.id,
            reports = reports,
            onNewReport = {
                editingReport = null
                screen = Screen.FORM
            },
            onBackHome = {
                screen = Screen.HOME
            },
            onMarkerClick = { selectedReport = it },
            onRefreshMap = { viewModel.refreshReports(currentProject?.id) },
            onExportZip = {
                if (exportingZip) return@MapScreen
                exportingZip = true
                val projectSuffix = currentProject?.id ?: "sem_projeto"
                val dateSuffix = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val file = File(context.cacheDir, "Relatorio_${projectSuffix}_${dateSuffix}.zip")
                viewModel.exportZip(file, currentProject?.id) { result ->
                    exportingZip = false
                    result.onSuccess {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Exportar ZIP"))
                    }.onFailure {
                        Toast.makeText(context, "Falha ao exportar ZIP: ${it.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )

        selectedReport?.let { report ->
            ReportDetailDialog(
                report = report,
                onDismiss = { selectedReport = null },
                onEdit = {
                    editingReport = report.report
                    selectedReport = null
                    screen = Screen.FORM
                }
            )
        }
    } else {
        FormScreen(
            viewModel = viewModel,
            project = currentProject,
            initialReport = editingReport,
            onFinish = {
                editingReport = null
                viewModel.refreshReports(currentProject?.id)
                screen = Screen.MAP
            }
        )
    }
}

@Composable
private fun MapScreen(
    projectId: String?,
    reports: List<ReportWithPhotos>,
    onNewReport: () -> Unit,
    onBackHome: () -> Unit,
    onMarkerClick: (ReportWithPhotos) -> Unit,
    onRefreshMap: () -> Unit,
    onExportZip: () -> Unit
) {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var basemap by rememberSaveable(projectId) { mutableStateOf(BasemapOption.ESTRADAS_ESRI) }
    var showLayers by rememberSaveable(projectId) { mutableStateOf(false) }
    var expandedClusterKey by rememberSaveable(projectId) { mutableStateOf<String?>(null) }
    var offlineMode by rememberSaveable(projectId) { mutableStateOf(false) }
    var autoCentered by rememberSaveable(projectId) { mutableStateOf(false) }

    val roadsSource = remember { esriTileSource("World_Street_Map") }
    val satelliteSource = remember { esriTileSource("World_Imagery") }
    val topoSource = remember { esriTileSource("World_Topo_Map") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onBackHome) { Text("Início") }
            Button(onClick = onNewReport) { Text("Novo relatório") }
            Button(onClick = onRefreshMap) { Text("Atualizar mapa") }
            Button(onClick = onExportZip) { Text("Baixar ZIP") }
            Text("v${appVersionName(context)}")
            Button(onClick = {
                offlineMode = !offlineMode
                Toast.makeText(
                    context,
                    if (offlineMode) "Modo offline ativo (usa cache local)" else "Modo online ativo",
                    Toast.LENGTH_SHORT
                ).show()
            }) { Text(if (offlineMode) "Offline ON" else "Offline OFF") }
            Button(onClick = {
                Toast.makeText(
                    context,
                    "Área atual marcada para uso offline (cache das tiles visualizadas)",
                    Toast.LENGTH_LONG
                ).show()
            }) { Text("Salvar área offline") }
        }

        if (showLayers) {
            AlertDialog(
                onDismissRequest = { showLayers = false },
                title = { Text("Selecionar mapa base") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { basemap = BasemapOption.ESTRADAS_ESRI; showLayers = false }) {
                            Text("Estradas (ESRI)")
                        }
                        TextButton(onClick = { basemap = BasemapOption.SATELITE_ESRI; showLayers = false }) {
                            Text("Satélite (ESRI)")
                        }
                        TextButton(onClick = { basemap = BasemapOption.TOPOGRAFIA_ESRI; showLayers = false }) {
                            Text("Topografia (ESRI)")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLayers = false }) { Text("Fechar") }
                }
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setMultiTouchControls(true)
                            controller.setZoom(4.5)
                            controller.setCenter(GeoPoint(-14.235, -51.925))
                            mapViewRef = this
                        }
                    },
                    update = { mapView ->
                mapViewRef = mapView
                Configuration.getInstance().userAgentValue = context.packageName

                mapView.setUseDataConnection(!offlineMode)

                when (basemap) {
                    BasemapOption.ESTRADAS_ESRI -> mapView.setTileSource(roadsSource)
                    BasemapOption.SATELITE_ESRI -> mapView.setTileSource(satelliteSource)
                    BasemapOption.TOPOGRAFIA_ESRI -> mapView.setTileSource(topoSource)
                }

                mapView.overlays.removeAll(mapView.overlays.filterIsInstance<Marker>())

                data class MarkerItem(
                    val report: ReportWithPhotos,
                    val lat: Double,
                    val lon: Double
                )

                val points = reports.mapNotNull { rw ->
                    val lat = rw.report.latitude
                    val lon = rw.report.longitude
                    if (lat != null && lon != null) MarkerItem(rw, lat, lon) else null
                }

                if (!autoCentered && points.isNotEmpty()) {
                    val latestPoint = points.maxByOrNull { it.report.report.createdAt }
                    latestPoint?.let {
                        mapView.controller.setCenter(GeoPoint(it.lat, it.lon))
                        mapView.controller.setZoom(16.0)
                        autoCentered = true
                    }
                }

                val zoom = mapView.zoomLevelDouble
                val precisionFactor = when {
                    zoom >= 17 -> 1_000_000
                    zoom >= 15 -> 100_000
                    zoom >= 13 -> 30_000
                    zoom >= 11 -> 10_000
                    zoom >= 9 -> 5_000
                    else -> 2_000
                }

                val clusters = points.groupBy {
                    val latBucket = (it.lat * precisionFactor).roundToInt()
                    val lonBucket = (it.lon * precisionFactor).roundToInt()
                    "$latBucket:$lonBucket"
                }

                clusters.forEach { (clusterKey, items) ->
                    val centerLat = items.map { it.lat }.average()
                    val centerLon = items.map { it.lon }.average()

                    if (items.size == 1) {
                        val item = items.first()
                        val marker = Marker(mapView).apply {
                            position = GeoPoint(item.lat, item.lon)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = "📍 ${item.report.report.cultura}"
                            subDescription = "${item.report.report.cultivar} • ${formatDate(item.report.report.createdAt)}"
                            setOnMarkerClickListener { selected, _ ->
                                onMarkerClick(item.report)
                                selected.showInfoWindow()
                                true
                            }
                        }
                        mapView.overlays.add(marker)
                    } else {
                        if (expandedClusterKey == clusterKey) {
                            val radius = 0.00035
                            items.forEachIndexed { index, item ->
                                val angle = (2.0 * PI * index) / items.size
                                val marker = Marker(mapView).apply {
                                    position = GeoPoint(
                                        centerLat + radius * cos(angle),
                                        centerLon + radius * sin(angle)
                                    )
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    title = "📍 ${item.report.report.cultura}"
                                    subDescription = "${item.report.report.cultivar} • ${formatDate(item.report.report.createdAt)}"
                                    setOnMarkerClickListener { selected, _ ->
                                        onMarkerClick(item.report)
                                        selected.showInfoWindow()
                                        true
                                    }
                                }
                                mapView.overlays.add(marker)
                            }

                            val centerMarker = Marker(mapView).apply {
                                position = GeoPoint(centerLat, centerLon)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                title = "${items.size} relatórios"
                                subDescription = "Toque para recolher"
                                setOnMarkerClickListener { _, _ ->
                                    expandedClusterKey = null
                                    true
                                }
                            }
                            mapView.overlays.add(centerMarker)
                        } else {
                            val clusterMarker = Marker(mapView).apply {
                                position = GeoPoint(centerLat, centerLon)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                title = "${items.size} relatórios"
                                subDescription = "Toque para expandir"
                                setOnMarkerClickListener { selected, _ ->
                                    expandedClusterKey = clusterKey
                                    selected.showInfoWindow()
                                    true
                                }
                            }
                            mapView.overlays.add(clusterMarker)
                        }
                    }
                }

                mapView.invalidate()
                if (expandedClusterKey != null && !clusters.containsKey(expandedClusterKey)) {
                    expandedClusterKey = null
                }
                    }
                )

                Button(
                    onClick = { showLayers = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    Text("🗺 Camadas")
                }

                Button(
                    onClick = {
                        val hasLocation = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED

                        if (!hasLocation) {
                            Toast.makeText(context, "Permita localização para centralizar o mapa", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        fusedLocationClient.lastLocation
                            .addOnSuccessListener { location ->
                                if (location == null) {
                                    Toast.makeText(context, "Localização indisponível", Toast.LENGTH_SHORT).show()
                                    return@addOnSuccessListener
                                }
                                mapViewRef?.controller?.setCenter(GeoPoint(location.latitude, location.longitude))
                                mapViewRef?.controller?.setZoom(17.0)
                                mapViewRef?.invalidate()
                            }
                            .addOnFailureListener {
                                Toast.makeText(context, "Falha ao obter localização atual", Toast.LENGTH_SHORT).show()
                            }
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 68.dp, end = 12.dp)
                ) {
                    Text("📍 Centralizar")
                }
            }
        }
    }
}

@Composable
private fun ReportDetailDialog(report: ReportWithPhotos, onDismiss: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    var playingAudioId by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { mediaPlayer?.stop() }
            runCatching { mediaPlayer?.release() }
            mediaPlayer = null
            playingAudioId = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🌱 ${report.report.cultura}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Cultivar: ${report.report.cultivar}")
                Text("Data/Hora: ${formatDate(report.report.createdAt)}")
                Text("Lat/Lon: ${report.report.latitude} / ${report.report.longitude}")
                Text("Altitude: ${report.report.altitude ?: "-"}")
                HorizontalDivider()
                Text("Sanidade geral: ${report.report.sanidadeGeral}")
                Text("Pragas: ${report.report.presencaPragas} • ${report.report.nomesPragas}")
                Text("Doenças: ${report.report.presencaDoencas} • ${report.report.nomesDoencas}")
                Text("Daninhas: ${report.report.presencaDaninhas} • ${report.report.nomesDaninhas}")
                HorizontalDivider()
                Text("Fotos (${report.photos.size}/5)")
                report.photos.take(5).forEachIndexed { index, photo ->
                    Text("${index + 1}. ${photo.filePath.substringAfterLast('/')}")
                    val bitmap = remember(photo.id) { loadPhotoBitmap(photo) }
                    bitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Foto ${index + 1}",
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                HorizontalDivider()
                Text("Áudios (${report.audios.size})")
                report.audios.forEachIndexed { index, audio ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${index + 1}. ${File(audio.filePath).name}", modifier = Modifier.weight(1f))
                        Button(onClick = {
                            if (playingAudioId == audio.id) {
                                runCatching { mediaPlayer?.stop() }
                                runCatching { mediaPlayer?.release() }
                                mediaPlayer = null
                                playingAudioId = null
                            } else {
                                runCatching { mediaPlayer?.stop() }
                                runCatching { mediaPlayer?.release() }
                                mediaPlayer = null

                                runCatching {
                                    MediaPlayer().apply {
                                        setDataSource(audio.filePath)
                                        setOnCompletionListener {
                                            runCatching { it.release() }
                                            mediaPlayer = null
                                            playingAudioId = null
                                        }
                                        prepare()
                                        start()
                                    }
                                }.onSuccess {
                                    mediaPlayer = it
                                    playingAudioId = audio.id
                                }.onFailure {
                                    Toast.makeText(context, "Falha ao reproduzir áudio", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) {
                            Text(if (playingAudioId == audio.id) "Parar" else "Reproduzir")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
        dismissButton = { TextButton(onClick = onEdit) { Text("Editar relatório") } }
    )
}

@Composable
private fun FormScreen(
    viewModel: ReportViewModel,
    project: InspectionProject?,
    initialReport: ReportEntity? = null,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val isEditing = initialReport != null
    val reportId = rememberSaveable(initialReport?.id, project?.id) {
        initialReport?.id ?: UUID.randomUUID().toString()
    }
    var form by rememberSaveable(reportId) { mutableStateOf(initialReport?.toFormState() ?: ReportFormState()) }
    var latitude by rememberSaveable(reportId) { mutableStateOf(initialReport?.latitude) }
    var longitude by rememberSaveable(reportId) { mutableStateOf(initialReport?.longitude) }
    var altitude by rememberSaveable(reportId) { mutableStateOf(initialReport?.altitude) }
    var currentPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var currentAudioPath by rememberSaveable { mutableStateOf<String?>(null) }
    var audioStartedAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var isRecordingAudio by rememberSaveable { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    val customQuestions = remember(project?.id) { parseProjectQuestions(project?.questionsJson) }
    val customAnswers = remember(reportId) { mutableStateMapOf<String, String>() }
    var unsaved by rememberSaveable { mutableStateOf(false) }
    var askLeave by rememberSaveable { mutableStateOf(false) }
    val photos by viewModel.photos.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    fun refreshLocation() {
        if (isEditing) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener {
                latitude = it?.latitude
                longitude = it?.longitude
                altitude = it?.takeIf { loc -> loc.hasAltitude() }?.altitude
            }
        }
    }

    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (!isEditing) refreshLocation()
    }

    LaunchedEffect(initialReport?.id, project?.id, customQuestions.size) {
        customAnswers.clear()
        val fromSaved = parseAnswersMap(initialReport?.surveyAnswersJson)
        customQuestions.forEach { q ->
            customAnswers[q.id] = fromSaved[q.id].orEmpty()
        }
    }

    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val path = currentPhotoPath
        if (!success) {
            path?.let { runCatching { File(it).delete() } }
            currentPhotoPath = null
            return@rememberLauncherForActivityResult
        }

        if (!path.isNullOrBlank()) {
            val photoFile = File(path)
            if (photoFile.exists()) {
                if (project != null) {
                    viewModel.savePhoto(
                        reportId = reportId,
                        project = project,
                        filePath = path,
                        latitude = latitude,
                        longitude = longitude,
                        inspectionType = form.inspectionType,
                        altitude = altitude
                    )
                }
                unsaved = true
            }
        }
        currentPhotoPath = null
    }

    BackHandler {
        if (unsaved) askLeave = true else onFinish()
    }

    LaunchedEffect(reportId, isEditing) {
        requestPermissionsLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
        if (!isEditing) {
            refreshLocation()
        }
        viewModel.loadPhotos(reportId)
    }

    DisposableEffect(lifecycleOwner, reportId, isEditing) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (!isEditing) {
                    refreshLocation()
                }
                viewModel.loadPhotos(reportId)
                viewModel.refreshReports(project?.id)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { recorder?.stop() }
            runCatching { recorder?.release() }
            recorder = null
            isRecordingAudio = false
            cancelAudioRecordingNotification(context)
        }
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
        Text(if (isEditing) "Editar relatório" else "Novo relatório", style = MaterialTheme.typography.titleLarge)
        Text("Data/Hora: ${formatDate(initialReport?.createdAt ?: System.currentTimeMillis())}")
        Text("Lat/Lon: ${latitude ?: "-"} / ${longitude ?: "-"}")
        Text("Altitude: ${altitude ?: "-"}")

        if (customQuestions.isEmpty()) {
            Text(
                "Projeto importado sem perguntas",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleMedium
            )
        } else {
            customQuestions.forEachIndexed { index, q ->
                when (q.type.lowercase()) {
                    "dropdown" -> {
                        DropdownField(
                            "${index + 1}- ${q.label}",
                            customAnswers[q.id].orEmpty(),
                            q.options
                        ) {
                            customAnswers[q.id] = it
                            unsaved = true
                        }
                    }
                    "number", "date", "text" -> {
                        TextField("${index + 1}- ${q.label}", customAnswers[q.id].orEmpty()) {
                            customAnswers[q.id] = it
                            unsaved = true
                        }
                    }
                    "boolean" -> {
                        DropdownField(
                            "${index + 1}- ${q.label}",
                            customAnswers[q.id].orEmpty(),
                            listOf("Sim", "Não")
                        ) {
                            customAnswers[q.id] = it
                            unsaved = true
                        }
                    }
                    "photo" -> {
                        Text("${index + 1}- ${q.label}")
                    }
                    "audio" -> {
                        Text("${index + 1}- ${q.label}")
                    }
                    else -> {
                        TextField("${index + 1}- ${q.label}", customAnswers[q.id].orEmpty()) {
                            customAnswers[q.id] = it
                            unsaved = true
                        }
                    }
                }
            }
        }

        val supportsMediaInCustomProject = project?.questionnaireMode.equals("CUSTOM", ignoreCase = true) ||
            project?.questionnaireMode.equals("IMPORTED", ignoreCase = true)
        val hasPhotoQuestion = customQuestions.any { it.type.equals("photo", ignoreCase = true) }
        val hasAudioQuestion = customQuestions.any { it.type.equals("audio", ignoreCase = true) }
        val showPhotoCapture = hasPhotoQuestion || supportsMediaInCustomProject
        val showAudioCapture = hasAudioQuestion || supportsMediaInCustomProject

        if (showPhotoCapture) {
        Button(onClick = {
            if (photos.size >= 5) return@Button

            val hasCameraPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasCameraPermission) {
                requestPermissionsLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                return@Button
            }

            val photoFile = createImageFile(context)
            currentPhotoPath = photoFile.absolutePath
            val outputUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            takePhotoLauncher.launch(outputUri)
        }) {
            Text("Adicionar foto (${photos.size}/5)")
        }

        }

        if (showAudioCapture) {
        Button(onClick = {
            if (isRecordingAudio) {
                runCatching { recorder?.stop() }
                runCatching { recorder?.release() }
                recorder = null
                isRecordingAudio = false
                cancelAudioRecordingNotification(context)
                val endedAt = System.currentTimeMillis()
                val startedAt = audioStartedAt ?: endedAt
                currentAudioPath?.let { path ->
                    if (File(path).exists()) {
                        project?.let {
                            val selectedInspectionType = customQuestions.firstOrNull {
                                it.label.contains("tipo de vistoria", ignoreCase = true)
                            }?.let { question -> customAnswers[question.id].orEmpty() }.orEmpty()

                            viewModel.saveAudio(
                                reportId = reportId,
                                project = it,
                                filePath = path,
                                latitude = latitude,
                                longitude = longitude,
                                accuracyMeters = null,
                                startedAt = startedAt,
                                endedAt = endedAt,
                                inspectionType = selectedInspectionType,
                                altitude = altitude
                            )
                        }
                    }
                }
                currentAudioPath = null
                audioStartedAt = null
            } else {
                val hasAudioPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasAudioPermission) {
                    requestPermissionsLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                    return@Button
                }
                val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                if (!canNotify && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestPermissionsLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                }

                val audioFile = createAudioFile(context, project?.id ?: reportId)
                currentAudioPath = audioFile.absolutePath
                runCatching {
                    val mediaRecorder = MediaRecorder(context)
                    mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                    mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    mediaRecorder.setOutputFile(audioFile.absolutePath)
                    mediaRecorder.prepare()
                    mediaRecorder.start()
                    recorder = mediaRecorder
                    audioStartedAt = System.currentTimeMillis()
                    isRecordingAudio = true
                    showAudioRecordingNotification(context)
                }.onFailure {
                    recorder = null
                    currentAudioPath = null
                    isRecordingAudio = false
                    cancelAudioRecordingNotification(context)
                    Toast.makeText(context, "Falha ao iniciar gravação de áudio: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
            unsaved = true
        }) {
            Text(if (isRecordingAudio) "Parar gravação de áudio" else "Gravar áudio georreferenciado")
        }

        }

        Button(onClick = {
            if (customQuestions.isEmpty()) {
                Toast.makeText(context, "Projeto importado sem perguntas", Toast.LENGTH_LONG).show()
                return@Button
            }

            fun findAnswer(vararg labels: String): String {
                val key = customQuestions.firstOrNull { q ->
                    labels.any { label -> q.label.contains(label, ignoreCase = true) }
                }?.id
                return key?.let { customAnswers[it].orEmpty() }.orEmpty()
            }

            fun findNumber(vararg labels: String): Double? = findAnswer(*labels).replace(',', '.').toDoubleOrNull()

            val entity = ReportEntity(
                id = reportId,
                projectId = project?.id ?: "SEM_PROJETO",
                projectName = project?.name ?: "Sem projeto",
                projectNumber = project?.number ?: "-",
                projectCreatedAt = project?.createdAt ?: System.currentTimeMillis(),
                createdAt = initialReport?.createdAt ?: System.currentTimeMillis(),
                inspectionType = findAnswer("tipo de vistoria"),
                surveyAnswersJson = JSONObject().apply {
                    customAnswers.forEach { (key, value) -> put(key, value) }
                }.toString(),
                latitude = latitude,
                longitude = longitude,
                altitude = altitude,
                cultura = findAnswer("cultura"),
                cultivar = findAnswer("cultivar"),
                faseFenologica = findAnswer("fenolog"),
                espacamentoLinha = findNumber("espaçamento linha"),
                espacamentoEntreLinha = findNumber("espaçamento entre", "entre linha"),
                altura = findNumber("altura"),
                comprimentoPivoRaiz = findNumber("comprimento", "raiz"),
                distribuicaoSistemaRadicular = findAnswer("distribuição sistema radicular"),
                sanidadeGeral = findAnswer("sanidade geral"),
                presencaPragas = findAnswer("presença de pragas"),
                nomesPragas = findAnswer("nome(s) da(s) praga"),
                intensidadeDanosPragas = findAnswer("intensidade dos danos (pragas)"),
                presencaDoencas = findAnswer("presença de doenças"),
                nomesDoencas = findAnswer("nome(s) da(s) doença"),
                intensidadeDanosDoencas = findAnswer("intensidade dos danos (doenças)"),
                presencaDaninhas = findAnswer("presença de daninhas"),
                nomesDaninhas = findAnswer("nome(s) da(s) daninha"),
                intensidadeInfestacao = findAnswer("intensidade da infestação"),
                coberturaPalha = findAnswer("cobertura de palha"),
                intensidadeErosao = findAnswer("intensidade eros"),
                corSolo = findAnswer("cor do solo"),
                texturaSolo = findAnswer("textura solo"),
                compactacao = findAnswer("compactação")
            )
            viewModel.saveReport(entity, project?.id)
            unsaved = false
            onFinish()
        }) {
            Text(if (isEditing) "Salvar alterações e voltar ao mapa" else "Salvar relatório e voltar ao mapa")
        }
    }
}

@Composable
private fun DropdownField(label: String, value: String, options: List<String>, onChange: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) }
    )

    Button(onClick = { showDialog = true }) {
        Text(if (value.isBlank()) "Selecionar opção" else "Trocar opção")
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(label) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    options.forEach { option ->
                        TextButton(
                            onClick = {
                                onChange(option)
                                showDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(option)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text("Fechar") }
            }
        )
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

private fun loadPhotoBitmap(photo: com.example.georeport.data.GeoPhotoEntity): android.graphics.Bitmap? {
    return runCatching {
        val file = File(photo.filePath)
        when {
            file.exists() -> decodeSampledBitmap(file.absolutePath, 1280, 1280)
            photo.base64Data.isNotBlank() -> {
                val raw = android.util.Base64.decode(photo.base64Data, android.util.Base64.DEFAULT)
                BitmapFactory.decodeByteArray(raw, 0, raw.size)
            }
            else -> null
        }
    }.getOrNull()
}

private fun decodeSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): android.graphics.Bitmap? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, options)

    options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
    options.inJustDecodeBounds = false
    return BitmapFactory.decodeFile(path, options)
}

private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val (height: Int, width: Int) = options.run { outHeight to outWidth }
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        var halfHeight = height / 2
        var halfWidth = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize.coerceAtLeast(1)
}

private fun createImageFile(context: Context): File {
    val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    val fileName = "IMG_${formatter.format(Date())}.jpg"
    val picturesDir = File(context.filesDir, "Pictures/GeoReport").apply { mkdirs() }
    return File(picturesDir, fileName)
}

private fun createAudioFile(context: Context, projectId: String): File {
    val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    val fileName = "AUD_${formatter.format(Date())}.m4a"
    val audioDir = File(context.filesDir, "audio/$projectId").apply { mkdirs() }
    return File(audioDir, fileName)
}

private fun esriTileSource(layerName: String): OnlineTileSourceBase {
    val base = "https://services.arcgisonline.com/ArcGIS/rest/services/$layerName/MapServer/tile"
    return object : OnlineTileSourceBase(
        "Esri$layerName",
        0,
        19,
        256,
        ".jpg",
        arrayOf(base)
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val zoom = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "$base/$zoom/$y/$x"
        }
    }
}

private fun ReportEntity.toFormState(): ReportFormState = ReportFormState(
    inspectionType = inspectionType,
    cultura = cultura,
    cultivar = cultivar,
    faseFenologica = faseFenologica,
    espacamentoLinha = espacamentoLinha?.toString().orEmpty(),
    espacamentoEntreLinha = espacamentoEntreLinha?.toString().orEmpty(),
    altura = altura?.toString().orEmpty(),
    comprimentoPivoRaiz = comprimentoPivoRaiz?.toString().orEmpty(),
    distribuicaoSistemaRadicular = distribuicaoSistemaRadicular,
    sanidadeGeral = sanidadeGeral,
    presencaPragas = presencaPragas,
    nomesPragas = nomesPragas,
    intensidadeDanosPragas = intensidadeDanosPragas,
    presencaDoencas = presencaDoencas,
    nomesDoencas = nomesDoencas,
    intensidadeDanosDoencas = intensidadeDanosDoencas,
    presencaDaninhas = presencaDaninhas,
    nomesDaninhas = nomesDaninhas,
    intensidadeInfestacao = intensidadeInfestacao,
    coberturaPalha = coberturaPalha,
    intensidadeErosao = intensidadeErosao,
    corSolo = corSolo,
    texturaSolo = texturaSolo,
    compactacao = compactacao
)




private fun showAudioRecordingNotification(context: Context) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            AUDIO_RECORD_CHANNEL_ID,
            "Gravação de áudio",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Exibe quando o áudio georreferenciado está sendo gravado"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    val notification = NotificationCompat.Builder(context, AUDIO_RECORD_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
        .setContentTitle("GeoReport")
        .setContentText("Gravação de áudio em andamento")
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()

    manager.notify(AUDIO_RECORD_NOTIFICATION_ID, notification)
}

private fun cancelAudioRecordingNotification(context: Context) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.cancel(AUDIO_RECORD_NOTIFICATION_ID)
}

private fun appVersionName(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "-"
}.getOrDefault("-")

private fun formatDate(ts: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(ts))

private fun loadProjects(context: Context): List<InspectionProject> {
    val prefs = context.getSharedPreferences("georeport_prefs", Context.MODE_PRIVATE)
    val raw = prefs.getString("projects", "[]") ?: "[]"
    val arr = JSONArray(raw)
    return buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            add(
                InspectionProject(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    name = o.optString("name", "Vistoria"),
                    number = o.optString("number", "-"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    questionnaireMode = o.optString("questionnaireMode", "DEFAULT"),
                    questionsJson = o.optJSONArray("questions")?.toString() ?: "[]"
                )
            )
        }
    }
}

private fun saveProjects(context: Context, projects: List<InspectionProject>) {
    val arr = JSONArray()
    projects.forEach { p ->
        arr.put(
            JSONObject()
                .put("id", p.id)
                .put("name", p.name)
                .put("number", p.number)
                .put("createdAt", p.createdAt)
                .put("questionnaireMode", p.questionnaireMode)
                .put("questions", JSONArray(p.questionsJson))
        )
    }
    context.getSharedPreferences("georeport_prefs", Context.MODE_PRIVATE)
        .edit()
        .putString("projects", arr.toString())
        .apply()
}

private fun projectFromJson(rawJson: String): InspectionProject {
    val root = JSONObject(rawJson)
    val firstReport = root.optJSONArray("reports")?.optJSONObject(0)
    val questionsArray = root.optJSONObject("project")?.optJSONArray("questions")
        ?: root.optJSONArray("questions")
        ?: JSONArray()
    val hasQuestions = questionsArray.length() > 0
    return InspectionProject(
        id = firstReport?.optString("projectId").takeUnless { it.isNullOrBlank() } ?: UUID.randomUUID().toString(),
        name = firstReport?.optString("projectName").takeUnless { it.isNullOrBlank() } ?: "Vistoria importada",
        number = firstReport?.optString("projectNumber").takeUnless { it.isNullOrBlank() } ?: "IMPORT",
        createdAt = firstReport?.optLong("projectCreatedAt") ?: System.currentTimeMillis(),
        questionnaireMode = if (hasQuestions) "IMPORTED" else "IMPORT_NO_QUESTIONS",
        questionsJson = questionsArray.toString()
    )
}

private fun parseProjectQuestions(raw: String?): List<ProjectQuestion> {
    val arr = JSONArray(raw ?: "[]")
    return buildList {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val options = o.optJSONArray("options")
            add(
                ProjectQuestion(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    label = o.optString("label", "Pergunta"),
                    type = o.optString("type", "text").lowercase().let { if (it == "select") "dropdown" else it },
                    options = buildList {
                        if (options != null) {
                            for (j in 0 until options.length()) add(options.optString(j))
                        }
                    },
                    required = o.optBoolean("required", false)
                )
            )
        }
    }
}

private fun defaultProjectQuestionsJson(): String = JSONArray().apply {
    fun add(id: String, label: String, type: String, options: List<String> = emptyList()) {
        put(
            JSONObject()
                .put("id", id)
                .put("label", label)
                .put("type", type)
                .put("required", false)
                .put("options", JSONArray(options))
        )
    }

    add("inspectionType", "Tipo de vistoria", "dropdown", listOf(
        "Monitoramento Pragas/Doenças/Daninhas",
        "Monitoramento Pragas/Doenças",
        "Monitoramento Daninhas",
        "Monitoramento Pragas",
        "Monitoramento Doenças",
        "Aptidão",
        "Amostra Solo",
        "Estimativa Produtividade",
        "Amostra Foliar",
        "Vistoria Preliminar",
        "Vistoria Final"
    ))
    add("cultura", "Cultura", "dropdown", listOf("Abacate", "Aveia", "Café", "Cana", "Laranja", "Limão", "Milho", "Sorgo", "Soja", "Trigo", "Amendoim", "Cobertura Verde"))
    add("cultivar", "Cultivar", "text")
    add("fase", "Fase Fenológica", "text")
    add("esp_linha", "Espaçamento Linha (m)", "number")
    add("esp_entre", "Espaçamento Entre Linha (m)", "number")
    add("altura", "Altura (m)", "number")
    add("raiz", "Comprimento do Pivo (Raiz)", "number")
    add("dist_rad", "Distribuição Sistema Radicular", "dropdown", listOf("Bom", "Regular", "Ruim"))
    add("sanidade", "Sanidade Geral", "dropdown", listOf("Bom", "Regular", "Ruim"))
    add("pragas", "Presença de Pragas", "boolean")
    add("nomes_pragas", "Nome(s) da(s) praga(s)", "text")
    add("int_pragas", "Intensidade dos Danos (pragas)", "dropdown", listOf("Alta", "Moderada", "Baixa"))
    add("doencas", "Presença de Doenças", "boolean")
    add("nomes_doencas", "Nome(s) da(s) Doença(s)", "text")
    add("int_doencas", "Intensidade dos Danos (doenças)", "dropdown", listOf("Alta", "Moderada", "Baixa"))
    add("daninhas", "Presença de Daninhas", "boolean")
    add("nomes_daninhas", "Nome(s) da(s) Daninha(s)", "text")
    add("int_daninhas", "Intensidade da Infestação", "dropdown", listOf("Alta", "Moderada", "Baixa"))
    add("palha", "Cobertura de Palha", "dropdown", listOf("Boa", "Média", "Ruim"))
    add("erosao", "Intensidade Erosão", "dropdown", listOf("Alta", "Média", "Baixa"))
    add("cor_solo", "Cor do Solo", "text")
    add("textura", "Textura Solo", "dropdown", listOf("Arenoso", "Textura Média", "Argiloso"))
    add("compactacao", "Compactação", "dropdown", listOf("Alta", "Moderada", "Baixa", "Nenhuma"))
    add("fotos", "Fotos", "photo")
    add("audio", "Áudio", "audio")
}.toString()

private fun parseAnswersMap(raw: String?): Map<String, String> {
    val obj = JSONObject(raw ?: "{}")
    return buildMap {
        obj.keys().forEach { key ->
            put(key, obj.optString(key, ""))
        }
    }
}
