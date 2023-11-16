package com.example.marshallsmetronome

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.marshallsmetronome.ui.theme.MarshallsMetronomeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * The main activity for our app. The android runtime calls this logic.
 */
class MainActivity : ComponentActivity() {
    private var mediaPlayer: MediaPlayer? = null
    private var errorMessage = mutableStateOf<String?>(null)

    private var viewModel = MarshallsMetronomeViewModel(::playSound)

    private fun playSound(soundResourceId: Int) {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.reset()

                val context: Context = this@MainActivity
                val uri = Uri.parse("android.resource://${context.packageName}/$soundResourceId")
                it.setDataSource(context, uri)

                it.prepare()
                it.start()
            } ?: run {
                // MediaPlayer is null. Handle the case here.
                errorMessage.value = "MediaPlayer is not initialized."
            }
        } catch (ioe: IOException) {
            reportError(errorMessage, ioe)
        } catch (ise: IllegalStateException) {
            reportError(errorMessage, ise)
        } catch (iae: IllegalArgumentException) {
            reportError(errorMessage, iae)
        } catch (se: SecurityException) {
            reportError(errorMessage, se)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    // Catching a generic exception intentionally for top-level error logging
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            mediaPlayer = MediaPlayer()
        } catch (e: Exception) {
            reportError(errorMessage, e)
        }
        setContent {
            MarshallsMetronomeTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MarshallsMetronome(
                        viewModel = viewModel,
                        errorMessage = errorMessage,
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.stop()
        }
        mediaPlayer?.release()
        mediaPlayer = null
        viewModel.clearResources()
        super.onDestroy()
    }
}

/**
 * Singleton object for centralized error handling across the application.
 *
 * `ErrorHandler` provides utility functions to handle exceptions in a unified manner.
 * It can log errors, update UI state based on error messages, and perform other
 * application-wide error handling tasks. By centralizing error handling logic,
 * `ErrorHandler` facilitates consistent and maintainable error management.
 *
 * Usage:
 * ErrorHandler.handleError(errorMessageState, exception, "CustomTag")
 */
object ErrorHandler {
    /**
     * Handles exceptions by logging the error and updating the UI error message state.
     *
     * This method should be used to handle exceptions that occur within the application.
     * It logs the error message and stack trace to the console and updates the provided
     * mutable state with the error message for UI display.
     *
     * @param errorMessage The mutable state that holds the error message for UI updates.
     * @param exception The exception that occurred.
     * @param tag Optional logging tag, used for categorizing the log messages.
     */
    fun handleError(errorMessage: MutableState<String?>, exception: Exception, tag: String = "AppError") {
        errorMessage.value = exception.message
        Log.e(tag, "Error occurred: ", exception)
        // Additional error handling logic here
    }
}

/**
 * Represents an interval (seconds of some activity).
 */
enum class IntervalType {
    Work,
    Rest,
}

/**
 * Constants for our app.
 */
object Constants {
    /**
     * The first interval that occurs in a Cycle is "Work".
     */
    val FirstIntervalTypeInCycle = IntervalType.Work

    /**
     * The last interval that occurs in a Cycle is "Rest".
     */
    val LastIntervalTypeInCycle = IntervalType.Rest

    /**
     * There are 60 seconds in every minute.
     */
    const val SecondsPerMinute = 60

    /**
     * There are 1000 milliseconds in every second.
     */
    const val MillisecondsPerSecond = 1000

    /**
     * In our logic we delay for short periods of time and then perform timer-related logic.
     */
    const val SmallDelay = 100

    /**
     * For the sake of simplicity, we limit the user to inputting numbers between 1 and 100.
     */
    const val MaxUserInputNum = 100
}

/**
 * Manages sound playback for various events in the application.
 *
 * This class encapsulates all sound-related operations, providing methods to play specific sounds
 * for different scenarios like the start of a workout, the end of a work interval, the end of a rest
 * interval, and the end of the entire workout cycle. It also maintains state to ensure sounds are not
 * repeatedly played when not necessary.
 *
 * @param playSound A function that takes a sound resource ID and plays the corresponding sound.
 *                  This allows for flexible integration with any sound playing mechanism.
 */
class SoundManager(private val playSound: (soundResourceId: Int) -> Unit) {

    private var refereeWhistlePlayed = false
    private var factoryWhistlePlayed = false
    private var endOfCycleBuzzerTriggered = false

