package com.phantombeats.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.phantombeats.core.network.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class ConnectivityViewModel @Inject constructor(
    networkMonitor: NetworkMonitor
) : ViewModel() {
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
}
