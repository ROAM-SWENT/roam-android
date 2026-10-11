package com.github.roamswent.roam.ui.settings

import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import com.github.roamswent.roam.model.authentication.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow


data class SettingsUiState(
    val cameraPermissionGranted: Boolean = false,
    val locationPermissionGranted: Boolean = false,
    val errorMsg: String? = null,
    val signedOut: Boolean = false
)
class SettingsViewModel ()
    : ViewModel(){
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun signOut(credentialManager: CredentialManager){}
    fun clearErrorMsg(){}
}