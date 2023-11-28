package com.example.marshallsmetronome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@Suppress("FunctionMaxLength")
class RunningStateKtTest {
    private lateinit var initialState: TimerState
    private lateinit var soundManager: SoundManager
    private val mockPlaySound: (Int) -> Unit = { }

    @Before
    fun setUp() {
        // Initialize with a standard state
        initialState =
            TimerState(
                millisecondsRemainingForEntireWorkout = 360_000,
                isPaused = false,
                currentIntervalType = IntervalType.Work,
                currentCycleNum = 1,
                millisecondsRemaining = 20_000,
                totalCycles = 8,
                workSecondsPerCycle = 20,
                restSecondsPerCycle = 10,
                workoutStage = WorkoutStage.Warmup,
                warmupTotalSeconds = 60,
                cooldownTotalSeconds = 60,
            )
        soundManager = SoundManager(mockPlaySound)
    }

    // Tests for updateTotalRemainingTime

    @Test
    fun `test updateTotalRemainingTime Normal Time Update`() {
        val elapsedTime = 1000L // Example elapsed time
        val newState = updateTotalRemainingTime(initialState, elapsedTime)
        assertEquals(
            initialState.millisecondsRemainingForEntireWorkout - elapsedTime,
            newState.millisecondsRemainingForEntireWorkout.toLong(),
        )
    }

    @Test
    fun `test updateTotalRemainingTime Exact Zero Remaining`() {
        val elapsedTime = initialState.millisecondsRemainingForEntireWorkout.toLong()
        val newState = updateTotalRemainingTime(initialState, elapsedTime)
        assertEquals(0, newState.millisecondsRemainingForEntireWorkout)
    }

    @Test
    fun `test updateTotalRemainingTime NegativeTime Remaining`() {
        val elapsedTime = initialState.millisecondsRemainingForEntireWorkout + 1000L
        val newState = updateTotalRemainingTime(initialState, elapsedTime)
        assertEquals(0, newState.millisecondsRemainingForEntireWorkout)
    }

    @Test
    fun `test updateTotalRemainingTime No Time Remaining Initially`() {
        val initialStateWithZeroTime = initialState.copy(millisecondsRemainingForEntireWorkout = 0)
        val elapsedTime = 1000L
        val newState = updateTotalRemainingTime(initialStateWithZeroTime, elapsedTime)
        assertEquals(0, newState.millisecondsRemainingForEntireWorkout)
    }

    @Test
    fun `test updateTotalRemainingTime Large Elapsed Time`() {
        val elapsedTime = initialState.millisecondsRemainingForEntireWorkout + 1_000_000L
        val newState = updateTotalRemainingTime(initialState, elapsedTime)
        assertEquals(0, newState.millisecondsRemainingForEntireWorkout)
    }

    // Tests for handleWarmUp

    @Test
    fun `test handleWarmUp Normal WarmUp Decrement`() {
        val elapsedTime = 1000L // 1 second
        val newState = handleWarmUp(initialState, elapsedTime)

        assertEquals(initialState.millisecondsRemaining - elapsedTime, newState.millisecondsRemaining.toLong())
        assertEquals(WorkoutStage.Warmup, newState.workoutStage)
    }

    @Test
    fun `test handleWarmUp WarmUp Ends`() {
        val elapsedTime = initialState.millisecondsRemaining.toLong() + Constants.MillisecondsPerSecond
        val newState = handleWarmUp(initialState, elapsedTime)

        assertEquals(19_000, newState.millisecondsRemaining)
        assertEquals(WorkoutStage.MainWorkout, newState.workoutStage)
        assertEquals(
            (
                initialState.workSecondsPerCycle - 1
                ) * Constants.MillisecondsPerSecond,
            newState.millisecondsRemaining,
        )
    }

    @Test
    fun `test handleWarmup we go partly into the work interval`() {
        val elapsedTime = initialState.millisecondsRemaining.toLong() + 1000
        val newState = handleWarmUp(initialState, elapsedTime)

        assertEquals(19_000, newState.millisecondsRemaining)
        assertEquals(WorkoutStage.MainWorkout, newState.workoutStage)
        assertEquals(
            (initialState.workSecondsPerCycle - 1) * Constants.MillisecondsPerSecond,
            newState.millisecondsRemaining,
        )
    }

    // Tests for handleMainWorkout

    @Test
    fun `test interval time decrement and leftover time calculation`() {
        val elapsedTime = 15_000L // 15 seconds elapsed

        // Convert to be in the work interval
        var newState =
            initialState.copy(
                workoutStage = WorkoutStage.MainWorkout,
                millisecondsRemaining = initialState.workSecondsPerCycle * Constants.MillisecondsPerSecond,
                millisecondsRemainingForEntireWorkout = 359_000,
            )

        newState = handleMainWorkout(newState, soundManager, elapsedTime)

        assertEquals(5000, newState.millisecondsRemaining)
    }

