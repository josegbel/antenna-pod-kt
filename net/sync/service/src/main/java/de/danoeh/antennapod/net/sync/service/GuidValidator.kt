package de.danoeh.antennapod.net.sync.service

object GuidValidator {

    @JvmStatic
    fun isValidGuid(guid: String?): Boolean {
        return guid != null &&
            !guid.trim().isEmpty() &&
            !guid.equals("null")
    }
}
