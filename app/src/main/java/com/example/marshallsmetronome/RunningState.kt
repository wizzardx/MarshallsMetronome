package com.example.marshallsmetronome

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

/**
 * Abstract base class for managing time in coroutine testing and runtime.
 *
 * Provides an interface for controlling time-related functions, especially in coroutine contexts.
 * Ideal for use in unit tests for time manipulation and coroutine testing. Extend with specific
 * implementations like LiveTimeController or TestTimeController for runtime or test use.
 */
abstract class BaseTimeController {
    // _delayLambda is a dist-type structure, keyed by scope, and the value is a lambda
    private val _delayLambdas = mutableMapOf<CoroutineScope, (suspend (Long) -> Unit)>()

    /**
     * Suspends execution for a specified duration within a given CoroutineScope.
     *
     * Uses a lambda function specific to the provided CoroutineScope to implement the delay.
     * @param delayTimeMillis The time to delay in milliseconds.
     * @param scope The CoroutineScope in which the delay is to be applied.
     * @throws IllegalStateException If the delay lambda is not set for the provided scope.
     */
    open suspend fun delay(
        delayTimeMillis: Long,
        scope: CoroutineScope,
    ) {
        // Error out if the lambda is not set for this coroutine scope.
        check(_delayLambdas.containsKey(scope)) { "Delay lambda not set for this scope. Call setDelayLambda() first." }
        val lambda = _delayLambdas.getValue(scope)
        lambda.invoke(delayTimeMillis)
    }

    /**
     * Retrieves the current system time in nanoseconds.
     *
     * This function is abstract and should be implemented to return the current time in nanoseconds.
     * @return The current time in nanoseconds.
     */
    abstract fun nanoTime(): Long

    /**
     * Advances the simulated time by a specified duration.
     *
     * This abstract function is designed to be overridden to simulate time advancement in milliseconds.
     * @param delayTimeMillis The duration in milliseconds by which to advance the time.
     */
    abstract suspend fun advanceTimeBy(delayTimeMillis: Long)

    /**
     * Assigns a delay lambda to a specific CoroutineScope.
     *
     * Sets a lambda function for delaying within a CoroutineScope, ensuring unique lambdas per scope.
     * @param lambda The delay lambda function.
     * @param scope The CoroutineScope for which the lambda is being set.
     * @throws IllegalArgumentException If a lambda is already set for the provided scope.
     */
    fun setDelayLambda(
        lambda: (suspend (Long) -> Unit),
        scope: CoroutineScope,
    ) {
        // Not allowed to set the lambda twice:
        require(!_delayLambdas.containsKey(scope)) { "Lambda already set for this scope" }
        // Now add the lambda:
        _delayLambdas[scope] = lambda
    }
}

/**
 * A concrete implementation of BaseTimeController for use in runtime environments.
 *
 * This class provides a real-time behavior for time-related functions, particularly useful for coroutine contexts
 * in production. It extends BaseTimeController and overrides the necessary methods to interact with the actual
 * system time. It includes an implementation of nanoTime to return the current system time in nanoseconds,
 * and an override of advanceTimeBy to introduce a real delay based on the system's capabilities.
 *
 * Functions:
 * - nanoTime: Returns the current system time in nanoseconds.
 * - advanceTimeBy: Delays the coroutine for a specified time in milliseconds in real-time.
 *
 * Usage:
 * This class is meant to be used where real-time control and testing of coroutines are required,
 * particularly in a live environment. It is not suitable for unit testing where simulated time control is needed.
 */
class LiveTimeController : BaseTimeController() {
    override fun nanoTime(): Long = System.nanoTime()

    override suspend fun advanceTimeBy(delayTimeMillis: Long) {
        // In live mode, we just delay (to avoid busy looping).
        delay(delayTimeMillis)
    }
}

/**
 * Represents a request to perform a user action along with a mechanism to signal its completion.
 *
 * This class encapsulates a user action and a CompletableDeferred object. The CompletableDeferred
 * is used to await the completion of the action. Once the action is processed and handled, the
 * CompletableDeferred can be completed to signal the completion of the action. This pattern is
 * particularly useful in asynchronous programming environments where actions are processed
 * separately from their initiation, allowing the initiator to wait for the action to be completed.
 *
 * @property action The user action to be performed.
 * @property completed A CompletableDeferred object that signals the completion of the action.
 */
