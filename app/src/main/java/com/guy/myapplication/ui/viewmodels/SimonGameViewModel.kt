package com.guy.myapplication.ui.viewmodels

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guy.myapplication.data.manager.SimonSoundManager
import com.guy.myapplication.domain.enums.SimonButton
import com.guy.myapplication.domain.enums.SoundPack
import com.guy.myapplication.domain.model.GameState
import com.guy.myapplication.domain.model.SimonGameUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * ViewModel for the Simon Says game logic with lifecycle awareness
 */
class SimonGameViewModel(
    // Inject SimonSoundManager through constructor
    private val soundManager: SimonSoundManager
) : ViewModel(), DefaultLifecycleObserver {

    // Get the application context from Koin
    private val appContext = soundManager.getContext()

    private val TAG = "SimonGameViewModel"

    // Flag to track if startup animation has been played in this session
    private var hasPlayedStartupAnimation = false

    // Timeout duration for player's turn (10 seconds)
    private val playerTimeoutDuration = 10000L // 10 seconds in milliseconds

    // Job to track the timeout timer
    private var timeoutJob: Job? = null

    // Track if the app is currently in foreground
    private var isAppInForeground = true

    // Track if the game was active before going to background
    private var wasGameActiveBeforeBackground = false

    // Track the game state before going to background
    private var gameStateBeforeBackground: GameState = GameState.WaitingToStart

    // SharedPreferences for storing settings
    private val preferences = appContext.getSharedPreferences(
        "simon_game_prefs", Context.MODE_PRIVATE
    )

    // Private and public state flows
    private val _uiState = MutableStateFlow(SimonGameUiState())
    val uiState: StateFlow<SimonGameUiState> = _uiState.asStateFlow()

    init {
        Log.d(TAG, "Initializing SimonGameViewModel")

        // Load settings from preferences
        loadSettings()

        // Play startup animation once when app starts, then start the game
        playStartupAnimation {
            hasPlayedStartupAnimation = true
            initializeNewGame()
        }
    }

    /**
     * Load saved settings from SharedPreferences
     */
    private fun loadSettings() {
        val savedSoundPackName = preferences.getString("sound_pack", SoundPack.STANDARD.name)
        val savedSoundPack = try {
            SoundPack.valueOf(savedSoundPackName ?: SoundPack.STANDARD.name)
        } catch (e: Exception) {
            SoundPack.STANDARD
        }

        val savedHighScore = preferences.getInt("high_score", 0)

        // Load vibration setting (default to true)
        val savedVibrateEnabled = preferences.getBoolean("vibrate_enabled", true)

        Log.d(TAG, "Loaded settings - Sound Pack: $savedSoundPack, High Score: $savedHighScore, Vibrate: $savedVibrateEnabled")

        // Update sound manager with saved sound pack and vibration setting
        soundManager.setSoundPack(savedSoundPack)
        soundManager.setVibrationEnabled(savedVibrateEnabled)

        // Update UI state with saved settings
        _uiState.update { it.copy(
            currentSoundPack = savedSoundPack,
            highScore = savedHighScore,
            vibrateEnabled = savedVibrateEnabled
        )}
    }

    /**
     * Save settings to SharedPreferences
     */
    private fun saveSettings() {
        preferences.edit {
            putString("sound_pack", _uiState.value.currentSoundPack.name)
                .putInt("high_score", _uiState.value.highScore)
                .putBoolean("vibrate_enabled", _uiState.value.vibrateEnabled)
        }

        Log.d(TAG, "Saved settings - Sound Pack: ${_uiState.value.currentSoundPack.name}, " +
                "High Score: ${_uiState.value.highScore}, " +
                "Vibrate: ${_uiState.value.vibrateEnabled}")
    }

    // Track previous game state before entering settings
    private var previousGameState: GameState = GameState.WaitingToStart

    // Track active coroutine jobs that need to be paused/resumed
    private var activeSequenceJob: Job? = null

    /**
     * Switch to settings screen
     */
    fun showSettings() {
        Log.d(TAG, "Switching to settings screen")

        // Store current state to restore when returning from settings
        previousGameState = _uiState.value.gameState

        // Cancel any active timeout timer when going to settings
        cancelTimeoutTimer()

        // Cancel any active sequence jobs
        activeSequenceJob?.cancel()
        activeSequenceJob = null


        // Switch to settings screen
        _uiState.update { it.copy(gameState = GameState.Settings) }
    }

    /**
     * Return from settings to game
     */
    fun exitSettings() {
        Log.d(TAG, "Exiting settings screen")

        // Only resume if app is in foreground
        if (!isAppInForeground) {
            Log.d(TAG, "App is in background, not resuming game from settings yet")
            return
        }

        when (previousGameState) {
            // If we were showing sequence when settings was opened, restart sequence display
            is GameState.ShowingSequence -> {
                Log.d(TAG, "Resuming from ShowingSequence state - restarting sequence")
                // First update state
                _uiState.update { it.copy(gameState = GameState.WaitingToStart) }
                // Then restart sequence display
                showSequence()
            }

            // If player was repeating a sequence, let them continue
            is GameState.PlayerRepeating -> {
                Log.d(TAG, "Resuming from PlayerRepeating state")
                _uiState.update { it.copy(gameState = GameState.PlayerRepeating) }
                // Reset the timeout timer when returning to the game
                resetTimeoutTimer()
            }

            // If we were in a transitional state, just start a new game
            is GameState.Settings,
            is GameState.GameOver,
            is GameState.WaitingToStart -> {
                Log.d(TAG, "Starting new game after returning from settings")
                // If there's no sequence yet or game was over, start a new game
                if (_uiState.value.sequence.isEmpty()) {
                    startNewGame()
                } else {
                    // If there was a sequence, go to player repeating state
                    _uiState.update { it.copy(gameState = GameState.PlayerRepeating) }
                    resetTimeoutTimer()
                }
            }
        }

        // Reset the previous state
        previousGameState = GameState.WaitingToStart
    }

    /**
     * Change sound pack
     */
    fun setSoundPack(soundPack: SoundPack) {
        Log.d(TAG, "Changing sound pack to: ${soundPack.name}")

        // Update sound manager
        soundManager.setSoundPack(soundPack)

        // Update UI state
        _uiState.update { it.copy(currentSoundPack = soundPack) }

        // Save to preferences
        saveSettings()
    }

    /**
     * Toggle vibration setting
     */
    fun setVibrationEnabled(enabled: Boolean) {
        Log.d(TAG, "Setting vibration enabled: $enabled")

        // Update sound manager
        soundManager.setVibrationEnabled(enabled)

        // Update UI state
        _uiState.update { it.copy(vibrateEnabled = enabled) }

        // Save to preferences
        saveSettings()
    }

    // Initialize game state for a new game without animation
    private fun initializeNewGame() {
        Log.d(TAG, "Initializing new game state")

        // Cancel any active timers to prevent unexpected behavior
        cancelTimeoutTimer()

        _uiState.update { currentState ->
            currentState.copy(
                gameState = GameState.WaitingToStart,
                level = 1,
                sequence = emptyList(),
                playerSequence = emptyList(),
                currentlyLit = null,
                allButtonsLit = false
            )
        }

        // Start the game by generating and showing the first sequence
        // Only if app is in foreground
        if (isAppInForeground) {
            generateNextSequence()
            showSequence()
        }
    }

    // Start a new game - public method called by UI
    fun startNewGame() {
        Log.d(TAG, "Starting new game")
        // No startup animation on manual game restart
        initializeNewGame()
    }

    // Play a startup animation by lighting up each button in sequence
    private fun playStartupAnimation(onComplete: () -> Unit) {
        Log.d(TAG, "Playing startup animation")

        // Don't play animation if app is in background
        if (!isAppInForeground) {
            Log.d(TAG, "App is in background, skipping startup animation")
            onComplete()
            return
        }

        // The buttons in order for the startup animation
        val buttonsInOrder = listOf(
            SimonButton.GREEN,
            SimonButton.RED,
            SimonButton.YELLOW,
            SimonButton.BLUE
        )

        // Cancel any existing animations
        activeSequenceJob?.cancel()

        // Start and track new animation
        activeSequenceJob = viewModelScope.launch {
            delay(500)
            // Flash each button in order
            buttonsInOrder.forEach { button ->
                // Check if app is still in foreground before each step
                if (!isAppInForeground) {
                    Log.d(TAG, "App went to background during startup animation, cancelling")
                    // Use this instead of cancel()
                    activeSequenceJob?.cancel()
                    return@launch
                }

                Log.d(TAG, "Startup animation: lighting up $button")
                _uiState.update { it.copy(currentlyLit = button) }
                soundManager.playSound(button)
                delay(300)
                _uiState.update { it.copy(currentlyLit = null) }
                delay(150)
            }

            // Slight pause before starting the game
            delay(500)

            // Call the completion handler
            onComplete()

            // Clear the active job reference
            activeSequenceJob = null
        }
    }

    // Add a new button to the sequence
    private fun generateNextSequence() {
        val buttons = SimonButton.entries.toTypedArray()
        val newButton = buttons.random()
        Log.d(TAG, "Adding new button to sequence: $newButton")

        _uiState.update { currentState ->
            currentState.copy(
                sequence = currentState.sequence + newButton
            )
        }
        Log.d(TAG, "Sequence is now: ${_uiState.value.sequence}")
    }

    // Display the sequence to the player
    private fun showSequence() {
        Log.d(TAG, "Showing sequence of length: ${_uiState.value.sequence.size}")

        // Don't show sequence if app is in background
        if (!isAppInForeground) {
            Log.d(TAG, "App is in background, not showing sequence now")
            return
        }

        _uiState.update { it.copy(gameState = GameState.ShowingSequence) }

        // Cancel any existing sequence job
        activeSequenceJob?.cancel()

        // Start a new sequence job and save the reference
        activeSequenceJob = viewModelScope.launch {
            delay(500) // Brief pause before showing sequence

            // Light up each button in the sequence and play sound
            _uiState.value.sequence.forEachIndexed { index, button ->
                // Check if app is still in foreground
                if (!isAppInForeground) {
                    Log.d(TAG, "App went to background while showing sequence, cancelling")
                    // Use this instead of cancel()
                    activeSequenceJob?.cancel()
                    return@launch
                }

                Log.d(TAG, "Showing sequence item $index: $button")

                // Update UI state to light the button
                _uiState.update { it.copy(currentlyLit = button) }

                // Play button sound
                soundManager.playSound(button)

                // Keep lit for sound duration
                delay(600)

                // Turn off light
                _uiState.update { it.copy(currentlyLit = null) }

                // Pause between buttons
                delay(400)
            }

            // After showing sequence, let player repeat it
            Log.d(TAG, "Finished showing sequence, now player's turn")
            _uiState.update {
                it.copy(
                    gameState = GameState.PlayerRepeating,
                    playerSequence = emptyList()
                )
            }

            // Start the inactivity timeout timer
            startTimeoutTimer()

            // Clear the active job reference since it's completed
            activeSequenceJob = null
        }
    }

    // Handle player button presses
    fun onButtonClick(button: SimonButton, isPress: Boolean) {
        Log.d(TAG, "Button ${if (isPress) "press" else "release"}: $button")

        // Ignore button presses if app is in background
        if (!isAppInForeground) {
            Log.d(TAG, "App is in background, ignoring button press")
            return
        }

        if (isPress) {
            // Handle press event

            // Ignore new presses when not in player repeating state
            if (_uiState.value.gameState != GameState.PlayerRepeating) {
                Log.d(TAG, "Button press ignored - not in PlayerRepeating state: ${_uiState.value.gameState}")
                return
            }

            // Check if this is the first button pressed in the current interaction
            val isFirstButtonPressed = _uiState.value.activeButtonPresses.isEmpty()

            // Update active button presses map
            val activeButtons = _uiState.value.activeButtonPresses.toMutableMap()
            activeButtons[button] = true
            _uiState.update { it.copy(activeButtonPresses = activeButtons) }

            // Only process game logic and play sound for the first button press
            if (isFirstButtonPressed) {
                // Reset the timeout timer since the player has acted
                resetTimeoutTimer()

                // Add button to player's sequence
                val updatedPlayerSequence = _uiState.value.playerSequence + button
                _uiState.update { it.copy(
                    playerSequence = updatedPlayerSequence,
                    // Set the button as lit
                    currentlyLit = button
                )}

                // Play the button sound
                soundManager.playSound(button, isPlayerPressed = true)

                // Schedule turning off the light after a delay
                viewModelScope.launch {
                    delay(300) // Match sound duration
                    // Only clear if this button is still the current one lit
                    if (_uiState.value.currentlyLit == button) {
                        _uiState.update { it.copy(currentlyLit = null) }
                    }
                }

                // Now check the sequence
                checkSequenceMatch(updatedPlayerSequence)
            }
        } else {
            // Handle button release event
            val activeButtons = _uiState.value.activeButtonPresses.toMutableMap()
            activeButtons.remove(button)
            _uiState.update { it.copy(activeButtonPresses = activeButtons) }
        }
    }

    private fun checkSequenceMatch(playerSequence: List<SimonButton>) {
        val index = playerSequence.size - 1

        // Safety check: make sure index is valid for both sequences
        if (index < 0 || index >= _uiState.value.sequence.size) {
            Log.e(TAG, "Invalid index $index: player sequence=${playerSequence.size}, game sequence=${_uiState.value.sequence.size}")

            // Player has clicked beyond the expected sequence length
            // This is a game over condition - the player made an error by entering too many buttons
            viewModelScope.launch {
                delay(300)
                handleGameOver("Game over - player entered too many buttons")
            }
            return
        }

        if (playerSequence[index] != _uiState.value.sequence[index]) {
            // Wrong button
            Log.d(TAG, "Wrong button selected! Expected: ${_uiState.value.sequence[index]}, Got: ${playerSequence[index]}")

            // Brief delay before game over to allow button sound to play
            viewModelScope.launch {
                delay(300)
                // Wrong button - game over
                handleGameOver()
            }
            return
        }

        // Correct button
        Log.d(TAG, "Correct button! ${playerSequence.size}/${_uiState.value.sequence.size} steps completed")

        // Check if player completed the entire sequence
        if (playerSequence.size == _uiState.value.sequence.size) {
            Log.d(TAG, "Player completed the entire sequence! Advancing to next level")
            // Cancel timeout timer as level is complete
            cancelTimeoutTimer()

            // Move to next level
            advanceToNextLevel()
        }
    }

    // Handle button release events
    fun onButtonRelease(button: SimonButton) {
        // Remove button from active presses
        val activeButtons = _uiState.value.activeButtonPresses.toMutableMap()
        activeButtons.remove(button)

        _uiState.update { it.copy(activeButtonPresses = activeButtons) }
    }

    // Handle game over state - Simplified version that calls the main implementation
    private fun handleGameOver() {
        handleGameOver("Wrong button pressed")
    }

    // Flash all buttons to indicate game over
    private fun flashAllButtons() {
        Log.d(TAG, "Flashing all buttons for game over animation")

        // Don't flash if app is in background
        if (!isAppInForeground) {
            Log.d(TAG, "App is in background, skipping game over animation")
            return
        }

        // Cancel any existing sequence animation
        activeSequenceJob?.cancel()

        // Start and track the flashing animation
        activeSequenceJob = viewModelScope.launch {
            // Flash all buttons 3 times
            repeat(3) { flashCount ->
                // Check if app is still in foreground
                if (!isAppInForeground) {
                    Log.d(TAG, "App went to background during game over animation, cancelling")
                    // Use this instead of cancel()
                    activeSequenceJob?.cancel()
                    return@launch
                }

                Log.d(TAG, "Flash sequence $flashCount")

                // Turn all buttons on
                _uiState.update { it.copy(allButtonsLit = true) }
                delay(300)

                // Turn all buttons off
                _uiState.update { it.copy(allButtonsLit = false) }
                delay(300)
            }

            // Clear the job reference when done
            activeSequenceJob = null
        }
    }

    // Advance to the next level
    private fun advanceToNextLevel() {
        Log.d(TAG, "Advancing to level ${_uiState.value.level + 1}")
        _uiState.update { it.copy(level = it.level + 1) }

        // Generate new sequence that includes the previous one
        generateNextSequence()

        // Cancel any existing sequence job
        activeSequenceJob?.cancel()

        // Brief delay before showing new sequence
        activeSequenceJob = viewModelScope.launch {
            delay(1000)

            // Check if app is still in foreground
            if (isAppInForeground) {
                showSequence()
            } else {
                Log.d(TAG, "App went to background before showing new sequence")
            }

            // Clear job reference after showSequence (which sets its own reference)
            activeSequenceJob = null
        }
    }

    // Start a timer that will end the game if the player doesn't act within the timeout period
    private fun startTimeoutTimer() {
        Log.d(TAG, "Starting player inactivity timeout timer (${playerTimeoutDuration/1000} seconds)")

        // Don't start timer if app is in background
        if (!isAppInForeground) {
            Log.d(TAG, "App is in background, not starting timeout timer")
            return
        }

        // Cancel any existing timer first
        cancelTimeoutTimer()

        // Start a new timer
        timeoutJob = viewModelScope.launch {
            delay(playerTimeoutDuration)

            // Check if app is still in foreground
            if (!isAppInForeground) {
                Log.d(TAG, "App is in background when timeout occurred, ignoring")
                return@launch
            }

            // If this code executes, the timeout has occurred
            Log.d(TAG, "Player timeout! No button pressed for ${playerTimeoutDuration/1000} seconds")

            // Ensure we're still in PlayerRepeating state (could have changed during the delay)
            if (_uiState.value.gameState == GameState.PlayerRepeating) {
                // Play timout sound and end game
                soundManager.playErrorSound()
                viewModelScope.launch {
                    delay(300)
                    // Handle game over due to timeout
                    handleGameOver("Timeout - no button pressed for ${playerTimeoutDuration/1000} seconds")
                }
            }
        }
    }

    // Reset the timeout timer (when a player presses a button)
    // Public to allow resetting from Activity during configuration changes
    fun resetTimeoutTimer() {
        // Only reset if app is in foreground
        if (isAppInForeground) {
            Log.d(TAG, "Resetting player inactivity timeout timer")
            startTimeoutTimer() // Cancel and start a new timeout
        }
    }

    // Cancel the timeout timer
    private fun cancelTimeoutTimer() {
        timeoutJob?.cancel()
        timeoutJob = null
    }

    // Handle game over state with an optional reason
    private fun handleGameOver(reason: String = "Game over") {
        // Cancel any running timeout timer
        cancelTimeoutTimer()
        val currentLevel = _uiState.value.level
        val currentHighScore = _uiState.value.highScore
        Log.d(TAG, "$reason at level $currentLevel (high score: $currentHighScore)")

        // Don't do game over animation if app is in background
        if (!isAppInForeground) {
            Log.d(TAG, "App is in background, skipping game over animation")
            // Just update the game state immediately
            updateGameOverState(currentLevel, currentHighScore)
            return
        }

        // Check if this is a new high score
        val newHighScore = if (currentLevel > currentHighScore) currentLevel else currentHighScore

        // Play error sound when game is over
        soundManager.playErrorSound()

        // Flash all buttons sequence to indicate game over
        flashAllButtons()

        // Update game state after the flashing animation
        viewModelScope.launch {
            // Wait for flash animation to complete (in flashAllButtons)
            delay(2000)

            updateGameOverState(currentLevel, newHighScore)
        }
    }

    // Helper function to update the game state after game over
    private fun updateGameOverState(currentLevel: Int, newHighScore: Int) {
        _uiState.update {
            it.copy(
                gameState = GameState.GameOver,
                highScore = newHighScore
            )
        }

        // Save high score if it changed
        if (newHighScore > _uiState.value.highScore) {
            saveSettings()
        }
    }

    // Lifecycle methods

    // Called when app goes to foreground
    override fun onResume(owner: LifecycleOwner) {
        Log.d(TAG, "onResume - App coming to foreground")
        isAppInForeground = true

        // Resume sounds
        soundManager.resumeSounds()

        // Check if we need to resume the game
        if (wasGameActiveBeforeBackground) {
            Log.d(TAG, "Resuming game from previous state: $gameStateBeforeBackground")
            wasGameActiveBeforeBackground = false

            when (gameStateBeforeBackground) {
                is GameState.ShowingSequence -> {
                    // Restart showing sequence
                    showSequence()
                }
                is GameState.PlayerRepeating -> {
                    // Return to player's turn and restart timeout
                    _uiState.update { it.copy(gameState = GameState.PlayerRepeating) }
                    resetTimeoutTimer()
                }
                is GameState.GameOver, is GameState.WaitingToStart -> {
                    // No special handling needed
                }
                is GameState.Settings -> {
                    // Stay in settings
                }
            }
        }
    }

    // Called when app goes to background
    override fun onPause(owner: LifecycleOwner) {
        Log.d(TAG, "onPause - App going to background")
        isAppInForeground = false

        // Save current game state to restore later
        gameStateBeforeBackground = _uiState.value.gameState
        wasGameActiveBeforeBackground = _uiState.value.gameState is GameState.ShowingSequence ||
                _uiState.value.gameState is GameState.PlayerRepeating

        // Cancel any active animations
        activeSequenceJob?.cancel()
        activeSequenceJob = null

        // Cancel timeout timer
        cancelTimeoutTimer()

        // Pause all sounds
        soundManager.pauseSounds()

        // Turn off any lit buttons
        _uiState.update { it.copy(
            currentlyLit = null,
            allButtonsLit = false
        )}
    }

    // Clean up resources when ViewModel is cleared
    override fun onCleared() {
        Log.d(TAG, "ViewModel cleared, releasing sound resources")
        super.onCleared()
        saveSettings()
        cancelTimeoutTimer() // Make sure to cancel any timers

        // Cancel any active animations or sequences
        activeSequenceJob?.cancel()
        activeSequenceJob = null

        soundManager.release()
    }
}