package com.guy.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ripple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guy.myapplication.R
import com.guy.myapplication.domain.enums.SimonButton
import com.guy.myapplication.domain.model.GameState
import com.guy.myapplication.domain.model.SimonGameUiState
import com.guy.myapplication.ui.components.SimonPanel
import com.guy.myapplication.ui.viewmodels.SimonGameViewModel

// Extension function to capitalize first letter
private fun String.capitalize(): String {
    return if (this.isNotEmpty()) {
        this.substring(0, 1).uppercase() + this.substring(1)
    } else {
        this
    }
}

@Composable
fun SimonSaysGame(viewModel: SimonGameViewModel) {
    // Collect UI state from ViewModel
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Display appropriate screen based on game state
    when (uiState.gameState) {
        is GameState.Settings -> {
            // Show Settings Screen
            SettingsScreen(
                currentSoundPack = uiState.currentSoundPack,
                vibrateEnabled = uiState.vibrateEnabled,
                highScore = uiState.highScore,
                onSoundPackSelected = { viewModel.setSoundPack(it) },
                onVibrateToggled = { viewModel.setVibrationEnabled(it) },
                onResetHighScore = { viewModel.resetHighScore() },
                onBackPressed = { viewModel.exitSettings() }
            )
        }
        else -> {
            // Show Main Game Screen with updated callback signature
            SimonGameScreen(
                uiState = uiState,
                onButtonClick = { button, isPress -> viewModel.onButtonClick(button, isPress) },
                onSettingsClick = { viewModel.showSettings() },
                onStartNewGame = { viewModel.startNewGame() },
                onToggleSound = { viewModel.toggleSound() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimonGameScreen(
    uiState: SimonGameUiState,
    onButtonClick: (SimonButton, Boolean) -> Unit,
    onSettingsClick: () -> Unit,
    onStartNewGame: () -> Unit,
    onToggleSound: () -> Unit
) {
    // Single source of truth for which buttons are physically pressed (for UI feedback only)
    var localPressedButtons by remember { mutableStateOf(mapOf<SimonButton, Boolean>()) }

    // Function to handle both press and release events
    val handleButtonInteraction = { button: SimonButton, isPress: Boolean ->
        if (isPress) {
            // Update local state for immediate visual feedback
            localPressedButtons = localPressedButtons + (button to true)
        } else {
            // Release this button
            localPressedButtons = localPressedButtons - button
        }

        // Pass all events to ViewModel with isPress parameter
        onButtonClick(button, isPress)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Simon Says") },
                actions = {
                    // Mute/Unmute button
                    IconButton(onClick = onToggleSound) {
                        Icon(
                            painter = painterResource(
                                id = if (uiState.soundEnabled) 
                                    R.drawable.volume_up_24px 
                                else 
                                    R.drawable.volume_off_24px
                            ),
                            contentDescription = if (uiState.soundEnabled) "Mute" else "Unmute",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    
                    // Settings button
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        // Game content with minimal padding to maximize button size
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = paddingValues.calculateTopPadding(),
                    bottom = 0.dp,
                    start = 0.dp,
                    end = 0.dp
                ),
            contentAlignment = Alignment.Center
        ) {
            // Simon Says Game UI - Using more vertical space
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.95f)
                    .fillMaxWidth(0.9f)
                    .padding(top = 8.dp), // Reduced top padding
                contentAlignment = Alignment.Center
            ) {
                // Background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                )

                // Simon Says colored panels
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(modifier = Modifier.weight(1f)) {
                        // Green panel (top-left)
                        SimonPanel(
                            color = SimonButton.GREEN.color,
                            isLit = uiState.currentlyLit == SimonButton.GREEN || uiState.allButtonsLit,
                            userPressed = localPressedButtons[SimonButton.GREEN] == true,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(2.dp),
                            onTouchStateChanged = { isPressed ->
                                handleButtonInteraction(SimonButton.GREEN, isPressed)
                            }
                        )

                        // Red panel (top-right)
                        SimonPanel(
                            color = SimonButton.RED.color,
                            isLit = uiState.currentlyLit == SimonButton.RED || uiState.allButtonsLit,
                            userPressed = localPressedButtons[SimonButton.RED] == true,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(4.dp),
                            onTouchStateChanged = { isPressed ->
                                handleButtonInteraction(SimonButton.RED, isPressed)
                            }
                        )
                    }

                    Row(modifier = Modifier.weight(1f)) {
                        // Yellow panel (bottom-left)
                        SimonPanel(
                            color = SimonButton.YELLOW.color,
                            isLit = uiState.currentlyLit == SimonButton.YELLOW || uiState.allButtonsLit,
                            userPressed = localPressedButtons[SimonButton.YELLOW] == true,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(4.dp),
                            onTouchStateChanged = { isPressed ->
                                handleButtonInteraction(SimonButton.YELLOW, isPressed)
                            }
                        )

                        // Blue panel (bottom-right)
                        SimonPanel(
                            color = SimonButton.BLUE.color,
                            isLit = uiState.currentlyLit == SimonButton.BLUE || uiState.allButtonsLit,
                            userPressed = localPressedButtons[SimonButton.BLUE] == true,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(4.dp),
                            onTouchStateChanged = { isPressed ->
                                handleButtonInteraction(SimonButton.BLUE, isPressed)
                            }
                        )
                    }
                }

                // Center counter/button with FAB-style when in GameOver state
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .then(
                            if (uiState.gameState == GameState.GameOver) {
                                // When in GameOver state, add elevation and shadow
                                Modifier
                                    .shadow(
                                        elevation = 6.dp,
                                        shape = CircleShape,
                                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    )
                                    .clip(CircleShape)
                                    .background(Color(0xFF1D1D1D))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(),
                                        onClick = { onStartNewGame() }
                                    )
                            } else {
                                // Normal state
                                Modifier
                                    .background(Color.Black, RoundedCornerShape(60.dp))
                                    .zIndex(3f)
                                    .clickable(
                                        enabled = (uiState.gameState == GameState.GameOver),
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        if (uiState.gameState == GameState.GameOver) {
                                            onStartNewGame()
                                        }
                                    }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Colored arcs
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 8.dp.toPx()

                        // Draw green arc (top-left)
                        drawArc(
                            color = SimonButton.GREEN.color,
                            startAngle = 180f,
                            sweepAngle = 90f,
                            useCenter = false,
                            topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                            size = size.copy(
                                width = size.width - strokeWidth,
                                height = size.height - strokeWidth
                            ),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = strokeWidth,
                                cap = StrokeCap.Butt
                            )
                        )

                        // Draw red arc (top-right)
                        drawArc(
                            color = SimonButton.RED.color,
                            startAngle = 270f,
                            sweepAngle = 90f,
                            useCenter = false,
                            topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                            size = size.copy(
                                width = size.width - strokeWidth,
                                height = size.height - strokeWidth
                            ),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = strokeWidth,
                                cap = StrokeCap.Butt
                            )
                        )

                        // Draw blue arc (bottom-right)
                        drawArc(
                            color = SimonButton.BLUE.color,
                            startAngle = 0f,
                            sweepAngle = 90f,
                            useCenter = false,
                            topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                            size = size.copy(
                                width = size.width - strokeWidth,
                                height = size.height - strokeWidth
                            ),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = strokeWidth,
                                cap = StrokeCap.Butt
                            )
                        )

                        // Draw yellow arc (bottom-left)
                        drawArc(
                            color = SimonButton.YELLOW.color,
                            startAngle = 90f,
                            sweepAngle = 90f,
                            useCenter = false,
                            topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                            size = size.copy(
                                width = size.width - strokeWidth,
                                height = size.height - strokeWidth
                            ),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = strokeWidth,
                                cap = StrokeCap.Butt
                            )
                        )
                    }

                    // Create a centered inner content Box
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Sound pack indicator - only show when NOT in GameOver state
                        if (uiState.gameState != GameState.GameOver) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 10.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.music_note_24px),
                                    contentDescription = "Sound Pack",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = uiState.currentSoundPack.name.lowercase().capitalize(),
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        // Center content (level number or play button)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (uiState.gameState == GameState.GameOver) {
                                // Play icon when game is over
                                Icon(
                                    painter = painterResource(R.drawable.play_arrow_24px),
                                    contentDescription = "Play Again",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )

                                // "Play Again" text below the icon
                                Text(
                                    text = "Play Again",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                // Number display - shows level when not in game over
                                Text(
                                    text = uiState.level.toString(),
                                    color = Color.White,
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // High score display at bottom - only show when NOT in GameOver state
                        if (uiState.highScore > 0 && uiState.gameState != GameState.GameOver) {
                            Text(
                                text = "High: ${uiState.highScore}",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}