    /**
     * Plays the start sound of the workout.
     *
     * This function triggers the playback of the initial sound that signifies the beginning
     * of the workout, typically a gong sound. It is intended to be called once at the start
     * of the workout session.
     */
    fun playStartSound() {
        playSound(R.raw.gong)
    }

    /**
     * Plays the sound indicating the end of a work interval.
     *
     * This function should be called at the conclusion of a work interval. It checks if the
     * factory whistle sound has already been played to avoid repetition. If not, it plays
     * the sound and sets the flag indicating that the sound has been played.
     */
    fun playWorkEndSound() {
        if (!factoryWhistlePlayed) {
            playSound(R.raw.factory_whistle)
            factoryWhistlePlayed = true
        }
    }

    /**
     * Plays the sound indicating the end of a rest interval or the entire workout.
     *
     * This function should be called at the conclusion of a rest interval. If it is the last
     * cycle of the workout, it plays a referee whistle sound to indicate the end of the workout.
     * Otherwise, it plays a buzzer sound to signify the end of the rest interval. It ensures
     * each sound is played only once per cycle.
     *
     * @param cycles The total number of cycles in the workout program.
     * @param currentCycle The current cycle number of the workout.
     */
    fun playRestEndSound(cycles: Int, currentCycle: Int) {
        if (currentCycle == cycles) {
            // At the end of the workout, play the referee whistle sound
            if (!refereeWhistlePlayed) {
                playSound(R.raw.referee_whistle)
                refereeWhistlePlayed = true
            }
        } else if (!endOfCycleBuzzerTriggered) {
            // At the end of all rest intervals (except the last one), play the buzzer sound
            playSound(R.raw.buzzer)
            endOfCycleBuzzerTriggered = true
        }
    }

    /**
     * Resets the flags indicating whether end-of-cycle sounds have been played.
     *
     * This function resets the internal flags that track whether specific sounds have been
     * played for the current cycle. It is intended to be called at the end of each cycle to
     * prepare for the next cycle's sound triggers.
     */
    fun resetEndOfCycleSounds() {
        factoryWhistlePlayed = false
        refereeWhistlePlayed = false
        endOfCycleBuzzerTriggered = false
    }
}

/**
 * Represents the state of a timer in the application.
 *
 * This class encapsulates all necessary information regarding the current state of the timer,
 * including the time remaining in the current interval and overall workout, the current interval type (work/rest),
 * and the number of cycles in the workout. It also holds information about the duration of work and rest intervals.
 *
 * @property millisecondsRemainingInAllCycles The total milliseconds remaining for all cycles in the workout.
 * @property isPaused A flag indicating whether the timer is currently paused.
 * @property currentIntervalType The type of the current interval, either work or rest.
 * @property currentCycleNum The current cycle number in the workout session.
 * @property millisecondsRemainingInCurrentInterval The milliseconds remaining in the current interval.
 * @property totalCycles The total number of cycles set for the workout session.
 * @property workSecondsPerCycle The number of seconds of work per cycle.
 * @property restSecondsPerCycle The number of seconds of rest per cycle.
 */
data class TimerState(
    val millisecondsRemainingInAllCycles: Int,
    val isPaused: Boolean,
    val currentIntervalType: IntervalType,
    val currentCycleNum: Int,
    val millisecondsRemainingInCurrentInterval: Int,
    val totalCycles: Int,
    val workSecondsPerCycle: Int,
    val restSecondsPerCycle: Int,
)

/**
 * Keeps track of the timer-related state for our app.
 */
