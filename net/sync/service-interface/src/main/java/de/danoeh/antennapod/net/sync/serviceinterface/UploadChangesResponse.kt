package de.danoeh.antennapod.net.sync.serviceinterface

abstract class UploadChangesResponse(
    /**
     * timestamp/ID that can be used for requesting changes since this upload.
     */
    @JvmField val timestamp: Long,
)
