package com.example.georeport.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.os.Parcelable
import com.example.georeport.data.GeoPhotoEntity
import com.example.georeport.data.ReportEntity
import com.example.georeport.data.ReportWithPhotos
import com.example.georeport.domain.GeoReportRepository
import kotlinx.parcelize.Parcelize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class DropOption(val label: String)

@Parcelize
data class ReportFormState(
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

    fun loadPhotos(reportId: String) {
        viewModelScope.launch {
            _photos.value = repository.photosByReport(reportId)
        }
    }

    fun refreshReports() {
        viewModelScope.launch {
            _reports.value = repository.listReports()
        }
    }

    fun saveReport(report: ReportEntity) {
        viewModelScope.launch {
            repository.saveReport(report)
            refreshReports()
        }
    }

    fun savePhoto(reportId: String, filePath: String, latitude: Double?, longitude: Double?, altitude: Double?) {
        viewModelScope.launch {
            repository.savePhoto(reportId, filePath, latitude, longitude, altitude)
            _photos.value = repository.photosByReport(reportId)
            refreshReports()
        }
    }

    fun exportZip(file: File, onDone: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { repository.exportReportsZip(file) }
            onDone(result)
        }
    }

    fun csvContent(): String {
        val header = listOf(
            "id", "createdAt", "latitude", "longitude", "cultura", "cultivar", "faseFenologica",
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
