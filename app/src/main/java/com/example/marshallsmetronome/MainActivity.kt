package com.example.marshallsmetronome

// TODO: Implement jumping to input fields that have errors
// TODO: Fewer magic numbers, more constants, in case eg, other interval types are added
// TODO: Run all the lints and other static checks.
// TODO: Unit tests / Integration tests?
// TODO: Upload to github
// TODO: Deploy somewhere, eg Play Store or somewhere free maybe Firebase?

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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.marshallsmetronome.ui.theme.MarshallsMetronomeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

enum class IntervalType {
    Work,
    Rest,
}

object Constants {
    val FirstIntervalTypeInCycle = IntervalType.Work
    val LastIntervalTypeInCycle = IntervalType.Rest
}

class RunningState(
    private val coroutineScope: CoroutineScope,
    val cycles: Int,
    val workSecondsPerCycle: Int,
    val restSecondsPerCycle: Int,
) {
    private var _secondsRemainingInAllCycles = mutableStateOf<Int>(
        getSecondsForAllCycles(
            cycles = cycles,
            workSecondsPerCycle = workSecondsPerCycle,
            restSecondsPerCycle = restSecondsPerCycle
        )
    )
    val secondsRemainingInAllCycles: State<Int> = _secondsRemainingInAllCycles

    private var _isPaused = mutableStateOf<Boolean>(false)
    fun isPaused(): Boolean = _isPaused.value

    private var _currentIntervalType =
        mutableStateOf<IntervalType>(Constants.FirstIntervalTypeInCycle)
    val currentIntervalType: State<IntervalType> = _currentIntervalType

    private var _currentCycleNum = mutableStateOf<Int>(1)
    val currentCycleNum: State<Int> = _currentCycleNum

    private var _secondsRemainingInCurrentInterval =
        mutableStateOf<Int>(getSecondsForFirstInterval(workSecondsPerCycle = workSecondsPerCycle))
    val secondsRemainingInCurrentInterval: State<Int> = _secondsRemainingInCurrentInterval

    private var countdownJob: Job = coroutineScope.launch {
        var millisecondsRemainingInInterval = workSecondsPerCycle * 1000L
        var millisecondsRemainingInAllCycles =
            (workSecondsPerCycle + restSecondsPerCycle) * cycles * 1000L

        var currentIntervalNum = 1

        while (true) {
            // Do nothing while paused.
            while (_isPaused.value) {
                delay(100)
            }

            // Not paused, so delay a bit, then continue our logic for this loop iteration.
            delay(100)

            // decrease remaining milliseconds in current interval by 100, but don't go past 0:
            millisecondsRemainingInInterval =
                (millisecondsRemainingInInterval - 100).coerceAtLeast(0)
            _secondsRemainingInCurrentInterval.value =
                (millisecondsRemainingInInterval / 1000).toInt()

            // Do the same with remaining milliseconds in all the cycles:
            millisecondsRemainingInAllCycles =
                (millisecondsRemainingInAllCycles - 100).coerceAtLeast(0)
            _secondsRemainingInAllCycles.value = (millisecondsRemainingInAllCycles / 1000).toInt()

            // Did we reach the end of the current interval?
            if (millisecondsRemainingInInterval == 0L) {

                // Did we also just reach the end of the last cycle?
                if (millisecondsRemainingInAllCycles == 0L) {
                    // Yes, so we're done. Break out of the loop, also meaning that
                    // the coroutine terminates.
                    println("TODO: We're done")
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
                    IntervalType.Work -> workSecondsPerCycle * 1000L
                    IntervalType.Rest -> restSecondsPerCycle * 1000L
                }
                // Over here we go from eg, 0 seconds remaining of a work interval, to
                // 9 seconds (rather than 10 seconds) remaining of the rest interval
                _secondsRemainingInCurrentInterval.value =
                    (millisecondsRemainingInInterval / 1000).toInt() - 1

                // Go to the next cycle if we just completed a previous cycle:
                if (justFinishedACycle) {
                    _currentCycleNum.value++
                }
            }
        }
    }

    fun shutdown() {
        // TODO: Test this
        countdownJob.cancel()
    }

    fun pause() {
        _isPaused.value = true
    }

    fun resume() {
        _isPaused.value = false
    }

}

private fun getSecondsForAllCycles(
    cycles: Int,
    workSecondsPerCycle: Int,
    restSecondsPerCycle: Int
): Int {
    return (workSecondsPerCycle + restSecondsPerCycle) * cycles
}

private fun getSecondsForFirstInterval(
    workSecondsPerCycle: Int
): Int {
    return workSecondsPerCycle
}

