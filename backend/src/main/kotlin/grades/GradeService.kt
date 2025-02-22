package com.loudless.grades

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import com.loudless.models.Student
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.ceil

object GradeService {

    private const val ONE = 0.96
    private const val TWO = 0.81
    private const val THREE = 0.66
    private const val FOUR = 0.5
    private const val FIVE = 0.25

    fun readFileAndWriteToStream(array: ByteArray, points: String?, outputStream: ByteArrayOutputStream) {
        val rows = csvReader().readAll(ByteArrayInputStream(array))
        val floatedPoints = points?.toFloat() ?: 0f
        val students = mutableListOf<Student>()
        rows.forEach {
            val score = it[1].toFloatOrNull() ?: 0f
            val student = Student(it[0], score, calcPoints(floatedPoints, score))
            students.add(student)
        }
        val studentData = students.map { listOf(it.name, it.points, it.grade) }
        val gradeCsv = ByteArrayOutputStream()
        csvWriter().writeAll(studentData, gradeCsv)

        val gradeToStudentCSV = ByteArrayOutputStream()
        createSecondCSV(students,floatedPoints,gradeToStudentCSV)

        createZIP(outputStream,gradeCsv,gradeToStudentCSV)
    }

    private fun createZIP(outputStream: ByteArrayOutputStream, gradeCsv: ByteArrayOutputStream, gradeToStudentCSV: ByteArrayOutputStream) {
        ZipOutputStream(outputStream).use { zipOut ->
            zipOut.putNextEntry(ZipEntry("grades.csv"))
            zipOut.write(gradeCsv.toByteArray())
            zipOut.closeEntry()

            zipOut.putNextEntry(ZipEntry("distribution.csv"))
            zipOut.write(gradeToStudentCSV.toByteArray())
            zipOut.closeEntry()
        }
    }

    private fun calcPoints(maxPoints: Float, points: Float): Int {
        val onePoints = ceil(maxPoints * ONE) - 0.5f
        val twoPoints = ceil(maxPoints * TWO) - 0.5f
        val threePoints = ceil(maxPoints * THREE) - 0.5f
        val fourPoints = ceil(maxPoints * FOUR) - 0.5f
        val fivePoints = ceil(maxPoints * FIVE) - 0.5f
        if(points >= onePoints) { return 1 }
        if(points >= twoPoints) { return 2 }
        if(points >= threePoints) { return 3 }
        if(points >= fourPoints) { return 4 }
        if(points >= fivePoints) { return 5 }
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