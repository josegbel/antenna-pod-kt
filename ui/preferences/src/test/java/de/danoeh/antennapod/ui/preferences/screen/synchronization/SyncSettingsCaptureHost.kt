package de.danoeh.antennapod.ui.preferences.screen.synchronization

import android.os.Bundle
import de.danoeh.antennapod.ui.common.ToolbarActivity
import de.danoeh.antennapod.ui.preferences.databinding.SettingsActivityBinding

class SyncSettingsCaptureHost : ToolbarActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setContentView(SettingsActivityBinding.inflate(layoutInflater).root)
    }
}
