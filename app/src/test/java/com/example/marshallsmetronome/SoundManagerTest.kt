package com.example.marshallsmetronome

import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@Suppress("FunctionMaxLength")
class SoundManagerTest {

    private lateinit var soundManager: SoundManager
    private val mockPlaySound: (Int) -> Unit = mock()

    @Before
    fun setup() {
        soundManager = SoundManager(mockPlaySound)
    }

    @Test
    fun `playStartSound triggers sound playback`() {
        soundManager.playStartSound()
        verify(mockPlaySound).invoke(R.raw.gong)
    }

    @Test
    fun `playWorkEndSound triggers sound playback once`() {
        soundManager.playWorkEndSound()
        soundManager.playWorkEndSound()
        verify(mockPlaySound, times(1)).invoke(R.raw.factory_whistle)
    }

    @Test
    fun `playRestEndSound triggers buzzer sound when not last cycle`() {
        soundManager.playRestEndSound(cycles = 3, currentCycle = 2)
        verify(mockPlaySound).invoke(R.raw.buzzer)
        verify(mockPlaySound, never()).invoke(R.raw.referee_whistle)
    }

    @Test
    fun `playRestEndSound triggers referee whistle sound on last cycle`() {
        soundManager.playRestEndSound(cycles = 3, currentCycle = 3)
        verify(mockPlaySound).invoke(R.raw.referee_whistle)
        verify(mockPlaySound, never()).invoke(R.raw.buzzer)
    }

    @Test
    fun `resetEndOfCycleSounds resets sound played flags`() {
        // Trigger sounds first
        soundManager.playWorkEndSound()
        soundManager.playRestEndSound(cycles = 3, currentCycle = 2)

        // Reset the flags
        soundManager.resetEndOfCycleSounds()

        // The sounds should play again after resetting
        soundManager.playWorkEndSound()
        soundManager.playRestEndSound(cycles = 3, currentCycle = 2)
        verify(mockPlaySound, times(2)).invoke(R.raw.factory_whistle)
        verify(mockPlaySound, times(2)).invoke(R.raw.buzzer)
    }
}
