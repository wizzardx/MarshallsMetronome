package com.example.marshallsmetronome

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
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

@Suppress("FunctionMaxLength", "LargeClass")
class MarshallsMetronomeViewModelTest {
    private val mockPlaySound: (Int) -> Unit = mock()

    private lateinit var dispatcher: TestDispatcher
    private lateinit var viewModel: MarshallsMetronomeViewModel
    private lateinit var scheduler: TestCoroutineScheduler
    private lateinit var testScope: CoroutineScope
    private lateinit var timeController: TestTimeController

    private fun clickButton() {
        viewModel.onButtonClick(wait = true)
    }

    private fun clickReset() {
        viewModel.onResetClick(wait = true)
    }

    private suspend fun shortTimeAdvance() {
        timeController.advanceTimeBy(Constants.SmallDelay)
    }

    private suspend fun advanceTestTime(delayTimeMillis: Long) {
        timeController.advanceTimeBy(delayTimeMillis)
    }

    private fun initScope(
        scope: TestScope,
        delayLambda: (suspend (Long) -> Unit),
    ) {
        scheduler = TestCoroutineScheduler()
        dispatcher = StandardTestDispatcher(scheduler)
        testScope = CoroutineScope(dispatcher + Job())
        timeController = TestTimeController(scheduler)
        timeController.setDelayLambda(delayLambda, scope)
        viewModel =
            MarshallsMetronomeViewModel(
                mockPlaySound,
                dispatcher = dispatcher,
                scope = testScope,
                timeController = timeController,
                errorMessage = mutableStateOf(null),
            )
    }

    @Test
    fun `initialState isCorrect`() =
        runTest {
            initScope(this) {
                delay(it)
            }

            assertEquals("8", viewModel.workoutInputs.totalCycles)
            assertEquals("20", viewModel.workoutInputs.secondsWork)
            assertEquals("10", viewModel.workoutInputs.secondsRest)
            assertNull(viewModel.totalCyclesInputError)
            assertNull(viewModel.secondsWorkInputError)
            assertNull(viewModel.secondsRestInputError)

            viewModel.clearResources()
        }

    @Test
    fun `validateInput whenInputIsValid`() =
        runTest {
            initScope(this) {
                delay(it)
            }
            viewModel.workoutInputs.totalCycles = "10"
            viewModel.workoutInputs.secondsWork = "30"
            viewModel.workoutInputs.secondsRest = "15"

            clickButton()

            assertNull(viewModel.totalCyclesInputError)
            assertNull(viewModel.secondsWorkInputError)
            assertNull(viewModel.secondsRestInputError)

            viewModel.clearResources()
        }

    @Test
    fun `validateInput whenInputIsInvalid`() =
        runTest {
            initScope(this) {
                delay(it)
            }

            viewModel.workoutInputs.totalCycles = "0"
            viewModel.workoutInputs.secondsWork = "abc"
            viewModel.workoutInputs.secondsRest = "200"

            clickButton()

            assertEquals("Must be at least 1", viewModel.totalCyclesInputError)
            assertEquals("Invalid number", viewModel.secondsWorkInputError)
            assertEquals("Must be at most 100", viewModel.secondsRestInputError)

            viewModel.clearResources()
        }

    @Test
    fun `onButtonClick startsTimer whenInputIsValid`() =
        runTest {
            initScope(this) {
                delay(it)
            }

            viewModel.workoutInputs.totalCycles = "8"
            viewModel.workoutInputs.secondsWork = "20"
            viewModel.workoutInputs.secondsRest = "10"

            clickButton()

            assertNotNull(viewModel.runningState.value)

            viewModel.clearResources()
            testScope.cancel()
        }

    @Test
    fun `onButtonClick doesNotStartTimer whenInputIsInvalid`() =
        runTest {
            initScope(this) {
                delay(it)
            }
            viewModel.workoutInputs.totalCycles = "0"
            clickButton()
            assertNull(viewModel.runningState.value)
            viewModel.clearResources()
        }

