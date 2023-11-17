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
 * Calculate the total number of seconds for all cycles in the workout.
 *
 * This function takes the number of cycles and the duration of work and rest
 * intervals per cycle to compute the total duration of the workout program in seconds.
 *
 * @param cycles Number of cycles in the workout program.
 * @param workSecondsPerCycle Duration of the work interval in each cycle, in seconds.
 * @param restSecondsPerCycle Duration of the rest interval in each cycle, in seconds.
 * @return Total duration of the workout program across all cycles, in seconds.
 */
fun getSecondsForAllCycles(
    cycles: Int,
    workSecondsPerCycle: Int,
    restSecondsPerCycle: Int
) = (workSecondsPerCycle + restSecondsPerCycle) * cycles

/**
 * Calculate the duration of the first interval in a workout cycle.
 *
 * This function returns the number of seconds for the first interval,
 * which is typically the work interval at the beginning of each cycle.
 * It assumes that the first interval of each cycle is consistent in duration.
 *
 * @param workSecondsPerCycle Duration of the work interval for each cycle, in seconds.
 * @return Duration of the first interval (work interval) in seconds.
 */
fun getSecondsForFirstInterval(
    workSecondsPerCycle: Int
) = workSecondsPerCycle

