package com.example.georeport.domain

import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import android.location.Location
import android.graphics.BitmapFactory
import com.example.georeport.data.AppDao
import com.example.georeport.data.GeoPhotoEntity
import com.example.georeport.data.ReportEntity
import com.example.georeport.data.ReportWithPhotos
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeoReportRepository(
    private val dao: AppDao
) {
    suspend fun saveReport(report: ReportEntity) {
        dao.insertReportIfAbsent(report)
        dao.updateReport(report)
    }

    suspend fun savePhoto(
        reportId: String,
        filePath: String,
        latitude: Double?,
        longitude: Double?,
        altitude: Double?
    ) = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) return@withContext

        ensureReportExists(reportId, latitude, longitude, altitude)

        optimizeJpegFile(filePath)
        writeExifGps(filePath, latitude, longitude, altitude)

        dao.insertPhoto(
            GeoPhotoEntity(
                id = UUID.randomUUID().toString(),
                reportId = reportId,
                filePath = filePath,
                base64Data = "",
                latitude = latitude,
                longitude = longitude,
                capturedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun ensureReportExists(
        reportId: String,
        latitude: Double?,
        longitude: Double?,
        altitude: Double?
    ) {
        if (dao.reportExists(reportId)) return

        dao.insertReportIfAbsent(
            ReportEntity(
                id = reportId,
                createdAt = System.currentTimeMillis(),
                latitude = latitude,
                longitude = longitude,
                altitude = altitude,
                cultura = "",
                cultivar = "",
                faseFenologica = "",
                espacamentoLinha = null,
                espacamentoEntreLinha = null,
                altura = null,
                comprimentoPivoRaiz = null,
                distribuicaoSistemaRadicular = "",
                sanidadeGeral = "",
                presencaPragas = "",
                nomesPragas = "",
                intensidadeDanosPragas = "",
                presencaDoencas = "",
                nomesDoencas = "",
                intensidadeDanosDoencas = "",
                presencaDaninhas = "",
                nomesDaninhas = "",
                intensidadeInfestacao = "",
                coberturaPalha = "",
                intensidadeErosao = "",
                corSolo = "",
                texturaSolo = "",
                compactacao = ""
            )
        )
    }

    suspend fun photosByReport(reportId: String): List<GeoPhotoEntity> = dao.photosByReport(reportId)

    suspend fun listReports(): List<ReportWithPhotos> = dao.listReportsWithPhotos()

    fun reportToCsvLine(reportWithPhotos: ReportWithPhotos): String {
        val r = reportWithPhotos.report
        val photoColumns = reportWithPhotos.photos
            .take(5)
            .map { photo ->
                val file = File(photo.filePath)
                val fileName = file.name
                val mimeType = mimeTypeFromFileName(fileName)
                val capturedAt = photo.capturedAt.toString()
                val base64 = if (photo.base64Data.isNotBlank()) photo.base64Data else filePathToBase64(photo.filePath)

                listOf(fileName, mimeType, capturedAt, base64)
            }
            .toMutableList()
            .apply {
                while (size < 5) add(listOf("", "", "", ""))
            }
            .flatten()

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
            r.compactacao
        ) + photoColumns

        return fields.joinToString(",") { value ->
            val sanitized = value.replace("\"", "\"\"")
            "\"$sanitized\""
        }
    }




    suspend fun exportReportsZip(outputZipFile: File) = withContext(Dispatchers.IO) {
        val reports = listReports()
        outputZipFile.parentFile?.mkdirs()
        ZipOutputStream(outputZipFile.outputStream().buffered()).use { zip ->
            val reportsJson = JSONArray()

            reports.forEach { reportWithPhotos ->
                val report = reportWithPhotos.report
                val photosJson = JSONArray()

                reportWithPhotos.photos.take(5).forEachIndexed { index, photo ->
                    val photoFile = File(photo.filePath)
                    val zipPhotoName = "photos/${report.id}/${index + 1}_${photoFile.name}"

                    if (photoFile.exists()) {
                        zip.putNextEntry(ZipEntry(zipPhotoName))
                        photoFile.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }

                    val sizeBytes = if (photoFile.exists()) photoFile.length() else 0L

                    photosJson.put(
                        JSONObject()
                            .put("id", photo.id)
                            .put("fileName", photoFile.name)
                            .put("zipPath", zipPhotoName)
                            .put("mimeType", mimeTypeFromFileName(photoFile.name))
                            .put("capturedAt", photo.capturedAt)
                            .put("latitude", photo.latitude)
                            .put("longitude", photo.longitude)
                            .put("sizeBytes", sizeBytes)
                            .put("absolutePath", photo.filePath)
                            .put("base64", photo.base64Data)
                    )
                }

                reportsJson.put(
                    JSONObject()
                        .put("id", report.id)
                        .put("createdAt", report.createdAt)
                        .put("latitude", report.latitude)
                        .put("longitude", report.longitude)
                        .put("altitude", report.altitude)
                        .put("cultura", report.cultura)
                        .put("cultivar", report.cultivar)
                        .put("faseFenologica", report.faseFenologica)
                        .put("espacamentoLinha", report.espacamentoLinha)
                        .put("espacamentoEntreLinha", report.espacamentoEntreLinha)
                        .put("altura", report.altura)
                        .put("comprimentoPivoRaiz", report.comprimentoPivoRaiz)
                        .put("distribuicaoSistemaRadicular", report.distribuicaoSistemaRadicular)
                        .put("sanidadeGeral", report.sanidadeGeral)
                        .put("presencaPragas", report.presencaPragas)
                        .put("nomesPragas", report.nomesPragas)
                        .put("intensidadeDanosPragas", report.intensidadeDanosPragas)
                        .put("presencaDoencas", report.presencaDoencas)
                        .put("nomesDoencas", report.nomesDoencas)
                        .put("intensidadeDanosDoencas", report.intensidadeDanosDoencas)
                        .put("presencaDaninhas", report.presencaDaninhas)
                        .put("nomesDaninhas", report.nomesDaninhas)
                        .put("intensidadeInfestacao", report.intensidadeInfestacao)
                        .put("coberturaPalha", report.coberturaPalha)
                        .put("intensidadeErosao", report.intensidadeErosao)
                        .put("corSolo", report.corSolo)
                        .put("texturaSolo", report.texturaSolo)
                        .put("compactacao", report.compactacao)
                        .put("photos", photosJson)
                )
            }

            val metadataJson = JSONObject()
                .put("generatedAt", System.currentTimeMillis())
                .put("reports", reportsJson)
                .toString(2)

            zip.putNextEntry(ZipEntry("metadata/reports.json"))
            zip.write(metadataJson.toByteArray())
            zip.closeEntry()
        }
    }

    private fun optimizeJpegFile(filePath: String) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(filePath, bounds)
            val sampleSize = calculateInSampleSize(bounds, 1920, 1920)

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeFile(filePath, decodeOptions) ?: return@runCatching

            val outFile = File(filePath)
            outFile.outputStream().use { output ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, output)
            }
            bitmap.recycle()
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            var halfHeight = height / 2
            var halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun writeExifGps(filePath: String, latitude: Double?, longitude: Double?, altitude: Double?) {
        if (latitude == null || longitude == null) return
        runCatching {
            val exif = ExifInterface(filePath)
            exif.setGpsInfo(Location("georeport").apply {
                this.latitude = latitude
                this.longitude = longitude
                if (altitude != null) this.altitude = altitude
            })
            exif.saveAttributes()
        }
    }

    private fun filePathToBase64(filePath: String): String = runCatching {
        val file = File(filePath)
        if (!file.exists()) return@runCatching ""
        val bytes = file.readBytes()
        Base64.encodeToString(bytes, Base64.NO_WRAP)
    }.getOrDefault("")

    private fun mimeTypeFromFileName(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
    }
}
