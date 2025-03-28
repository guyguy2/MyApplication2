package com.guy.myapplication.di

import com.guy.myapplication.data.manager.SimonSoundManager
import com.guy.myapplication.ui.viewmodels.SimonGameViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Main application module for Dependency Injection
 */
val appModule = module {
    // Single instance of SimonSoundManager using constructor reference
    single { SimonSoundManager(androidContext()) }

    // ViewModel using the core module dsl syntax for Koin 4.x
    viewModelOf(::SimonGameViewModel)
}