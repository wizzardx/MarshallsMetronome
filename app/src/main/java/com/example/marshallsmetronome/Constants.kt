package com.example.marshallsmetronome

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
