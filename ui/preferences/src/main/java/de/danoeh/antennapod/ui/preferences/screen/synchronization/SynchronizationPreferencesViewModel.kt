package de.danoeh.antennapod.ui.preferences.screen.synchronization

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.danoeh.antennapod.event.SyncServiceEvent
import de.danoeh.antennapod.ui.preferences.R
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class SynchronizationPreferencesViewModel : ViewModel() {

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
}

data class SyncSettingsUiState(
    @StringRes val titleRes: Int = R.string.synchronization_pref,
    val subtitle: SyncSubtitle = SyncSubtitle.Absent
)

sealed interface SyncSubtitle {
    data object Absent : SyncSubtitle
    data object Cleared : SyncSubtitle
    data class Message(@StringRes val resId: Int) : SyncSubtitle
    data class LastSyncReport(val successful: Boolean, val attemptedAt: Long) : SyncSubtitle
}
