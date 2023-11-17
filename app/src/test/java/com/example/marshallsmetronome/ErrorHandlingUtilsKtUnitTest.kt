package com.example.marshallsmetronome

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import java.io.IOException

@Suppress("FunctionMaxLength")
class ErrorHandlingUtilsKtUnitTest {

    private lateinit var mockLog: MockedStatic<Log>

    @Before
    fun setUp() {
        mockLog = Mockito.mockStatic(Log::class.java)
    }

    @After
    fun tearDown() {
        mockLog.close()
    }

    // Unit tests for getErrorInfoFor

    @Test
    fun `getErrorInfoFor returns no error info when error message is null`() {
        val result = getErrorInfoFor(null)
        assertFalse(result.hasError)
        assertNull(result.errorMessage)
    }

    @Test
    fun `getErrorInfoFor returns error info when error message is not null`() {
        val result = getErrorInfoFor("Error")
        assertTrue(result.hasError)
        assertEquals("Error", result.errorMessage)
    }

    // Unit tests for reportError

    @Test
    fun `reportError updates errorMessage with generic exception`() {
        val errorMessage = mutableStateOf<String?>(null)
        val exception = Exception("Generic error")

        reportError(errorMessage, exception)

        assertEquals("Generic error", errorMessage.value)
        mockLog.verify { Log.e("AppError", "Error occurred: ", exception) }
    }

    @Test
    fun `reportError updates errorMessage with specific exception type`() {
        val errorMessage = mutableStateOf<String?>(null)
        val specificException = IOException("Specific IO error")

        reportError(errorMessage, specificException)

        assertEquals("Specific IO error", errorMessage.value)
        mockLog.verify { Log.e("AppError", "Error occurred: ", specificException) }
    }

    @Test
    fun `reportError handles null exception message`() {
        val errorMessage = mutableStateOf<String?>(null)
        val exceptionWithNullMessage = Exception(null as String?)

        reportError(errorMessage, exceptionWithNullMessage)

        assertEquals(null, errorMessage.value)
        mockLog.verify { Log.e("AppError", "Error occurred: ", exceptionWithNullMessage) }
    }
}
