package com.example.niju_project.data

import com.example.niju_project.data.remote.NijuErrors
import com.example.niju_project.data.repository.ProfileRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ErrorsAndValidationTest {

    @Test
    fun serverCodesBecomeSpanishMessages() {
        // Así llega un "raise exception 'no_due_reviews'" a través de PostgREST.
        val e = RuntimeException("no_due_reviews\nURL: https://x.supabase.co/rest/v1/rpc/start_session")
        assertTrue(NijuErrors.hasCode(e, NijuErrors.NO_DUE_REVIEWS))
        assertEquals("¡Estás al día! No tienes repasos pendientes.", NijuErrors.messageFor(e))
        assertEquals("Ya respondiste este ejercicio.", NijuErrors.messageFor("item_already_answered"))
        assertEquals("No encontramos esta sesión.", NijuErrors.messageFor("session_not_found"))
    }

    @Test
    fun codeInsideCauseIsFound() {
        val e = IllegalStateException("wrapper", RuntimeException("not_authenticated"))
        assertTrue(NijuErrors.isSessionExpired(e))
        assertTrue(NijuErrors.isSessionExpired(RuntimeException("JWT expired")))
        assertFalse(NijuErrors.isSessionExpired(RuntimeException("no_words")))
    }

    @Test
    fun networkAndUnknownErrors() {
        assertEquals(
            "Sin conexión. Revisa tu internet e intenta de nuevo.",
            NijuErrors.messageFor(IOException("timeout"))
        )
        assertEquals("Ocurrió un error. Intenta de nuevo.", NijuErrors.messageFor(RuntimeException("???")))
        assertEquals("Ocurrió un error. Intenta de nuevo.", NijuErrors.messageFor(null as String?))
    }

    @Test
    fun profileValidation() {
        assertNull(ProfileRepository.validate("Ana", 50))
        assertNotNull(ProfileRepository.validate("", 50))
        assertNotNull(ProfileRepository.validate("A", 50))
        assertNotNull(ProfileRepository.validate("x".repeat(61), 50))
        assertNotNull(ProfileRepository.validate("Ana", null))
        assertNotNull(ProfileRepository.validate("Ana", 9))
        assertNotNull(ProfileRepository.validate("Ana", 501))
        assertNull(ProfileRepository.validate("Ana", 10))
        assertNull(ProfileRepository.validate("Ana", 500))
    }
}