    @Test
    fun `onResetClick stopsTimer`() =
        runTest {
            initScope(this) {
                delay(it)
            }
            clickButton() // Start the timer
            clickReset() // Stop the timer (and clear internal state, etc).
            assertNull(viewModel.runningState.value)
            viewModel.clearResources()
        }

    @Test
    fun `Pressing reset button clears error message`() =
        runTest {
            // Arrange
            initScope(this) {
                delay(it)
            }
            viewModel.errorMessage!!.value = "Error message"

            // Act
            clickReset()

            // Assert
            assertNull(viewModel.errorMessage!!.value)
        }

    @Test
    fun `formatTotalTimeRemainingString returnsCorrectFormat`() =
        runTest {
            initScope(this) {
                delay(it)
            }

            // Set any valid interval durations
            viewModel.workoutInputs.totalCycles = "5" // 5 cycles
            viewModel.workoutInputs.secondsWork = "30" // 30 seconds for work interval
            viewModel.workoutInputs.secondsRest = "15" // 15 seconds for rest interval
            viewModel.workoutInputs.secondsWarmup = "60" // 60 seconds for warmup
            viewModel.workoutInputs.secondsCooldown = "60" // 60 seconds for cooldown

            // Start the timer
            clickButton()

            // Fetch the formatted total time remaining
            val formattedTotalTimeRemaining = viewModel.formatTotalTimeRemainingString()

            // Check if the result matches the MM:SS format
            val isCorrectFormat = formattedTotalTimeRemaining.matches(Regex("\\d{2}:\\d{2}"))

            // Assert the format is correct
            assertTrue("Formatted total time remaining should be in MM:SS format", isCorrectFormat)

            // Cleanup
            viewModel.clearResources()
        }

    @Test
    fun `playSound isCalledOnStart`() =
        runTest {
            initScope(this) {
                delay(it)
            }

            // Create a list to record the timer states
            val timerStates = mutableListOf<TimerState>()

            // Launch a coroutine to collect the timer states
            val job =
                launch {
                    viewModel.runningState.value?.timerState?.collect { state ->
                        timerStates.add(state)
                    }
                }

            // Click the 'start button:
            clickButton()

            // Advance time a bit:
            shortTimeAdvance()

            // Run our check:
            verify(mockPlaySound).invoke(R.raw.gong)

            // Tidy up things at the end:
            viewModel.clearResources()
            job.cancel()
            job.join()
        }

    @Test
    fun `playSound isNotCalledBeforeStart`() =
        runTest {
            initScope(this) {
                delay(it)
            }

            // Create a list to record the timer states
            val timerStates = mutableListOf<TimerState>()

            // Launch a coroutine to collect the timer states
            val job =
                launch {
                    viewModel.runningState.value?.timerState?.collect { state ->
                        timerStates.add(state)
                    }
                }

            // Advance time a bit:
            shortTimeAdvance()

            verify(mockPlaySound, never()).invoke(R.raw.gong)

            // Tidy up things at the end:
            job.cancel()
            job.join()
            viewModel.clearResources()
        }

    // Unit tests for buttonText:

    @Test
    fun `buttonText is 'Start' when there is no running state`() =
        runTest {
            initScope(this) {
                delay(it)
            }

            // Ensure runningState is null
            viewModel.clearResources()

            // Check buttonText
            assertEquals("Start", viewModel.buttonText)
        }

    @Test
    fun `buttonText is 'Pause' when timer is running`() =
        runTest {
            initScope(this) {
                delay(it)
            }

            // Mock starting the timer
            clickButton()

            // Check buttonText
            assertEquals("Pause", viewModel.buttonText)

            viewModel.clearResources()
            testScope.cancel()
        }

    @Test
    fun `buttonText is 'Resume' when timer is paused`() =
        runTest {
            initScope(this) {
                delay(it)
            }

            // Create a list to record the timer states
            val timerStates = mutableListOf<TimerState>()

            // Launch a coroutine to collect the timer states
            val job =
                launch {
                    viewModel.runningState.value?.timerState?.collect { state ->
                        timerStates.add(state)
                    }
                }

            // Mock starting and pausing the timer

            // MOo - get label on text at start:
            clickButton() // Start
            clickButton() // Pause

            // Check buttonText
            assertEquals("Resume", viewModel.buttonText)

            // Tidy up things at the end:
            viewModel.clearResources()
            job.cancel()
            job.join()
            testScope.cancel()
        }

