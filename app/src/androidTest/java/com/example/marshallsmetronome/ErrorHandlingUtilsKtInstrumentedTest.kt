package com.example.marshallsmetronome

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@Suppress("FunctionMaxLength")
class ErrorHandlingUtilsKtInstrumentedTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun displayErrorInfo_showsErrorMessage() {
        val errorMessage = "Test Error Message"

        val errorInfo = ErrorInfo(true, errorMessage)

        rule.setContent {
            val composable = displayErrorInfo(errorInfo)
            composable!!.invoke()
        }

        rule
            .onNodeWithText(errorMessage)
            .assertIsDisplayed()
    }

    @Test
    @Suppress("SwallowedException")
    fun displayErrorInfo_doesNotShowErrorMessageWhenNoError() {
        val noErrorMessage = null // or use an empty string ""
        val errorInfo = ErrorInfo(false, noErrorMessage)
        rule.setContent {
            // Create a return object based on the error info
            val composable = displayErrorInfo(errorInfo)
            // There is no error, so the composable should be null:
            assertNull(composable)
        }
    }
}
