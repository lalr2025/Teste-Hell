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

    @Query("SELECT EXISTS(SELECT 1 FROM report WHERE id = :reportId)")
    suspend fun reportExists(reportId: String): Boolean

    @Query("SELECT * FROM geo_photo WHERE reportId = :reportId ORDER BY capturedAt DESC")
    suspend fun photosByReport(reportId: String): List<GeoPhotoEntity>

    @Transaction
    @Query("SELECT * FROM report ORDER BY createdAt DESC")
    suspend fun listReportsWithPhotos(): List<ReportWithPhotos>
}