    // Tests for textInputControlsEnabled

    @Test
    fun `textInputControlsEnabled is true when there is no running state`() =
        runTest {
            initScope(this) {
                delay(it)
            }

            // Ensure runningState is null
            viewModel.clearResources()

            // Check textInputControlsEnabled
            assertTrue("Text inputs should be enabled in initial state", viewModel.textInputControlsEnabled)
        }

    @Test
    fun `textInputControlsEnabled is false when timer is running`() =
        runTest {
            initScope(this) {
                delay(it)
            }

            // Mock starting the timer
            clickButton()

            // Check textInputControlsEnabled
            assertFalse("Text inputs should be disabled when timer is running", viewModel.textInputControlsEnabled)

            viewModel.clearResources()
        }

    @Test
    fun `textInputControlsEnabled is false when timer is paused`() =
        runTest {
            initScope(this) {
                delay(it)
            }

            // Create a list to record the timer states
            val timerStates = mutableListOf<TimerState>()

            // Launch a coroutine to collect the timer states
            val job =
                launch {
                    viewModel.runningState.value?.timerState?.collect { state ->
                        timerStates.add(state)
                    }
                }

            // Mock starting and pausing the timer
            clickButton() // Start
            clickButton() // Pause

            // Check textInputControlsEnabled
            assertFalse("Text inputs should be disabled when timer is paused", viewModel.textInputControlsEnabled)

            // Tidy up things at the end:
            viewModel.clearResources()
            job.cancel()
            job.join()
        }

    // Tests for formatTotalTimeRemainingString

    @Test
    fun `formatTotalTimeRemainingString with default values`() =
        runTest {
            initScope(this) {
                delay(it)
            }
            assertEquals("06:00", viewModel.formatTotalTimeRemainingString())
            viewModel.clearResources()
        }

    @Test
    fun `formatTotalTimeRemainingString with valid custom input`() =
        runTest {
            initScope(this) {
                delay(it)
            }

            // Set custom intervals including warmup and cooldown
            viewModel.workoutInputs.totalCycles = "10" // Total 10 cycles
            viewModel.workoutInputs.secondsWork = "30" // 30 seconds for work interval
            viewModel.workoutInputs.secondsRest = "15" // 15 seconds for rest interval
            viewModel.workoutInputs.secondsWarmup = "60" // 60 seconds for warmup
            viewModel.workoutInputs.secondsCooldown = "60" // 60 seconds for cooldown

            // Start the timer
            clickButton()

            // Calculate total workout time
            val totalWorkoutTime = viewModel.getTotalWorkoutSeconds() * 1000L

            // Simulate part of the workout
            val partialWorkoutTime = 5 * 60 * 1000L // Simulate 5 minutes of the workout
            advanceTestTime(partialWorkoutTime)

            // Fetch the formatted total time remaining
            val formattedTotalTimeRemaining = viewModel.formatTotalTimeRemainingString()

            // Calculate expected remaining time
            val expectedRemainingTime = totalWorkoutTime - partialWorkoutTime
            val expectedFormattedTotalTimeRemaining = formatMinSec((expectedRemainingTime / 1000).safeToInt())

            // Assert the formatted total time remaining
            assertEquals(
                "Formatted total time remaining should match the expected value",
                expectedFormattedTotalTimeRemaining,
                formattedTotalTimeRemaining,
            )

            // Cleanup
            viewModel.clearResources()
            testScope.cancel()
        }

