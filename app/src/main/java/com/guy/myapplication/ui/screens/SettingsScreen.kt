package com.guy.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guy.myapplication.data.manager.SimonSoundManager
import com.guy.myapplication.domain.enums.SimonButton
import com.guy.myapplication.domain.enums.SoundPack
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Settings screen for the Simon Says game
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

    // Dialog visibility state
    var showAboutDialog by remember { mutableStateOf(false) }

    // LazyListState for the sound pack list
    val soundPackListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Check if the list can scroll down
    val canScrollDown by remember {
        derivedStateOf {
            soundPackListState.canScrollForward
        }
    }

    // Initialize with the current sound pack
    LaunchedEffect(currentSoundPack) {
        soundManager.setSoundPack(currentSoundPack)
    }

    Scaffold(
        topBar = {
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
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // SOUND PACKS SECTION
            Text(
                text = "Sound Packs",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            // Sound pack selection with scroll indicators
            Box(
                modifier = Modifier
                    .height(280.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF121212))
            ) {
                // Main list of sound packs
                LazyColumn(
                    state = soundPackListState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(SoundPack.entries.toList()) { soundPack ->
                        SoundPackOption(
                            soundPack = soundPack,
                            isSelected = soundPack == currentSoundPack,
                            onSelect = {
                                soundManager.setSoundPack(soundPack)
                                soundManager.playSound(SimonButton.GREEN)
                                onSoundPackSelected(soundPack)
                            }
                        )
                    }

                    // Add empty item to ensure last item isn't hidden behind the scroll indicator
                    item { Spacer(modifier = Modifier.height(40.dp)) }
                }

                // Scroll indicator at the bottom
                if (canScrollDown) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color(0xE6121212)
                                    )
                                )
                            )
                            .clickable {
                                coroutineScope.launch {
                                    // Scroll down when clicked
                                    soundPackListState.animateScrollBy(100f)
                                }
                            }
                            .padding(vertical = 8.dp)
                            .align(Alignment.BottomCenter),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "More options",
                                tint = Color.Gray
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "More sound options",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Fade overlay at the top when scrolled
                val showTopFade by remember {
                    derivedStateOf {
                        soundPackListState.firstVisibleItemIndex > 0 ||
                                soundPackListState.firstVisibleItemScrollOffset > 0
                    }
                }

                if (showTopFade) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xE6121212),
                                        Color.Transparent
                                    )
                                )
                            )
                            .align(Alignment.TopCenter)
                    )
                }
            }

            // HAPTIC FEEDBACK SECTION
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                thickness = 1.dp,
                color = Color(0xFF303030)
            )

            // Vibration toggle card
            SettingsCard(
                onClick = { onVibrateToggled(!vibrateEnabled) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
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

            // RATE APP SECTION
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                thickness = 1.dp,
                color = Color(0xFF303030)
            )

            // Rate app card
            SettingsCard(
                onClick = { /* TODO: Implement app rating action */ }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Rate App",
                        tint = Color(0xFFFFC107), // Amber/gold color
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Rate this App",
                            color = Color.White,
                            fontSize = 16.sp
                        )

                        Text(
                            text = "Enjoying Simon Says? Let us know!",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // ABOUT SECTION
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                thickness = 1.dp,
                color = Color(0xFF303030)
            )

            // About card
            SettingsCard(
                onClick = { showAboutDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "About",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "App Info",
                            color = Color.White,
                            fontSize = 16.sp
                        )

                        Text(
                            text = "Version 1.0.0 • © 2025",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Bottom spacing to ensure everything is visible
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // About dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Text(
                    text = "About Simon Says",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
            },
            text = {
                Column {
                    Text(
                        text = "Simon is a classic electronic memory game that challenges players to repeat sequences of lights and sounds.",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Each round adds a new step to the sequence, testing your memory limits as you progress through increasingly difficult patterns.",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Version: 1.0.0",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        text = "© 2025 Simon Says Game",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(
                        text = "Close",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            containerColor = Color(0xFF1A1A1A),
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }
}

@Composable
fun SettingsCard(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1D1D1D)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        content()
    }
}

@Composable
fun SoundPackOption(
    soundPack: SoundPack,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        Color(0xFF303030)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
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
}