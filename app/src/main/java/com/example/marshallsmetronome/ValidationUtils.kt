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
 * Normalizes a string input to remove non-numeric characters and leading zeros.
 *
 * This function processes a string input, typically representing a number, and
 * removes any non-digit characters. It also removes leading zeros while
 * preserving the numerical value of the string. If the string does not contain
 * any digits, or only contains zeros, the result is "0". This function is
 * useful for sanitizing and standardizing numerical user inputs, ensuring they
 * are in a format suitable for further processing or conversion to an integer.
 *
 * @param s The string input to be normalized.
 * @return A normalized string with non-numeric characters removed and leading zeros stripped.
 */
fun normaliseIntInput(s: String): String {
    // Go through the string, and remove any non-numeric characters.
    // Don't allow 0 at the start of the number. But if there is no number at the
    // end, then our result is 0
    var result = ""
    var foundNonZero = false
    for (c in s) {
        if (c.isDigit()) {
            if (c != '0') {
                foundNonZero = true
            }
            if (foundNonZero) {
                result += c
            }
        }
    }
    return result
}