    @Test
    fun `formatTotalTimeRemainingString with invalid input`() =
        runTest {
            initScope(this) {
                delay(it)
            }

            // Set invalid values for cycles, work, rest, warm-up, and cooldown times
            viewModel.workoutInputs.totalCycles = "invalid_number"
            viewModel.workoutInputs.secondsWork = "-abc"
            viewModel.workoutInputs.secondsRest = "another_invalid_number"
            viewModel.workoutInputs.secondsWarmup = "invalid_warmup" // Invalid warm-up time
            viewModel.workoutInputs.secondsCooldown = "zzzz"

            // Start the timer
            clickButton()

            // Expected format for total time remaining when input is invalid
            // Usually, this would be zero, assuming that the ViewModel handles invalid input gracefully
            val expectedFormattedTime = "00:00"

            // Assert that the formatted remaining time is as expected
            assertEquals(expectedFormattedTime, viewModel.formatTotalTimeRemainingString())

            // Cleanup
            viewModel.clearResources()
            testScope.cancel()
        }

    @Test
    fun `formatTotalTimeRemainingString with edge cases`() =
        runTest {
            initScope(this) {
                delay(it)
            }

            // Set interval durations to edge cases
            viewModel.workoutInputs.totalCycles = "1" // Minimum number of cycles
            viewModel.workoutInputs.secondsWork = "1" // Minimum duration for work interval
            viewModel.workoutInputs.secondsRest = "1" // Minimum duration for rest interval
            viewModel.workoutInputs.secondsWarmup = "0" // Minimum duration for warmup (no warmup)
            viewModel.workoutInputs.secondsCooldown = "0" // Minimum duration for cooldown (no cooldown)

            // Start the timer
            clickButton()
            advanceTestTime(1) // prime things so we get into the main body of the timer loop.

            // Simulate the workout for a brief period, less than the total duration
            advanceTestTime(500) // Advance by 0.5 seconds

            // Fetch the formatted total time remaining
            val formattedTotalTimeRemaining = viewModel.formatTotalTimeRemainingString()

            // Calculate expected remaining time
            val totalWorkoutTime = 2 * 1000L // 1 second work + 1 second rest
            val expectedRemainingTime = totalWorkoutTime - 500 // Subtract the advanced time
            val expectedFormattedTotalTimeRemaining = formatMinSec((expectedRemainingTime / 1000).safeToInt())

            // Assert the formatted total time remaining
            assertEquals(
                "Formatted total time remaining should match the expected value for edge cases",
                expectedFormattedTotalTimeRemaining,
                formattedTotalTimeRemaining,
            )

            // Cleanup
            viewModel.clearResources()
        }

    @Test
    fun `formatTotalTimeRemainingString during timer running`() =
        runTest {
            initScope(this) {
                delay(it)
            }

            // Set intervals including warmup and cooldown
            viewModel.workoutInputs.totalCycles = "8" // 8 cycles
            viewModel.workoutInputs.secondsWork = "20" // 20 seconds for work interval
            viewModel.workoutInputs.secondsRest = "10" // 10 seconds for rest interval
            viewModel.workoutInputs.secondsWarmup = "60" // 60 seconds for warmup
            viewModel.workoutInputs.secondsCooldown = "60" // 60 seconds for cooldown

            // Start the timer
            clickButton()

            // Simulate partway through the workout
            val elapsedWorkoutTime = 5 * 60 * 1000L // 5 minutes
            advanceTestTime(elapsedWorkoutTime)

            // Fetch the formatted total time remaining
            val formattedTotalTimeRemaining = viewModel.formatTotalTimeRemainingString()

            // Calculate the expected total time remaining
            val totalTimeForWorkout = (60 + ((20 + 10) * 8) + 60) * 1000L // Warmup + (Work + Rest cycles) + Cooldown
            val expectedRemainingTime = totalTimeForWorkout - elapsedWorkoutTime
            val expectedFormattedTotalTimeRemaining = formatMinSec((expectedRemainingTime / 1000).safeToInt())

            // Assert the formatted total time remaining
            assertEquals(
                "Formatted total time remaining should match the expected value during timer running",
                expectedFormattedTotalTimeRemaining,
                formattedTotalTimeRemaining,
            )

            // Cleanup
            viewModel.clearResources()
        }

