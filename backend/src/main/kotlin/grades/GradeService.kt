package com.loudless.grades

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import com.loudless.models.Student
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.ceil

class GradeUploadValidationException(message: String) : IllegalArgumentException(message)

object GradeService {
    private val LOGGER = LoggerFactory.getLogger(GradeService::class.java)

    private const val ONE = 0.96
    private const val TWO = 0.81
    private const val THREE = 0.66
    private const val FOUR = 0.5
    private const val FIVE = 0.25

    fun readFileAndWriteToStream(
        array: ByteArray,
        maxPoints: Float,
        maxRows: Int,
        outputStream: ByteArrayOutputStream
    ) {
        LOGGER.info("Processing grade CSV upload")
        val rows = csvReader().readAll(ByteArrayInputStream(array))
        if (rows.isEmpty()) {
            throw GradeUploadValidationException("CSV file is empty")
        }
        if (rows.size > maxRows) {
            throw GradeUploadValidationException("CSV file contains more than $maxRows rows")
        }

        val students = rows.mapIndexed { index, row ->
            val rowNumber = index + 1
            if (row.size < 2) {
                throw GradeUploadValidationException("Row $rowNumber must contain a name and score")
            }
            val name = row[0].trim()
            if (name.isEmpty()) {
                throw GradeUploadValidationException("Row $rowNumber contains an empty student name")
            }
            val score = row[1].trim().toFloatOrNull()
            if (score == null || !score.isFinite() || score < 0) {
                throw GradeUploadValidationException("Row $rowNumber contains an invalid score")
            }
            Student(name, score, calcPoints(maxPoints, score))
        }

        val studentData = students.map { listOf(it.name, it.points, it.grade) }
        val gradeCsv = ByteArrayOutputStream()
        csvWriter().writeAll(studentData, gradeCsv)

        val gradeToStudentCSV = ByteArrayOutputStream()
        createSecondCSV(students, maxPoints, gradeToStudentCSV)

        createZIP(outputStream, gradeCsv, gradeToStudentCSV)
        LOGGER.info("Processed grade CSV with {} rows", students.size)
    }

    private fun createZIP(outputStream: ByteArrayOutputStream, gradeCsv: ByteArrayOutputStream, gradeToStudentCSV: ByteArrayOutputStream) {
        LOGGER.info("Creating grade ZIP response")
        ZipOutputStream(outputStream).use { zipOut ->
            zipOut.putNextEntry(ZipEntry("grades.csv"))
            zipOut.write(gradeCsv.toByteArray())
            zipOut.closeEntry()

            zipOut.putNextEntry(ZipEntry("distribution.csv"))
            zipOut.write(gradeToStudentCSV.toByteArray())
            zipOut.closeEntry()
        }
        LOGGER.info("Created grade ZIP response")
    }

    private fun calcPoints(maxPoints: Float, points: Float): Int {
        val onePoints = ceil(maxPoints * ONE) - 0.5f
        val twoPoints = ceil(maxPoints * TWO) - 0.5f
        val threePoints = ceil(maxPoints * THREE) - 0.5f
        val fourPoints = ceil(maxPoints * FOUR) - 0.5f
        val fivePoints = ceil(maxPoints * FIVE) - 0.5f
        if (points >= onePoints) { return 1 }
        if (points >= twoPoints) { return 2 }
        if (points >= threePoints) { return 3 }
        if (points >= fourPoints) { return 4 }
        if (points >= fivePoints) { return 5 }
        return 6
    }

    private fun createSecondCSV(students: List<Student>, points: Float, byteArrayOutputStream: ByteArrayOutputStream) {
        val floatMap: Map<Float, MutableList<String>> = generateSequence(0.0) { it + 0.5 }
            .takeWhile { it <= points }
            .associate { it.toFloat() to mutableListOf() }
        students.forEach { floatMap[it.points]?.add(it.name) }

        val csvData: List<List<String>> = floatMap.map { (points, names) ->
            listOf(" $points") + names.map { it }
        }

        csvWriter().writeAll(csvData.reversed(), byteArrayOutputStream)
    }

    private fun csvReader() = csvReader() {
        delimiter = ';'
        charset = "UTF-8"
    }

    private fun csvWriter() = csvWriter() {
        delimiter = ';'
        charset = "UTF-8"
    }

}