data class UserActionRequest(
    val action: UserAction,
    val completed: CompletableDeferred<Unit> = CompletableDeferred(),
)

/**
 * Encapsulates configuration details for a workout session.
 *
 * This data class groups together various parameters related to the structure and timing of a workout.
 * It includes the number of cycles in the workout, the duration of intense work and rest within each cycle,
 * and the lengths of the warmup and cooldown periods. This class is used to simplify the passing of
 * multiple related parameters for workout configuration.
 *
 * @property cycles The number of cycles in the workout program (e.g., usually 8).
 * @property workSecondsPerCycle The duration of intense work in seconds for each cycle (e.g., usually 20).
 * @property restSecondsPerCycle The duration of rest in seconds within each cycle (e.g., usually 10).
 * @property warmupSeconds The total duration of the warmup period in seconds.
 * @property cooldownSeconds The total duration of the cooldown period in seconds.
 */
data class WorkoutConfig(
    val cycles: Int,
    val workSecondsPerCycle: Int,
    val restSecondsPerCycle: Int,
    val warmupSeconds: Int,
    val cooldownSeconds: Int,
)

/**
 * Type alias representing a raw resource ID in Android.
 *
 * This typealias is used to enhance code readability and clarify that certain integers are intended
 * to be used as IDs for raw resources (such as sound files) stored in the `res/raw` directory of an
 * Android project. By using this typealias instead of a generic Int, the code more clearly communicates
 * its purpose and expectations.
 */
typealias RawResId = Int

/**
 * Manages and tracks the state of a running workout timer in the application.
 *
 * This class is responsible for handling various stages of a workout timer, including warmup,
 * main workout intervals, cooldown, and the finished state. It updates and observes the timer state,
 * handles user actions, and manages the timer's internal event loop. It also integrates sound notifications
 * and interfaces with a view model for UI updates.
 *
 * The class provides functions to interact with and observe the timer's state, including methods to
 * start, pause, resume, and stop the timer, as well as to handle user actions and update the total elapsed time.
 */
