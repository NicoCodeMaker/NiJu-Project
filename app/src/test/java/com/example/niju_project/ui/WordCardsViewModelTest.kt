package com.example.niju_project.ui

import com.example.niju_project.TestFixtures
import com.example.niju_project.data.model.LessonWord
import com.example.niju_project.data.remote.NijuJson
import com.example.niju_project.ui.lessons.CardsUiState
import com.example.niju_project.ui.lessons.WordCardsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class WordCardsViewModelTest {

    private val words: List<LessonWord> = NijuJson.decodeFromString(TestFixtures.read("lesson_content"))

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val WordCardsViewModel.card get() = uiState.value as CardsUiState.Card

    @Test
    fun navigatesThroughCardsWithinBounds() {
        val vm = WordCardsViewModel({ Result.success(words.reversed()) }, { Result.success(true) })
        assertEquals("se ordenan por posición", 1, vm.card.word.position)
        assertTrue(vm.card.isFirst)
        vm.previous()
        assertEquals(1, vm.card.number)
        repeat(10) { vm.next() }
        assertEquals(6, vm.card.number)
        assertTrue(vm.card.isLast)
    }

    @Test
    fun favoriteToggleIsOptimisticAndRevertsOnError() {
        var fail = false
        val vm = WordCardsViewModel(
            { Result.success(words.map { it.copy(isFavorite = false) }) },
            { if (fail) Result.failure(IOException("sin red")) else Result.success(true) }
        )
        vm.toggleFavoriteCurrent()
        assertTrue(vm.card.word.isFavorite)

        fail = true
        vm.next(); vm.toggleFavoriteCurrent()
        assertFalse("si falla, vuelve al estado anterior", vm.card.word.isFavorite)
        assertTrue(vm.messages.value!!.contains("Sin conexión"))
        vm.previous()
        assertTrue("la primera sigue marcada", vm.card.word.isFavorite)
    }

    @Test
    fun lockedLessonAndEmptyLessonShowErrors() {
        val locked = WordCardsViewModel({ Result.failure(RuntimeException("lesson_locked")) }, { Result.success(true) })
        assertEquals("Supera la lección anterior para abrir esta.", (locked.uiState.value as CardsUiState.Error).message)

        val empty = WordCardsViewModel({ Result.success(emptyList()) }, { Result.success(true) })
        assertTrue(empty.uiState.value is CardsUiState.Error)
    }
}
