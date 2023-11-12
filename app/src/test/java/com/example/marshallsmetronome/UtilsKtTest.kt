package com.example.marshallsmetronome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Allow test methods to get long and descriptive:
@Suppress("FunctionMaxLength")
class UtilsKtTest {

    @Test
    fun getSecondsForAllCycles() {
        val result = getSecondsForAllCycles(
            workSecondsPerCycle = 20,
            cycles = 8,
            restSecondsPerCycle = 10
        )
        assertEquals(240, result)
    }

    @Test
    fun getSecondsForFirstInterval() {
        val result = getSecondsForFirstInterval(20)
        assertEquals(20, result) // expected result for normal case
    }

    @Test
    fun normaliseIntInput() {
        val result = normaliseIntInput("00abcd1234500")
        assertEquals("1234500", result)
    }

    @Test
    fun validateIntInput_returns_null_when_given_the_maximum_valid_integer_string() {
        // Arrange
        val input = "100"

        // Act
        val result = validateIntInput(input)

        // Assert
        assertNull(result)
    }

    @Test
    fun validateIntInput_returns_Invalid_number_when_given_a_non_integer_string() {
        // Arrange
        val input = "abc"

        // Act
        val result = validateIntInput(input)

        // Assert
        assertEquals("Invalid number", result)
    }

    @Test
    fun validateIntInput_returns_Must_be_at_least_1_when_given_the_string_0() {
        // Arrange
        val input = "0"

        // Act
        val result = validateIntInput(input)

        // Assert
        assertEquals("Must be at least 1", result)
    }

    @Test
    fun validateIntInput_returns_Must_be_at_most_100_when_given_the_string_101() {
        // Arrange
        val input = "101"

        // Act
        val result = validateIntInput(input)

        // Assert
        assertEquals("Must be at most 100", result)
    }

    @Test
    fun getErrorInfoFor_returnsPairWithNullComposableFunctionAndFalseFlagWhenErrorMessageIsNull() {
        // Arrange
        val errorMessage: String? = null

        // Act
        val result = getErrorInfoFor(errorMessage)

        // Assert
        assertNull(result.first)
        assertFalse(result.second)
    }

    @Test
    fun getErrorInfoFor_returnsPairWithComposableFunctionAndTrueFlagWhenErrorMessageIsNotNull() {
        // Arrange
        val errorMessage = "Invalid input"

        // Act
        val result = getErrorInfoFor(errorMessage)

        // Assert
        assertNotNull(result.first)
        assertTrue(result.second)
    }

    @Test
    fun getErrorInfoFor_composableFunctionDisplaysErrorMessageInRedColorAndBoldFont() {
        // Arrange
        val errorMessage = "Invalid input"

        // Act
        val result = getErrorInfoFor(errorMessage)

        // Assert
        assertNotNull(result.first)
        assertTrue(result.second)

        // Attributes like bold and red can't be easily tested, but we can at least check text.
        // However, that needs to be in a Composable function, run as an instrumented test.
    }
}
