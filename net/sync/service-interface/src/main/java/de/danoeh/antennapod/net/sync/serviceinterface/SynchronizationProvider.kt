package de.danoeh.antennapod.net.sync.serviceinterface

enum class SynchronizationProvider(val identifier: String) {
    GPODDER_NET("GPODDER_NET"),
    NEXTCLOUD_GPODDER("NEXTCLOUD_GPODDER")
    ;

    companion object {
        @JvmStatic
        fun fromIdentifier(provider: String?): SynchronizationProvider? =
            entries.firstOrNull { it.identifier == provider }
    }
}
