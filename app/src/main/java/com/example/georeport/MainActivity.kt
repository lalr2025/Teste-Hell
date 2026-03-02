package com.example.georeport

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
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
import org.osmdroid.tileprovider.MapTileIndex
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.json.JSONArray
import org.json.JSONObject

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

@Composable
private fun HomeScreen(
    projects: List<InspectionProject>,
    onOpenProject: (InspectionProject) -> Unit,
    onCreateProject: (name: String, number: String) -> Unit,
    onImportProjectJson: (String) -> Unit
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var number by rememberSaveable { mutableStateOf("") }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()?.let(onImportProjectJson)
        }
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
                    onCreateProject(name.trim(), number.trim())
                    name = ""
                    number = ""
                }
            }) { Text("Criar nova vistoria") }

            Button(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }) {
                Text("Upload JSON")
            }
        }

        HorizontalDivider()
        Text("Vistorias existentes", style = MaterialTheme.typography.titleMedium)
        projects.forEach { project ->
            Button(onClick = { onOpenProject(project) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    Text(project.name, style = MaterialTheme.typography.titleMedium)
                    Text("Número: ${project.number}")
                    Text("Criado em: ${formatDate(project.createdAt)}")
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
            onCreateProject = { name, number ->
                val created = InspectionProject(UUID.randomUUID().toString(), name, number, System.currentTimeMillis())
                projects = (projects + created).sortedByDescending { p -> p.createdAt }
                saveProjects(context, projects)
                currentProject = created
                viewModel.refreshReports(created.id)
                screen = Screen.MAP
            },
            onImportProjectJson = { rawJson ->
                val imported = projectFromJson(rawJson)
                projects = (projects + imported).distinctBy { it.id }.sortedByDescending { p -> p.createdAt }
                saveProjects(context, projects)
            }
        )
    } else if (screen == Screen.MAP) {
        MapScreen(
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
                val file = File(context.cacheDir, "relatorios_georeferenciados.zip")
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
    reports: List<ReportWithPhotos>,
    onNewReport: () -> Unit,
    onBackHome: () -> Unit,
    onMarkerClick: (ReportWithPhotos) -> Unit,
    onRefreshMap: () -> Unit,
    onExportZip: () -> Unit
) {
    val context = LocalContext.current
    var basemap by rememberSaveable { mutableStateOf(BasemapOption.ESTRADAS_ESRI) }
    var showLayers by rememberSaveable { mutableStateOf(false) }
    var expandedClusterKey by rememberSaveable { mutableStateOf<String?>(null) }
    var offlineMode by rememberSaveable { mutableStateOf(false) }
    var autoCentered by rememberSaveable { mutableStateOf(false) }

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
            Button(onClick = { showLayers = true }) { Text("🗺 Camadas") }
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
                        }
                    },
                    update = { mapView ->
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
            }
        }
    }
}

@Composable
private fun ReportDetailDialog(report: ReportWithPhotos, onDismiss: () -> Unit, onEdit: () -> Unit) {
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
    val reportId = initialReport?.id ?: rememberSaveable { UUID.randomUUID().toString() }
    var form by rememberSaveable(reportId) { mutableStateOf(initialReport?.toFormState() ?: ReportFormState()) }
    var latitude by rememberSaveable(reportId) { mutableStateOf(initialReport?.latitude) }
    var longitude by rememberSaveable(reportId) { mutableStateOf(initialReport?.longitude) }
    var altitude by rememberSaveable(reportId) { mutableStateOf(initialReport?.altitude) }
    var currentPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }
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
                viewModel.refreshReports()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

        DropdownField("1- Cultura", form.cultura, viewModel.culturaOptions) { form = form.copy(cultura = it); unsaved = true }
        DropdownField("Tipo de vistoria", form.inspectionType, viewModel.inspectionTypeOptions) { form = form.copy(inspectionType = it); unsaved = true }
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
        DropdownField("18- Cobertura de Palha", form.coberturaPalha, viewModel.coberturaOptions) { form = form.copy(coberturaPalha = it); unsaved = true }
        DropdownField("19- Intensidade Erosão", form.intensidadeErosao, viewModel.erosaoOptions) { form = form.copy(intensidadeErosao = it); unsaved = true }
        TextField("20- Cor do Solo", form.corSolo) { form = form.copy(corSolo = it); unsaved = true }
        DropdownField("21- Textura Solo", form.texturaSolo, viewModel.texturaOptions) { form = form.copy(texturaSolo = it); unsaved = true }
        DropdownField("22- Compactação", form.compactacao, viewModel.compactacaoOptions) { form = form.copy(compactacao = it); unsaved = true }

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

        Button(onClick = {
            val entity = ReportEntity(
                id = reportId,
                projectId = project?.id ?: "SEM_PROJETO",
                projectName = project?.name ?: "Sem projeto",
                projectNumber = project?.number ?: "-",
                projectCreatedAt = project?.createdAt ?: System.currentTimeMillis(),
                createdAt = initialReport?.createdAt ?: System.currentTimeMillis(),
                inspectionType = form.inspectionType,
                latitude = latitude,
                longitude = longitude,
                altitude = altitude,
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
                    createdAt = o.optLong("createdAt", System.currentTimeMillis())
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
    return InspectionProject(
        id = firstReport?.optString("projectId").takeUnless { it.isNullOrBlank() } ?: UUID.randomUUID().toString(),
        name = firstReport?.optString("projectName").takeUnless { it.isNullOrBlank() } ?: "Vistoria importada",
        number = firstReport?.optString("projectNumber").takeUnless { it.isNullOrBlank() } ?: "IMPORT",
        createdAt = firstReport?.optLong("projectCreatedAt") ?: System.currentTimeMillis()
    )
}
