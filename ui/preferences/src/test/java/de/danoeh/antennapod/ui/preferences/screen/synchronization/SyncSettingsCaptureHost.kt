package de.danoeh.antennapod.ui.preferences.screen.synchronization

import android.os.Bundle
import dagger.hilt.android.AndroidEntryPoint
import de.danoeh.antennapod.ui.common.ToolbarActivity
import de.danoeh.antennapod.ui.preferences.databinding.SettingsActivityBinding

@AndroidEntryPoint
class SyncSettingsCaptureHost : ToolbarActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setContentView(SettingsActivityBinding.inflate(layoutInflater).root)
    }
}
