package com.androidservice.singbox

import io.nekohasekai.libbox.CommandClientHandler
import io.nekohasekai.libbox.ConnectionEvents
import io.nekohasekai.libbox.LogIterator
import io.nekohasekai.libbox.OutboundGroupIterator
import io.nekohasekai.libbox.OutboundGroupItemIterator
import io.nekohasekai.libbox.StatusMessage
import io.nekohasekai.libbox.StringIterator

internal class SingBoxTrafficHandler(
    private val onStatus: (StatusMessage) -> Unit,
) : CommandClientHandler {
    override fun connected() = Unit

    override fun disconnected(message: String?) = Unit

    override fun clearLogs() = Unit

    override fun setDefaultLogLevel(level: Int) = Unit

    override fun initializeClashMode(modeList: StringIterator?, currentMode: String?) = Unit

    override fun updateClashMode(newMode: String?) = Unit

    override fun writeLogs(messageList: LogIterator?) = Unit

    override fun writeGroups(message: OutboundGroupIterator?) = Unit

    override fun writeOutbounds(message: OutboundGroupItemIterator?) = Unit

    override fun writeConnectionEvents(events: ConnectionEvents?) = Unit

    override fun writeStatus(message: StatusMessage?) {
        if (message != null) onStatus(message)
    }
}

internal fun StatusMessage.toTrafficStats() = com.androidservice.data.SingBoxTrafficStats(
    uplinkSpeed = uplink,
    downlinkSpeed = downlink,
    uplinkTotal = uplinkTotal,
    downlinkTotal = downlinkTotal,
    available = trafficAvailable,
)
