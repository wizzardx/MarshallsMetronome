/**
 * Functions related to input validation and normalization.
 */

package com.example.marshallsmetronome

/**
 * Validates a string input to ensure it represents a valid integer within specified bounds.
 *
 * This function checks if the input string can be converted to an integer and
 * whether it falls within the predefined bounds of 1 and `Constants.MaxUserInputNum`
 * (inclusive). It is primarily used to validate user inputs for settings like
 * cycle duration, work intervals, or rest intervals in the application. If the
 * input is not a valid integer or falls outside the acceptable range, an
 * appropriate error message is returned. Otherwise, the function returns null,
 * indicating a valid input.
 *
 * @param value The string input to be validated.
 * @return A string containing an error message if the input is invalid, or null if the input is valid.
 */
fun validateIntInput(value: String): String? {
    val intVal = value.toIntOrNull()
    return when {
        intVal == null -> "Invalid number"
        intVal < 1 -> "Must be at least 1"
        intVal > Constants.MaxUserInputNum -> "Must be at most 100"
        else -> null // valid input
    }
}

/**
 * Normalizes a string input to ensure it contains only numeric digits.
 *
 * This function checks the provided `input` string and returns it if it's either
 * empty or contains only digit characters. If `input` contains any non-digit
 * characters, the function returns the `orig` string, which represents the
 * original or previous value.
 *
 * @param input The string to be normalized.
 * @param orig The original string to revert to if `input` is invalid.
 * @return A string that is either the normalized input or the original string.
 */
fun normaliseIntInput(
    input: String,
    orig: String,
): String {
    return if (input.isEmpty() || input.all { it.isDigit() }) {
        input // Use newText if it's empty or all digits
    } else {
        orig // Retain the previous value of text if the input contains non-digit characters
    }
}

/**
 * Safely casts a Long value to an Int.
 * Throws an ArithmeticException if the Long value is outside the range of Int.
 *
 * @return The Int value corresponding to this Long.
 * @throws ArithmeticException if this value is not within the range of Int.
 */
fun Long.safeToInt(): Int {
    if (this < Int.MIN_VALUE || this > Int.MAX_VALUE) {
        throw ArithmeticException("Long value $this cannot be cast to Int without data loss.")
    }
    return this.toInt()
}
