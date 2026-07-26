package de.danoeh.antennapod.event

import android.content.Context
import androidx.core.util.Consumer

class MessageEvent(
    @JvmField val message: String?,
    @JvmField val action: Consumer<Context>?,
    @JvmField val actionText: String?
) {
    constructor(message: String?) : this(message, null, null)
}
