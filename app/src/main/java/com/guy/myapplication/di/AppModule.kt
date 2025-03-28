package com.guy.myapplication.di

import com.guy.myapplication.data.manager.SimonSoundManager
import com.guy.myapplication.ui.viewmodels.SimonGameViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Main application module for Dependency Injection
 */
val appModule = module {
    // Single instance of SimonSoundManager
    single { SimonSoundManager(androidContext()) }

    // ViewModel using the factory scope (new instance each time)
    viewModel { SimonGameViewModel(get()) }
}