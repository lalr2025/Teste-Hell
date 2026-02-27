package com.example.georeport.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.georeport.data.GeoPhotoEntity
import com.example.georeport.domain.GeoReportRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class QuestionUi(
    val id: String,
    val title: String,
    val options: List<OptionUi>
)

data class OptionUi(
    val id: String,
    val label: String
)

class ReportViewModel(
    private val repository: GeoReportRepository
) : ViewModel() {

    private val _photos = MutableStateFlow<List<GeoPhotoEntity>>(emptyList())
    val photos: StateFlow<List<GeoPhotoEntity>> = _photos.asStateFlow()

    val questions = listOf(
        QuestionUi(
            id = "q1",
            title = "Condição do local",
            options = listOf(
                OptionUi("q1_o1", "Bom"),
                OptionUi("q1_o2", "Regular"),
                OptionUi("q1_o3", "Ruim")
            )
        ),
        QuestionUi(
            id = "q2",
            title = "Acesso",
            options = listOf(
                OptionUi("q2_o1", "Livre"),
                OptionUi("q2_o2", "Parcial"),
                OptionUi("q2_o3", "Bloqueado")
            )
        )
    )

    fun loadPhotos(reportId: String) {
        viewModelScope.launch {
            _photos.value = repository.photosByReport(reportId)
        }
    }

    fun saveReport(
        reportId: String,
        latitude: Double?,
        longitude: Double?,
        answers: Map<String, String>
    ) {
        viewModelScope.launch {
            repository.saveReport(reportId, latitude, longitude, answers)
        }
    }

    fun savePhoto(reportId: String, filePath: String, latitude: Double?, longitude: Double?) {
        viewModelScope.launch {
            repository.savePhoto(reportId, filePath, latitude, longitude)
            _photos.value = repository.photosByReport(reportId)
        }
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
