package de.danoeh.antennapod.ui.preferences.screen.synchronization

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.danoeh.antennapod.event.SyncServiceEvent
import de.danoeh.antennapod.ui.preferences.R
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

@HiltViewModel
class SynchronizationPreferencesViewModel @Inject constructor(
    private val repository: SyncSettingsRepository
) : ViewModel() {

    constructor() : this(DefaultSyncSettingsRepository())

    internal class SyncServiceEventSubscriber(private val onEvent: (SyncServiceEvent) -> Unit) {
        @Subscribe(threadMode = ThreadMode.POSTING, sticky = true)
        fun onSyncServiceEvent(event: SyncServiceEvent) = onEvent(event)
    }

    val syncStatus: StateFlow<SyncServiceEvent?> = callbackFlow {
        val subscriber = SyncServiceEventSubscriber { trySend(it) }
        EventBus.getDefault().register(subscriber)
        awaitClose { EventBus.getDefault().unregister(subscriber) }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(stopTimeoutMillis = 0, replayExpirationMillis = 0),
        null
    )

    private val _uiState = MutableStateFlow(SyncSettingsUiState())
    val uiState: StateFlow<SyncSettingsUiState> = _uiState

    fun onStarted() {
        _uiState.value = _uiState.value.copy(
            subtitle = if (repository.isProviderConnected()) {
                SyncSubtitle.LastSyncReport(
                    repository.isLastSyncSuccessful(),
                    repository.lastSyncAttempt()
                )
            } else {
                SyncSubtitle.Absent
            }
        )
    }

    fun onSyncEvent(event: SyncServiceEvent) {
        _uiState.value = _uiState.value.copy(
            subtitle = if (event.messageResId == R.string.sync_status_error ||
                event.messageResId == R.string.sync_status_success
            ) {
                SyncSubtitle.LastSyncReport(
                    repository.isLastSyncSuccessful(),
                    repository.lastSyncAttempt()
                )
            } else {
                SyncSubtitle.Message(event.messageResId)
            }
        )
    }
}

data class SyncSettingsUiState(
    @StringRes val titleRes: Int = R.string.synchronization_pref,
    val subtitle: SyncSubtitle = SyncSubtitle.Absent
)

sealed interface SyncSubtitle {
    data object Absent : SyncSubtitle
    data class Message(@StringRes val resId: Int) : SyncSubtitle
    data class LastSyncReport(val successful: Boolean, val attemptedAt: Long) : SyncSubtitle
}
