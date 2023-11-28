/*
 * Functions specifically related to managing and calculating intervals in your workout or timer logic.
 */

package com.example.marshallsmetronome

/**
 * Determines if the current state represents the end of a cycle.
 *
 * This function checks if the current interval type is the last interval in a cycle,
 * which is typically the rest interval. It's used to identify when to transition from
 * one cycle to the next in the workout program.
 *
 * @param state The current TimerState instance containing information about the timer.
 * @return True if the current interval type is the last interval in a cycle, False otherwise.
 */
fun isCycleEnd(state: TimerState) = state.currentIntervalType == Constants.LastIntervalTypeInCycle

/**
 * Determines the type of the next interval in the workout cycle.
 *
 * This function alternates the interval type between work and rest. If the current interval
 * type is work, it will return rest for the next interval, and vice versa. This alternation
 * helps in managing the transition between work and rest intervals in each cycle of the workout.
 *
 * @param currentType The type of the current interval, either work or rest.
 * @return The interval type for the next interval.
 */
fun getNextIntervalType(currentType: IntervalType) =
    // Logic to switch interval type
    if (currentType == IntervalType.Work) IntervalType.Rest else IntervalType.Work

/**
 * Calculates the duration in milliseconds for the next interval.
 *
 * Based on the type of the next interval (work or rest), this function returns the
 * appropriate duration in milliseconds. It uses the predefined work and rest intervals
 * per cycle to calculate this duration. This is essential for timing each interval accurately
 * within the workout cycle.
 *
 * @param nextIntervalType The type of the next interval, either work or rest.
 * @param workSecondsPerCycle The duration of the work interval per cycle, in seconds.
 * @param restSecondsPerCycle The duration of the rest interval per cycle, in seconds.
 * @return The duration of the next interval in milliseconds.
 */
fun getNextIntervalMilliseconds(
    nextIntervalType: IntervalType,
    workSecondsPerCycle: Int,
    restSecondsPerCycle: Int,
) = when (nextIntervalType) {
    IntervalType.Work -> workSecondsPerCycle * Constants.MillisecondsPerSecond
    IntervalType.Rest -> restSecondsPerCycle * Constants.MillisecondsPerSecond
}

/**
 * Calculates the number of the next cycle in the workout session.
 *
 * This function determines the cycle number that follows the current cycle
 * within the workout program. It ensures the cycle number does not exceed
 * the total number of cycles specified for the workout. If the current cycle
 * is the last one, this function will return the maximum cycle number,
 * effectively indicating the end of the workout cycles.
 *
 * @param state The current state of the timer, which includes details about
 *              the current cycle number and total cycles.
 * @return The cycle number for the next cycle, which will be the same as the
 *         total cycles if the current cycle is the last one.
 */
fun getNextCycleNumber(state: TimerState) =
    // Increment cycle number, ensuring it does not exceed total cycles
    (state.currentCycleNum + 1).coerceAtMost(state.totalCycles)
