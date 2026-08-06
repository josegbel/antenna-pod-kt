package de.danoeh.antennapod.ui.preferences.screen.synchronization

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import de.danoeh.antennapod.ui.common.R as CommonR

class SyncSettingsTestHost : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(CommonR.style.Theme_AntennaPod_Light)
        super.onCreate(savedInstanceState)
    }
}
