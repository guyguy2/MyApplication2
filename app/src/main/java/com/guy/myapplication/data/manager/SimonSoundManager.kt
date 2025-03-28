package com.guy.myapplication.data.manager

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.guy.myapplication.R
import com.guy.myapplication.domain.enums.SimonButton
import com.guy.myapplication.domain.enums.SoundPack
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages sound effects for the Simon Says game with support for multiple sound packs
 * Enhanced with debug logging and lifecycle awareness
 */
class SimonSoundManager(private val context: Context) {

    private val TAG = "SimonSoundManager"

    private val soundPool: SoundPool

    // Flag to track if sounds are paused when app is in background
    private var isPaused = false

    // Vibration settings and control
    private var vibrateEnabled = true
    private val vibrator: Vibrator by lazy {
        Log.d(TAG, "Initializing vibrator")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    // Maps to store sound IDs for different sound packs
    private val soundPackMap = mutableMapOf<SoundPack, Map<SimonButton, Int>>()
    private val errorSoundMap = mutableMapOf<SoundPack, Int>()

    // Currently active sound pack
    private var currentSoundPack = SoundPack.STANDARD

    // Track load status
    private val loadStatusMap = ConcurrentHashMap<Int, Boolean>()

    // Track currently playing sound streams to be able to stop them
    private var activeStreamId: Int = 0

    init {
        Log.d(TAG, "Initializing SimonSoundManager")

        // List all raw resources to verify what's available
        listAllRawResources()

        // Configure audio attributes for game sounds
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        // Create a SoundPool optimized for short game sounds
        soundPool = SoundPool.Builder()
            .setMaxStreams(10)
            .setAudioAttributes(audioAttributes)
            .build()

        // Set up load listener to track sound loading status
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            val success = status == 0
            loadStatusMap[sampleId] = success
            Log.d(TAG, "Sound loaded: ID=$sampleId, Success=$success")
        }

        // Load all sound packs
        loadAllSounds()
    }

    /**
     * Get the context
     * Used by ViewModel to access application context
     */
    fun getContext(): Context {
        return context.applicationContext
    }

    /**
     * List all raw resources to help debug resource loading issues
     */
    private fun listAllRawResources() {
        try {
            Log.d(TAG, "=== LISTING ALL RAW RESOURCES ===")
            val fields = R.raw::class.java.fields
            if (fields.isEmpty()) {
                Log.e(TAG, "No raw resources found in R.raw!")
            } else {
                for (field in fields) {
                    val resourceName = field.name
                    val resourceId = field.getInt(null)
                    Log.d(TAG, "Raw resource: $resourceName (ID: $resourceId)")
                }
            }
            Log.d(TAG, "=== END OF RAW RESOURCES LIST ===")
        } catch (e: Exception) {
            Log.e(TAG, "Error listing raw resources", e)
        }
    }

