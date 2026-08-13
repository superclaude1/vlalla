package com.storybrain.app.settings

import java.net.URI

/** Canonical validation for user-configured API service roots. */
object ApiEndpointPolicy {
    fun normalize(value: String): String {
        var candidate = value.trim().trimEnd('/')
        require(candidate.isNotBlank()) { "请输入 API URL" }
        if (!candidate.contains("://")) candidate = "https://$candidate"

        val uri = runCatching { URI(candidate) }
            .getOrElse { throw IllegalArgumentException("API URL 格式不正确", it) }
        require(uri.scheme.equals("https", ignoreCase = true)) {
            "API URL 必须使用 HTTPS，以保护 API Key"
        }
        require(uri.host != null && uri.host.isNotBlank()) { "API URL 格式不正确" }
        require(uri.rawUserInfo == null) { "API URL 不能包含用户名或密码" }
        require(uri.rawQuery == null) { "API URL 不能包含查询参数" }
        require(uri.rawFragment == null) { "API URL 不能包含片段" }
        require(uri.port == -1 || uri.port in 1..65535) { "API URL 端口不正确" }

        val normalizedPath = uri.rawPath.orEmpty().trimEnd('/').ifBlank { "" }
        val defaultPath = if (uri.host.equals("api.openai.com", ignoreCase = true) && normalizedPath.isBlank()) {
            "/v1"
        } else {
            normalizedPath
        }
        val authority = buildString {
            append(uri.host.lowercase())
            if (uri.port != -1) append(':').append(uri.port)
        }
        return "https://$authority$defaultPath"
    }
}