    @Test
    fun `formatTotalTimeRemainingString after timer pause`() =
        runTest {
            initScope(this) {
                delay(it)
            }

            // Set initial values for cycles, work, rest, warm-up, and cooldown times
            viewModel.workoutInputs.totalCycles = "8"
            viewModel.workoutInputs.secondsWork = "20"
            viewModel.workoutInputs.secondsRest = "10"
            viewModel.workoutInputs.secondsWarmup = "60" // 1 minute warm-up
            viewModel.workoutInputs.secondsCooldown = "60" // 1 minute cooldown

            // Start the timer
            clickButton()

            // Pause the timer
            clickButton()

            // Calculate expected remaining time
            val totalWorkoutTime = viewModel.getTotalWorkoutSeconds() * 1000L

            // The time might be calculated over here to be something like "06:00", but if the timer
            // has been even briefly active, then it will really display "05:59" instead, as part of
            // an existing workaround. We make that adaptation here:
            val expectedRemainingTime = totalWorkoutTime - 1_000

            // Format expected remaining time in MM:SS format using the standalone formatMinSec function
            val expectedFormattedTime = formatMinSec((expectedRemainingTime / 1000).safeToInt())

            // Assert that the formatted remaining time is as expected
            val found = viewModel.formatTotalTimeRemainingString()
            assertEquals(expectedFormattedTime, found)

            // Cleanup
            viewModel.clearResources()
            testScope.cancel()
        }

    @Test
    fun `formatTotalTimeRemainingString format consistency`() =
        runTest {
            initScope(this) {
                delay(it)
            }

            viewModel.workoutInputs.totalCycles = "5"
            viewModel.workoutInputs.secondsWork = "15"
            viewModel.workoutInputs.secondsRest = "10"

            val result = viewModel.formatTotalTimeRemainingString()

            // Check if the result matches the MM:SS format
            assert(result.matches(Regex("\\d{2}:\\d{2}")))

            // Cleanup
            viewModel.clearResources()
        }

    // Tests for formatCurrentCycleNumber

    @Test
    fun `formatCurrentCycleNumber returns correct cycle at start of workout`() =
        runTest {
            initScope(this) {
                delay(it)
            }

            // Setup initial values
            viewModel.workoutInputs.totalCycles = "8" // Total 8 cycles

            // Start the timer
            clickButton()

            // The cycle number at the start should be "1/8"
            val expectedFormat = "1/8"
            val currentCycleFormat = viewModel.formatCurrentCycleNumber()

            assertEquals("Cycle format at start should be 1/8", expectedFormat, currentCycleFormat)

            // Cleanup
            viewModel.clearResources()
        }

    @Test
    fun `formatCurrentCycleNumber returns correct cycle in the middle of workout`() =
        runTest {
            initScope(this) {
                delay(it)
            }

            // Setup workout configuration
            viewModel.workoutInputs.totalCycles = "8" // Total 8 cycles
            viewModel.workoutInputs.secondsWork = "20" // 20 seconds work interval
            viewModel.workoutInputs.secondsRest = "10" // 10 seconds rest interval
            viewModel.workoutInputs.secondsWarmup = "1" // 1 second for warm-up
            viewModel.workoutInputs.secondsCooldown = "1" // 1 second for cooldown

            // Start the timer
            clickButton()

            // Calculate the time to advance to the middle of the workout (4th cycle)
            // Time for 3 complete cycles plus warm-up: 3 * (20s work + 10s rest) + 1s warm-up
            val timeToAdvance =
                (
                    3 *
                        (viewModel.workoutInputs.secondsWork.toInt() + viewModel.workoutInputs.secondsRest.toInt()) +
                        viewModel.workoutInputs.secondsWarmup.toInt()
                    ) * 1000L // Convert to milliseconds

            // Advance the timer by the time to advance
            advanceTestTime(timeToAdvance)

            // Fetch the formatted cycle number
            val formattedCycleNumber = viewModel.formatCurrentCycleNumber()

            // Expected cycle number in the middle of the workout should be "3/8"
            val expectedFormattedCycleNumber = "3/8"

            // Assert the formatted cycle number
            assertEquals(
                "Formatted cycle number should match the middle of the workout",
                expectedFormattedCycleNumber,
                formattedCycleNumber,
            )

            // Cleanup
            viewModel.clearResources()
        }