class RunningState(
    /**
     * The configuration details for the workout session including cycles, work and rest durations, and warmup and
     * cooldown times.
     */
    val workoutConfig: WorkoutConfig,
    /**
     * A lambda function property for playing a sound resource.
     *
     * This property is a functional interface that takes a raw resource ID of a sound file (denoted by `RawResId`)
     * and plays the corresponding sound. The `RawResId` typealias enhances the readability and semantic understanding
     * of the code, indicating that the integer passed to this lambda function should reference a sound resource
     * located in the `res/raw` directory of an Android project.
     */
    playSoundProp: (soundResourceId: RawResId) -> Unit,
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
    timeController: BaseTimeController,
) {
    /**
     * A channel for handling user actions in the context of the running state.
     *
     * This channel is used to receive and process user actions, such as pause, resume, or other commands
     * related to the timer's operation. User actions are sent to this channel, and the corresponding logic
     * to handle these actions is executed asynchronously. This design allows for non-blocking user interaction
     * and a responsive UI experience.
     */
    val runningStateUserActionChannel: Channel<UserActionRequest> = Channel(Channel.CONFLATED)

    private var _timerEventLoopStarted: Boolean = false

    private val _timerState =
        MutableStateFlow(
            TimerState(
                millisecondsRemainingForEntireWorkout =
                getSecondsForEntireWorkout(
                    cycles = workoutConfig.cycles,
                    workSecondsPerCycle = workoutConfig.workSecondsPerCycle,
                    restSecondsPerCycle = workoutConfig.restSecondsPerCycle,
                    warmupSeconds = workoutConfig.warmupSeconds,
                    cooldownSeconds = workoutConfig.cooldownSeconds,
                ) * Constants.MillisecondsPerSecond,
                isPaused = false,
                currentIntervalType = Constants.FirstIntervalTypeInCycle,
                currentCycleNum = 1,
                totalCycles = workoutConfig.cycles,
                workSecondsPerCycle = workoutConfig.workSecondsPerCycle,
                restSecondsPerCycle = workoutConfig.restSecondsPerCycle,
                // Start with the warm-up phase:
                workoutStage = WorkoutStage.Warmup,
                millisecondsRemaining = workoutConfig.warmupSeconds * Constants.MillisecondsPerSecond,
                warmupTotalSeconds = workoutConfig.warmupSeconds,
                cooldownTotalSeconds = workoutConfig.cooldownSeconds,
            ),
        )

    /**
     * A StateFlow object holding the current state of the timer.
     *
     * This property encapsulates the entire state of the timer at any given point in time,
     * including the remaining time for the current interval and the entire workout,
     * the current interval type (work or rest), the number of cycles, and other relevant timer settings.
     * As a StateFlow, it offers a way to observe and react to changes in the timer's state.
     *
     * @property timerState The StateFlow<TimerState> object that can be observed for state changes.
     *                      Accessing timerState.value provides the current TimerState instance
     *                      representing the current state of the timer.
     */
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

    // Sound-management logic moved to a separate class:
    private val soundManager = SoundManager(playSoundProp)

    private class LoopState {
        var stopLoop = false
    }

    private var totalElapsedTime = 0L

    // Things used inside countdownJob:
    private val accumulatedTimeMillis = AtomicLong(0L)

    private var tickCount = 0

    private val countdownJob =
        scope.launch(dispatcher + CoroutineUtils.sharedExceptionHandler) {

            try {
                timeController.setDelayLambda({ delay(it) }, this)

                // We play a noise a "gong" noise at the start of the workout:
                soundManager.playStartSound()

                var lastTickTimeNano = timeController.nanoTime()

                val smallDelayMillis = Constants.SmallDelay

                val loopState = LoopState()

                while (isActive && !loopState.stopLoop) {
                    // If we get here then the event loop has started, and so external things
                    // waiting for it to start can now do their thing.
                    _timerEventLoopStarted = true

                    val currentTimeNano = timeController.nanoTime()

                    // If the logic gets this far, then the timer is in a valid state and we can proceed.
                    val elapsedTimeNano = currentTimeNano - lastTickTimeNano

                    // Convert nanoseconds to milliseconds and accumulate
                    accumulatedTimeMillis.addAndGet(TimeUnit.NANOSECONDS.toMillis(elapsedTimeNano))

                    // Call tick for each whole and partial tick

                    while (accumulatedTimeMillis.get() >= smallDelayMillis) {
                        tick(smallDelayMillis, loopState)
                        accumulatedTimeMillis.addAndGet(-smallDelayMillis)
                    }

                    val remainingTime = accumulatedTimeMillis.get()

                    if (remainingTime > 0) {
                        val timeForTick = min(remainingTime, smallDelayMillis)
                        tick(timeForTick, loopState)
                        accumulatedTimeMillis.addAndGet(-timeForTick)
                    }

                    // Update lastTickTime after processing
                    lastTickTimeNano = timeController.nanoTime() // timeController2.nanoTime()

                    // Call tick for each whole and partial tick

                    // Calculate time taken for this iteration including the tick processing time
                    val iterationTimeMillis = TimeUnit.NANOSECONDS.toMillis(timeController.nanoTime() - currentTimeNano)

                    // Calculate the delay needed to maintain the loop frequency
                    val delayTimeMillis = smallDelayMillis - iterationTimeMillis

                    // Sanity check for delayTime
                    check(delayTimeMillis <= smallDelayMillis) { "Delay time is out of range: $delayTimeMillis" }

                    // Delay for the remaining time of this iteration. Also handle the case that
                    // delayTimeMillis can sometimes be a very large negative value if for some
                    // reason (eg testing) the logic thinks that the most recent loop iteration
                    // took a very long time. In that case can just skip this delay.

                    if (delayTimeMillis > 0) {
                        timeController.delay(delayTimeMillis, this)
                    }
                } // END: while (isActive && !stopLoop)``
            } finally {
                // Tidy up anything needed over here
            }
        }

    private val userActionCoroutine =
        scope.launch(dispatcher + CoroutineUtils.sharedExceptionHandler) {
            while (isActive) {
                val request = runningStateUserActionChannel.receive()
                handleUserAction(request.action)
                request.completed.complete(Unit)
            }
        }

    private fun tick(
        elapsedTime: Long,
        loopState: LoopState,
    ) {
        tickCount += 1

        totalElapsedTime += elapsedTime

        // Call tick for each whole and partial tick
        var state = _timerState.value

        // Are we currently paused?
        if (!state.isPaused) {
            // We are not currently paused

            // Decrease remaining time for the entire remaining workout
            state = updateTotalRemainingTime(state, elapsedTime = elapsedTime)

            // (the per-stage-related countdowns are decreased in their respective
            //  handler functions).

            state =
                when (state.workoutStage) {
                    WorkoutStage.Warmup -> {
                        handleWarmUp(
                            state,
                            elapsedTime = elapsedTime,
                        )
                    }

                    WorkoutStage.MainWorkout -> {
                        handleMainWorkout(
                            state,
                            soundManager = soundManager,
                            elapsedTime = elapsedTime,
                        )
                    }

                    WorkoutStage.Cooldown -> {
                        handleCooldown(
                            state,
                            elapsedTime = elapsedTime,
                            soundManager = soundManager,
                        )
                    }

                    WorkoutStage.WorkoutEnded -> {
                        // Workout just ended, break the loop:
                        loopState.stopLoop = true
                        // Just return the current state, as a placeholder:
                        state
                    }
                } // END: when (currentState.workoutStage)
        } else {
            // Still paused....
        } // END: if (!currentState.isPaused)

        _timerState.value = state
    }

    private fun handleUserAction(userAction: UserAction) {
        when (userAction) {
            is UserAction.Pause -> {
                pause()
            }
            is UserAction.Resume -> {
                resume()
            }
            else -> {
                throw IllegalArgumentException(
                    "Unknown user action: $userAction. " +
                        "Were they meant to be sent to and handled in a different coroutine?",
                )
            }
        }
    }

    /**
     * Return how many seconds remain for the entire workout program.
     */
    fun getSecondsRemainingInWorkout(): Int =
        _timerState.value.millisecondsRemainingForEntireWorkout / Constants.MillisecondsPerSecond

    /**
     * Returns true if we are currently paused.
     */
    fun isPaused(): Boolean = _timerState.value.isPaused

    /**
     * Shuts down the coroutine and it's timer, etc. Should be called when we're done with this
     * object.
     */
    fun shutdown() {
        // Shut down coroutines.
        countdownJob.cancel()
        userActionCoroutine.cancel()
    }

    private fun pause() {
        // In tests, we now need to send events for causing pausing, rather than
        // directly calling this function.
        val currentState = _timerState.value
        val updatedState = currentState.copy(isPaused = true)
        _timerState.value = updatedState
    }

    private fun resume() {
        // In tests, we now need to send events into the channel for causing pausing, rather than
        // directly calling this function.
        val currentState = _timerState.value
        val updatedState = currentState.copy(isPaused = false)
        _timerState.value = updatedState
    }

    /**
     * Checks if the countdown job is currently active.
     *
     * This method is used primarily for testing purposes to verify the state of the coroutine job
     * responsible for the countdown logic. It returns `true` if the job is active, meaning it is
     * either in the process of executing or awaiting action, and `false` if the job is completed or cancelled.
     *
     * @return Boolean indicating whether the countdown job is active.
     */
    fun isJobActive() = countdownJob.isActive
}

