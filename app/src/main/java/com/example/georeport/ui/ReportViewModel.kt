package com.example.georeport.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.os.Parcelable
import com.example.georeport.data.GeoPhotoEntity
import com.example.georeport.data.GeoAudioEntity
import com.example.georeport.data.ReportEntity
import com.example.georeport.data.ReportWithPhotos
import com.example.georeport.domain.GeoReportRepository
import kotlinx.parcelize.Parcelize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import android.content.Context
import android.net.Uri

data class InspectionProject(
    val id: String,
    val name: String,
    val number: String,
    val createdAt: Long,
    val questionnaireMode: String = "DEFAULT",
    val questionsJson: String = "[]"
)

data class DropOption(val label: String)

@Parcelize
data class ReportFormState(
    val inspectionType: String = "",
    val cultura: String = "",
    val cultivar: String = "",
    val faseFenologica: String = "",
    val espacamentoLinha: String = "",
    val espacamentoEntreLinha: String = "",
    val altura: String = "",
    val comprimentoPivoRaiz: String = "",
    val distribuicaoSistemaRadicular: String = "",
    val sanidadeGeral: String = "",
    val presencaPragas: String = "",
    val nomesPragas: String = "",
    val intensidadeDanosPragas: String = "",
    val presencaDoencas: String = "",
    val nomesDoencas: String = "",
    val intensidadeDanosDoencas: String = "",
    val presencaDaninhas: String = "",
    val nomesDaninhas: String = "",
    val intensidadeInfestacao: String = "",
    val coberturaPalha: String = "",
    val intensidadeErosao: String = "",
    val corSolo: String = "",
    val texturaSolo: String = "",
    val compactacao: String = ""
) : Parcelable

class ReportViewModel(
    private val repository: GeoReportRepository
) : ViewModel() {

    private val _photos = MutableStateFlow<List<GeoPhotoEntity>>(emptyList())
    val photos: StateFlow<List<GeoPhotoEntity>> = _photos.asStateFlow()

    private val _reports = MutableStateFlow<List<ReportWithPhotos>>(emptyList())
    val reports: StateFlow<List<ReportWithPhotos>> = _reports.asStateFlow()

    private val _audios = MutableStateFlow<List<GeoAudioEntity>>(emptyList())
    val audios: StateFlow<List<GeoAudioEntity>> = _audios.asStateFlow()

    val culturaOptions = listOf(
        "Abacate", "Aveia", "Café", "Cana", "Laranja", "Limão", "Milho", "Sorgo", "Soja", "Trigo", "Amendoim", "Cobertura Verde"
    )

    val qualidadeOptions = listOf("Bom", "Regular", "Ruim")
    val simNaoOptions = listOf("Sim", "Não")
    val intensidadeOptions = listOf("Alta", "Moderada", "Baixa")
    val coberturaOptions = listOf("Boa", "Média", "Ruim")
    val erosaoOptions = listOf("Alta", "Média", "Baixa")
    val texturaOptions = listOf("Arenoso", "Textura Média", "Argiloso")
    val compactacaoOptions = listOf("Alta", "Moderada", "Baixa", "Nenhuma")
    val inspectionTypeOptions = listOf(
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
    )

    fun loadPhotos(reportId: String) {
        viewModelScope.launch {
            _photos.value = repository.photosByReport(reportId)
            _audios.value = repository.audiosByReport(reportId)
        }
    }

    fun refreshReports(projectId: String? = null) {
        viewModelScope.launch {
            _reports.value = repository.listReports(projectId)
        }
    }

    fun saveReport(report: ReportEntity) {
        viewModelScope.launch {
            repository.saveReport(report)
            refreshReports()
        }
    }

    fun savePhoto(
        reportId: String,
        project: InspectionProject,
        filePath: String,
        latitude: Double?,
        longitude: Double?,
        inspectionType: String,
        altitude: Double?
    ) {
        viewModelScope.launch {
            repository.savePhoto(
                reportId = reportId,
                projectId = project.id,
                projectName = project.name,
                projectNumber = project.number,
                projectCreatedAt = project.createdAt,
                filePath = filePath,
                latitude = latitude,
                longitude = longitude,
                inspectionType = inspectionType,
                altitude = altitude
            )
            _photos.value = repository.photosByReport(reportId)
            refreshReports(project.id)
        }
    }

    fun exportZip(file: File, projectId: String?, onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { repository.exportReportsZip(file, projectId) }
            onDone(result)
        }
    }

    fun saveAudio(
        reportId: String,
        filePath: String,
        latitude: Double?,
        longitude: Double?,
        accuracyMeters: Double?,
        startedAt: Long,
        endedAt: Long
    ) {
        viewModelScope.launch {
            repository.saveAudio(reportId, filePath, latitude, longitude, accuracyMeters, startedAt, endedAt)
            _audios.value = repository.audiosByReport(reportId)
        }
    }

    fun deleteProject(project: InspectionProject, projectMediaDir: File?, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteProject(project.id, projectMediaDir)
            refreshReports(null)
            onDone()
        }
    }

    fun importDocument(context: Context, uri: Uri, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val name = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val i = c.getColumnIndex("_display_name")
                    if (c.moveToFirst() && i >= 0) c.getString(i) else "import"
                } ?: "import"
                if (name.lowercase().endsWith(".zip")) {
                    val tempZip = File(context.cacheDir, "import_${System.currentTimeMillis()}.zip")
                    context.contentResolver.openInputStream(uri)!!.use { input ->
                        tempZip.outputStream().use { input.copyTo(it) }
                    }
                    val outDir = File(context.filesDir, "imports/${System.currentTimeMillis()}")
                    repository.importPortableZip(tempZip, outDir)
                } else {
                    val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                    repository.importPortableJson(raw, null)
                }
            }.getOrNull()
            onDone(result)
        }
    }

    fun csvContent(): String {
        val header = listOf(
            "id", "projectId", "projectName", "projectNumber", "projectCreatedAt", "createdAt", "inspectionType", "latitude", "longitude", "cultura", "cultivar", "faseFenologica",
            "espacamentoLinha", "espacamentoEntreLinha", "altura", "comprimentoPivoRaiz",
            "distribuicaoSistemaRadicular", "sanidadeGeral", "presencaPragas", "nomesPragas", "intensidadeDanosPragas",
            "presencaDoencas", "nomesDoencas", "intensidadeDanosDoencas", "presencaDaninhas", "nomesDaninhas",
            "intensidadeInfestacao", "coberturaPalha", "intensidadeErosao", "corSolo", "texturaSolo", "compactacao",
            "photo1FileName", "photo1MimeType", "photo1CapturedAt", "photo1Base64",
            "photo2FileName", "photo2MimeType", "photo2CapturedAt", "photo2Base64",
            "photo3FileName", "photo3MimeType", "photo3CapturedAt", "photo3Base64",
            "photo4FileName", "photo4MimeType", "photo4CapturedAt", "photo4Base64",
            "photo5FileName", "photo5MimeType", "photo5CapturedAt", "photo5Base64"
        ).joinToString(",") { "\"$it\"" }

        val body = reports.value.joinToString("\n") { repository.reportToCsvLine(it) }
        return "$header\n$body"
    }
}

class ReportViewModelFactory(
    private val repository: GeoReportRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReportViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ReportViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
