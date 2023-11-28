package com.example.marshallsmetronome

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeoutException

/**
 * Represents user inputs for configuring a workout session.
 *
 * This class holds various parameters that a user can input to customize a workout session.
 * It includes total number of cycles, duration of workout and rest intervals,
 * as well as warmup and cooldown durations.
 */
class WorkoutUserInputs {
    /**
     * Input that we've received from the "Cycles" TextField.
     */
    var totalCycles by mutableStateOf("8")

    /**
     * Input that we've received from the "Work" TextField.
     */
    var secondsWork by mutableStateOf("20")

    /**
     * Input that we've received from the "Rest" TextField.
     */
    var secondsRest by mutableStateOf("10")

    /**
     * Duration of warmup period before starting the main workout session.
     *
     * This property represents the length of time in seconds that the user should
     * spend warming up before beginning the workout cycles. Warmup activities generally
     * include light exercises or stretches to prepare the body for more intense activity.
     */
    var secondsWarmup by mutableStateOf("60")

    /**
     * Duration of cooldown period after completing the workout session.
     *
     * This property represents the length of time in seconds for the cooldown period
     * after completing the main workout cycles. Cooldown activities typically involve
     * light exercises or stretches to help the body gradually return to a resting state.
     */
    var secondsCooldown by mutableStateOf("60")

    /**
     * Calculates the total duration of the workout session in seconds.
     *
     * This method computes the overall duration of the workout session based on the user inputs.
     * It includes the time spent in active workout (secondsWork), resting between cycles
     * (secondsRest), warming up before the session (secondsWarmup), and cooling down after the session
     * (secondsCooldown). The calculation accounts for the total number of cycles (totalCycles) specified.
     *
     * @return The total workout duration in seconds, as an Int. If any input value is not a valid integer,
     * it defaults to 0 in the calculation.
     */
    fun calculateWorkoutDurationSecs(): Int {
        val secondsWork = secondsWork.toIntOrNull() ?: 0
        val secondsRest = secondsRest.toIntOrNull() ?: 0
        val totalCycles = totalCycles.toIntOrNull() ?: 0
        val warmupSeconds = secondsWarmup.toIntOrNull() ?: 0
        val cooldownSeconds = secondsCooldown.toIntOrNull() ?: 0
        return (secondsWork + secondsRest) * totalCycles + warmupSeconds + cooldownSeconds
    }
}

/**
 * ViewModel for managing and orchestrating the timer logic in a workout application.
 *
 * This ViewModel is central to handling user inputs for workout configuration, managing timer states,
 * and coordinating with the UI for real-time updates. It maintains the workout timer's state, processes
 * user actions, and triggers auditory feedback at appropriate intervals. The ViewModel also handles
 * validation of user inputs, ensuring they are correct before starting the timer.
 *
 * Properties:
 * - `playSoundProp`: Lambda function for playing sound notifications.
 * - `scope`: CoroutineScope for managing coroutines.
 * - `dispatcher`: CoroutineDispatcher for executing coroutines.
 * - `timeController`: Manages time-related functions, especially in coroutines.
 * - `errorMessage`: MutableState for displaying error messages in the UI. Nullable for optional usage.
 *
 * The ViewModel uses a StateFlow to observe and react to changes in the timer's state. It also
 * provides utility functions for formatting time-related information for the UI and handles
 * user interactions such as starting, pausing, and resetting the timer.
 */