/**
 * Updates the total remaining time for the entire workout session.
 *
 * This function reduces the total remaining time of the workout by the elapsed time.
 * It clamps the remaining time to zero if it falls below, ensuring the total remaining time
 * never becomes negative.
 *
 * @param state The current state of the timer, including the total remaining time for the workout.
 * @param elapsedTime The amount of time elapsed since the last update, in milliseconds.
 * @return A new `TimerState` object with updated remaining time for the entire workout.
 */
fun updateTotalRemainingTime(
    state: TimerState,
    elapsedTime: Long,
): TimerState {
    var newState = state

    // Decrease remaining time for the entire remaining workout, but clamp to 0:
    val newTotalRemainingTime = state.millisecondsRemainingForEntireWorkout - elapsedTime
    newState =
        newState.copy(
            millisecondsRemainingForEntireWorkout = newTotalRemainingTime.safeToInt().coerceAtLeast(0),
        )

    return newState
}

/**
 * Manages the warm-up phase of the workout, updating the state based on elapsed time.
 *
 * During the warm-up phase, this function decrements the warm-up timer by the elapsed time.
 * It ensures the remaining warm-up time does not go below zero and sets a flag if the warm-up
 * period ends. If the warm-up ends, the function transitions the workout to the first work interval
 * of the main workout stage.
 *
 * @param state The current state of the timer, including the remaining warm-up time.
 * @param elapsedTime The amount of time elapsed since the last update, in milliseconds.
 * @return A new `TimerState` object representing the updated state after processing the warm-up.
 */
