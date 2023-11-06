package com.example.marshallsmetronome

// TODO: Unit tests / Integration tests?
// TODO: Upload to github
// TODO: Deploy somewhere, eg Play Store or somewhere free maybe Firebase?
// TODO: Setup CI/CD?

import android.os.Bundle
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.marshallsmetronome.ui.theme.MarshallsMetronomeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The main activity for our app. The android runtime calls this logic.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MarshallsMetronomeTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MarshallsMetronome()
                }
            }
        }
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
) {
    private var _secondsRemainingInAllCycles = mutableStateOf(
        getSecondsForAllCycles(
            cycles = cycles,
            workSecondsPerCycle = workSecondsPerCycle,
            restSecondsPerCycle = restSecondsPerCycle
        )
    )

    /**
     * How many seconds remain for the entire workout program.
     */
    val secondsRemainingInAllCycles: State<Int> = _secondsRemainingInAllCycles

    private var _isPaused = mutableStateOf(false)

    private var _currentIntervalType =
        mutableStateOf(Constants.FirstIntervalTypeInCycle)

    /**
     * The current interval type, eg, "Work" or "Rest".
     */
    val currentIntervalType: State<IntervalType> = _currentIntervalType

    private var _currentCycleNum = mutableStateOf(1)

    /**
     * The current cycle number, eg, 1, 2, 3, etc. Starts at 1, usually goes up to 8.
     */
    val currentCycleNum: State<Int> = _currentCycleNum

    private var _secondsRemainingInCurrentInterval =
        mutableStateOf(getSecondsForFirstInterval(workSecondsPerCycle = workSecondsPerCycle))

    /**
     * How many seconds remain in the current interval, eg, 20 seconds of work, or 10 seconds of
     * rest.
     */
    val secondsRemainingInCurrentInterval: State<Int> = _secondsRemainingInCurrentInterval

    private var countdownJob: Job = coroutineScope.launch {
        var millisecondsRemainingInInterval = workSecondsPerCycle * Constants.MillisecondsPerSecond
        var millisecondsRemainingInAllCycles =
            (workSecondsPerCycle + restSecondsPerCycle) * cycles * Constants.MillisecondsPerSecond

        while (true) {
            // Do nothing while paused.
            while (_isPaused.value) {
                delay(Constants.SmallDelay.toLong())
            }

            // Not paused, so delay a bit, then continue our logic for this loop iteration.
            delay(Constants.SmallDelay.toLong())

            // decrease remaining milliseconds in current interval by 100, but don't go past 0:
            millisecondsRemainingInInterval =
                (millisecondsRemainingInInterval - Constants.SmallDelay).coerceAtLeast(0)
            _secondsRemainingInCurrentInterval.value =
                millisecondsRemainingInInterval / Constants.MillisecondsPerSecond

            // Do the same with remaining milliseconds in all the cycles:
            millisecondsRemainingInAllCycles =
                (millisecondsRemainingInAllCycles - Constants.SmallDelay).coerceAtLeast(0)
            _secondsRemainingInAllCycles.value = (
                millisecondsRemainingInAllCycles / Constants.MillisecondsPerSecond
                ).toInt()

            // Did we reach the end of the current interval?
            if (millisecondsRemainingInInterval == 0) {

                // Did we also just reach the end of the last cycle?
                if (millisecondsRemainingInAllCycles == 0) {
                    // Yes, so we're done. Break out of the loop, also meaning that
                    // the coroutine terminates.
                    break
                }

                // Did we just finish a cycle?
                val justFinishedACycle =
                    _currentIntervalType.value == Constants.LastIntervalTypeInCycle

                // Switch over to next interval type
                _currentIntervalType.value = when (_currentIntervalType.value) {
                    IntervalType.Work -> IntervalType.Rest
                    IntervalType.Rest -> IntervalType.Work
                }

                // Switch over to seconds remaining of the next interval
                millisecondsRemainingInInterval = when (_currentIntervalType.value) {
                    IntervalType.Work -> workSecondsPerCycle * Constants.MillisecondsPerSecond
                    IntervalType.Rest -> restSecondsPerCycle * Constants.MillisecondsPerSecond
                }
                // Over here we go from eg, 0 seconds remaining of a work interval, to
                // 9 seconds (rather than 10 seconds) remaining of the rest interval
                _secondsRemainingInCurrentInterval.value =
                    (millisecondsRemainingInInterval / Constants.MillisecondsPerSecond).toInt() - 1

                // Go to the next cycle if we just completed a previous cycle:
                if (justFinishedACycle) {
                    _currentCycleNum.value++
                }
            }
        }
    }

    /**
     * Returns true if we are currently paused.
     */
    fun isPaused(): Boolean = _isPaused.value

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
        _isPaused.value = true
    }

    /**
     * Resumes the timer.
     */
    fun resume() {
        _isPaused.value = false
    }
}

private fun getSecondsForAllCycles(
    cycles: Int,
    workSecondsPerCycle: Int,
    restSecondsPerCycle: Int
) = (workSecondsPerCycle + restSecondsPerCycle) * cycles

private fun getSecondsForFirstInterval(
    workSecondsPerCycle: Int
) = workSecondsPerCycle

private fun formatMinSec(totalSeconds: Int): String {
    val minutes = totalSeconds / Constants.SecondsPerMinute
    val seconds = totalSeconds % Constants.SecondsPerMinute
    return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
}

