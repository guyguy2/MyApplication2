package com.guy.myapplication.domain.model

import com.guy.myapplication.domain.enums.SimonButton
import com.guy.myapplication.domain.enums.SoundPack

/**
 * Data class to represent game UI state
 */
data class SimonGameUiState(
    val gameState: GameState = GameState.WaitingToStart,
    val level: Int = 1,
    val sequence: List<SimonButton> = emptyList(),
    val playerSequence: List<SimonButton> = emptyList(),
    val currentlyLit: SimonButton? = null,
    val allButtonsLit: Boolean = false,  // Flag for when all buttons should light up
    val highScore: Int = 0,
    val currentSoundPack: SoundPack = SoundPack.STANDARD,
    val activeButtonPresses: MutableMap<SimonButton, Boolean> = mutableMapOf(), // Track which buttons are currently pressed
    val vibrateEnabled: Boolean = true, // Whether button vibration is enabled
    val soundEnabled: Boolean = true // Whether sound is enabled (mute/unmute)
)