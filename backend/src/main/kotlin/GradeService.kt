package com.loudless

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.ceil

object GradeService {

    private const val ONE = 0.96
    private const val TWO = 0.81
    private const val THREE = 0.66
    private const val FOUR = 0.5
    private const val FIVE = 0.25

    fun readFileAndWriteToStream(array: ByteArray, points: String?, outputStream: ByteArrayOutputStream) {
        val rows = csvReader().readAll(ByteArrayInputStream(array))
        val students = mutableListOf<Student>()
        rows.forEach {
            val score = it[1].toFloatOrNull() ?: 0f
            val student = Student(it[0], score, calcPoints(points, score))
            students.add(student)
        }
        val studentData = students.map { listOf(it.name, it.points, it.grade) }
        csvWriter().writeAll(studentData, outputStream)
    }

    private fun calcPoints(maxPoints: String?, points: Float): Int {
        val floatedPoints = maxPoints?.toFloat() ?: 0f
        val onePoints = ceil(floatedPoints * ONE) - 0.5f
        val twoPoints = ceil(floatedPoints * TWO) - 0.5f
        val threePoints = ceil(floatedPoints * THREE) - 0.5f
        val fourPoints = ceil(floatedPoints * FOUR) - 0.5f
        val fivePoints = ceil(floatedPoints * FIVE) - 0.5f
        if(points >= onePoints) { return 1 }
        if(points >= twoPoints) { return 2 }
        if(points >= threePoints) { return 3 }
        if(points >= fourPoints) { return 4 }
        if(points >= fivePoints) { return 5 }
        return 6
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