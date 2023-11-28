/*
 * Functions dealing with error reporting and handling.
 */

package com.example.marshallsmetronome

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/**
 * Reports an exception using the centralized ErrorHandler.
 *
 * This function is a convenience wrapper around ErrorHandler.handleError. It allows
 * other parts of the application to report exceptions without directly interacting
 * with ErrorHandler.
 *
 * @param errorMessage The mutable state for updating the UI with the error message.
 * @param exception The exception to be reported.
 */
fun reportError(
    errorMessage: MutableState<String?>?,
    exception: Throwable,
) {
    ErrorHandler.handleError(errorMessage, exception)
}

/**
 * Creates an `ErrorInfo` object based on the presence of an error message.
 *
 * This function checks if there is an error message and constructs an `ErrorInfo`
 * object accordingly. It sets the `hasError` flag to `true` if an error message
 * exists, and `false` otherwise. The function encapsulates the state of an error
 * (whether there is an error and what the error message is) into a single object.
 *
 * @param error The error message string. Can be `null` if there is no error.
 * @return An `ErrorInfo` object encapsulating the error state and message.
 */
fun getErrorInfoFor(error: String?) = ErrorInfo(error != null, error)

/**
 * Data class representing information about an error.
 *
 * This class holds data related to an error, including a flag indicating whether
 * an error has occurred and the corresponding error message. It is typically used
 * to convey error information across different parts of the application, such as
 * from a function where an error occurs to a UI component that displays the error.
 *
 * @property hasError A boolean flag indicating whether an error has occurred.
 * @property errorMessage The error message associated with the error, if any.
 */
data class ErrorInfo(val hasError: Boolean, val errorMessage: String?)

/**
 * Displays an error message in the UI if an error has occurred.
 *
 * This composable function takes an `ErrorInfo` object and, based on its content,
 * displays an error message on the UI. If `ErrorInfo` indicates that an error has
 * occurred (`hasError` is `true`), it renders the error message. Otherwise, it
 * does not render anything. This function is typically used within UI compositions
 * to conditionally display error messages based on the current error state.
 *
 * @param errorInfo The `ErrorInfo` object containing error state and message.
 * @return A composable function that displays the error message if an error exists.
 */
@Composable
fun displayErrorInfo(errorInfo: ErrorInfo): @Composable (() -> Unit)? {
    return if (errorInfo.hasError) {
        { Text(text = errorInfo.errorMessage ?: "", color = Color.Red, fontWeight = FontWeight.Bold) }
    } else {
        null
    }
}
