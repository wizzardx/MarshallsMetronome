package com.example.marshallsmetronome

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@Suppress("FunctionMaxLength")
class MarshallsMetronomeViewModelTest {

    private val mockPlaySound: (Int) -> Unit = mock()

    private fun newViewModel(coroutineScope: CoroutineScope) =
        MarshallsMetronomeViewModel(mockPlaySound, coroutineScope = coroutineScope)

    @Test
    fun `initialState isCorrect`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)

        assertEquals("8", viewModel.totalCyclesInput)
        assertEquals("20", viewModel.secondsWorkInput)
        assertEquals("10", viewModel.secondsRestInput)
        assertNull(viewModel.totalCyclesInputError)
        assertNull(viewModel.secondsWorkInputError)
        assertNull(viewModel.secondsRestInputError)
    }

    @Test
    fun `validateInput whenInputIsValid`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)

        viewModel.totalCyclesInput = "10"
        viewModel.secondsWorkInput = "30"
        viewModel.secondsRestInput = "15"

        viewModel.onButtonClick()

        assertNull(viewModel.totalCyclesInputError)
        assertNull(viewModel.secondsWorkInputError)
        assertNull(viewModel.secondsRestInputError)
    }

    @Test
    fun `validateInput whenInputIsInvalid`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)

        viewModel.totalCyclesInput = "0"
        viewModel.secondsWorkInput = "abc"
        viewModel.secondsRestInput = "200"

        viewModel.onButtonClick()

        assertEquals("Must be at least 1", viewModel.totalCyclesInputError)
        assertEquals("Invalid number", viewModel.secondsWorkInputError)
        assertEquals("Must be at most 100", viewModel.secondsRestInputError)
    }

    @Test
    fun `onButtonClick startsTimer whenInputIsValid`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)

        viewModel.totalCyclesInput = "8"
        viewModel.secondsWorkInput = "20"
        viewModel.secondsRestInput = "10"

        viewModel.onButtonClick()

        assertNotNull(viewModel.runningState.value)
    }

    @Test
    fun `onButtonClick doesNotStartTimer whenInputIsInvalid`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)
        viewModel.totalCyclesInput = "0"
        viewModel.onButtonClick()
        assertNull(viewModel.runningState.value)
    }

    @Test
    fun `onResetClick stopsTimer`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)
        viewModel.onButtonClick() // Start the timer
        viewModel.onResetClick() // Reset the timer
        assertNull(viewModel.runningState.value)
    }

    @Test
    fun `formatTotalTimeRemainingString returnsCorrectFormat`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)
        assertEquals(
            "04:00",
            viewModel.formatTotalTimeRemainingString()
        ) // 8 cycles of 20s work + 10s rest
    }

    @Test
    fun `formatCurrentIntervalTime returnsCorrectFormat`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)
        viewModel.onButtonClick() // Start the timer
        assertEquals("Work: 19", viewModel.formatCurrentIntervalTime())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `playSound isCalledOnStart`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)

        // Create a list to record the timer states
        val timerStates = mutableListOf<TimerState>()

        // Launch a coroutine to collect the timer states
        val job = launch {
            viewModel.runningState.value?.timerState?.collect { state ->
                timerStates.add(state)
            }
        }

        // Click the 'start button:
        viewModel.onButtonClick()

        // Advance time a bit:
        advanceTimeBy(Constants.SmallDelay.toLong())

        // Run our check:
        verify(mockPlaySound).invoke(R.raw.gong)

        // Tidy up things at the end:
        job.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `playSound isNotCalledBeforeStart`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)

        // Create a list to record the timer states
        val timerStates = mutableListOf<TimerState>()

        // Launch a coroutine to collect the timer states
        val job = launch {
            viewModel.runningState.value?.timerState?.collect { state ->
                timerStates.add(state)
            }
        }

        // Advance time a bit:
        advanceTimeBy(Constants.SmallDelay.toLong())

        verify(mockPlaySound, never()).invoke(R.raw.gong)

        // Tidy up things at the end:
        job.cancel()
    }

    // Unit tests for buttonText:

    @Test
    fun `buttonText is 'Start' when there is no running state`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)

        // Ensure runningState is null
        viewModel.clearResources()

        // Check buttonText
        assertEquals("Start", viewModel.buttonText)
    }

    @Test
    fun `buttonText is 'Pause' when timer is running`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)

        // Mock starting the timer
        viewModel.onButtonClick()

        // Check buttonText
        assertEquals("Pause", viewModel.buttonText)
    }

    @Test
    fun `buttonText is 'Resume' when timer is paused`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)

        // Create a list to record the timer states
        val timerStates = mutableListOf<TimerState>()

        // Launch a coroutine to collect the timer states
        val job = launch {
            viewModel.runningState.value?.timerState?.collect { state ->
                timerStates.add(state)
            }
        }

        // Mock starting and pausing the timer
        viewModel.onButtonClick() // Start
        viewModel.onButtonClick() // Pause

        // Check buttonText
        assertEquals("Resume", viewModel.buttonText)

        // Tidy up things at the end:
        viewModel.clearResources()
        job.cancel()
    }

    // Tests for textInputControlsEnabled

    @Test
    fun `textInputControlsEnabled is true when there is no running state`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)

        // Ensure runningState is null
        viewModel.clearResources()

        // Check textInputControlsEnabled
        assertTrue("Text inputs should be enabled in initial state", viewModel.textInputControlsEnabled)
    }

    @Test
    fun `textInputControlsEnabled is false when timer is running`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)

        // Mock starting the timer
        viewModel.onButtonClick()

        // Check textInputControlsEnabled
        assertFalse("Text inputs should be disabled when timer is running", viewModel.textInputControlsEnabled)
    }

    @Test
    fun `textInputControlsEnabled is false when timer is paused`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)

        // Create a list to record the timer states
        val timerStates = mutableListOf<TimerState>()

        // Launch a coroutine to collect the timer states
        val job = launch {
            viewModel.runningState.value?.timerState?.collect { state ->
                timerStates.add(state)
            }
        }

        // Mock starting and pausing the timer
        viewModel.onButtonClick() // Start
        viewModel.onButtonClick() // Pause

        // Check textInputControlsEnabled
        assertFalse("Text inputs should be disabled when timer is paused", viewModel.textInputControlsEnabled)

        // Tidy up things at the end:
        viewModel.clearResources()
        job.cancel()
    }

    // Tests for formatTotalTimeRemainingString

    @Test
    fun `formatTotalTimeRemainingString with default values`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)
        assertEquals("04:00", viewModel.formatTotalTimeRemainingString())
    }

    @Test
    fun `formatTotalTimeRemainingString with valid custom input`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)

        viewModel.totalCyclesInput = "10"
        viewModel.secondsWorkInput = "30"
        viewModel.secondsRestInput = "20"

        assertEquals("08:20", viewModel.formatTotalTimeRemainingString())
    }

    @Test
    fun `formatTotalTimeRemainingString with invalid input`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)

        viewModel.totalCyclesInput = "abc"
        viewModel.secondsWorkInput = "-1"
        viewModel.secondsRestInput = "101"

        assertEquals("00:00", viewModel.formatTotalTimeRemainingString())
    }

    @Test
    fun `formatTotalTimeRemainingString with edge cases`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)

        viewModel.totalCyclesInput = "1"
        viewModel.secondsWorkInput = "1"
        viewModel.secondsRestInput = "1"

        assertEquals("00:02", viewModel.formatTotalTimeRemainingString())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `formatTotalTimeRemainingString during timer running`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)

        // Setup initial state
        viewModel.totalCyclesInput = "8"
        viewModel.secondsWorkInput = "20"
        viewModel.secondsRestInput = "10"

        // Start the timer
        viewModel.onButtonClick()

        // Advance the coroutine timer
        // For instance, advance 5 minutes (300 seconds)
        advanceTimeBy(300 * 1000L)

        // Get the formatted remaining time
        val formattedTime = viewModel.formatTotalTimeRemainingString()

        // Calculate expected remaining time
        // Total time = 8 cycles * (20s work + 10s rest) = 240s
        // Time elapsed = 300s
        // Expected remaining time = 240s - 300s = -60s (but it should not go below 0)
        val expectedFormattedTime = "00:00" // As the time elapsed is more than total time

        // Assert
        assertEquals("Formatted time should match expected remaining time", expectedFormattedTime, formattedTime)

        // Cleanup
        viewModel.clearResources()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `formatTotalTimeRemainingString after timer pause`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)

        // Setup initial state
        viewModel.totalCyclesInput = "8"
        viewModel.secondsWorkInput = "20"
        viewModel.secondsRestInput = "10"

        // Start the timer
        viewModel.onButtonClick()

        // Advance the coroutine timer for a certain period, say 2 minutes (120 seconds)
        advanceTimeBy(120 * 1000L)

        // Pause the timer
        viewModel.onButtonClick()

        // Get the formatted remaining time after pause
        val formattedTime = viewModel.formatTotalTimeRemainingString()

        // Calculate expected remaining time
        // Total time for 8 cycles = 8 * (20s work + 10s rest) = 240s
        // Time elapsed = 120s
        // Expected remaining time = 240s - 120s = 120s
        val expectedFormattedTime = "02:00" // Remaining time in MM:SS format

        // Assert
        assertEquals("Formatted time should match expected remaining time", expectedFormattedTime, formattedTime)

        // Cleanup
        viewModel.clearResources()
    }

    @Test
    fun `formatTotalTimeRemainingString format consistency`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)

        viewModel.totalCyclesInput = "5"
        viewModel.secondsWorkInput = "15"
        viewModel.secondsRestInput = "10"

        val result = viewModel.formatTotalTimeRemainingString()

        // Check if the result matches the MM:SS format
        assert(result.matches(Regex("\\d{2}:\\d{2}")))
    }

    // Tests for formatCurrentIntervalTime

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `formatCurrentIntervalTime returns correct time during work interval`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)

        // Assuming the default state is a work interval
        viewModel.secondsWorkInput = "20" // 20 seconds for work interval

        // Start the timer
        viewModel.onButtonClick()

        // Advance the coroutine timer by 5 seconds
        advanceTimeBy(5 * 1000L)

        // Invoke formatCurrentIntervalTime
        val formattedTime = viewModel.formatCurrentIntervalTime()

        // Expected format "Work: 15"
        val expectedFormattedTime = "Work: 15"

        // Assert
        assertEquals("Formatted time should match expected remaining work time", expectedFormattedTime, formattedTime)

        // Cleanup
        viewModel.clearResources()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `formatCurrentIntervalTime returns correct time during rest interval`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)

        // Set intervals
        viewModel.secondsWorkInput = "20" // 20 seconds for work interval
        viewModel.secondsRestInput = "10" // 10 seconds for rest interval

        // Start the timer
        viewModel.onButtonClick()

        // Advance the coroutine timer to end of work interval and into rest interval
        advanceTimeBy(21 * 1000L) // 20 seconds for work + 1 second into rest

        // Invoke formatCurrentIntervalTime
        val formattedTime = viewModel.formatCurrentIntervalTime()

        // Expected format "Rest: 9"
        val expectedFormattedTime = "Rest: 9"

        // Assert
        assertEquals("Formatted time should match expected remaining rest time", expectedFormattedTime, formattedTime)

        // Cleanup
        viewModel.clearResources()
    }

    // Tests for formatCurrentCycleNumber

    @Test
    fun `formatCurrentCycleNumber returns correct cycle at start of workout`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)

        // Setup initial values
        viewModel.totalCyclesInput = "8" // Total 8 cycles

        // Start the timer
        viewModel.onButtonClick()

        // The cycle number at the start should be "1/8"
        val expectedFormat = "1/8"
        val currentCycleFormat = viewModel.formatCurrentCycleNumber()

        assertEquals("Cycle format at start should be 1/8", expectedFormat, currentCycleFormat)

        // Cleanup
        viewModel.clearResources()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `formatCurrentCycleNumber returns correct cycle in the middle of workout`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)

        // Setup initial values
        viewModel.totalCyclesInput = "8" // Total 8 cycles
        viewModel.secondsWorkInput = "20" // 20 seconds for work interval
        viewModel.secondsRestInput = "10" // 10 seconds for rest interval

        // Start the timer
        viewModel.onButtonClick()

        // Advance the timer to mid-workout, e.g., at cycle 5
        // Each cycle = 20s work + 10s rest = 30s, so 5 completed cycles = 120s
        advanceTimeBy(120 * 1000L)

        // The cycle number mid-workout should be "5/8"
        val expectedFormat = "5/8"
        val currentCycleFormat = viewModel.formatCurrentCycleNumber()

        assertEquals("Cycle format mid-workout should be 5/8", expectedFormat, currentCycleFormat)

        // Cleanup
        viewModel.clearResources()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `formatCurrentCycleNumber returns correct cycle towards end of workout`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)

        // Setup initial values
        viewModel.totalCyclesInput = "8" // Total 8 cycles
        viewModel.secondsWorkInput = "20" // 20 seconds for work interval
        viewModel.secondsRestInput = "10" // 10 seconds for rest interval

        // Start the timer
        viewModel.onButtonClick()

        // Advance the timer towards the end of the workout, e.g., at cycle 8
        // Each cycle = 30s, so 8 completed cycles = 210s
        advanceTimeBy(210 * 1000L)

        // The cycle number towards the end should be 8/8"
        val expectedFormat = "8/8"
        val currentCycleFormat = viewModel.formatCurrentCycleNumber()

        assertEquals("Cycle format towards end should be 8/8", expectedFormat, currentCycleFormat)

        // Cleanup
        viewModel.clearResources()
    }

    // Tests for onButtonClick

    @Test
    fun `onButtonClick starts timer with valid input`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)

        viewModel.totalCyclesInput = "8"
        viewModel.secondsWorkInput = "20"
        viewModel.secondsRestInput = "10"

        viewModel.onButtonClick()

        assertNotNull(viewModel.runningState.value)
    }

    @Test
    fun `onButtonClick does not start timer with invalid input`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)

        viewModel.totalCyclesInput = "abc"

        viewModel.onButtonClick()

        assertNull(viewModel.runningState.value)
    }

    @Test
    fun `onButtonClick pauses timer when running`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)

        // Create a list to record the timer states
        val timerStates = mutableListOf<TimerState>()

        // Launch a coroutine to collect the timer states
        val job = launch {
            viewModel.runningState.value?.timerState?.collect { state ->
                timerStates.add(state)
            }
        }

        viewModel.onButtonClick() // Start the timer
        viewModel.onButtonClick() // Pause the timer

        assertTrue(viewModel.runningState.value?.isPaused() == true)

        // Tidy up things at the end:
        viewModel.clearResources()
        job.cancel()
    }

    @Test
    fun `onButtonClick resumes timer when paused`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)

        viewModel.onButtonClick() // Start the timer
        viewModel.onButtonClick() // Pause the timer
        viewModel.onButtonClick() // Resume the timer

        assertFalse(viewModel.runningState.value?.isPaused() == true)
    }

    @Test
    fun `onButtonClick does not affect ongoing timer when called without input changes`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)

        viewModel.onButtonClick() // Start the timer

        val initialState = viewModel.runningState.value

        viewModel.onButtonClick() // Pause the timer
        viewModel.onButtonClick() // Resume the timer

        val resumedState = viewModel.runningState.value

        assertEquals(initialState, resumedState)
    }

    @Test
    fun `onButtonClick validates input correctly`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)

        viewModel.totalCyclesInput = "-1"

        viewModel.onButtonClick()

        assertEquals("Must be at least 1", viewModel.totalCyclesInputError)
    }

    @Test
    fun `onButtonClick restarts timer with new inputs after stopping`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)

        viewModel.onButtonClick() // Start the timer
        viewModel.onResetClick() // Reset the timer

        viewModel.totalCyclesInput = "10"
        viewModel.secondsWorkInput = "30"
        viewModel.onButtonClick() // Start with new settings

        assertEquals("10", viewModel.totalCyclesInput)
        assertEquals("30", viewModel.secondsWorkInput)
    }

    @Test
    fun `onButtonClick handles edge cases properly`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)

        viewModel.totalCyclesInput = "100" // Test maximum allowed cycles
        viewModel.secondsWorkInput = "60" // Test maximum allowed work seconds

        viewModel.onButtonClick()

        assertNotNull(viewModel.runningState.value)
    }

    @Test
    fun `onButtonClick resumes timer after being paused`() = runTest {
        val viewModel = newViewModel(coroutineScope = this)

        // Initialize the ViewModel with default values
        viewModel.totalCyclesInput = "8"
        viewModel.secondsWorkInput = "20"
        viewModel.secondsRestInput = "10"

        // Start the timer
        assertFalse(viewModel.timerStarted())
        viewModel.onButtonClick()
        assertTrue(viewModel.timerStarted())

        // Pause the timer
        assertFalse(viewModel.isTimerPausedOrFail())
        viewModel.onButtonClick()
        assertTrue(viewModel.isTimerPausedOrFail())

        // Resume the timer
        assertTrue(viewModel.isTimerPausedOrFail())
        viewModel.onButtonClick()
        assertFalse(viewModel.isTimerPausedOrFail())
    }
}