    @Test
    fun `formatCurrentCycleNumber returns correct cycle towards end of workout`() =
        runTest {
            initScope(this) {
                delay(it)
            }

            // Set interval durations including warmup and cooldown
            viewModel.workoutInputs.secondsWork = "20" // 20 seconds for work interval
            viewModel.workoutInputs.secondsRest = "10" // 10 seconds for rest interval
            viewModel.workoutInputs.secondsWarmup = "60" // 60 seconds for warmup
            viewModel.workoutInputs.secondsCooldown = "60" // 60 seconds for cooldown
            viewModel.workoutInputs.totalCycles = "8" // Total 8 cycles

            // Start the timer
            clickButton()

            // Simulate the workout duration up to the last cycle
            // Warmup + (Work + Rest) * Total Cycles - 1 (to stay in the last cycle)
            val totalWorkoutTimeBeforeLastCycle = 60 + (20 + 10) * (8 - 1)
            advanceTestTime(totalWorkoutTimeBeforeLastCycle * 1000L)

            // Fetch the formatted cycle number
            val formattedCycleNumber = viewModel.formatCurrentCycleNumber()

            // Expected cycle number towards the end of the workout should be "7/8"
            val expectedFormattedCycleNumber = "7/8"

            // Assert the formatted cycle number
            assertEquals(
                "Formatted cycle number should match the last cycle towards the end of the workout",
                expectedFormattedCycleNumber,
                formattedCycleNumber,
            )

            // Cleanup
            viewModel.clearResources()
            testScope.cancel()
        }

    // Tests for onButtonClick

    @Test
    fun `onButtonClick starts timer with valid input`() =
        runTest {
            initScope(this) {
                delay(it)
            }

            viewModel.workoutInputs.totalCycles = "8"
            viewModel.workoutInputs.secondsWork = "20"
            viewModel.workoutInputs.secondsRest = "10"

            clickButton()

            assertNotNull(viewModel.runningState.value)

            // Cleanup
            viewModel.clearResources()
        }

    @Test
    fun `onButtonClick does not start timer with invalid input`() =
        runTest {
            initScope(this) {
                delay(it)
            }

            viewModel.workoutInputs.totalCycles = "abc"

            clickButton()

            assertNull(viewModel.runningState.value)

            // Cleanup
            viewModel.clearResources()
        }

    @Test
    fun `onButtonClick pauses timer when running`() =
        runTest {
            initScope(this) {
                delay(it)
            }

            // Create a list to record the timer states
            val timerStates = mutableListOf<TimerState>()

            // Launch a coroutine to collect the timer states
            val job =
                launch {
                    viewModel.runningState.value?.timerState?.collect { state ->
                        timerStates.add(state)
                    }
                }

            clickButton() // Start the timer
            advanceTestTime(100)

            clickButton() // Pause the timer
            advanceTestTime(100)

            assertTrue(viewModel.runningState.value?.isPaused() == true)

            // Tidy up things at the end:
            viewModel.clearResources()
            job.cancel()
            job.join()
        }

    @Test
    fun `onButtonClick resumes timer when paused`() =
        runTest {
            initScope(this) {
                delay(it)
            }

            clickButton() // Start the timer
            clickButton() // Pause the timer
            clickButton() // Resume the timer

            assertFalse(viewModel.runningState.value?.isPaused() == true)

            viewModel.clearResources()
        }

    @Test
    fun `onButtonClick does not affect ongoing timer when called without input changes`() =
        runTest {
            initScope(this) {
                delay(it)
            }

            clickButton() // Start the timer

            val initialState = viewModel.runningState.value

            clickButton() // Pause the timer

            clickButton() // Resume the timer

            val resumedState = viewModel.runningState.value

            assertEquals(initialState, resumedState)

            viewModel.clearResources()
            testScope.cancel()
        }

