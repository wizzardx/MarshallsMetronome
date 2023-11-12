package com.example.marshallsmetronome

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UtilsKtTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun testGetErrorInfoForWithError() {
        val errorMessage = "Test Error Message"

        rule.setContent {
            val (composable, isError) = getErrorInfoFor(errorMessage)

            // Assert that the error is true
            assertTrue(isError)

            // Invoke the composable lambda, it must also be none-null by this point
            composable!!.invoke()
        }

        // Check that the composable has a text node, and the value of the text is the error message.
        rule.onNodeWithText(errorMessage).assertIsDisplayed()
    }
}