class MarshallsMetronomeViewModel(
    private val playSoundProp: (Int) -> Unit = {},
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val timeController: BaseTimeController = LiveTimeController(),
    /**
     * MutableState for holding and observing error messages in the UI.
     * This property is used to display error messages related to timer operations or user inputs.
     * It's a nullable property, allowing the option not to display error messages if not required.
     */
    val errorMessage: MutableState<String?>? = null,
) {
    private val viewModelUserActionChannel: Channel<UserActionRequest> = Channel(Channel.CONFLATED)

    private var _runningState: MutableState<RunningState?> = mutableStateOf(null)

    /**
     * Represents the public read-only state of the running workout timer.
     *
     * This state includes all the essential information about the current state of the
     * workout timer, such as whether it is paused, the current interval type, cycle number,
     * and time remaining in both the current interval and the entire workout session.
     * It's observed by the UI to update and display the timer information.
     *
     * @property runningState The StateFlow<TimerState> that can be observed for state changes.
     *                        Accessing runningState.value provides the current TimerState instance
     *                        representing the timer's current state.
     */
    val runningState: State<RunningState?> = _runningState

    /**
     * Instance of WorkoutUserInputs holding the current workout session's configurations.
     *
     * This variable is an instantiation of the WorkoutUserInputs class, serving as a container
     * for all user-specified parameters for a workout session. It includes properties such as
     * total number of cycles, duration of active workout and rest periods, and the lengths of
     * the warmup and cooldown periods. This instance can be used to access and modify the workout
     * session's settings as per user input.
     */
    val workoutInputs = WorkoutUserInputs()

    /**
     * Error message for the "Cycles" TextField.
     */
    var totalCyclesInputError by mutableStateOf<String?>(null)

    /**
     * Error message for the "Work" TextField.
     */
    var secondsWorkInputError by mutableStateOf<String?>(null)

    /**
     * Error message for the "Rest" TextField.
     */
    var secondsRestInputError by mutableStateOf<String?>(null)

    /**
     * Holds an error message related to the warmup duration input.
     *
     * This property is used to store and display an error message if the user input for the warmup duration
     * is invalid (e.g., non-numeric or out of acceptable range). It's used to provide feedback to the user
     * in the UI. The property is nullable and defaults to null, indicating no error.
     */
    var secondsWarmupInputError by mutableStateOf<String?>(null)

    /**
     * Holds an error message related to the cooldown duration input.
     *
     * This property is used to store and display an error message if the user input for the cooldown duration
     * is invalid (e.g., non-numeric or out of acceptable range). It's used to provide feedback to the user
     * in the UI. The property is nullable and defaults to null, indicating no error.
     */
    var secondsCooldownInputError by mutableStateOf<String?>(null)

    private var _alreadyCleared = false
    private val resourcesCleared: Boolean
        get() = _alreadyCleared

    // Coroutine for handling the incoming user actions:

    private val userActionCoroutine =
        scope.launch(dispatcher + CoroutineUtils.sharedExceptionHandler) {
            while (isActive) {
                val request = viewModelUserActionChannel.receive()
                handleUserAction(request.action)
                request.completed.complete(Unit)
            }
        }

    /**
     * Return the text to display on the Start/Pause/Resume button.
     */
    val buttonText: String
        get() {
            return if (runningState.value == null) {
                "Start"
            } else {
                if (runningState.value?.isPaused() == true) {
                    "Resume"
                } else {
                    "Pause"
                }
            }
        }

    /**
     * Return true if the user can edit the text fields.
     */
    val textInputControlsEnabled: Boolean
        get() = runningState.value == null

    @Suppress("MagicNumber")
    private fun sendUserAction(
        userAction: UserAction,
        wait: Boolean = false,
    ) {
        // If we're going to wait for response from the backend, then clear the response channel:
        // We use different reply and response queues, depending on a condition:

        val requestQueue: Channel<UserActionRequest> =
            when (userAction) {
                UserAction.Start, UserAction.Reset -> {
                    viewModelUserActionChannel
                }

                UserAction.Pause, UserAction.Resume -> {
                    checkNotNull(_runningState.value) { "Running state is not set!" }
                    _runningState.value!!.runningStateUserActionChannel
                }
            }

        val request = UserActionRequest(userAction)

        runBlocking {
            requestQueue.send(request)
        }

        val totalIterations = SendUserActionTotalWaitTimeMs / SendUserActionDelayPerIterationMs

        // Wait for the user action to be handled, if requested:
        if (wait) {
            var iterationsRemaining = totalIterations
            while (true) {
                if (request.completed.isCompleted) {
                    // We got something, so we're done waiting
                    break
                } else {
                    // We didn't get anything, so sleep a bit and then try again:
                    runBlocking {
                        // This is a messy hack, mainly for testing mode..we're making
                        // time advance in other timers/etc so that they can proceed
                        // further while this waits for them. This logic is basically, during
                        // the test, itself a testing function.
                        timeController.advanceTimeBy(SendUserActionDelayPerIterationMs)
                    }
                }

                iterationsRemaining--
                if (iterationsRemaining <= 0) {
                    throw TimeoutException("Timed out waiting for user action to be handled.")
                }
            }
        }
    }

    /**
     * Reacts to a button click by dispatching an event/request to a separate coroutine for asynchronous processing.
     *
     * Upon a button click, this function determines the relevant user action using 'getUserActionForButton'
     * and sends it to a separate coroutine via 'sendUserAction'. This design ensures that the event handling
     * does not block user interaction, maintaining a responsive UI. If any exceptions occur during the
     * event dispatch, they are caught and managed. The 'TooGenericExceptionCaught' warning from Detekt
     * is suppressed to allow catching any Exception, thus preventing app crashes and enabling a consistent
     * user experience. The caught exceptions are reported through 'reportError', which updates 'errorMessage',
     * a MutableState string. This update reflects the error message on the UI for user awareness.
     *
     * @param wait Optional boolean parameter, if set to true, the function will wait for a specific condition
     *             before proceeding.
     * @suppress 'TooGenericExceptionCaught' to enable catching of any Exception.
     */
    @Suppress("TooGenericExceptionCaught")
    fun onButtonClick(wait: Boolean = false) {
        try {
            val userAction = getUserActionForButton()
            sendUserAction(userAction, wait = wait)
        } catch (e: Exception) {
            reportError(errorMessage, e)
        }
    }

    /**
     * Called when the user clicks the Reset button.
     *
     * This function attempts to send a reset action. If an error occurs during this process,
     * it is caught and handled by reporting it through `reportError`. This ensures that the
     * app does not crash from unhandled exceptions and provides a mechanism to inform the
     * user and log the issue for further investigation.
     *
     * @param wait Boolean flag indicating if the function should wait for some condition.
     */
    @Suppress("TooGenericExceptionCaught")
    fun onResetClick(wait: Boolean = false) {
        try {
            // Need to do something similar to this here: userActionChannel.offer(UserAction.Start)
            sendUserAction(UserAction.Reset, wait)
        } catch (e: Exception) {
            reportError(errorMessage, e)
        }
    }

    private fun getUserActionForButton(): UserAction {
        return if (runningState.value == null) {
            UserAction.Start
        } else {
            if (runningState.value?.isPaused() == true) {
                UserAction.Resume
            } else {
                UserAction.Pause
            }
        }
    }

    /**
     * Called when we're done with this ViewModel.
     */
    fun clearResources() {
        // Just quit if we were already run.
        if (resourcesCleared) {
            println("clearResources was already run.")
            return
        }
        userActionCoroutine.cancel()
        runningState.value?.shutdown()

        viewModelUserActionChannel.close()

        _alreadyCleared = true
    }

    /**
     * Determines if the timer has been started.
     *
     * This function checks the state of the ViewModel to determine if the timer has been initialized
     * and started. It returns `true` if the timer is currently running or paused (i.e., not null),
     * indicating that the workout session has begun. If the timer has not been started yet, or if it
     * has been reset and is currently null, the function returns `false`.
     *
     * @return Boolean indicating whether the timer has been started.
     */
    fun timerStarted(): Boolean = runningState.value != null

    /**
     * Determines whether the timer is currently paused, returning a boolean value.
     *
     * This method primarily checks if the timer is in a paused state and returns `true` if so,
     * and `false` if it is not paused (i.e., it is actively running). It is designed to be
     * straightforward for typical use cases where the timer's state is known to be initialized.
     *
     * However, this method has a specific behavior for debugging purposes: if the timer has not
     * been started or initialized yet, invoking this method will cause the system to crash. This
     * behavior is intentional to aid in identifying issues during the development phase where the
     * timer's state might be incorrectly assumed or managed.
     *
     * Usage:
     * - Returns `true` if the timer is paused.
     * - Returns `false` if the timer is not paused (running).
     * - Crashes if the timer is not initialized, to highlight improper usage during debugging.
     */
    fun isTimerPausedOrFail(): Boolean = runningState.value!!.timerState.value.isPaused

    private fun handleUserAction(userAction: UserAction) {
        when (userAction) {
            is UserAction.Start -> {
                handleStartUserAction()
            }
            is UserAction.Reset -> {
                handleResetUserAction()
            }
            // Add other cases as necessary
            else -> {
                throw IllegalArgumentException(
                    "Unknown user action: $userAction. " +
                        "Were they meant to be sent to and handled in a different coroutine?",
                )
            }
        }
    }

    private fun handleResetUserAction() {
        // Clear out the running state for the timer, to reset all it's state:
        runningState.value?.shutdown()
        _runningState.value = null
        // Also clear out any displayed error:
        errorMessage?.value = null
    }

    private fun handleStartUserAction() {
        // Make sure that the UI is in a state compatible with the "Start" action
        check(runningState.value == null) { "Timer already started!" }

        // First validate the inputs

        // Validate Cycles user input
        totalCyclesInputError = validateIntInput(workoutInputs.totalCycles)

        // Validate Warm-Up input
        secondsWarmupInputError = validateIntInput(workoutInputs.secondsWarmup)

        // Validate Work seconds user input
        secondsWorkInputError = validateIntInput(workoutInputs.secondsWork)

        // Validate Rest seconds user input
        secondsRestInputError = validateIntInput(workoutInputs.secondsRest)

        // Validate Cooldown input
        secondsCooldownInputError = validateIntInput(workoutInputs.secondsCooldown)

        // If all the validations passed then we can start up our main timer
        if (totalCyclesInputError == null &&
            secondsWorkInputError == null &&
            secondsRestInputError == null
        ) {
            val newVal =
                RunningState(
                    workoutConfig =
                    WorkoutConfig(
                        cycles = workoutInputs.totalCycles.toInt(),
                        workSecondsPerCycle = workoutInputs.secondsWork.toInt(),
                        restSecondsPerCycle = workoutInputs.secondsRest.toInt(),
                        warmupSeconds = workoutInputs.secondsWarmup.toInt(),
                        cooldownSeconds = workoutInputs.secondsCooldown.toInt(),
                    ),
                    playSoundProp = playSoundProp,
                    dispatcher = dispatcher,
                    scope = scope,
                    timeController = timeController,
                )
            _runningState.value = newVal
        }
    }

    private companion object {
        // Total wait time of 5 seconds
        const val SendUserActionTotalWaitTimeMs = 5 * Constants.MillisecondsPerSecond

        // Delay per iteration in milliseconds
        const val SendUserActionDelayPerIterationMs = Constants.SmallDelay
    }
}