    @Test
    fun `onButtonClick validates input correctly`() =
        runTest {
            initScope(this) {
                delay(it)
            }

            viewModel.workoutInputs.totalCycles = "-1"

            clickButton()

            assertEquals("Must be at least 1", viewModel.totalCyclesInputError)

            viewModel.clearResources()
            testScope.cancel()
        }

    @Test
    fun `onButtonClick restarts timer with new inputs after stopping`() =
        runTest {
            initScope(this) {
                delay(it)
            }

            clickButton() // Start the timer

            clickReset() // Pause the timer

            viewModel.workoutInputs.totalCycles = "10"
            viewModel.workoutInputs.secondsWork = "30"
            clickButton() // Start with new settings

            assertEquals("10", viewModel.workoutInputs.totalCycles)
            assertEquals("30", viewModel.workoutInputs.secondsWork)

            viewModel.clearResources()
        }

    @Test
    fun `onButtonClick handles edge cases properly`() =
        runTest {
            initScope(this) {
                delay(it)
            }

            viewModel.workoutInputs.totalCycles = "100" // Test maximum allowed cycles
            viewModel.workoutInputs.secondsWork = "60" // Test maximum allowed work seconds

            clickButton()

            assertNotNull(viewModel.runningState.value)

            viewModel.clearResources()
            testScope.cancel()
        }

    @Test
    fun `onButtonClick resumes timer after being paused`() =
        runTest {
            initScope(this) {
                delay(it)
            }

            // Initialize the ViewModel with default values
            viewModel.workoutInputs.totalCycles = "8"
            viewModel.workoutInputs.secondsWork = "20"
            viewModel.workoutInputs.secondsRest = "10"

            // Start the timer
            assertFalse(viewModel.timerStarted())
            clickButton()
            assertTrue(viewModel.timerStarted())

            // Pause the timer
            assertFalse(viewModel.isTimerPausedOrFail())
            clickButton()
            assertTrue(viewModel.isTimerPausedOrFail())

            // Resume the timer
            assertTrue(viewModel.isTimerPausedOrFail())
            clickButton()
            assertFalse(viewModel.isTimerPausedOrFail())

            viewModel.clearResources()
        }

    // Tests for formatCurrentStageTime

    @Test
    fun `formatCurrentStageTime returns expected messages during workout`() =
        runTest {
            initScope(this) {
                delay(it)
            }

            // By default we start within the warmup phase.
            assertEquals("Warmup: 01:00", viewModel.formatCurrentStageTime())

            // Just after the click, the message should be the same:
            clickButton()
            assertEquals("Warmup: 00:59", viewModel.formatCurrentStageTime())

            // Advance time by 61 seconds, this should take us to the start of the work interval:
            advanceTestTime(61 * 1000L)
            assertEquals("Work: 00:19", viewModel.formatCurrentStageTime())

            // Advancing time by 19 seconds, this brings us to the very beginning of the work interval:
            advanceTestTime(19 * 1000L)
            assertEquals("Work: 00:00", viewModel.formatCurrentStageTime())

            // Go one second further, this takes us to the (visible) start of the rest interval:
            advanceTestTime(1 * 1000L)
            assertEquals("Rest: 00:09", viewModel.formatCurrentStageTime())

            // There are 8 cycles in this workout. Skip through another 7 cycles, and this should
            // bring us back to very close to the same point in the cycle:
            advanceTestTime((20 + 10) * 7 * 1000L)
            assertEquals("Rest: 00:09", viewModel.formatCurrentStageTime())

            // Try advancing by 10 seconds, that should leave us at the start of the cooldown period:
            advanceTestTime(10 * 1000L)
            assertEquals("Cooldown: 00:59", viewModel.formatCurrentStageTime())

            // Advance another 65 seconds, that should bring us to the end of the cooldown period,
            // and also into the "workout ended" state:
            advanceTestTime(65 * 1000L)
            assertEquals("Cooldown: 00:00", viewModel.formatCurrentStageTime())

            viewModel.clearResources()
        }
}
