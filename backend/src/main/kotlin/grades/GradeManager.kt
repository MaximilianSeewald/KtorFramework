package com.loudless.grades

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
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
            val multipartData = call.receiveMultipart()
            var byteArrayContent: ByteArray? = null
            var points: String? = null
            multipartData.forEachPart { part ->
                if (part is PartData.FormItem) {
                    points = part.value
                }
                if (part is PartData.FileItem) {
                    byteArrayContent = part.provider().toByteArray()
                }
            }
            if (byteArrayContent == null) {
                LOGGER.warn("Rejected grade upload because no file was uploaded")
                call.respond(HttpStatusCode.BadRequest, "No file uploaded")
            }
            if (byteArrayContent != null) {
                val outputStream = ByteArrayOutputStream()
                GradeService.readFileAndWriteToStream(byteArrayContent!!, points, outputStream)
                call.respondBytes(
                    bytes = outputStream.toByteArray(),
                    contentType = ContentType.Application.Zip,
                    status = HttpStatusCode.OK
                )
                LOGGER.info("Processed grade upload and returned ZIP")
            }
        }
    }
}
