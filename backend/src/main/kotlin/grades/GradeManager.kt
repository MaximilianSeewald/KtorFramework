package com.loudless.grades

import com.loudless.config.BackendConfig
import com.loudless.shared.RateLimiter
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.readAvailable
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream

class GradeManager {
    private val LOGGER = LoggerFactory.getLogger(GradeManager::class.java)

    fun initRouting(routing: Route) {
        routing.uploadGrade()
    }

    private fun Route.uploadGrade() {
        post("/upload") {
            LOGGER.info("Handling grade upload request")
            if (!RateLimiter.check(call, "grade-upload")) return@post

            val multipartData = call.receiveMultipart()
            var byteArrayContent: ByteArray? = null
            var points: String? = null
            var filePartCount = 0

            try {
                multipartData.forEachPart { part ->
                    try {
                        when (part) {
                            is PartData.FormItem -> {
                                if (part.name == "points") {
                                    points = part.value
                                }
                            }
                            is PartData.FileItem -> {
                                filePartCount += 1
                                if (filePartCount > 1) {
                                    throw GradeUploadValidationException("Exactly one CSV file is required")
                                }
                                byteArrayContent = readBoundedFilePart(part)
                            }
                            else -> Unit
                        }
                    } finally {
                        part.dispose()
                    }
                }
            } catch (exception: GradeUploadTooLargeException) {
                LOGGER.warn("Rejected grade upload because file exceeded configured size limit")
                call.respond(HttpStatusCode.PayloadTooLarge, mapOf("message" to exception.message))
                return@post
            } catch (exception: GradeUploadValidationException) {
                LOGGER.warn("Rejected grade upload because input was invalid")
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to exception.message))
                return@post
            }

            val content = byteArrayContent
            if (content == null) {
                LOGGER.warn("Rejected grade upload because no file was uploaded")
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Exactly one CSV file is required"))
                return@post
            }

            val maxPoints = parsePoints(points)
            if (maxPoints == null) {
                LOGGER.warn("Rejected grade upload because points value was invalid")
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Points must be a positive number up to ${BackendConfig.gradeUploadMaxPoints}"))
                return@post
            }

            try {
                val outputStream = ByteArrayOutputStream()
                GradeService.readFileAndWriteToStream(
                    content,
                    maxPoints,
                    BackendConfig.gradeUploadMaxRows,
                    outputStream
                )
                call.respondBytes(
                    bytes = outputStream.toByteArray(),
                    contentType = ContentType.Application.Zip,
                    status = HttpStatusCode.OK
                )
                LOGGER.info("Processed grade upload and returned ZIP")
            } catch (exception: GradeUploadValidationException) {
                LOGGER.warn("Rejected grade upload because CSV content was invalid")
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to exception.message))
            }
        }
    }

    private suspend fun readBoundedFilePart(part: PartData.FileItem): ByteArray {
        val maxBytes = BackendConfig.gradeUploadMaxBytes
        val outputStream = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val channel = part.provider()
        var totalBytes = 0L

        while (!channel.isClosedForRead) {
            val read = channel.readAvailable(buffer, 0, buffer.size)
            if (read == -1) break
            totalBytes += read
            if (totalBytes > maxBytes) {
                throw GradeUploadTooLargeException("CSV file must be $maxBytes bytes or smaller")
            }
            outputStream.write(buffer, 0, read)
        }

        return outputStream.toByteArray()
    }

    private fun parsePoints(points: String?): Float? {
        val parsed = points?.trim()?.toFloatOrNull() ?: return null
        return parsed.takeIf {
            it.isFinite() && it > 0 && it <= BackendConfig.gradeUploadMaxPoints
        }
    }
}

private class GradeUploadTooLargeException(message: String) : IllegalArgumentException(message)
