package de.danoeh.antennapod.model.download

class DownloadStatus(private val state: Int, private val progress: Int) {

    fun getState(): Int {
        return state
    }

    fun getProgress(): Int {
        return progress
    }

    companion object {
        const val STATE_QUEUED = 0
        const val STATE_COMPLETED = 1 // Both successful and not successful
        const val STATE_RUNNING = 2
    }
}