@Composable
fun MarshallsMetronome(modifier: Modifier = Modifier) {

    // Our state:
    val coroutineScope = rememberCoroutineScope()
    var runningState: RunningState? by remember { mutableStateOf(null) }

    // Also make sure that we shut down the coroutine and it's timer, etc, at the end when we're
    // done, if it was allocated at that point.
    DisposableEffect(Unit) {
        onDispose {
            runningState?.shutdown()
            runningState = null
        }
    }

    var totalCyclesInput by remember { mutableStateOf("8") }
    var secondsWorkInput by remember { mutableStateOf("20") }
    var secondsRestInput by remember { mutableStateOf("10") }

    var totalCyclesInputError: String? by remember { mutableStateOf(null) }
    var secondsWorkInputError: String? by remember { mutableStateOf(null) }
    var secondsRestInputError: String? by remember { mutableStateOf(null) }

    // Column of controls, centered:

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .padding(30.dp)
    ) {

        // Total time remaining

        fun getTotalSecondsForAllCycles(): Int {
            return ((secondsWorkInput.toIntOrNull() ?: 0) +
                    (secondsRestInput.toIntOrNull() ?: 0)) * (totalCyclesInput.toIntOrNull() ?: 0)
        }

        fun formatSecondsAsMinutesAndSeconds(seconds: Int): String {
            val minutes = seconds / 60
            val seconds = seconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }

        var timeString = formatSecondsAsMinutesAndSeconds(getTotalSecondsForAllCycles())

        runningState?.secondsRemainingInAllCycles?.value?.let { totalSecondsRemaining ->
            timeString = formatSecondsAsMinutesAndSeconds(totalSecondsRemaining)
        }

        Text(
            text = timeString,
            fontSize = 50.sp,
            modifier = modifier
        )

        // Seconds remaining in current interval

        timeString = "Work: ${secondsWorkInput.toIntOrNull() ?: 0}"

        runningState?.currentIntervalType?.value?.let { intervalType ->
            runningState?.secondsRemainingInCurrentInterval?.value?.let { secondsRemainingInCurrentInterval ->
                val intervalTypeName = when (intervalType) {
                    IntervalType.Work -> "Work"
                    IntervalType.Rest -> "Rest"
                }
                timeString = "$intervalTypeName: $secondsRemainingInCurrentInterval"
            }
        }

        Text(
            text = timeString,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
            // Use a smaller font size
            style = TextStyle(fontSize = 70.sp)
        )

        // Current cycle number
        val totalCycles = (totalCyclesInput.toIntOrNull() ?: 0)
        timeString = "1/$totalCycles"

        runningState?.currentCycleNum?.value?.let { currentCycleNum ->
            timeString = "$currentCycleNum/$totalCycles"
        }

        Text(
            text = timeString,
            fontSize = 90.sp,
            modifier = modifier
        )

        fun normaliseIntInput(s: String): String {
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

        // Start/Pause and Reset button, next to each other
        Row {
            Button(
                onClick = {
                    if (runningState == null) {
                        // We're stopped.

                        // First validate the inputs

                        fun validateIntInput(value: String): String? {
                            val intVal = value.toIntOrNull()
                            return when {
                                intVal == null -> "Invalid number"
                                intVal < 1 -> "Must be at least 1"
                                intVal > 100 -> "Must be at most 100"
                                else -> null // valid input
                            }
                        }

                        // Validate Cycles user input
                        totalCyclesInputError = validateIntInput(totalCyclesInput)

                        // Validate Work seconds user input
                        secondsWorkInputError = validateIntInput(secondsWorkInput)

                        // Validate Rest seconds user input
                        secondsRestInputError = validateIntInput(secondsRestInput)

                        // If all the validations passed then we can start up our main timer
                        if (totalCyclesInputError == null && secondsWorkInputError == null && secondsRestInputError == null) {
                            // Validations passed, so we can start up our running state.
                            runningState = RunningState(
                                coroutineScope = coroutineScope,
                                cycles = totalCyclesInput.toInt(),
                                workSecondsPerCycle = secondsWorkInput.toInt(),
                                restSecondsPerCycle = secondsRestInput.toInt()
                            )
                        }
                    } else {
                        // We're running, but are we currently paused?
                        if (runningState?.isPaused() == true) {
                            // We are paused, so resume.
                            runningState?.resume()
                        } else {
                            // We are not paused, so pause.
                            runningState?.pause()
                        }
                    }
                }
            ) {
                // If we're not yet running, then show Start.
                // Otherwise, if we are running, but are paused, show Resume.
                // Otherwise, if we are running, and are not paused, then show Pause.
                val buttonText = if (runningState == null) {
                    "Start"
                } else if (runningState?.isPaused() == true) {
                    "Resume"
                } else {
                    "Pause"
                }

                Text(
                    text = buttonText,
                    modifier = modifier
                )
            }

            Spacer(modifier = modifier.width(10.dp))

            Button(
                onClick = {
                    runningState?.shutdown()
                    runningState = null
                }
            ) {
                Text(
                    text = "Reset",
                    modifier = modifier
                )
            }
        }

        fun getErrorInfoFor(error: String?): Pair<(@Composable (() -> Unit))?, Boolean> {
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

        // Configure cycles (eg, 8):
        val (supportingText, isError) = getErrorInfoFor(totalCyclesInputError)
        TextField(
            value = totalCyclesInput,
            onValueChange = { totalCyclesInput = normaliseIntInput(it) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            label = { Text(text = "Cycles") },
            modifier = modifier.padding(top = 20.dp),
            enabled = runningState == null,
            supportingText = supportingText,
            isError = isError,
        )

        // Configure Work (seconds, eg 20):
        val (supportingText2, isError2) = getErrorInfoFor(secondsWorkInputError)
        TextField(
            value = secondsWorkInput,
            onValueChange = { secondsWorkInput = normaliseIntInput(it) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            label = { Text(text = "Work") },
            modifier = modifier.padding(top = 20.dp),
            enabled = runningState == null,
            supportingText = supportingText2,
            isError = isError2,
        )

        // Configure Rest (seconds, eg 10):
        val (supportingText3, isError3) = getErrorInfoFor(secondsRestInputError)
        TextField(
            value = secondsRestInput,
            onValueChange = { secondsRestInput = normaliseIntInput(it) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            label = { Text(text = "Rest") },
            modifier = modifier.padding(top = 20.dp),
            enabled = runningState == null,
            supportingText = supportingText3,
            isError = isError3,
        )

    }

}

@Preview(showSystemUi = true)
@Composable
fun MarshallsMetronomePreview() {
    MarshallsMetronomeTheme {
        MarshallsMetronome()
    }
}