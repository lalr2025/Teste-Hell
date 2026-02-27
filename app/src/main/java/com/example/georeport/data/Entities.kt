package com.example.georeport.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "report")
data class ReportEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val latitude: Double?,
    val longitude: Double?,
    val syncStatus: String = "PENDING_SYNC"
)

@Entity(tableName = "question")
data class QuestionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val required: Boolean = true
)

@Entity(
    tableName = "question_option",
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class QuestionOptionEntity(
    @PrimaryKey val id: String,
    val questionId: String,
    val label: String
)

@Entity(
    tableName = "answer",
    foreignKeys = [
        ForeignKey(
            entity = ReportEntity::class,
            parentColumns = ["id"],
            childColumns = ["reportId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class AnswerEntity(
    @PrimaryKey val id: String,
    val reportId: String,
    val questionId: String,
    val optionId: String,
    val answeredAt: Long
)

@Entity(
    tableName = "geo_photo",
    foreignKeys = [
        ForeignKey(
            entity = ReportEntity::class,
            parentColumns = ["id"],
            childColumns = ["reportId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class GeoPhotoEntity(
    @PrimaryKey val id: String,
    val reportId: String,
    val filePath: String,
    val latitude: Double?,
    val longitude: Double?,
    val capturedAt: Long,
    val syncStatus: String = "PENDING_SYNC"
)
