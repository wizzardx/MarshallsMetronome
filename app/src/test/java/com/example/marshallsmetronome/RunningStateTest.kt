package com.example.marshallsmetronome

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

@Suppress("FunctionMaxLength")
class RunningStateTest {

    private lateinit var mockPlaySound: (Int) -> Unit
    private lateinit var soundManager: SoundManager

    @Before
    fun setUp() {
        // Mock the playSound function
        mockPlaySound = mock()

        // Create an instance of SoundManager with the mocked function
        soundManager = SoundManager(mockPlaySound)
    }

    private fun newRunningState(coroutineScope: CoroutineScope) =
        RunningState(
            coroutineScope = coroutineScope,
            cycles = 8,
            workSecondsPerCycle = 20,
            restSecondsPerCycle = 10,
            playSound = mockPlaySound
        )

    @Test
    fun initialState_isCorrect() = runTest {
        // val runningState = newRunningState(coroutineScope = this, playSound = mockPlaySound)
        val runningState = newRunningState(coroutineScope = this)
        // Assert initial state values
        val timerState = runningState.timerState.value
        assertEquals(false, timerState.isPaused)
        assertEquals(8 * (20 + 10) * 1000, timerState.millisecondsRemainingInAllCycles)
        assertEquals(IntervalType.Work, timerState.currentIntervalType)
        assertEquals(1, timerState.currentCycleNum)
        assertEquals(20 * 1000, timerState.millisecondsRemainingInCurrentInterval)
        assertEquals(8, timerState.totalCycles)
        assertEquals(20, timerState.workSecondsPerCycle)
        assertEquals(10, timerState.restSecondsPerCycle)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun pause_setsIsPausedTrue() = runTest {
        val runningState = newRunningState(coroutineScope = this)

        // Create a list to record the timer states
        val timerStates = mutableListOf<TimerState>()

        // Launch a coroutine to collect the timer states
        val job = launch {
            runningState.timerState.collect { state ->
                timerStates.add(state)
            }
        }

        // Trigger the pause function
        runningState.pause()

        // Advance time a bit:
        advanceTimeBy(Constants.SmallDelay.toLong())

        // Get state at this point in the logic:
        val lastState = timerStates.last()

        // Assert that isPaused is set to true
        assertTrue("Timer should be paused", lastState.isPaused)

        // Terminate the  logic:
        runningState.shutdown()

        // Advance time a bit:
        advanceTimeBy(Constants.SmallDelay.toLong())

        // Tidy up things at the end:
        job.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun resume_clearsIsPaused() = runTest {
        val runningState = newRunningState(coroutineScope = this)

        // Create a list to record the timer states
        val timerStates = mutableListOf<TimerState>()

        // Launch a coroutine to collect the timer states
        val job = launch {
            runningState.timerState.collect { state ->
                timerStates.add(state)
            }
        }

        // Advance a bit
        advanceTimeBy(Constants.SmallDelay.toLong())

        // Pause the timer to ensure it's in a paused state
        runningState.pause()

        // Advance a bit
        advanceTimeBy(Constants.SmallDelay.toLong())

        // Then, we resume the timer
        runningState.resume()

        // Advance a bit
        advanceTimeBy(Constants.SmallDelay.toLong())

        // Get the last recorded state after the time advancement
        val lastState = timerStates.last()

        // Assert that isPaused is false
        assertFalse("Timer should not be paused", lastState.isPaused)

        // Tidy up things at the end:
        job.cancel()
    }

    @Test
    fun shutdown_cancelsCountdownJob() = runTest {
        val runningState = newRunningState(coroutineScope = this)

        // Call shutdown, which should cancel the job
        runningState.shutdown()

        // Check if the job is no longer active
        assertFalse("Job should no longer be active after shutdown", runningState.isJobActive())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun timerState_changesCorrectly() = runTest {
        val runningState = newRunningState(coroutineScope = this)

        // Get the initial state
        val initialState = runningState.timerState.value

        // Create a list to record the timer states
        val timerStates = mutableListOf<TimerState>()

        // Launch a coroutine to collect the timer states
        val job = launch {
            runningState.timerState.collect { state ->
                timerStates.add(state)
            }
        }

        // Advance time by a specific interval
        val timeToAdvance = 5000L // 5 seconds
        advanceTimeBy(timeToAdvance)

        // Get the last recorded state after the time advancement
        val lastState = timerStates.last()

        // Perform assertions

        // Check if the time remaining in the current interval has decreased by (close to) 5 seconds
        // (this value is extremely implementation-dependent)
        assertEquals(
            (initialState.millisecondsRemainingInCurrentInterval - timeToAdvance).toInt(),
            lastState.millisecondsRemainingInCurrentInterval
        )

        // Check if the time remaining in the current interval has decreased by (close to) 5 seconds
        // (this value is extremely implementation-dependent)
        assertEquals(
            (initialState.millisecondsRemainingInAllCycles - timeToAdvance).toInt(),
            lastState.millisecondsRemainingInAllCycles
        )

        // Tidy up things at the end:
        job.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun playSound_triggeredAtStartOfWorkout() = runTest {
        val mockPlaySound: (Int) -> Unit = mock()
        val runningState = RunningState(
            coroutineScope = this,
            cycles = 8,
            workSecondsPerCycle = 20,
            restSecondsPerCycle = 10,
            playSound = mockPlaySound
        )

        // Trigger the start of the workout
        // This is where the logic for starting the workout would normally be invoked
        // For this test, we'll simulate the start by advancing the coroutine time
        advanceTimeBy(1000) // Advance by 1 second or an appropriate interval

        // Verify that playSound was called with the correct sound resource at the start of the workout
        verify(mockPlaySound).invoke(R.raw.gong) // Assuming R.raw.gong is the start sound resource ID

        // Clean up
        runningState.shutdown()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun playSound_triggeredAtEndOfWorkInterval() = runTest {
        // Initialize RunningState with mockPlaySound
        val runningState = RunningState(
            coroutineScope = this,
            cycles = 8,
            workSecondsPerCycle = 20,
            restSecondsPerCycle = 10,
            playSound = mockPlaySound
        )

        // Start the timer in RunningState
        runningState.resume()

        // Advance time by work interval duration to reach the end of a work interval
        // Since workSecondsPerCycle is 20 seconds, we advance by 20,000 milliseconds
        advanceTimeBy(20 * Constants.MillisecondsPerSecond.toLong())

        // Verify that playSound was called with the correct sound resource ID for the end of work interval
        verify(mockPlaySound).invoke(R.raw.factory_whistle)

        // Clean up
        runningState.shutdown()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun playSound_triggeredAtEndOfRestInterval() = runTest {
        // Initialize RunningState with mockPlaySound
        val runningState = RunningState(
            coroutineScope = this,
            cycles = 8,
            workSecondsPerCycle = 20,
            restSecondsPerCycle = 10,
            playSound = mockPlaySound
        )

        // Start the timer in RunningState
        runningState.resume()

        // First, advance time by work interval duration to reach the end of a work interval
        advanceTimeBy(20 * Constants.MillisecondsPerSecond.toLong())

        // Then, advance time by rest interval duration to reach the end of a rest interval
        // Since restSecondsPerCycle is 10 seconds, we advance by 10,000 milliseconds
        advanceTimeBy(10 * Constants.MillisecondsPerSecond.toLong())

        // Verify that playSound was called with the correct sound resource ID for the end of rest interval
        // The specific sound resource ID will depend on the implementation, for this example, assuming R.raw.buzzer
        verify(mockPlaySound).invoke(R.raw.buzzer) // Assuming R.raw.buzzer is the end-of-rest sound resource ID

        // Clean up
        runningState.shutdown()
    }

    // Add more tests as needed for complete coverage
}
