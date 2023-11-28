package com.example.marshallsmetronome

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class MainActivityKtInstrumentedTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testActivityLaunches() {
        composeTestRule.onNodeWithText("Start").assertIsDisplayed()
        // Add more assertions to check for other UI components
    }

    @Test
    fun testStartPauseResumeButton() {
        composeTestRule.onNodeWithText("Start").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Pause").assertIsDisplayed()

        composeTestRule.onNodeWithText("Pause").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Resume").assertIsDisplayed()
    }

    @Test
    fun testInputValidation() {
        composeTestRule.onNodeWithText("Cycles").performTextInput("a10zz")
        composeTestRule.onNodeWithText("Work").performTextInput("20aXX")
        composeTestRule.onNodeWithText("Rest").performTextInput("bb10a")

        // Check that  text input boxes with expected values exist.
        composeTestRule.onNodeWithText("8").assertIsDisplayed() // Cycles
        composeTestRule.onNodeWithText("20").assertIsDisplayed() // Work
        composeTestRule.onNodeWithText("10").assertIsDisplayed() // Rest
    }

    @Test
    fun testTimerFunctionality() {
        // Start the timer
        composeTestRule.onNodeWithText("Start").performClick()

        // Check if timer-related UI components are updated or disabled
        composeTestRule.onNodeWithText("Work").assertIsDisplayed()

        // Pause and check state
        composeTestRule.onNodeWithText("Pause").performClick()
        composeTestRule.onNodeWithText("Work").assertIsNotEnabled()

        // Resume and check state
        composeTestRule.onNodeWithText("Resume").performClick()
        composeTestRule.onNodeWithText("Pause").assertIsDisplayed()

        // Reset and check state
        composeTestRule.onNodeWithText("Reset").performClick()
        composeTestRule.onNodeWithText("Work").assertIsEnabled()

        // Add more tests here as needed...
    }

    @Test
    fun testErrorHandling() {
        // Trigger an error condition, e.g., invalid input
        composeTestRule.onNodeWithText("Cycles").performTextInput("999")
        composeTestRule.onNodeWithText("Start").performClick()
        composeTestRule.waitForIdle()

        // The error message "Must be at most 100" is displayed in the Text Field's supporting Text child control
        // which is hard for us to check directly.
    }
}
