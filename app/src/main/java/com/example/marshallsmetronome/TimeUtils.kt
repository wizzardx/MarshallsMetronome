/**
 * Functions related to time manipulation and formatting.
 */

package com.example.marshallsmetronome

import java.util.Locale

/**
 * Formats a total number of seconds into a minutes:seconds string.
 *
 * This function converts an integer representing a total duration in seconds
 * into a formatted string displaying minutes and remaining seconds. The format
 * used is MM:SS, where MM is minutes and SS is seconds, both padded with zeros
 * if necessary. This is useful for displaying time durations in a user-friendly
 * format.
 *
 * @param totalSeconds The total duration in seconds to be formatted.
 * @return A string formatted as MM:SS representing the input duration.
 */
fun formatMinSec(totalSeconds: Int): String {
    val minutes = totalSeconds / Constants.SecondsPerMinute
    val seconds = totalSeconds % Constants.SecondsPerMinute
    return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
}

/**
 * Calculate the total number of seconds for all cycles in the workout, including warmup and cooldown.
 *
 * @param cycles Number of cycles in the workout program.
 * @param workSecondsPerCycle Duration of the work interval in each cycle, in seconds.
 * @param restSecondsPerCycle Duration of the rest interval in each cycle, in seconds.
 * @param warmupSeconds Duration of the warmup period, in seconds.
 * @param cooldownSeconds Duration of the cooldown period, in seconds.
 * @return Total duration of the workout program, including all cycles, warmup, and cooldown, in seconds.
 */
fun getSecondsForEntireWorkout(
    cycles: Int,
    workSecondsPerCycle: Int,
    restSecondsPerCycle: Int,
    warmupSeconds: Int,
    cooldownSeconds: Int,
): Int {
    val totalSecondsPerCycle = workSecondsPerCycle + restSecondsPerCycle
    val totalSecondsForCycles = totalSecondsPerCycle * cycles
    return warmupSeconds + totalSecondsForCycles + cooldownSeconds
}
