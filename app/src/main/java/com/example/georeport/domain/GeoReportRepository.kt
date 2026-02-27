package com.example.georeport.domain

import com.example.georeport.data.AnswerEntity
import com.example.georeport.data.AppDao
import com.example.georeport.data.GeoPhotoEntity
import com.example.georeport.data.ReportEntity
import java.util.UUID

class GeoReportRepository(
    private val dao: AppDao
) {
    suspend fun saveReport(
        reportId: String,
        latitude: Double?,
        longitude: Double?,
        answers: Map<String, String>
    ) {
        val report = ReportEntity(
            id = reportId,
            createdAt = System.currentTimeMillis(),
            latitude = latitude,
            longitude = longitude
        )

        val answerEntities = answers.map { (questionId, optionId) ->
            AnswerEntity(
                id = UUID.randomUUID().toString(),
                reportId = reportId,
                questionId = questionId,
                optionId = optionId,
                answeredAt = System.currentTimeMillis()
            )
        }

        dao.saveReportWithAnswers(report, answerEntities)
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
}