fun handleWarmUp(
    state: TimerState,
    elapsedTime: Long,
): TimerState {
    var newState = state

    // Decrement the warm-up timer, but clamp to 0, and also set a flag if the warm-up timer
    // would have gone negative:
    var warmupEnded = false
    var newWarmupTime = state.millisecondsRemaining - elapsedTime
    var timeToChopOffNextPart = 0L
    if (newWarmupTime < 0) {
        timeToChopOffNextPart = -newWarmupTime
        newWarmupTime = 0
        warmupEnded = true
    }

    newState =
        newState.copy(
            millisecondsRemaining = newWarmupTime.safeToInt(),
        )

    // Check if the warm-up period has ended
    if (warmupEnded) {
        // Transition to the first work interval
        val workIntervalMilliseconds = state.workSecondsPerCycle * Constants.MillisecondsPerSecond
        newState =
            newState.copy(
                workoutStage = WorkoutStage.MainWorkout,
                currentIntervalType = IntervalType.Work,
                millisecondsRemaining = (workIntervalMilliseconds - timeToChopOffNextPart).safeToInt(),
            )
    }
    return newState
}

/**
 * Manages the main workout phase of the timer, updating the state based on elapsed time.
 *
 * This function processes the current timer state for the main workout stage, handling the passage of
 * time and transitioning between intervals. It plays interval end sounds and calculates the remaining
 * time for the current interval. If an interval ends, it handles the transition to the next interval or
 * stage of the workout. The function also determines if the main workout stage has concluded and, if so,
 * transitions the state to the cooldown stage.
 *
 * Key operations:
 * - Plays sounds just before the end of each interval using `playIntervalEndSounds`.
 * - Decreases the remaining time for the current interval, clamping it to zero.
 * - Handles interval end and transitions to the next interval or workout stage.
 * - Transitions to cooldown stage when the entire workout cycle is completed.
 *
 * @param state The current state of the timer, including interval and cycle information.
 * @param soundManager An instance of `SoundManager` to manage sound playing.
 * @param elapsedTime The amount of time elapsed since the last update, in milliseconds.
 * @return A new `TimerState` object representing the updated state after processing the main workout.
 */
fun handleMainWorkout(
    state: TimerState,
    soundManager: SoundManager,
    elapsedTime: Long,
): TimerState {
    var newState = state

    // Do some logic at the very start of every interval:
    playIntervalStartSounds(state, soundManager, currentCycleNum = state.currentCycleNum)

    // Do some logic just before the end of every interval:
    playIntervalEndSounds(state, soundManager)

    // Decrease time remaining for the current interval, but clamp to 0:
    var newIntervalTime = state.millisecondsRemaining - elapsedTime
    var currentIntervalJustEnded = false
    var timeToChopOffNextPart = 0L
    if (newIntervalTime < 0) {
        timeToChopOffNextPart = -newIntervalTime
        newIntervalTime = 0
        currentIntervalJustEnded = true
    }

    newState =
        newState.copy(
            millisecondsRemaining = newIntervalTime.safeToInt(),
        )

    // Determine if the workout just ended
    val workoutJustEnded =
        currentIntervalJustEnded &&
            state.currentCycleNum == state.totalCycles &&
            state.currentIntervalType == Constants.LastIntervalTypeInCycle

    if (!workoutJustEnded && currentIntervalJustEnded) {
        newState = handleIntervalEnd(newState, soundManager, timeToChopOffNextPart)
    }

    // If the workout just ended then change to the cooldown stage:
    if (workoutJustEnded) {
        newState =
            newState.copy(
                workoutStage = WorkoutStage.Cooldown,
                millisecondsRemaining = (
                    state.cooldownTotalSeconds * Constants.MillisecondsPerSecond - timeToChopOffNextPart
                    ).safeToInt(),
            )
    }

    return newState
}

