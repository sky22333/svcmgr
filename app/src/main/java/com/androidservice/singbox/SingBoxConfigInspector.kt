package com.androidservice.singbox

import com.google.gson.JsonParser

object SingBoxConfigInspector {
    fun hasTunInbound(configJson: String): Boolean {
        return runCatching {
            val inbounds = JsonParser.parseString(configJson).asJsonObject.getAsJsonArray("inbounds") ?: return false
            inbounds.any { element ->
                element.isJsonObject && element.asJsonObject.get("type")?.asString == "tun"
            }
        }.getOrDefault(false)
    }
}
