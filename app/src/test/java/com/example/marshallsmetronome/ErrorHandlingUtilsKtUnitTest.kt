package com.example.marshallsmetronome

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.io.IOException

@Suppress("FunctionMaxLength")
class ErrorHandlingUtilsKtUnitTest {
    private lateinit var mockLog: MockedStatic<Log>

    @Before
    fun setUp() {
        mockLog = mockStatic(Log::class.java)
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

        // Mock FirebaseCrashlytics
        val mockFirebaseCrashlytics = mock<FirebaseCrashlytics>()
        val mockedFirebaseCrashlytics = mockStatic(FirebaseCrashlytics::class.java)
        Mockito.`when`(FirebaseCrashlytics.getInstance()).thenReturn(mockFirebaseCrashlytics)

        reportError(errorMessage, exception)

        assertEquals("Generic error", errorMessage.value)
        mockLog.verify { Log.e("AppError", "Error occurred: ", exception) }
        verify(mockFirebaseCrashlytics).recordException(exception)

        // Clean up
        mockedFirebaseCrashlytics.close()
    }

    @Test
    fun `reportError updates errorMessage with specific exception type`() {
        // Arrange
        val errorMessage = mutableStateOf<String?>(null)
        val specificException = IOException("Specific IO error")

        val mockFirebaseCrashlytics = mock<FirebaseCrashlytics>()
        val mockedFirebaseCrashlytics = mockStatic(FirebaseCrashlytics::class.java)
        Mockito.`when`(FirebaseCrashlytics.getInstance()).thenReturn(mockFirebaseCrashlytics)

        // Act
        reportError(errorMessage, specificException)

        // Assert
        assertEquals("Specific IO error", errorMessage.value)
        mockLog.verify { Log.e("AppError", "Error occurred: ", specificException) }
        verify(mockFirebaseCrashlytics).recordException(specificException)

        // Clean up
        mockedFirebaseCrashlytics.close()
    }

    @Test
    fun `reportError handles null exception message`() {
        val errorMessage = mutableStateOf<String?>(null)
        val exceptionWithNullMessage = Exception(null as String?)

        // Mock FirebaseCrashlytics
        val mockFirebaseCrashlytics = mock<FirebaseCrashlytics>()
        val mockedFirebaseCrashlytics = mockStatic(FirebaseCrashlytics::class.java)
        Mockito.`when`(FirebaseCrashlytics.getInstance()).thenReturn(mockFirebaseCrashlytics)

        reportError(errorMessage, exceptionWithNullMessage)

        assertEquals(null, errorMessage.value)
        mockLog.verify { Log.e("AppError", "Error occurred: ", exceptionWithNullMessage) }
        verify(mockFirebaseCrashlytics).recordException(exceptionWithNullMessage)

        // Clean up
        mockedFirebaseCrashlytics.close()
    }
}