private fun normaliseIntInput(s: String): String {
    // Go through the string, and remove any non-numeric characters.
    // Don't allow 0 at the start of the number. But if there is no number at the
    // end, then our result is 0
    var result = ""
    var foundNonZero = false
    for (c in s) {
        if (c.isDigit()) {
            if (c != '0') {
                foundNonZero = true
            }
            if (foundNonZero) {
                result += c
            }
        }
    }
    return result
}

private fun validateIntInput(value: String): String? {
    val intVal = value.toIntOrNull()
    return when {
        intVal == null -> "Invalid number"
        intVal < 1 -> "Must be at least 1"
        intVal > Constants.MaxUserInputNum -> "Must be at most 100"
        else -> null // valid input
    }
}

private fun getErrorInfoFor(error: String?): Pair<(@Composable (() -> Unit))?, Boolean> {
    return if (error == null) {
        Pair(null, false)
    } else {
        Pair(
            {
                Text(
                    text = error,
                    color = Color.Red,
                    fontWeight = FontWeight.Bold
                )
            },
            true
        )
    }
}

/**
 * ViewModel to help separate business logic from UI logic.
 */
class MarshallsMetronomeViewModel : ViewModel() {

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var runningState: MutableState<RunningState?> = mutableStateOf(null)

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

        runningState.value?.secondsRemainingInAllCycles?.value?.let {
            timeString = formatMinSec(it)
        }

        return timeString
    }

    /**
     * Return the time remaining in the current interval, in the format "Work: 20" or "Rest: 10".
     */
    fun formatCurrentIntervalTime(): String {
        var timeString = "Work: ${secondsWorkInput.toIntOrNull() ?: 0}"

        runningState.value?.currentIntervalType?.value?.let { intervalType ->
            runningState.value?.secondsRemainingInCurrentInterval?.value?.let { secondsRemainingInCurrentInterval ->
                val intervalTypeName = when (intervalType) {
                    IntervalType.Work -> "Work"
                    IntervalType.Rest -> "Rest"
                }
                timeString = "$intervalTypeName: $secondsRemainingInCurrentInterval"
            }
        }

        return timeString
    }

    /**
     * Return the current cycle number, in the format "1/8" or "2/8".
     * Format is "current cycle number / total cycles".
     */
    fun formatCurrentCycleNumber(): String {
        val totalCycles = totalCyclesInput.toIntOrNull() ?: 0
        var timeString = "1/$totalCycles"

        runningState.value?.currentCycleNum?.value?.let { currentCycleNum ->
            timeString = "$currentCycleNum/$totalCycles"
        }

        return timeString
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
                runningState.value = RunningState(
                    coroutineScope = coroutineScope,
                    cycles = totalCyclesInput.toInt(),
                    workSecondsPerCycle = secondsWorkInput.toInt(),
                    restSecondsPerCycle = secondsRestInput.toInt()
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
        runningState.value?.shutdown()
        runningState.value = null
    }

    /**
     * Called when we're done with this ViewModel.
     */
    fun clearResources() {
        runningState.value?.shutdown()
    }

    override fun onCleared() {
        clearResources()
        super.onCleared()
    }
}

/**
 * The main UI for our app.
 */
@Composable
fun MarshallsMetronome(
    modifier: Modifier = Modifier,
    viewModel: MarshallsMetronomeViewModel = viewModel()
) {
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

        Text(
            text = viewModel.formatTotalTimeRemainingString(),
            fontSize = 50.sp,
            modifier = modifier,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

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

        Text(
            text = viewModel.formatCurrentCycleNumber(),
            fontSize = 90.sp,
            modifier = modifier,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // Start/Pause and Reset buttons
        Row {
            Button(onClick = { viewModel.onButtonClick() }) {
                Text(text = viewModel.buttonText, modifier = modifier)
            }

            Spacer(modifier = modifier.width(10.dp))

            Button(onClick = { viewModel.onResetClick() }) {
                Text(text = "Reset", modifier = modifier)
            }
        }

        // Configure cycles (eg, 8):
        InputTextField(
            errorMessage = viewModel.totalCyclesInputError,
            value = viewModel.totalCyclesInput,
            onValueChange = { viewModel.totalCyclesInput = normaliseIntInput(it) },
            labelText = "Cycles",
            modifier = modifier,
            enabled = viewModel.textInputControlsEnabled
        )

        // Configure Work (seconds, eg 20):
        InputTextField(
            errorMessage = viewModel.secondsWorkInputError,
            value = viewModel.secondsWorkInput,
            onValueChange = { viewModel.secondsWorkInput = normaliseIntInput(it) },
            labelText = "Work",
            modifier = modifier,
            enabled = viewModel.textInputControlsEnabled,
        )

        // Configure Rest (seconds, eg 10):
        InputTextField(
            errorMessage = viewModel.secondsRestInputError,
            value = viewModel.secondsRestInput,
            onValueChange = { viewModel.secondsRestInput = normaliseIntInput(it) },
            labelText = "Rest",
            modifier = modifier,
            enabled = viewModel.textInputControlsEnabled
        )
    }
}

/***
 * Customized TextField used by our app.
 */
@Composable
fun InputTextField(
    errorMessage: String?,
    value: String,
    onValueChange: (String) -> Unit,
    labelText: String,
    modifier: Modifier = Modifier,
    enabled: Boolean,
) {
    val (supportingText, isError) = getErrorInfoFor(errorMessage)
    TextField(
        value = value,
        onValueChange = onValueChange,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        label = { Text(text = labelText) },
        modifier = modifier.padding(top = 20.dp),
        enabled = enabled,
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
