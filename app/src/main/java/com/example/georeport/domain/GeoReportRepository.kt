package com.example.georeport.domain

import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import android.location.Location
import android.graphics.BitmapFactory
import com.example.georeport.data.AppDao
import com.example.georeport.data.GeoAudioEntity
import com.example.georeport.data.GeoPhotoEntity
import com.example.georeport.data.ReportEntity
import com.example.georeport.data.ReportWithPhotos
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
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
        projectId: String,
        projectName: String,
        projectNumber: String,
        projectCreatedAt: Long,
        filePath: String,
        latitude: Double?,
        longitude: Double?,
        inspectionType: String,
        altitude: Double?
    ) = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) return@withContext

        ensureReportExists(
            reportId = reportId,
            projectId = projectId,
            projectName = projectName,
            projectNumber = projectNumber,
            projectCreatedAt = projectCreatedAt,
            latitude = latitude,
            longitude = longitude,
            inspectionType = inspectionType,
            altitude = altitude
        )

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
        projectId: String,
        projectName: String,
        projectNumber: String,
        projectCreatedAt: Long,
        latitude: Double?,
        longitude: Double?,
        inspectionType: String,
        altitude: Double?
    ) {
        if (dao.reportExists(reportId)) return

        dao.insertReportIfAbsent(
            ReportEntity(
                id = reportId,
                projectId = projectId,
                projectName = projectName,
                projectNumber = projectNumber,
                projectCreatedAt = projectCreatedAt,
                createdAt = System.currentTimeMillis(),
                inspectionType = inspectionType,
                surveyAnswersJson = "{}",
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

    suspend fun audiosByReport(reportId: String): List<GeoAudioEntity> = dao.audiosByReport(reportId)

    suspend fun saveAudio(
        reportId: String,
        filePath: String,
        latitude: Double?,
        longitude: Double?,
        accuracyMeters: Double?,
        startedAt: Long,
        endedAt: Long
    ) {
        dao.insertAudio(
            GeoAudioEntity(
                id = UUID.randomUUID().toString(),
                reportId = reportId,
                filePath = filePath,
                latitude = latitude,
                longitude = longitude,
                accuracyMeters = accuracyMeters,
                startedAt = startedAt,
                endedAt = endedAt
            )
        )
    }

    suspend fun deleteProject(projectId: String, mediaDir: File?) = withContext(Dispatchers.IO) {
        dao.deleteProjectReports(projectId)
        mediaDir?.takeIf { it.exists() }?.deleteRecursively()
    }

    suspend fun listReports(projectId: String?): List<ReportWithPhotos> =
        if (projectId.isNullOrBlank()) dao.listReportsWithPhotos() else dao.listReportsWithPhotosByProject(projectId)

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
            r.projectId,
            r.projectName,
            r.projectNumber,
            r.projectCreatedAt.toString(),
            r.createdAt.toString(),
            r.inspectionType,
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




    suspend fun exportReportsZip(outputZipFile: File, projectId: String?) = withContext(Dispatchers.IO) {
        val reports = listReports(projectId)
        outputZipFile.parentFile?.mkdirs()
        ZipOutputStream(outputZipFile.outputStream().buffered()).use { zip ->
            val reportsJson = JSONArray()
            val featuresJson = JSONArray()

            reports.forEach { reportWithPhotos ->
                val report = reportWithPhotos.report
                val photosJson = JSONArray()
                val audioJson = JSONArray()

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
                            .put("file", zipPhotoName)
                            .put("mimeType", mimeTypeFromFileName(photoFile.name))
                            .put("capturedAt", photo.capturedAt)
                            .put("latitude", photo.latitude)
                            .put("longitude", photo.longitude)
                            .put("sizeBytes", sizeBytes)
                            .put("absolutePath", photo.filePath)
                            .put("base64", photo.base64Data)
                    )
                }

                dao.audiosByReport(report.id).forEachIndexed { index, audio ->
                    val audioFile = File(audio.filePath)
                    val zipAudioName = "audio/${report.projectId}/${index + 1}_${audioFile.name}"
                    if (audioFile.exists()) {
                        zip.putNextEntry(ZipEntry(zipAudioName))
                        audioFile.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                    audioJson.put(
                        JSONObject()
                            .put("file", zipAudioName)
                            .put("mime", "audio/mp4")
                            .put("lat", audio.latitude)
                            .put("lon", audio.longitude)
                            .put("accuracy_m", audio.accuracyMeters)
                            .put("start_ts", audio.startedAt)
                            .put("end_ts", audio.endedAt)
                    )
                }

                featuresJson.put(
                    JSONObject()
                        .put("type", "Feature")
                        .put(
                            "geometry",
                            JSONObject()
                                .put("type", "Point")
                                .put("coordinates", JSONArray().put(report.longitude).put(report.latitude))
                        )
                        .put(
                            "properties",
                            JSONObject()
                                .put("id", report.id)
                                .put("cultura", report.cultura)
                                .put("createdAt", report.createdAt)
                                .put("photos", JSONArray().apply {
                                    for (i in 0 until photosJson.length()) put(photosJson.getJSONObject(i).optString("file"))
                                })
                                .put("audio", JSONArray().apply {
                                    for (i in 0 until audioJson.length()) put(audioJson.getJSONObject(i).optString("file"))
                                })
                        )
                )

                reportsJson.put(
                    JSONObject()
                        .put("id", report.id)
                        .put("projectId", report.projectId)
                        .put("projectName", report.projectName)
                        .put("projectNumber", report.projectNumber)
                        .put("projectCreatedAt", report.projectCreatedAt)
                        .put("createdAt", report.createdAt)
                        .put("inspectionType", report.inspectionType)
                        .put("surveyAnswers", JSONObject(report.surveyAnswersJson.ifBlank { "{}" }))
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
                        .put("audio", audioJson)
                )
            }

            val projectJson = reports.firstOrNull()?.report?.let {
                JSONObject()
                    .put("id", it.projectId)
                    .put("name", it.projectName)
                    .put("number", it.projectNumber)
                    .put("createdAt", it.projectCreatedAt)
            } ?: JSONObject()

            val metadataJson = JSONObject()
                .put("schema_version", "1.0")
                .put("generatedAt", System.currentTimeMillis())
                .put("project", projectJson)
                .put("export", JSONObject().put("original_export_folder_on_device", outputZipFile.parentFile?.absolutePath ?: ""))
                .put("reports", reportsJson)
                .toString(2)

            val geoJson = JSONObject()
                .put("type", "FeatureCollection")
                .put("features", featuresJson)
                .toString(2)

            zip.putNextEntry(ZipEntry("report.json"))
            zip.write(metadataJson.toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("map.geojson"))
            zip.write(geoJson.toByteArray())
            zip.closeEntry()
        }
    }

    suspend fun importPortableJson(rawJson: String, mediaBase: File?): String? = withContext(Dispatchers.IO) {
        val root = JSONObject(rawJson)
        val reports = root.optJSONArray("reports") ?: return@withContext null
        var importedProjectId: String? = null
        for (i in 0 until reports.length()) {
            val r = reports.optJSONObject(i) ?: continue
            val reportId = r.optString("id", UUID.randomUUID().toString())
            val projectId = r.optString("projectId", root.optJSONObject("project")?.optString("id") ?: "IMPORT")
            importedProjectId = projectId
            val entity = ReportEntity(
                id = reportId,
                projectId = projectId,
                projectName = r.optString("projectName", root.optJSONObject("project")?.optString("name") ?: "Importado"),
                projectNumber = r.optString("projectNumber", root.optJSONObject("project")?.optString("number") ?: "IMPORT"),
                projectCreatedAt = r.optLong("projectCreatedAt", root.optJSONObject("project")?.optLong("createdAt") ?: System.currentTimeMillis()),
                createdAt = r.optLong("createdAt", System.currentTimeMillis()),
                inspectionType = r.optString("inspectionType", ""),
                surveyAnswersJson = r.optJSONObject("surveyAnswers")?.toString() ?: "{}",
                latitude = r.optDouble("latitude").takeUnless { it.isNaN() },
                longitude = r.optDouble("longitude").takeUnless { it.isNaN() },
                altitude = r.optDouble("altitude").takeUnless { it.isNaN() },
                cultura = r.optString("cultura", ""),
                cultivar = r.optString("cultivar", ""),
                faseFenologica = r.optString("faseFenologica", ""),
                espacamentoLinha = r.optDouble("espacamentoLinha").takeUnless { it.isNaN() },
                espacamentoEntreLinha = r.optDouble("espacamentoEntreLinha").takeUnless { it.isNaN() },
                altura = r.optDouble("altura").takeUnless { it.isNaN() },
                comprimentoPivoRaiz = r.optDouble("comprimentoPivoRaiz").takeUnless { it.isNaN() },
                distribuicaoSistemaRadicular = r.optString("distribuicaoSistemaRadicular", ""),
                sanidadeGeral = r.optString("sanidadeGeral", ""),
                presencaPragas = r.optString("presencaPragas", ""),
                nomesPragas = r.optString("nomesPragas", ""),
                intensidadeDanosPragas = r.optString("intensidadeDanosPragas", ""),
                presencaDoencas = r.optString("presencaDoencas", ""),
                nomesDoencas = r.optString("nomesDoencas", ""),
                intensidadeDanosDoencas = r.optString("intensidadeDanosDoencas", ""),
                presencaDaninhas = r.optString("presencaDaninhas", ""),
                nomesDaninhas = r.optString("nomesDaninhas", ""),
                intensidadeInfestacao = r.optString("intensidadeInfestacao", ""),
                coberturaPalha = r.optString("coberturaPalha", ""),
                intensidadeErosao = r.optString("intensidadeErosao", ""),
                corSolo = r.optString("corSolo", ""),
                texturaSolo = r.optString("texturaSolo", ""),
                compactacao = r.optString("compactacao", "")
            )
            saveReport(entity)

            val photos = r.optJSONArray("photos") ?: JSONArray()
            for (p in 0 until photos.length()) {
                val pj = photos.optJSONObject(p) ?: continue
                val rel = pj.optString("file", pj.optString("zipPath"))
                val absolute = mediaBase?.resolve(rel)?.absolutePath ?: rel
                dao.insertPhoto(
                    GeoPhotoEntity(
                        id = UUID.randomUUID().toString(),
                        reportId = reportId,
                        filePath = absolute,
                        base64Data = "",
                        latitude = pj.optDouble("latitude").takeUnless { it.isNaN() },
                        longitude = pj.optDouble("longitude").takeUnless { it.isNaN() },
                        capturedAt = pj.optLong("capturedAt", System.currentTimeMillis())
                    )
                )
            }

            val audios = r.optJSONArray("audio") ?: JSONArray()
            for (a in 0 until audios.length()) {
                val aj = audios.optJSONObject(a) ?: continue
                val rel = aj.optString("file")
                val absolute = mediaBase?.resolve(rel)?.absolutePath ?: rel
                dao.insertAudio(
                    GeoAudioEntity(
                        id = UUID.randomUUID().toString(),
                        reportId = reportId,
                        filePath = absolute,
                        latitude = aj.optDouble("lat").takeUnless { it.isNaN() },
                        longitude = aj.optDouble("lon").takeUnless { it.isNaN() },
                        accuracyMeters = aj.optDouble("accuracy_m").takeUnless { it.isNaN() },
                        startedAt = aj.optLong("start_ts", System.currentTimeMillis()),
                        endedAt = aj.optLong("end_ts", System.currentTimeMillis())
                    )
                )
            }
        }
        importedProjectId
    }

    suspend fun importPortableZip(zipFile: File, outputDir: File): String? = withContext(Dispatchers.IO) {
        outputDir.mkdirs()
        ZipFile(zipFile).use { zf ->
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                val target = outputDir.resolve(entry.name)
                target.parentFile?.mkdirs()
                zf.getInputStream(entry).use { input ->
                    target.outputStream().use { out -> input.copyTo(out) }
                }
            }
        }
        val reportFile = outputDir.resolve("report.json").takeIf { it.exists() }
            ?: outputDir.resolve("metadata/reports.json").takeIf { it.exists() }
            ?: return@withContext null
        importPortableJson(reportFile.readText(), outputDir)
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