    /**
     * Load sounds for all available sound packs
     */
    private fun loadAllSounds() {
        try {
            // Log available sound packs
            Log.d(TAG, "Available sound packs: ${SoundPack.entries.joinToString { it.name }}")

            // Load each sound pack
            for (soundPack in SoundPack.entries) {
                loadSoundPack(soundPack)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading sounds", e)
        }
    }

    /**
     * Load sounds for a specific sound pack
     */
    private fun loadSoundPack(soundPack: SoundPack) {
        val prefix = soundPack.resourcePrefix
        Log.d(TAG, "Loading sound pack: ${soundPack.name} with prefix: $prefix")

        try {
            // Map to store button sounds for this pack
            val buttonSoundMap = mutableMapOf<SimonButton, Int>()

            // Load sound for each button using the proper prefix
            SimonButton.entries.forEach { button ->
                val resourceName = "${prefix}_${button.name.lowercase()}_tone"
                Log.d(TAG, "Looking for resource: $resourceName")

                val resourceId = getResourceId(resourceName)

                if (resourceId != 0) {
                    val soundId = soundPool.load(context, resourceId, 1)
                    buttonSoundMap[button] = soundId
                    Log.d(TAG, "✓ Loaded $resourceName sound: ID=$soundId")
                } else {
                    Log.e(TAG, "✗ Resource not found: $resourceName")

                    // Try to debug why resource wasn't found
                    debugResourceNotFound(resourceName)
                }
            }

            // Load error sound for this pack
            val errorResourceName = "${prefix}_error_tone"
            Log.d(TAG, "Looking for error resource: $errorResourceName")

            val errorResourceId = getResourceId(errorResourceName)

            if (errorResourceId != 0) {
                val errorSoundId = soundPool.load(context, errorResourceId, 1)
                errorSoundMap[soundPack] = errorSoundId
                Log.d(TAG, "✓ Loaded $errorResourceName sound: ID=$errorSoundId")
            } else {
                Log.e(TAG, "✗ Error resource not found: $errorResourceName")

                // Try to debug why resource wasn't found
                debugResourceNotFound(errorResourceName)
            }

            // Add the button sound map to the pack map
            soundPackMap[soundPack] = buttonSoundMap

            // Log summary of loaded sounds for this pack
            logSoundPackSummary(soundPack, buttonSoundMap)

        } catch (e: Exception) {
            Log.e(TAG, "Error loading $prefix sounds", e)
            e.printStackTrace()
        }
    }

    /**
     * Log summary of loaded sounds for a sound pack
     */
    private fun logSoundPackSummary(soundPack: SoundPack, buttonSoundMap: Map<SimonButton, Int>) {
        Log.d(TAG, "=== ${soundPack.name} SOUND PACK SUMMARY ===")
        Log.d(TAG, "Buttons loaded: ${buttonSoundMap.size}/${SimonButton.entries.size}")
        buttonSoundMap.forEach { (button, soundId) ->
            Log.d(TAG, "  - ${button.name}: SoundID=$soundId")
        }
        val errorSoundId = errorSoundMap[soundPack]
        if (errorSoundId != null) {
            Log.d(TAG, "  - ERROR: SoundID=$errorSoundId")
        } else {
            Log.d(TAG, "  - ERROR: Not loaded")
        }
        Log.d(TAG, "===================================")
    }

    /**
     * Try to debug why a resource wasn't found
     */
    private fun debugResourceNotFound(resourceName: String) {
        Log.e(TAG, "Debug - Attempting to find similar resources:")

        // Check for resources with similar names
        val fields = R.raw::class.java.fields
        var foundSimilar = false

        for (field in fields) {
            val name = field.name
            if (name.contains(resourceName) || resourceName.contains(name)) {
                foundSimilar = true
                Log.e(TAG, "  - Similar resource found: $name")
            }
        }

        if (!foundSimilar) {
            Log.e(TAG, "  - No similar resources found. Check file names in res/raw folder.")
        }

        // Extract prefix information from the resource name
        val parts = resourceName.split("_")
        if (parts.isNotEmpty()) {
            val extractedPrefix = parts[0]
            Log.e(TAG, "  - Resource prefix from name: $extractedPrefix")
            Log.e(TAG, "  - Check if this matches any SoundPack.resourcePrefix value")

            // Log all available resource prefixes for comparison
            SoundPack.entries.forEach { pack ->
                Log.e(TAG, "  - Available prefix: '${pack.resourcePrefix}' from ${pack.name}")
            }
        }

        Log.e(TAG, "  - Ensure your file is named exactly: $resourceName.wav in res/raw folder")
    }

    /**
     * Get the resource ID using direct references instead of dynamic lookup
     */
    private fun getResourceId(resourceName: String): Int {
        // Use direct resource references to avoid reflection
        return when (resourceName) {
            // Standard sound pack
            "standard_green_tone" -> R.raw.standard_green_tone
            "standard_red_tone" -> R.raw.standard_red_tone
            "standard_yellow_tone" -> R.raw.standard_yellow_tone
            "standard_blue_tone" -> R.raw.standard_blue_tone
            "standard_error_tone" -> R.raw.standard_error_tone

            // Funny sound pack - fallback to standard for now
            "funny_green_tone" -> R.raw.standard_green_tone
            "funny_red_tone" -> R.raw.standard_red_tone
            "funny_yellow_tone" -> R.raw.standard_yellow_tone
            "funny_blue_tone" -> R.raw.standard_blue_tone
            "funny_error_tone" -> R.raw.standard_error_tone

            // For all other resources, return 0 (not found)
            else -> {
                Log.w(TAG, "Resource not found in direct mapping: $resourceName")
                0
            }
        }
    }

    /**
     * Pause all sounds
     * Called when app goes to background
     */
    fun pauseSounds() {
        Log.d(TAG, "Pausing all sounds")
        isPaused = true

        // Stop any currently playing sounds
        stopAllSounds()

        // Auto-pause SoundPool
        soundPool.autoPause()
    }

    /**
     * Resume sounds
     * Called when app returns to foreground
     */
    fun resumeSounds() {
        Log.d(TAG, "Resuming sounds")
        isPaused = false

        // Auto-resume SoundPool
        soundPool.autoResume()
    }

    /**
     * Change the active sound pack
     */
    fun setSoundPack(soundPack: SoundPack) {
        Log.d(TAG, "Changing sound pack to: ${soundPack.name}")
        currentSoundPack = soundPack
    }

    /**
     * Set whether vibration is enabled
     */
    fun setVibrationEnabled(enabled: Boolean) {
        Log.d(TAG, "Setting vibration enabled: $enabled")
        vibrateEnabled = enabled

        // Test vibration immediately if enabling (only if not paused)
        if (enabled && !isPaused) {
            try {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Log.d(TAG, "Testing vibration after enabling")
                    vibrate()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to test vibration", e)
            }
        }
    }

    /**
     * Get current vibration setting
     */
    fun isVibrationEnabled(): Boolean {
        return vibrateEnabled
    }

    /**
     * Check if the vibrator can actually vibrate
     */
    private fun hasVibrator(): Boolean {
        return vibrator.hasVibrator()
    }

    /**
     * Trigger a short vibration for button press
     */
    private fun vibrate() {
        if (!vibrateEnabled || isPaused) {
            Log.d(TAG, "Vibration disabled or app paused, skipping")
            return
        }

        if (!hasVibrator()) {
            Log.d(TAG, "Device does not have vibration capability")
            return
        }

        try {
            Log.d(TAG, "Triggering vibration")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(effect)
                Log.d(TAG, "Vibrated using modern API")
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(100)
                Log.d(TAG, "Vibrated using legacy API")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to vibrate", e)
            e.printStackTrace()
        }
    }

    /**
     * Play the sound associated with a specific Simon button using the current sound pack
     *
     * @param button The button to play sound for
     * @param isPlayerPressed Whether this sound is from a player pressing a button (for vibration)
     */
    fun playSound(button: SimonButton, isPlayerPressed: Boolean = false) {
        Log.d(TAG, "Request to play sound for button: $button with sound pack: ${currentSoundPack.name}, player pressed: $isPlayerPressed")

        // Don't play if paused
        if (isPaused) {
            Log.d(TAG, "Sounds are paused, not playing")
            return
        }

        // Stop any currently playing sound
        stopAllSounds()

        val soundMap = soundPackMap[currentSoundPack]
        if (soundMap == null) {
            Log.e(TAG, "❌ Sound map not found for sound pack: ${currentSoundPack.name}")
            return
        }

        val soundId = soundMap[button]
        if (soundId == null) {
            Log.e(TAG, "❌ Sound ID not found for button: $button in sound pack: ${currentSoundPack.name}")
            return
        }

        val loadStatus = loadStatusMap[soundId] ?: false
        Log.d(TAG, "Playing ${currentSoundPack.name} sound for button $button (ID=$soundId, Loaded=$loadStatus)")

        if (!loadStatus) {
            Log.w(TAG, "⚠️ Attempting to play sound that hasn't finished loading: $soundId")
        }

        // Check audio state before playing
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        Log.d(TAG, "Current audio state: Volume=${currentVolume}/${maxVolume}")

        // Play sound with default priority, no loop, normal rate
        val playId = soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
        if (playId == 0) {
            Log.e(TAG, "❌ Failed to play sound for button $button (playId=0)")
        } else {
            Log.d(TAG, "✓ Successfully playing sound for button $button (playId=$playId)")
            activeStreamId = playId

            // Only vibrate if this is a player-initiated press, not computer sequence
            if (isPlayerPressed) {
                Log.d(TAG, "Player pressed button, triggering vibration")
                // Force this to run on the main thread - it might be getting lost otherwise
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    vibrate()
                }
            }
        }
    }

