package com.example.georeport.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface AppDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReportIfAbsent(report: ReportEntity): Long

    @Update
    suspend fun updateReport(report: ReportEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: GeoPhotoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudio(audio: GeoAudioEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM report WHERE id = :reportId)")
    suspend fun reportExists(reportId: String): Boolean

    @Query("SELECT * FROM geo_photo WHERE reportId = :reportId ORDER BY capturedAt DESC")
    suspend fun photosByReport(reportId: String): List<GeoPhotoEntity>

    @Query("SELECT * FROM geo_audio WHERE reportId = :reportId ORDER BY startedAt DESC")
    suspend fun audiosByReport(reportId: String): List<GeoAudioEntity>

    @Transaction
    @Query("SELECT * FROM report ORDER BY createdAt DESC")
    suspend fun listReportsWithPhotos(): List<ReportWithPhotos>

    @Transaction
    @Query("SELECT * FROM report WHERE projectId = :projectId ORDER BY createdAt DESC")
    suspend fun listReportsWithPhotosByProject(projectId: String): List<ReportWithPhotos>

    @Query("DELETE FROM report WHERE projectId = :projectId")
    suspend fun deleteProjectReports(projectId: String)
}
