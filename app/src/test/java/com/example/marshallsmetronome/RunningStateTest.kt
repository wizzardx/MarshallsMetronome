package com.example.marshallsmetronome

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.util.concurrent.TimeUnit

class TestTimeController(
    private val scheduler: TestCoroutineScheduler,
) : BaseTimeController() {
    private var timeMillis = 0L

    // Convert time from milliseconds to nanos and return:
    override fun nanoTime(): Long = TimeUnit.MILLISECONDS.toNanos(timeMillis)

    override suspend fun delay(
        delayTimeMillis: Long,
        scope: CoroutineScope,
    ) {
        super.delay(delayTimeMillis, scope)
        timeMillis += delayTimeMillis
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun advanceTimeBy(delayTimeMillis: Long) {
        scheduler.advanceTimeBy(delayTimeMillis)
    }
}

@Suppress("FunctionMaxLength")
class RunningStateTest {
    private lateinit var mockPlaySound: (Int) -> Unit
    private lateinit var soundManager: SoundManager
    private lateinit var userActionChannel: Channel<UserAction>
    private lateinit var timeController: TestTimeController
    private lateinit var scheduler: TestCoroutineScheduler
    private lateinit var dispatcher: CoroutineDispatcher
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        // Mock the playSound function
        mockPlaySound = mock()

        // Create an instance of SoundManager with the mocked function
        soundManager = SoundManager(mockPlaySound)

        // Initialize the channels before each test
        userActionChannel = Channel(Channel.CONFLATED)

        scheduler = TestCoroutineScheduler()
        dispatcher = StandardTestDispatcher(scheduler)
        scope = CoroutineScope(dispatcher + Job())
        timeController = TestTimeController(scheduler)
    }

    @After
    fun tearDown() {
        // Close the channel after each test
        userActionChannel.close()
    }

    private fun newRunningState(): RunningState {
        return RunningState(
            workoutConfig =
            WorkoutConfig(
                cycles = 8,
                workSecondsPerCycle = 20,
                restSecondsPerCycle = 10,
                warmupSeconds = 60,
                cooldownSeconds = 60,
            ),
            playSoundProp = mockPlaySound,
            dispatcher = dispatcher,
            scope = scope,
            timeController = timeController,
        )
    }

    @Test
    fun initialState_isCorrect() =
        runTest {
            val runningState = newRunningState()

            // Assert initial state values
            val timerState = runningState.timerState.value

            // Assert that the timer is not paused initially
            assertFalse("Timer should not be paused initially", timerState.isPaused)

            // Calculate the total milliseconds for the entire workout including warmup and cooldown
            val totalWorkoutMilliseconds =
                (
                    runningState.workoutConfig.warmupSeconds +
                        runningState.workoutConfig.cycles *
                        (
                            runningState.workoutConfig.workSecondsPerCycle +
                                runningState.workoutConfig.restSecondsPerCycle
                            ) +
                        runningState.workoutConfig.cooldownSeconds
                    ) * Constants.MillisecondsPerSecond

            // Assert total milliseconds remaining for the entire workout
            assertEquals(
                "Incorrect total milliseconds for entire workout",
                totalWorkoutMilliseconds,
                timerState.millisecondsRemainingForEntireWorkout,
            )

            // Assert the workout starts with warm-up phase
            assertEquals("Workout should start with warm-up phase", WorkoutStage.Warmup, timerState.workoutStage)

            // Assert the correct duration for the warm-up period
            assertEquals(
                "Incorrect warm-up duration",
                runningState.workoutConfig.warmupSeconds * Constants.MillisecondsPerSecond,
                timerState.millisecondsRemaining,
            )

            // Assert initial cycle number
            assertEquals("Initial cycle number should be 1", 1, timerState.currentCycleNum)

            // Assert the initial interval type should be work (but only after warm-up completes)
            assertEquals(
                "Initial interval type after warm-up should be work",
                IntervalType.Work,
                timerState.currentIntervalType,
            )

            // Assert correct initial milliseconds remaining in the warmup period
            assertEquals(
                "Incorrect milliseconds in the initial warmup period",
                runningState.workoutConfig.warmupSeconds * Constants.MillisecondsPerSecond,
                timerState.millisecondsRemaining,
            )

            // Assert the correct number of total cycles
            assertEquals("Incorrect total cycles", runningState.workoutConfig.cycles, timerState.totalCycles)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun pause_setsIsPausedTrue() =
        runTest {
            val runningState = newRunningState()

            // Create a list to record the timer states
            val timerStates = mutableListOf<TimerState>()

            // Launch a coroutine to collect the timer states
            val job =
                launch {
                    runningState.timerState.collect { state ->
                        timerStates.add(state)
                    }
                }

            // Trigger the pause function:
            pauseTimer(runningState, timeController)

            // Advance time a bit:
            advanceTimeBy(Constants.SmallDelay)

            // Get state at this point in the logic:
            val lastState = timerStates.last()

            // Assert that isPaused is set to true
            assertTrue("Timer should be paused", lastState.isPaused)

            // Terminate the  logic:
            runningState.shutdown()

            // Advance time a bit:
            advanceTimeBy(Constants.SmallDelay)

            // Tidy up things at the end:
            job.cancel()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun resume_clearsIsPaused() =
        runTest {
            val runningState = newRunningState()

            // Create a list to record the timer states
            val timerStates = mutableListOf<TimerState>()

            // Launch a coroutine to collect the timer states
            val job =
                launch {
                    runningState.timerState.collect { state ->
                        timerStates.add(state)
                    }
                }

            // Advance a bit
            advanceTimeBy(Constants.SmallDelay)

            // Pause the timer to ensure it's in a paused state
            pauseTimer(runningState, timeController)

            // Advance a bit
            advanceTimeBy(Constants.SmallDelay)

            // Then, we resume the timer
            resumeTimer(runningState, timeController)

            // Advance a bit
            advanceTimeBy(Constants.SmallDelay)

            // Get the last recorded state after the time advancement
            val lastState = timerStates.last()

            // Assert that isPaused is false
            assertFalse("Timer should not be paused", lastState.isPaused)

            // Tidy up things at the end:
            runningState.shutdown()
            job.cancel()
        }

    @Test
    fun shutdown_cancelsCountdownJob() =
        runTest {
            val runningState = newRunningState()

            // Call shutdown, which should cancel the job
            runningState.shutdown()

            // Check if the job is no longer active
            assertFalse("Job should no longer be active after shutdown", runningState.isJobActive())
        }

    @Test
    fun timerState_changesCorrectly() =
        runTest {
            val runningState = newRunningState()

            // Get the initial state
            val initialState = runningState.timerState.value

            // Create a list to record the timer states
            val timerStates = mutableListOf<TimerState>()

            // Launch a coroutine to collect the timer states
            val job =
                scope.launch {
                    runningState.timerState.collect { state ->
                        timerStates.add(state)
                    }
                }

            // Advance time by a specific interval considering timer imprecision and warm-up period
            val targetTimeToAdvance = 5000L // Target: 5 seconds
            val warmupTime = initialState.millisecondsRemaining
            val totalTargetTime = warmupTime + targetTimeToAdvance
            val tickGranularity = Constants.SmallDelay // 100ms
            val timeBuffer = 100L // Additional buffer to ensure passing the granularity threshold
            val adjustedTimeToAdvance = totalTargetTime + timeBuffer - (totalTargetTime + timeBuffer) % tickGranularity
            timeController.advanceTimeBy(adjustedTimeToAdvance)

            // Get the last recorded state after the time advancement
            val lastState = timerStates.last()

            // Perform assertions

            // Check if the warm-up time has passed
            assertEquals("Warm-up period should be completed", WorkoutStage.MainWorkout, lastState.workoutStage)
            assertEquals("Warm-up period should be completed", IntervalType.Work, lastState.currentIntervalType)
            assertEquals("Warm-up period should be completed", 15_000, lastState.millisecondsRemaining)

            // Check if the total time remaining for the entire workout has decreased by at least the target time
            val minExpectedRemainingForEntireWorkout =
                initialState.millisecondsRemainingForEntireWorkout - totalTargetTime
            assertTrue(lastState.millisecondsRemainingForEntireWorkout <= minExpectedRemainingForEntireWorkout)

            // Tidy up things at the end:
            job.cancel()
            scope.cancel()
        }

    @Test
    fun playSound_triggeredAtStartOfWorkout() =
        runTest {
            val runningState = newRunningState()

            // Trigger the start of the workout
            // This is where the logic for starting the workout would normally be invoked
            // For this test, we'll simulate the start by advancing the coroutine time
            timeController.advanceTimeBy(1000) // Advance by 1 second or an appropriate interval

            // Verify that playSound was called with the correct sound resource at the start of the workout
            verify(mockPlaySound).invoke(R.raw.gong) // Assuming R.raw.gong is the start sound resource ID

            // Clean up
            runningState.shutdown()
            scope.cancel()
        }

    @Test
    fun playSound_triggeredAtEndOfWorkInterval() =
        runTest {
            val runningState = newRunningState()

            // Start the timer in RunningState
            resumeTimer(runningState, timeController)

            // Advance time to cover the warmup period and the work interval
            // Total time = warmup time + work interval
            val totalTimeToAdvance =
                runningState.workoutConfig.warmupSeconds *
                    Constants.MillisecondsPerSecond +
                    runningState.workoutConfig.workSecondsPerCycle * Constants.MillisecondsPerSecond

            // Add a buffer to ensure we cross the interval end
            val buffer = 100L
            timeController.advanceTimeBy(totalTimeToAdvance + buffer)

            // Verify that playSound was called with the correct sound resource ID at the end of the work interval
            verify(mockPlaySound).invoke(R.raw.factory_whistle)

            // Clean up
            runningState.shutdown()
            scope.cancel()
        }

    @Test
    fun playSound_triggeredAtEndOfRestInterval() =
        runTest {
            val runningState = newRunningState()

            // Start the timer in RunningState
            resumeTimer(runningState, timeController)

            // Calculate the time to advance to reach the end of the first rest interval:
            // Total time = warmup time + first work interval + first rest interval
            val totalTimeToAdvance =
                runningState.workoutConfig.warmupSeconds * Constants.MillisecondsPerSecond +
                    runningState.workoutConfig.workSecondsPerCycle * Constants.MillisecondsPerSecond +
                    runningState.workoutConfig.restSecondsPerCycle * Constants.MillisecondsPerSecond

            // Add a buffer to ensure we cross the interval end
            val buffer = 100L
            timeController.advanceTimeBy(totalTimeToAdvance + buffer)

            // Verify that playSound was called with the correct sound resource ID for the end of the rest interval
            // Assuming R.raw.buzzer is the sound resource ID for the end of the rest interval
            verify(mockPlaySound).invoke(R.raw.buzzer)

            // Clean up
            runningState.shutdown()
        }

    @Test
    fun playSound_triggeredAtStartOfWorkInterval() =
        runTest {
            val runningState = newRunningState()

            // Start the timer in RunningState
            resumeTimer(runningState, timeController)

            // Advance time to cover the warmup period
            val totalTimeToAdvance =
                runningState.workoutConfig.warmupSeconds * Constants.MillisecondsPerSecond

            // Add a buffer so that we cross over into the start of the interval:
            val buffer = 200L
            timeController.advanceTimeBy(totalTimeToAdvance + buffer)

            // Verify that playSound was called with the correct sound resource ID at the start of the work interval
            verify(mockPlaySound).invoke(R.raw.airhorn)

            // Clean up
            runningState.shutdown()
            scope.cancel()
        }

    @Test
    fun playSound_triggeredAtEndOfCooldownPeriod() =
        runTest {
            val runningState = newRunningState()

            // Start the timer in RunningState
            resumeTimer(runningState, timeController)

            // Advance past the warmup, all the phases, and also the cooldown:
            val totalTimeToAdvance =
                (
                    runningState.workoutConfig.warmupSeconds +
                        runningState.workoutConfig.cycles *
                        (
                            runningState.workoutConfig.workSecondsPerCycle +
                                runningState.workoutConfig.restSecondsPerCycle
                            ) +
                        runningState.workoutConfig.cooldownSeconds
                    ) * Constants.MillisecondsPerSecond

            // Add a buffer so that we cross over into the start of the interval:
            val buffer = 200L
            timeController.advanceTimeBy(totalTimeToAdvance + buffer)

            // Verify that playSound was called with the correct sound resource ID at the start of the work interval
            verify(mockPlaySound).invoke(R.raw.chimes)

            // Clean up
            runningState.shutdown()
            scope.cancel()
        }

    // Add more tests as needed for complete coverage
}

private fun pauseTimer(
    runningState: RunningState,
    timeController: TestTimeController,
) {
    // Send a pause event:
    val request = UserActionRequest(UserAction.Pause)
    runBlocking {
        runningState.runningStateUserActionChannel.send(request)
        timeController.advanceTimeBy(1)
    }

    // Wait for the next "event was handled" message:
    runBlocking {
        request.completed.await()
    }
}

private fun resumeTimer(
    runningState: RunningState,
    timeController: TestTimeController,
) {
    // Send a resume request:
    val request = UserActionRequest(UserAction.Resume)
    runBlocking {
        runningState.runningStateUserActionChannel.send(request)
        timeController.advanceTimeBy(1)
    }

    // Wait for the request to be handled
    runBlocking {
        request.completed.await()
    }
}
