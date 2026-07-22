package com.storybrain.app.settings

import java.net.URI

object EndpointPolicy {
    fun requireAllowed(value: String, allowInsecureHttp: Boolean): String {
        val normalized = OpenAiCompatibleClient.normalizeBaseUrl(value)
        val uri = URI(normalized)
        require(uri.scheme.equals("https", true) || (uri.scheme.equals("http", true) && allowInsecureHttp)) {
            "该地址使用明文 HTTP。请先确认仅在可信局域网中使用，并启用“不安全 HTTP”选项。"
        }
        return normalized
    }

    fun isInsecure(value: String): Boolean = runCatching {
        URI(OpenAiCompatibleClient.normalizeBaseUrl(value)).scheme.equals("http", true)
    }.getOrDefault(false)
}
