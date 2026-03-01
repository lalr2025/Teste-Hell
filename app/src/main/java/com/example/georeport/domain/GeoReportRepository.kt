package com.example.georeport.domain

import android.util.Base64
import com.example.georeport.data.AppDao
import com.example.georeport.data.GeoPhotoEntity
import com.example.georeport.data.ReportEntity
import com.example.georeport.data.ReportWithPhotos
import java.io.File
import java.util.UUID

class GeoReportRepository(
    private val dao: AppDao
) {
    suspend fun saveReport(report: ReportEntity) {
        dao.insertReport(report)
    }

    suspend fun savePhoto(
        reportId: String,
        filePath: String,
        latitude: Double?,
        longitude: Double?
    ) {
        dao.insertPhoto(
            GeoPhotoEntity(
                id = UUID.randomUUID().toString(),
                reportId = reportId,
                filePath = filePath,
                latitude = latitude,
                longitude = longitude,
                capturedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun photosByReport(reportId: String): List<GeoPhotoEntity> = dao.photosByReport(reportId)

    suspend fun listReports(): List<ReportWithPhotos> = dao.listReportsWithPhotos()

    fun reportToCsvLine(reportWithPhotos: ReportWithPhotos): String {
        val r = reportWithPhotos.report
        val photoBase64 = reportWithPhotos.photos.joinToString("|") { photo ->
            runCatching {
                val bytes = File(photo.filePath).readBytes()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }.getOrDefault("")
        }

        val fields = listOf(
            r.id,
            r.createdAt.toString(),
            r.latitude?.toString().orEmpty(),
            r.longitude?.toString().orEmpty(),
            r.cultura,
            r.cultivar,
            r.faseFenologica,
            r.espacamentoLinha?.toString().orEmpty(),
            r.espacamentoEntreLinha?.toString().orEmpty(),
            r.altura?.toString().orEmpty(),
            r.comprimentoPivoRaiz?.toString().orEmpty(),
            r.distribuicaoSistemaRadicular,
            r.sanidadeGeral,
            r.presencaPragas,
            r.nomesPragas,
            r.intensidadeDanosPragas,
            r.presencaDoencas,
            r.nomesDoencas,
            r.intensidadeDanosDoencas,
            r.presencaDaninhas,
            r.nomesDaninhas,
            r.intensidadeInfestacao,
            r.coberturaPalha,
            r.intensidadeErosao,
            r.corSolo,
            r.texturaSolo,
            r.compactacao,
            photoBase64
        )

        return fields.joinToString(",") { value ->
            val sanitized = value.replace("\"", "\"\"")
            "\"$sanitized\""
        }
    }
}