class RunningState(
    /**
     * Coroutine scope, used to help us to tidy up the coroutine logic where we manage the timer
     * state.
     */
    val coroutineScope: CoroutineScope,

    /**
     * How many cycles in the intense workout program. Usually 8.
     */
    val cycles: Int,

    /**
     *  How many seconds of intense work per cycle. Usually 20.
     */
    val workSecondsPerCycle: Int,

    /**
     * How many seconds of rest per cycle. Usually 10.
     */
    val restSecondsPerCycle: Int,

    /**
     * Callback function to play a noise.
     */
    val playSound: (soundResourceId: Int) -> Unit,
) {

    private val _timerState = MutableStateFlow(
        TimerState(
            millisecondsRemainingInAllCycles =
            getSecondsForAllCycles(
                cycles = cycles,
                workSecondsPerCycle = workSecondsPerCycle,
                restSecondsPerCycle = restSecondsPerCycle
            ) * Constants.MillisecondsPerSecond,

            isPaused = false,

            currentIntervalType = Constants.FirstIntervalTypeInCycle,

            currentCycleNum = 1,

            millisecondsRemainingInCurrentInterval =
            getSecondsForFirstInterval(
                workSecondsPerCycle = workSecondsPerCycle
            ) * Constants.MillisecondsPerSecond,

            totalCycles = cycles,

            workSecondsPerCycle = workSecondsPerCycle,

            restSecondsPerCycle = restSecondsPerCycle,
        )
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
    private val soundManager = SoundManager(playSound)

    private var countdownJob: Job = coroutineScope.launch {
        // We play a noise a "gong" noise at the start of the workout:
        soundManager.playStartSound()

        while (isActive) {
            val currentState = _timerState.value

            if (currentState.isPaused) {
                // Do nothing while paused. A small delay to avoid busy waiting.
                delay(Constants.SmallDelay.toLong())
            } else {
                // Not paused, so delay a bit, then continue our logic for this loop iteration.
                delay(Constants.SmallDelay.toLong()) // Delay for the specified interval

                // Perform timing logic
                val newState = currentState.copy(
                    millisecondsRemainingInAllCycles =
                    currentState.millisecondsRemainingInAllCycles - Constants.SmallDelay,

                    millisecondsRemainingInCurrentInterval =
                    currentState.millisecondsRemainingInCurrentInterval - Constants.SmallDelay
                )

                // This function will encapsulate the logic for changing states
                val updatedState = handleStateTransitions(newState)

                _timerState.value = updatedState // Update the state

                // Check if the workout has ended
                if (updatedState.millisecondsRemainingInAllCycles <= 0) {
                    // Workout just ended, break the loop
                    break
                }
            }
        }
    }

    private fun handleStateTransitions(state: TimerState): TimerState {
        // Do some logic just before the end of every interval:
        playIntervalEndSounds(state)

        // Set some flags if we've just reached the very end of an interval:
        val currentIntervalJustEnded = state.millisecondsRemainingInCurrentInterval <= 0
        val workoutJustEnded = state.millisecondsRemainingInAllCycles <= 0

        var updatedState = state

        if (!workoutJustEnded && currentIntervalJustEnded) {
            updatedState = handleIntervalEnd(state)
        }

        return updatedState
    }

    private fun playIntervalEndSounds(state: TimerState) {
        if (state.millisecondsRemainingInCurrentInterval < Constants.MillisecondsPerSecond) {
            if (state.currentIntervalType == IntervalType.Work) {
                soundManager.playWorkEndSound()
            } else {
                soundManager.playRestEndSound(state.totalCycles, state.currentCycleNum)
            }
        }
    }

    private fun transitionToNextCycleAndReset(state: TimerState): TimerState {
        soundManager.resetEndOfCycleSounds()
        val nextCycleNum = getNextCycleNumber(state)

        return state.copy(currentCycleNum = nextCycleNum)
    }

    private fun getNextCycleNumber(state: TimerState) =
        // Increment cycle number, ensuring it does not exceed total cycles
        (state.currentCycleNum + 1).coerceAtMost(state.totalCycles)

    private fun handleIntervalEnd(state: TimerState): TimerState {
        val nextIntervalType = getNextIntervalType(state.currentIntervalType)
        val nextIntervalMilliseconds = getNextIntervalMilliseconds(
            nextIntervalType,
            state.workSecondsPerCycle,
            state.restSecondsPerCycle
        )

        var updatedState = state.copy(
            currentIntervalType = nextIntervalType,
            millisecondsRemainingInCurrentInterval = nextIntervalMilliseconds
        )

        if (isCycleEnd(state)) {
            updatedState = transitionToNextCycleAndReset(updatedState)
        }

        return updatedState
    }

    /**
     * Return how many seconds remain for the entire workout program.
     */
    fun getSecondsRemainingInAllCycles() =
        _timerState.value.millisecondsRemainingInAllCycles / Constants.MillisecondsPerSecond

    /**
     * Returns true if we are currently paused.
     */
    fun isPaused(): Boolean = _timerState.value.isPaused

    /**
     * Shuts down the coroutine and it's timer, etc. Should be called when we're done with this
     * object.
     */
    fun shutdown() {
        countdownJob.cancel()
    }

    /**
     * Pauses the timer.
     */
    fun pause() {
        val currentState = _timerState.value
        val updatedState = currentState.copy(isPaused = true)
        _timerState.value = updatedState
    }

    /**
     * Resumes the timer.
     */
    fun resume() {
        val currentState = _timerState.value
        val updatedState = currentState.copy(isPaused = false)
        _timerState.value = updatedState
    }
}

/**
 * ViewModel to help separate business logic from UI logic.
 */
class MarshallsMetronomeViewModel(
    private val playSound: (Int) -> Unit = {},
) {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    // Private mutable state
    private var _runningState: MutableState<RunningState?> = mutableStateOf(null)

    // Public read-only state
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
     * Input that we've received from the "Cycles" TextField.
     */
    var totalCyclesInput by mutableStateOf("8")

    /**
     * Input that we've received from the "Work" TextField.
     */
    var secondsWorkInput by mutableStateOf("20")

    /**
     * Input that we've received from the "Rest" TextField.
     */
    var secondsRestInput by mutableStateOf("10")

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

    private var _cachedAppVersion: String? = null

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

    private fun getTotalSecondsForAllCycles(): Int {
        val secondsWork = secondsWorkInput.toIntOrNull() ?: 0
        val secondsRest = secondsRestInput.toIntOrNull() ?: 0
        val totalCycles = totalCyclesInput.toIntOrNull() ?: 0
        return (secondsWork + secondsRest) * totalCycles
    }

    /**
     * Return a mm:ss-formatted string for the total time remaining for the entire workout.
     */
    fun formatTotalTimeRemainingString(): String {
        var timeString = formatMinSec(getTotalSecondsForAllCycles())

        runningState.value?.getSecondsRemainingInAllCycles()?.let {
            timeString = formatMinSec(it)
        }

        return timeString
    }

    /**
     * Return the time remaining in the current interval, in the format "Work: 20" or "Rest: 10".
     */
    fun formatCurrentIntervalTime(): String {
        val currentState = runningState.value?.timerState?.value
        var result = "Work: ${secondsWorkInput.toIntOrNull() ?: 0}" // Default value

        currentState?.let { state ->
            val intervalType = state.currentIntervalType
            val totalSecondsForInterval = when (intervalType) {
                IntervalType.Work -> state.workSecondsPerCycle
                IntervalType.Rest -> state.restSecondsPerCycle
            }
            val secondsRemainingInCurrentInterval =
                state.millisecondsRemainingInCurrentInterval / Constants.MillisecondsPerSecond

            // As a workaround to avoid having seconds of new intervals show briefly (100 mss and then jump down),
            // when our seconds is at the maximum value for the interval, then we bump it down here instead.
            val adjustedSecondsRemaining = if (secondsRemainingInCurrentInterval == totalSecondsForInterval) {
                secondsRemainingInCurrentInterval - 1
            } else {
                secondsRemainingInCurrentInterval
            }

            val intervalTypeName = when (intervalType) {
                IntervalType.Work -> "Work"
                IntervalType.Rest -> "Rest"
            }

            result = "$intervalTypeName: $adjustedSecondsRemaining"
        }

        return result
    }

    /**
     * Return the current cycle number, in the format "1/8" or "2/8".
     * Format is "current cycle number / total cycles".
     */
    fun formatCurrentCycleNumber(): String {
        val totalCycles = totalCyclesInput.toIntOrNull() ?: 0
        val currentCycleNum = _runningState.value?.timerState?.value?.currentCycleNum ?: 1
        return "$currentCycleNum/$totalCycles"
    }

    private fun playSound(soundResourceId: Int) {
        this.playSound.invoke(soundResourceId)
    }

    /**
     * Called when the user clicks the Start/Pause/Resume button.
     */
    fun onButtonClick() {
        if (runningState.value == null) {
            // We're stopped.

            // First validate the inputs

            // Validate Cycles user input
            totalCyclesInputError = validateIntInput(totalCyclesInput)

            // Validate Work seconds user input
            secondsWorkInputError = validateIntInput(secondsWorkInput)

            // Validate Rest seconds user input
            secondsRestInputError = validateIntInput(secondsRestInput)

            // If all the validations passed then we can start up our main timer
            if (totalCyclesInputError == null &&
                secondsWorkInputError == null &&
                secondsRestInputError == null
            ) {
                // Validations passed, so we can start up our running state.
                _runningState.value = RunningState(
                    coroutineScope = coroutineScope,
                    cycles = totalCyclesInput.toInt(),
                    workSecondsPerCycle = secondsWorkInput.toInt(),
                    restSecondsPerCycle = secondsRestInput.toInt(),
                    playSound = ::playSound,
                )
            }
        } else {
            // We're running, but are we currently paused?
            if (runningState.value?.isPaused() == true) {
                // We are paused, so resume.
                runningState.value?.resume()
            } else {
                // We are not paused, so pause.
                runningState.value?.pause()
            }
        }
    }

    /**
     * Called when the user clicks the Reset button.
     */
    fun onResetClick() {
        clearResources()
    }

    /**
     * Called when we're done with this ViewModel.
     */
    fun clearResources() {
        runningState.value?.shutdown()
        _runningState.value = null
    }

    /**
     * Return the version number of our app.
     */
    // We specifically want to catch the NameNotFoundException exception here, and return
    // "Unknown" if we can't get the version number. We don't care about the other details
    // related to the exception. Also, we catch the NullPointerException so that we can
    // show the Preview.
    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    fun getAppVersion(context: Context): String {
        // Return cached app version if we have it already:
        if (_cachedAppVersion != null) {
            return _cachedAppVersion!!
        }
        // Otherwise, cache and return the app version:
        _cachedAppVersion = try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            // This part lets us use the Preview again:
            try {
                packageInfo.versionName
            } catch (e: NullPointerException) {
                "NULL_POINTER_EXCEPTION"
            }
        } catch (e: PackageManager.NameNotFoundException) {
            "Unknown"
        }
        return _cachedAppVersion!!
    }
}

