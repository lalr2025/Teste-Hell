package com.example.georeport.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "report")
data class ReportEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val latitude: Double?,
    val longitude: Double?,
    val cultura: String,
    val cultivar: String,
    val faseFenologica: String,
    val espacamentoLinha: Double?,
    val espacamentoEntreLinha: Double?,
    val altura: Double?,
    val comprimentoPivoRaiz: Double?,
    val distribuicaoSistemaRadicular: String,
    val sanidadeGeral: String,
    val presencaPragas: String,
    val nomesPragas: String,
    val intensidadeDanosPragas: String,
    val presencaDoencas: String,
    val nomesDoencas: String,
    val intensidadeDanosDoencas: String,
    val presencaDaninhas: String,
    val nomesDaninhas: String,
    val intensidadeInfestacao: String,
    val coberturaPalha: String,
    val intensidadeErosao: String,
    val corSolo: String,
    val texturaSolo: String,
    val compactacao: String,
    val syncStatus: String = "PENDING_SYNC"
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
    ],
    indices = [Index(value = ["reportId"])]
)
data class GeoPhotoEntity(
    @PrimaryKey val id: String,
    val reportId: String,
    val filePath: String,
    val base64Data: String,
    val latitude: Double?,
    val longitude: Double?,
    val capturedAt: Long,
    val syncStatus: String = "PENDING_SYNC"
)

data class ReportWithPhotos(
    @Embedded val report: ReportEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "reportId"
    )
    val photos: List<GeoPhotoEntity>
)
