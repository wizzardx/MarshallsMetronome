package com.example.marshallsmetronome

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RawRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.marshallsmetronome.ui.theme.MarshallsMetronomeTheme
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * The main activity for our app. The android runtime calls this logic.
 */
class MainActivity : ComponentActivity() {
    private var mediaPlayer: MediaPlayer? = null
    private val errorMessage = mutableStateOf<String?>(null)

    private var viewModel = MarshallsMetronomeViewModel(::playSoundImpl, errorMessage = errorMessage)

    private fun playSoundImpl(
        @RawRes soundResourceId: Int,
    ) {
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
    }

    @Suppress("TooGenericExceptionCaught")
    // Catching a generic exception intentionally for top-level error logging
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the Mobile Ads SDK.
        MobileAds.initialize(this) {}

        try {
            mediaPlayer = MediaPlayer()
        } catch (e: Exception) {
            reportError(errorMessage, e)
        }
        setContent {
            // Setup sentry
            SentryAndroid.init(this) { options ->
                options.isDebug = true // Enable Sentry debug mode
                // Set tracesSampleRate to 1.0 to capture 100% of transactions for performance monitoring.
                // We recommend adjusting this value in production.
                options.tracesSampleRate = 1.0
            }

            MarshallsMetronomeTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
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
 * A Composable function that creates an Ad Banner using Google's Mobile Ads SDK.
 *
 * This function creates an AndroidView and initializes it with an AdView. The AdView is configured
 * with the necessary parameters such as ad size, ad unit ID, and request configuration for test devices.
 * The ad is then loaded and displayed in the AndroidView.
 *
 * The ad unit ID and test device IDs are retrieved from the BuildConfig.
 */
@Composable
fun AdBanner() {
    AndroidView(
        factory = { context ->
            // Initialize the request configuration for test device.
            val deviceIds = BuildConfig.TEST_DEVICE_IDS.split(',')
            val requestConfiguration = RequestConfiguration.Builder()
                .setTestDeviceIds(deviceIds)
                .build()
            MobileAds.setRequestConfiguration(requestConfiguration)

            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = BuildConfig.ADMOB_AD_UNIT_ID
                loadAd(AdRequest.Builder().build())
            }
        }
    )
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
    fun handleError(
        errorMessage: MutableState<String?>?,
        exception: Throwable,
        tag: String = "AppError",
    ) {
        if (errorMessage == null) {
            Log.w("ErrorHandler", "errorMessage was not setup, cannot populate UI with error")
        } else {
            errorMessage.value = exception.message
        }
        Log.e(tag, "Error occurred: ", exception)
        // Additional error handling logic here
        Sentry.captureException(exception)
        FirebaseCrashlytics.getInstance().recordException(exception)
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
 * Manages sound playback for various events in the application.
 *
 * This class encapsulates all sound-related operations, providing methods to play specific sounds
 * for different scenarios like the start of a workout, the end of a work interval, the end of a rest
 * interval, and the end of the entire workout cycle. It also maintains state to ensure sounds are not
 * repeatedly played when not necessary.
 *
 * @property playSoundProp A function that takes a sound resource ID and plays the corresponding
 *                         sound. This allows for flexible integration with any
 *                         sound playing mechanism.
 */
class SoundManager(val playSoundProp: (soundResourceId: Int) -> Unit = {}) {
    private var refereeWhistlePlayed = false
    private var factoryWhistlePlayed = false
    private var endOfCycleBuzzerTriggered = false
    private var airhornPlayed = false
    private var chimesPlayed = false

    /**
     * Plays the start sound of the workout.
     *
     * This function triggers the playback of the initial sound that signifies the beginning
     * of the workout, typically a gong sound. It is intended to be called once at the start
     * of the workout session.
     */
    fun playStartSound() {
        playSoundProp(R.raw.gong)
    }

    /***
     * Plays the sound indicating the start of a work interval.
     */
    fun playWorkStartSound(currentCycleNum: Int) {
        // We only play the airhorn at the start of the first work interval.
        if (currentCycleNum == 1 && !airhornPlayed) {
            playSoundProp(R.raw.airhorn)
            airhornPlayed = true
        }
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
            playSoundProp(R.raw.factory_whistle)
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
     * @param currentCycleNum The current cycle number of the workout.
     */
    fun playRestEndSound(
        cycles: Int,
        currentCycleNum: Int,
    ) {
        if (currentCycleNum == cycles) {
            // At the end of the workout, play the referee whistle sound
            if (!refereeWhistlePlayed) {
                playSoundProp(R.raw.referee_whistle)
                refereeWhistlePlayed = true
            }
        } else if (!endOfCycleBuzzerTriggered) {
            // At the end of all rest intervals (except the last one), play the buzzer sound
            playSoundProp(R.raw.buzzer)
            endOfCycleBuzzerTriggered = true
        }
    }

    /**
     * Plays the sound indicating the end of the cooldown period.
     */
    fun playCooldownEndSound() {
        if (!chimesPlayed) {
            playSoundProp(R.raw.chimes)
            chimesPlayed = true
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
        airhornPlayed = false
        chimesPlayed = false
    }
}

/**
 * Represents the state of a timer in the application.
 *
 * This class encapsulates all necessary information regarding the current state of the timer,
 * including the time remaining in the current interval and overall workout, the current interval type (work/rest),
 * and the number of cycles in the workout. It also holds information about the duration of work and rest intervals.
 *
 * @property millisecondsRemainingForEntireWorkout The total milliseconds remaining for all cycles in the workout.
 * @property isPaused A flag indicating whether the timer is currently paused.
 * @property currentIntervalType The type of the current interval, either work or rest.
 * @property currentCycleNum The current cycle number in the workout session.
 * @property totalCycles The total number of cycles set for the workout session.
 * @property workSecondsPerCycle The number of seconds of work per cycle.
 * @property restSecondsPerCycle The number of seconds of rest per cycle.
 * @property millisecondsRemaining The number of milliseconds remaining in the current phase of the workout.
 * @property warmupTotalSeconds Duration in seconds for the warmup period.
 * @property cooldownTotalSeconds Duration in seconds for the cooldown period.
 * @property workoutStage Current workout stage (e.g., Warmup, MainWorkout, Cooldown, WorkoutEnded).
 */
data class TimerState(
    val millisecondsRemainingForEntireWorkout: Int,
    val isPaused: Boolean,
    val currentIntervalType: IntervalType,
    val currentCycleNum: Int,
    val totalCycles: Int,
    val workSecondsPerCycle: Int,
    val restSecondsPerCycle: Int,
    // Merged storage for the original:
    // - warmupMillisecondsRemaining
    // - cooldownMillisecondsRemaining
    // - millisecondsRemainingInCurrentInterval
    val millisecondsRemaining: Int,
    val warmupTotalSeconds: Int,
    val cooldownTotalSeconds: Int,
    val workoutStage: WorkoutStage,
)

/**
 * Enumerates the stages of a workout in a fitness application.
 *
 * This enum classifies the distinct phases of a workout session, facilitating the management
 * and tracking of the user's progress through the workout.
 *
 * - Warmup: The initial phase of the workout, typically involving lighter exercises to prepare the body.
 * - MainWorkout: The core phase of the workout, consisting of the primary exercise activities.
 * - Cooldown: The concluding phase, involving exercises to gradually reduce intensity and promote recovery.
 * - WorkoutEnded: Indicates the completion of the workout session.
 */
enum class WorkoutStage {
    Warmup,
    MainWorkout,
    Cooldown,
    WorkoutEnded,
}

/**
 * Transitions to the next cycle of the workout and resets necessary state.
 *
 * This function is called at the end of each cycle to update the state for the next cycle. It resets
 * end-of-cycle sounds and updates the cycle number. It ensures the timer and associated logic
 * are correctly set up for the next cycle, maintaining the continuity of the workout session.
 *
 * @param state The current state of the timer.
 * @param soundManager The SoundManager instance used to manage sound playback.
 * @return The updated TimerState reflecting the transition to the next cycle.
 */
fun transitionToNextCycleAndReset(
    state: TimerState,
    soundManager: SoundManager,
): TimerState {
    soundManager.resetEndOfCycleSounds()
    val nextCycleNum = getNextCycleNumber(state)
    return state.copy(currentCycleNum = nextCycleNum)
}

/**
 * Utility object containing shared elements for coroutine handling.
 */
object CoroutineUtils {
    /**
     * A mutable state for managing error messages in the UI.
     *
     * `errorMessage` is a `MutableState<String?>` expected to be set early in the app's lifecycle.
     * It's vital for dynamically displaying error messages in the UI. If `errorMessage` is not initialized
     * (left as null) and the UI is active, a "errorMessage was never populated" error will be displayed.
     * This is primarily relevant for unit tests or design previews, where error display is not critical.
     * A null `value` in `errorMessage` indicates no error message is currently needed for display.
     *
     * Example:
     * CoroutineUtils.errorMessage?.value = "Network error"
     */
    var errorMessage: MutableState<String?>? = null

    /**
     * A CoroutineExceptionHandler for handling uncaught exceptions in coroutines.
     * Logs the exception and terminates the application.
     *
     * The handler first logs the exception details, including the coroutine context.
     * Then, it forcefully stops the process and exits the application.
     *
     * Usage of this handler is suitable for cases where any uncaught exception in a coroutine
     * is considered critical and should result in a complete application shutdown.
     */
    val sharedExceptionHandler =
        CoroutineExceptionHandler { context, exception ->
            if (exception !is CancellationException) {
                // Log the exception
                Log.e("CoroutineException", "Exception in coroutine context: $context", exception)
                reportError(errorMessage, exception)
            }
        }
}

/**
 * Return the version number of our app.
 */
fun getAppVersion(): String = BuildConfig.VERSION_NAME

/**
 * Display the total time remaining for the entire workout.
 */
@Composable
@Suppress("FunctionNaming")
fun TotalTimeRemainingView(
    viewModel: MarshallsMetronomeViewModel,
    modifier: Modifier,
) {
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
@Suppress("FunctionNaming")
fun ControlButtons(
    viewModel: MarshallsMetronomeViewModel,
    modifier: Modifier,
) {
    Row {
        Button(
            onClick = {
                viewModel.onButtonClick()
            },
            modifier = Modifier.semantics { contentDescription = "Start workout timer" },
        ) {
            Text(text = viewModel.buttonText, modifier = modifier)
        }

        Spacer(modifier = modifier.width(8.dp))

        Button(
            onClick = {
                viewModel.onResetClick()
            },
            modifier = Modifier.semantics { contentDescription = "Reset workout timer" },
        ) {
            Text(text = "Reset", modifier = modifier)
        }
    }
}

/***
 * Display the current cycle number.
 */
@Composable
@Suppress("FunctionNaming")
fun CurrentCycleNumberView(
    viewModel: MarshallsMetronomeViewModel,
    modifier: Modifier,
) {
    // Collect the current state of the timer from the ViewModel's runningState and observe changes
    val timerState = viewModel.runningState.value?.timerState?.collectAsState()

    // Observe changes in the timerState and trigger recomposition when it changes
    LaunchedEffect(timerState?.value) {
        // This block is empty but it's enough to observe changes and trigger recompositions
    }

    // Only show the below during the main workout, not during warmup or cooldown.
    val currentStage = timerState?.value?.workoutStage
    if (currentStage == WorkoutStage.MainWorkout) {
        Text(
            text = viewModel.formatCurrentCycleNumber(),
            fontSize = 90.sp,
            modifier = modifier,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/***
 * Customized TextField used by our app.
 */
class InputTextFieldCreator(
    private val workoutInputs: WorkoutUserInputs,
    private val viewModel: MarshallsMetronomeViewModel,
    private val modifier: Modifier,
) {
    /***
     * Generate and return a user input TextField to be used in the UI.
     */
    @Composable
    fun TextField(
        label: String,
        value: String,
        setValue: (WorkoutUserInputs, String) -> Unit,
        getErrorMessage: (MarshallsMetronomeViewModel) -> String?,
    ) {
        val errorInfo = getErrorInfoFor(getErrorMessage(viewModel))
        val errorTextComposable = displayErrorInfo(errorInfo)

        TextField(
            value = value,
            onValueChange = { newValue -> setValue(workoutInputs, normaliseIntInput(newValue, value)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            label = { Text(text = label) },
            modifier = modifier.padding(8.dp),
            enabled = viewModel.textInputControlsEnabled,
            isError = errorInfo.hasError,
            supportingText = errorTextComposable,
        )
    }
}

/***
 * Display the configuration input fields.
 */
@Composable
@Suppress("FunctionNaming")
fun ConfigInputFields(
    viewModel: MarshallsMetronomeViewModel,
    modifier: Modifier,
) {
    val workoutInputs = viewModel.workoutInputs
    val textFieldCreator = InputTextFieldCreator(workoutInputs, viewModel, modifier)

    // Configure cycles (eg, 8):
    textFieldCreator.TextField(
        label = "Cycles",
        value = workoutInputs.totalCycles,
        setValue = { inputs, newValue -> inputs.totalCycles = newValue },
        getErrorMessage = { vm -> vm.totalCyclesInputError },
    )

    // Warmup Input Field
    textFieldCreator.TextField(
        label = "Warmup",
        value = workoutInputs.secondsWarmup,
        setValue = { inputs, newValue -> inputs.secondsWarmup = newValue },
        getErrorMessage = { vm -> vm.secondsWarmupInputError },
    )

    // Configure Work (seconds, eg 20):
    textFieldCreator.TextField(
        label = "Work",
        value = workoutInputs.secondsWork,
        setValue = { inputs, newValue -> inputs.secondsWork = newValue },
        getErrorMessage = { vm -> vm.secondsWorkInputError },
    )

    // Configure Rest (seconds, eg 10):
    textFieldCreator.TextField(
        label = "Rest",
        value = workoutInputs.secondsRest,
        setValue = { inputs, newValue -> inputs.secondsRest = newValue },
        getErrorMessage = { vm -> vm.secondsRestInputError },
    )

    // Cooldown Input Field
    textFieldCreator.TextField(
        label = "Cooldown",
        value = workoutInputs.secondsCooldown,
        setValue = { inputs, newValue -> inputs.secondsCooldown = newValue },
        getErrorMessage = { vm -> vm.secondsCooldownInputError },
    )
}

/**
 * The main UI for our app.
 */
@Composable
@Suppress("FunctionNaming")
fun MarshallsMetronome(
    modifier: Modifier = Modifier,
    errorMessage: MutableState<String?>? = null,
    viewModel: MarshallsMetronomeViewModel = MarshallsMetronomeViewModel(errorMessage = errorMessage),
) {
    // Collect the current state of the timer from the ViewModel's runningState and observe changes
    val timerState = viewModel.runningState.value?.timerState?.collectAsState()

    // Get and remember (don't keep recomputing) the app version number:
    val appVersion = remember { getAppVersion() }

    // Enable automatic coroutine error handlers to update the UI:
    CoroutineUtils.errorMessage = errorMessage

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
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // Total time remaining
        TotalTimeRemainingView(viewModel, modifier)

        // Seconds remaining in current interval
        Text(
            text = viewModel.formatCurrentStageTime(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
            // Use a smaller font size
            style = TextStyle(fontSize = 30.sp),
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
        val displayText =
            when {
                errorMessage == null -> "ERROR: errorMessage was never populated"
                errorMessage.value != null -> "ERROR: ${errorMessage.value}"
                else -> "" // Or any default message when errorMessage.value is null
            }
        if (displayText.isNotEmpty()) {
            Text(
                text = displayText,
                color = Color.Red,
                fontWeight = FontWeight.Bold,
                modifier = modifier,
                textAlign = TextAlign.Center,
            )
        }

        // Version number of our app:
        Text(
            text = "Version: $appVersion",
            modifier = modifier,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        AdBanner()
    }
}

/**
 * Preview our app.
 */
@Preview(showSystemUi = true)
@Composable
@Suppress("FunctionNaming")
fun MarshallsMetronomePreview() {
    MarshallsMetronomeTheme {
        MarshallsMetronome()
    }
}

/**
 * Represents user actions for controlling a Tabata exercise timer within the app.
 */
sealed class UserAction {
    /**
     * Starts the Tabata timer.
     */
    data object Start : UserAction()

    /**
     * Pauses the ongoing Tabata timer.
     */
    data object Pause : UserAction()

    /**
     * Resumes the Tabata timer after being paused.
     */
    data object Resume : UserAction()

    /**
     * Resets the Tabata timer to its initial state.
     */
    data object Reset : UserAction()
    // Add other actions as necessary
}