    @Test
    fun `test interval end with negative remaining time`() {
        val elapsedTime = 25_000L // 25 seconds elapsed, more than the interval time

        // Convert to be in the work interval
        var newState =
            initialState.copy(
                workoutStage = WorkoutStage.MainWorkout,
                millisecondsRemaining = initialState.workSecondsPerCycle * Constants.MillisecondsPerSecond,
                millisecondsRemainingForEntireWorkout = 359_000,
            )

        newState = handleMainWorkout(newState, soundManager, elapsedTime)

        // The above logic transports us from the work interval, into the next interval, with 5
        // (out of 10 total)  seconds remaining
        assertEquals(5000, newState.millisecondsRemaining)
    }

    @Test
    fun `test workout end transition to cooldown`() {
        // Convert to be in the work interval
        var newState =
            initialState.copy(
                workoutStage = WorkoutStage.MainWorkout,
                millisecondsRemaining = initialState.workSecondsPerCycle * Constants.MillisecondsPerSecond,
                millisecondsRemainingForEntireWorkout = 359_000,
            )

        val endOfWorkoutState =
            newState.copy(
                currentCycleNum = newState.totalCycles,
                currentIntervalType = Constants.LastIntervalTypeInCycle,
                millisecondsRemaining = 0,
            )
        val elapsedTime = 1000L

        newState = handleMainWorkout(endOfWorkoutState, soundManager, elapsedTime)

        assertEquals(WorkoutStage.Cooldown, newState.workoutStage)
    }

    @Test
    fun `test interval end and not workout end`() {
        // Convert to be in the work interval
        var newState =
            initialState.copy(
                workoutStage = WorkoutStage.MainWorkout,
                currentIntervalType = IntervalType.Work, // Ensure it's not the last interval type in the cycle
                millisecondsRemaining = initialState.workSecondsPerCycle * Constants.MillisecondsPerSecond,
                millisecondsRemainingForEntireWorkout = 359_000,
                currentCycleNum = initialState.totalCycles - 1, // Ensure it's not the last cycle
            )

        // exactly the length of the interval, plus one second
        val elapsedTime = (initialState.workSecondsPerCycle + 1) * Constants.MillisecondsPerSecond

        newState = handleMainWorkout(newState, soundManager, elapsedTime.toLong())

        assertEquals(9000, newState.millisecondsRemaining) // Interval just ended, but got an updated value.

        // Not the end of the workout
        assertFalse(
            newState.currentCycleNum == newState.totalCycles &&
                newState.currentIntervalType == Constants.LastIntervalTypeInCycle,
        )
        // Additional assertions...
    }

    // Tests for handleCooldown

    @Test
    fun `test handleCooldown - cooldown time decrements normally`() {
        val modifiedState = initialState.copy(millisecondsRemaining = 60_000) // 1 minute
        val elapsedTime = 10_000L // 10 seconds
        val newState = handleCooldown(modifiedState, elapsedTime, soundManager)
        assertEquals(50_000, newState.millisecondsRemaining) // 50 seconds remaining
    }

    @Test
    fun `test handleCooldown - transition to workout ended state when cooldown ends`() {
        // Modify the existing initialState for the test scenario
        // Ensure the workoutStage is set to Cooldown and set the cooldown time to a specific value
        val testState =
            initialState.copy(
                workoutStage = WorkoutStage.Cooldown, // Set the stage to Cooldown using the enum class name
                millisecondsRemaining = 30_000, // 30 seconds for cooldown
                millisecondsRemainingForEntireWorkout = 30_000, // 30 seconds remaining for the entire workout.
            )

        // Define the elapsedTime to be equal to the entire cooldown period + 1 second.
        val elapsedTime = 31_000L // 31 seconds

        // Call the handleCooldown function with the test state and the elapsedTime
        val newState = handleCooldown(testState, elapsedTime, soundManager)

        // Assert that the newState's workoutStage is now WorkoutEnded
        assertEquals(WorkoutStage.WorkoutEnded, newState.workoutStage)
    }

    @Test
    fun `test handleCooldown - cooldown time does not go negative`() {
        val modifiedState = initialState.copy(millisecondsRemaining = 15_000) // 15 seconds
        val excessTime = 20_000L // 20 seconds, 5 seconds more than cooldown time
        val newState = handleCooldown(modifiedState, excessTime, soundManager)
        assertEquals(0, newState.millisecondsRemaining) // Cooldown time clamps at 0
    }

    // Additional helper methods and constants as needed...
}
