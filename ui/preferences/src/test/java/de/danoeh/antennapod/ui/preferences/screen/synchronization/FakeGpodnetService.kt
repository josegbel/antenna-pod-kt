package de.danoeh.antennapod.ui.preferences.screen.synchronization

import de.danoeh.antennapod.net.sync.gpoddernet.GpodnetService
import de.danoeh.antennapod.net.sync.gpoddernet.model.GpodnetDevice
import okhttp3.OkHttpClient

class FakeGpodnetService(
    private val devicesToReturn: List<GpodnetDevice> = emptyList()
) : GpodnetService(OkHttpClient(), null, null, null, null) {

    val calls = mutableListOf<String>()
    val configuredDeviceIds = mutableListOf<String>()
    val configuredCaptions = mutableListOf<String?>()

    var loginError: Throwable? = null
    var getDevicesError: Throwable? = null
    var configureDeviceError: Throwable? = null

    var beforeGetDevices: (() -> Unit)? = null

    override fun setCredentials(username: String?, password: String?) {
        calls.add("setCredentials")
    }

    override fun login() {
        calls.add("login")
        loginError?.let { throw it }
    }

    override fun getDevices(): List<GpodnetDevice> {
        calls.add("getDevices")
        beforeGetDevices?.invoke()
        getDevicesError?.let { throw it }
        return devicesToReturn.toList()
    }

    override fun configureDevice(deviceId: String, caption: String?, type: GpodnetDevice.DeviceType?) {
        calls.add("configureDevice")
        configuredDeviceIds.add(deviceId)
        configuredCaptions.add(caption)
        configureDeviceError?.let { throw it }
    }
}
