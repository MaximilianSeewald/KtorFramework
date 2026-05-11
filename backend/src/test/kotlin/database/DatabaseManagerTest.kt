package com.loudless.database

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DatabaseManagerTest {
    @Test
    fun `hash password does not store plain text`() {
        val hashedPassword = DatabaseManager.hashPassword("correct-password")

        assertNotEquals("correct-password", hashedPassword)
    }

    @Test
    fun `verify password accepts matching password`() {
        val hashedPassword = DatabaseManager.hashPassword("correct-password")

        assertTrue(DatabaseManager.verifyPassword("correct-password", hashedPassword))
    }

    @Test
    fun `verify password rejects non matching password`() {
        val hashedPassword = DatabaseManager.hashPassword("correct-password")

        assertFalse(DatabaseManager.verifyPassword("wrong-password", hashedPassword))
    }
}