/**
 * Return a mm:ss-formatted string for the total time remaining for the entire workout.
 */
fun MarshallsMetronomeViewModel.formatTotalTimeRemainingString(): String {
    var timeString = formatMinSec(workoutInputs.calculateWorkoutDurationSecs())

    runningState.value?.getSecondsRemainingInWorkout()?.let {
        timeString = formatMinSec(it)
    }

    return timeString
}

/**
 * Formats the remaining time of the current stage of a workout into a readable string.
 *
 * This function determines the current stage of the workout and the remaining time in that stage,
 * then formats and returns this information as a string. The string includes the stage name (like
 * 'Warmup', 'Cooldown', 'Work', or 'Rest') and the remaining time in minutes and seconds.
 *
 * If the timer is in the 'WorkoutEnded' stage, it displays 'Cooldown: 00:00'. If the timer has not
 * been initialized (indicating a reset state or that it hasn't started yet), it uses default values
 * for warmup. The function also contains logic to adjust the seconds display to avoid briefly showing
 * the maximum interval seconds at the start of a new interval.
 *
 * @return A formatted string representing the current workout stage and the remaining time.
 */
fun MarshallsMetronomeViewModel.formatCurrentStageTime(): String {
    // Get current timer state, otherwise null.
    val currentState = runningState.value?.timerState?.value

    var millisecondsPart: Int
    val textPart: String
    val totalSeconds: Int
    if (currentState != null) {
        // Timer has been initialised, so it's either up and counting down, or currently
        // paused (or resumed).
        val workoutStage = currentState.workoutStage
        millisecondsPart = currentState.millisecondsRemaining
        when (workoutStage) {
            WorkoutStage.Warmup -> {
                textPart = "Warmup"
                totalSeconds = currentState.warmupTotalSeconds
            }

            WorkoutStage.Cooldown -> {
                textPart = "Cooldown"
                totalSeconds = currentState.cooldownTotalSeconds
            }

            WorkoutStage.MainWorkout -> {
                when (currentState.currentIntervalType) {
                    IntervalType.Work -> {
                        textPart = "Work"
                        totalSeconds = currentState.workSecondsPerCycle
                    }

                    IntervalType.Rest -> {
                        textPart = "Rest"
                        totalSeconds = currentState.restSecondsPerCycle
                    }
                }
            }

            WorkoutStage.WorkoutEnded -> {
                // Over here, we would cause "Cooldown: 00:00" to be shown.
                millisecondsPart = 0
                totalSeconds = currentState.cooldownTotalSeconds
                textPart = "Cooldown"
            }
        }
    } else {
        // Timer not initialised, so it's either been reset, or not started yet.
        // Get default figures to work with:
        textPart = "Warmup"
        millisecondsPart = (workoutInputs.secondsWarmup.toIntOrNull() ?: 0) * Constants.MillisecondsPerSecond
        totalSeconds = workoutInputs.secondsWarmup.toIntOrNull() ?: 0
    }

    // As a workaround to avoid having seconds of new intervals show briefly (100 mss and then jump down) when
    // the timer is active, and our seconds are at the maximum value for the interval, then we bump it down here
    // instead.
    val timerActive = currentState != null
    val secondsRemaining = millisecondsPart / Constants.MillisecondsPerSecond
    val adjustedSecondsRemaining =
        if (timerActive && secondsRemaining == totalSeconds) {
            secondsRemaining - 1
        } else {
            secondsRemaining
        }

    // Combine the parts together:
    return "$textPart: " + formatMinSec(adjustedSecondsRemaining)
}

