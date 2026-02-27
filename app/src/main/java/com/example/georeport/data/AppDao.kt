package com.example.georeport.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface AppDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: ReportEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnswers(answers: List<AnswerEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: GeoPhotoEntity)

    @Query("SELECT * FROM geo_photo WHERE reportId = :reportId ORDER BY capturedAt DESC")
    suspend fun photosByReport(reportId: String): List<GeoPhotoEntity>

    @Transaction
    suspend fun saveReportWithAnswers(report: ReportEntity, answers: List<AnswerEntity>) {
        insertReport(report)
        insertAnswers(answers)
    }
}
