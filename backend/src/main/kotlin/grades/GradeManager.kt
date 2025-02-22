package com.loudless.grades

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import java.io.ByteArrayOutputStream

class GradeManager {

    fun initRouting(routing: Routing) {
        routing.uploadGrade()
    }

    private fun Routing.uploadGrade() {
        post("/upload") {
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
            }
        }
    }
}