    /**
     * Stop all currently playing sounds
     */
    fun stopAllSounds() {
        if (activeStreamId != 0) {
            Log.d(TAG, "Stopping active sound stream: $activeStreamId")
            soundPool.stop(activeStreamId)
            activeStreamId = 0
        }
    }

    /**
     * Play the error sound for when player makes a mistake using the current sound pack
     */
    fun playErrorSound() {
        Log.d(TAG, "Request to play error sound with sound pack: ${currentSoundPack.name}")

        // Don't play if paused
        if (isPaused) {
            Log.d(TAG, "Sounds are paused, not playing error sound")
            return
        }

        // Stop any currently playing sound
        stopAllSounds()

        val errorSoundId = errorSoundMap[currentSoundPack]
        if (errorSoundId == null) {
            Log.e(TAG, "❌ Error sound ID not found for sound pack: ${currentSoundPack.name}")
            return
        }

        val loadStatus = loadStatusMap[errorSoundId] ?: false
        Log.d(TAG, "Playing ${currentSoundPack.name} error sound (ID=$errorSoundId, Loaded=$loadStatus)")

        if (!loadStatus) {
            Log.w(TAG, "⚠️ Attempting to play error sound that hasn't finished loading")
        }

        // Check audio state before playing
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val currentVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        Log.d(TAG, "Current audio state: Volume=$currentVolume")

        // Play error sound with slightly higher priority, no loop, normal rate
        val playId = soundPool.play(errorSoundId, 1.2f, 1.2f, 2, 0, 1.0f)
        if (playId == 0) {
            Log.e(TAG, "Failed to play error sound (playId=0)")
        } else {
            Log.d(TAG, "✓ Successfully playing error sound (playId=$playId)")
            activeStreamId = playId

            // Stronger vibration for error (double pulse)
            if (vibrateEnabled && !isPaused) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        // Create a pattern for error: vibrate-pause-vibrate
                        val timings = longArrayOf(0, 100, 100, 100)
                        val amplitudes = intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE)
                        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(longArrayOf(0, 100, 100, 100), -1)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to vibrate for error", e)
                }
            }
        }
    }

    /**
     * Release resources when no longer needed
     */
    fun release() {
        Log.d(TAG, "Releasing SoundPool resources")
        stopAllSounds()
        soundPool.release()
    }
}