/**
 * Display the total time remaining for the entire workout.
 */
@Composable
fun TotalTimeRemainingView(viewModel: MarshallsMetronomeViewModel, modifier: Modifier) {
    // Collect the current state of the timer from the ViewModel's runningState and observe changes
    val timerState = viewModel.runningState.value?.timerState?.collectAsState()

    // Observe changes in the timerState and trigger recomposition when it changes
    LaunchedEffect(timerState?.value) {
        // This block is empty but it's enough to observe changes and trigger recompositions
    }
    Text(
        text = viewModel.formatTotalTimeRemainingString(),
        fontSize = 50.sp,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/***
 * Display the Start/Pause/Resume and Reset buttons.
 */
@Composable
fun ControlButtons(viewModel: MarshallsMetronomeViewModel, modifier: Modifier) {
    Row {
        Button(
            onClick = { viewModel.onButtonClick() },
            modifier = Modifier.semantics { contentDescription = "Start workout timer" }
        ) {
            Text(text = viewModel.buttonText, modifier = modifier)
        }

        Spacer(modifier = modifier.width(10.dp))

        Button(
            onClick = { viewModel.onResetClick() },
            modifier = Modifier.semantics { contentDescription = "Reset workout timer" }
        ) {
            Text(text = "Reset", modifier = modifier)
        }
    }
}

/***
 * Display the current cycle number.
 */
@Composable
fun CurrentCycleNumberView(viewModel: MarshallsMetronomeViewModel, modifier: Modifier) {
    // Collect the current state of the timer from the ViewModel's runningState and observe changes
    val timerState = viewModel.runningState.value?.timerState?.collectAsState()

    // Observe changes in the timerState and trigger recomposition when it changes
    LaunchedEffect(timerState?.value) {
        // This block is empty but it's enough to observe changes and trigger recompositions
    }

    Text(
        text = viewModel.formatCurrentCycleNumber(),
        fontSize = 90.sp,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/***
 * Display the configuration input fields.
 */
@Composable
fun ConfigInputFields(viewModel: MarshallsMetronomeViewModel, modifier: Modifier) {
    // Configure cycles (eg, 8):
    InputTextField(
        InputTextFieldParams(
            errorMessage = viewModel.totalCyclesInputError,
            value = viewModel.totalCyclesInput,
            onValueChange = { viewModel.totalCyclesInput = normaliseIntInput(it) },
            labelText = "Cycles",
            modifier = modifier,
            enabled = viewModel.textInputControlsEnabled,
        )
    )

    // Configure Work (seconds, eg 20):
    InputTextField(
        InputTextFieldParams(
            errorMessage = viewModel.secondsWorkInputError,
            value = viewModel.secondsWorkInput,
            onValueChange = { viewModel.secondsWorkInput = normaliseIntInput(it) },
            labelText = "Work",
            modifier = modifier,
            enabled = viewModel.textInputControlsEnabled,
        )
    )

    // Configure Rest (seconds, eg 10):
    InputTextField(
        InputTextFieldParams(
            errorMessage = viewModel.secondsRestInputError,
            value = viewModel.secondsRestInput,
            onValueChange = { viewModel.secondsRestInput = normaliseIntInput(it) },
            labelText = "Rest",
            modifier = modifier,
            enabled = viewModel.textInputControlsEnabled,
        )
    )
}

/**
 * The main UI for our app.
 */
@Composable
fun MarshallsMetronome(
    modifier: Modifier = Modifier,
    errorMessage: State<String?> = mutableStateOf(null),
    viewModel: MarshallsMetronomeViewModel = MarshallsMetronomeViewModel(),
) {
    // Collect the current state of the timer from the ViewModel's runningState and observe changes
    val timerState = viewModel.runningState.value?.timerState?.collectAsState()

    // Observe changes in the timerState and trigger recomposition when it changes
    LaunchedEffect(timerState?.value) {
        // This block is empty but it's enough to observe changes and trigger recompositions
    }

    /**
     * Also make sure that we shut down the coroutine and it's timer, etc, at the end when we're
     *  done, if it was allocated at that point.
     */
    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearResources()
        }
    }

    // Column of controls, centered:

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .padding(30.dp)
    ) {
        // Total time remaining
        TotalTimeRemainingView(viewModel, modifier)

        // Seconds remaining in current interval
        Text(
            text = viewModel.formatCurrentIntervalTime(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
            // Use a smaller font size
            style = TextStyle(fontSize = 40.sp)
        )

        // Current cycle number
        CurrentCycleNumberView(viewModel, modifier)

        // Start/Pause and Reset buttons
        ControlButtons(viewModel, modifier)

        // Configuration Input Fields
        ConfigInputFields(viewModel, modifier)

        // Padding so that everything after this point gets pushed to the bottom of the screen.
        Spacer(modifier = modifier.weight(1f))

        // Error message at the bottom of the screen, if applicable:
        if (errorMessage.value != null) {
            Text(
                text = "ERROR: ${errorMessage.value}",
                color = Color.Red,
                fontWeight = FontWeight.Bold,
            )
        }

        // Version number of our app:
        Text(
            text = "Version: ${viewModel.getAppVersion(LocalContext.current)}",
            modifier = modifier,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Data class representing parameters for the InputTextField composable function.
 *
 * This class encapsulates all the parameters needed for customizing the appearance
 * and behavior of the InputTextField.
 *
 * @property errorMessage An optional error message to display. If non-null, the TextField
 *                        indicates an error state.
 * @property value The current text to be displayed in the TextField.
 * @property onValueChange Callback function to be invoked when the text changes.
 * @property labelText The label text to be displayed above the TextField.
 * @property modifier Modifier for styling and layout of the TextField.
 * @property enabled Flag to indicate whether the TextField is enabled or disabled.
 */
data class InputTextFieldParams(
    val errorMessage: String?,
    val value: String,
    val onValueChange: (String) -> Unit,
    val labelText: String,
    val modifier: Modifier = Modifier,
    val enabled: Boolean,
)

/***
 * Customized TextField used by our app.
 */
@Composable
fun InputTextField(params: InputTextFieldParams) {
    val (supportingText, isError) = getErrorInfoFor(params.errorMessage)
    TextField(
        value = params.value,
        onValueChange = params.onValueChange,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        label = { Text(text = params.labelText) },
        modifier = params.modifier.padding(top = 20.dp),
        enabled = params.enabled,
        supportingText = supportingText,
        isError = isError,
    )
}

/**
 * Preview our app.
 */
@Preview(showSystemUi = true)
@Composable
fun MarshallsMetronomePreview() {
    MarshallsMetronomeTheme {
        MarshallsMetronome()
    }
}