/**
 * Handles the cooldown stage of the timer by updating the state based on elapsed time.
 *
 * This function takes the current timer state and the elapsed time as inputs and decrements the cooldown
 * timer accordingly. It ensures that the remaining cooldown time does not fall below zero, clamping it to
 * zero if needed. If the cooldown time reaches zero, indicating the end of the cooldown period, the
 * function updates the timer state to reflect that the workout has ended.
 *
 * The function returns a new `TimerState` with updated values for the remaining cooldown time and, if
 * applicable, the workout stage set to 'WorkoutEnded'.
 *
 * @param state The current state of the timer, including details like the remaining cooldown time.
 * @param elapsedTime The amount of time elapsed since the last update, in milliseconds.
 * @param soundManager An instance of `SoundManager` to manage sound playing.
 * @return A new `TimerState` object representing the updated state after handling the cooldown.
 */
fun handleCooldown(
    state: TimerState,
    elapsedTime: Long,
    soundManager: SoundManager,
): TimerState {
    var newState = state

    // Play sound at the end of the cooldown period:
    playCooldownEndSound(state, soundManager)

    // Decrement the cooldown timer, but clamp to 0
    var newCooldownTime = state.millisecondsRemaining - elapsedTime
    var cooldownEnded = false
    if (newCooldownTime < 0) {
        newCooldownTime = 0
        cooldownEnded = true
    }

    newState =
        newState.copy(
            millisecondsRemaining = newCooldownTime.safeToInt(),
        )

    // Check if the cooldown period has ended
    if (cooldownEnded) {
        // End of cooldown, transition to end workout state
        newState =
            newState.copy(
                workoutStage = WorkoutStage.WorkoutEnded,
            )
        // Optionally, trigger a sound or any end-of-workout action
    }
    return newState
}

private fun playIntervalStartSounds(
    state: TimerState,
    soundManager: SoundManager,
    currentCycleNum: Int
) {
    if (state.currentIntervalType == IntervalType.Work) {
        // Play a sound at the start of work intervals
        soundManager.playWorkStartSound(currentCycleNum = currentCycleNum)
    } else {
        // Nothing to do yet for the rest interval
    }
}

private fun playIntervalEndSounds(
    state: TimerState,
    soundManager: SoundManager,
) {
    if (state.millisecondsRemaining < Constants.MillisecondsPerSecond) {
        if (state.currentIntervalType == IntervalType.Work) {
            soundManager.playWorkEndSound()
        } else {
            soundManager.playRestEndSound(state.totalCycles, state.currentCycleNum)
        }
    }
}

private fun playCooldownEndSound(
    state: TimerState,
    soundManager: SoundManager,
) {
    if (state.millisecondsRemaining < Constants.MillisecondsPerSecond) {
        soundManager.playCooldownEndSound()
    }
}

private fun handleIntervalEnd(
    state: TimerState,
    soundManager: SoundManager,
    timeToChopOffNextPart: Long,
): TimerState {
    val nextIntervalType = getNextIntervalType(state.currentIntervalType)
    val nextIntervalMilliseconds =
        getNextIntervalMilliseconds(
            nextIntervalType,
            state.workSecondsPerCycle,
            state.restSecondsPerCycle,
        ) - timeToChopOffNextPart

    var updatedState =
        state.copy(
            currentIntervalType = nextIntervalType,
            millisecondsRemaining = nextIntervalMilliseconds.safeToInt(),
        )

    if (isCycleEnd(state)) {
        updatedState = transitionToNextCycleAndReset(updatedState, soundManager)
    }
    return updatedState
}
