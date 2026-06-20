package com.androidservice.singbox

import com.google.gson.JsonParser

object SingBoxConfigInspector {
    private val PROXY_INBOUND_TYPES = setOf("mixed", "socks", "http", "redirect")

    fun hasTunInbound(configJson: String): Boolean {
        return runCatching {
            val inbounds = JsonParser.parseString(configJson).asJsonObject.getAsJsonArray("inbounds") ?: return false
            inbounds.any { element ->
                element.isJsonObject && element.asJsonObject.get("type")?.asString == "tun"
            }
        }.getOrDefault(false)
    }

    fun extractListenEndpoint(configJson: String): String? {
        return runCatching {
            val inbounds = JsonParser.parseString(configJson).asJsonObject.getAsJsonArray("inbounds") ?: return null
            for (element in inbounds) {
                if (!element.isJsonObject) continue
                val inbound = element.asJsonObject
                val type = inbound.get("type")?.asString ?: continue
                if (type !in PROXY_INBOUND_TYPES) continue
                val listen = inbound.get("listen")?.asString?.takeIf { it.isNotBlank() } ?: "127.0.0.1"
                val port = inbound.get("listen_port")?.asInt ?: continue
                return "$listen:$port"
            }
            null
        }.getOrNull()
    }
}