/**
 * Return the current cycle number, in the format "1/8" or "2/8".
 * Format is "current cycle number / total cycles".
 */
fun MarshallsMetronomeViewModel.formatCurrentCycleNumber(): String {
    val totalCycles = workoutInputs.totalCycles.toIntOrNull() ?: 0
    val currentCycleNum = runningState.value?.timerState?.value?.currentCycleNum ?: 1
    return "$currentCycleNum/$totalCycles"
}

/**
 * Calculates the total seconds for the entire workout.
 * This includes cycles of work and rest, along with warmup and cooldown.
 *
 * @return Total seconds for the complete workout.
 */
fun MarshallsMetronomeViewModel.getTotalWorkoutSeconds(): Int {
    val cycles = workoutInputs.totalCycles.toIntOrNull() ?: 0
    val workSecondsPerCycle = workoutInputs.secondsWork.toIntOrNull() ?: 0
    val restSecondsPerCycle = workoutInputs.secondsRest.toIntOrNull() ?: 0
    val warmupSeconds = workoutInputs.secondsWarmup.toIntOrNull() ?: 0
    val cooldownSeconds = workoutInputs.secondsCooldown.toIntOrNull() ?: 0
    return getSecondsForEntireWorkout(
        cycles = cycles,
        workSecondsPerCycle = workSecondsPerCycle,
        restSecondsPerCycle = restSecondsPerCycle,
        warmupSeconds = warmupSeconds,
        cooldownSeconds = cooldownSeconds,
    )
}
