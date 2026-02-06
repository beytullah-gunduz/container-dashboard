package com.containerdashboard.ui.screens.viewmodel

import androidx.lifecycle.ViewModel
import com.containerdashboard.data.repository.PreferenceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

class SettingsScreenViewModel : ViewModel() {

    var engineHost: MutableStateFlow<String> = MutableStateFlow(runBlocking {   PreferenceRepository.engineHost })

}
