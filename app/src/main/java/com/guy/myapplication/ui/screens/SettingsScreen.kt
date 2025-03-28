package com.guy.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guy.myapplication.data.manager.SimonSoundManager
import com.guy.myapplication.domain.enums.SimonButton
import com.guy.myapplication.domain.enums.SoundPack
import org.koin.compose.koinInject

/**
 * Settings screen for the Simon Says game
 * Allows users to configure game preferences like sound packs
 *
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentSoundPack: SoundPack,
    vibrateEnabled: Boolean = true,
    onSoundPackSelected: (SoundPack) -> Unit,
    onVibrateToggled: (Boolean) -> Unit = {},
    onBackPressed: () -> Unit
) {
    // Inject SimonSoundManager from Koin
    val soundManager = koinInject<SimonSoundManager>()

    // Initialize with the current sound pack
    LaunchedEffect(currentSoundPack) {
        soundManager.setSoundPack(currentSoundPack)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top app bar with back button
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )

            // Settings content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Section header
                Text(
                    text = "Sound Packs",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Sound pack selection with Material3 RadioButtons in a LazyColumn for scrolling
                LazyColumn(
                    modifier = Modifier
                        .weight(1f) // Give it weight so it can scroll if needed
                        .fillMaxWidth()
                ) {
                    items(SoundPack.entries.toList()) { soundPack ->
                        SoundPackOption(
                            soundPack = soundPack,
                            isSelected = soundPack == currentSoundPack,
                            onSelect = {
                                // Set the sound pack first so it plays with the new sound pack
                                soundManager.setSoundPack(soundPack)

                                // Play green button sound as feedback
                                soundManager.playSound(SimonButton.GREEN)

                                // Call the original handler to update the ViewModel
                                onSoundPackSelected(soundPack)
                            }
                        )
                    }
                }

                // Vibration option
                Text(
                    text = "Haptic Feedback",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)
                )

                // Vibration toggle switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1D1D1D))
                        .clickable { onVibrateToggled(!vibrateEnabled) }
                        .padding(vertical = 16.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Vibration",
                            color = Color.White,
                            fontSize = 16.sp
                        )

                        Text(
                            text = "Short vibration (100ms) when buttons are pressed",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }

                    Switch(
                        checked = vibrateEnabled,
                        onCheckedChange = {
                            // Also play a test vibration if turning on
                            if (it) soundManager.setVibrationEnabled(true)
                            onVibrateToggled(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun SoundPackOption(
    soundPack: SoundPack,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    // Enhance visual appearance with slight background for selected item
    val backgroundColor = if (isSelected) {
        Color(0xFF1D1D1D)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable { onSelect() }
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = Color.Gray
            )
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = soundPack.displayName,
                color = Color.White,
                fontSize = 16.sp
            )

            Text(
                text = soundPack.description,
                color = Color.Gray,
                fontSize = 14.sp
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
}