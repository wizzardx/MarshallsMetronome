package com.example.marshallsmetronome

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class MainActivityKtTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun marshallsMetronome() {
        rule.setContent {
            MarshallsMetronome()
        }

        // Type "ABC0123" in the "Cycles" Text Entry box:
        rule.onNodeWithText("Cycles").performTextInput("ABC0123")

        // This is extremely weird, but it looks like the performTextInput doesn't register
        // properly until you click somewhere afterward?
        rule.onNodeWithText("Cycles").performClick()

        // Check that the text is "80123":
        rule.onNodeWithText("Cycles").assertTextContains(
            "80123"
        )
    }
}
