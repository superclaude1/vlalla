package com.storybrain.app.network

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Response

object NetworkClients {
    val standard: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    val longRunning: OkHttpClient by lazy {
        standard.newBuilder()
            .readTimeout(180, TimeUnit.SECONDS)
            .build()
    }

    val webSocket: OkHttpClient by lazy {
        standard.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWith(Result.failure(e))
        }

        override fun onResponse(call: Call, response: Response) {
            if (continuation.isActive) continuation.resume(response) else response.close()
        }
    })
}

open class ProviderFailure(
    message: String,
    cause: Throwable? = null,
    open val retryable: Boolean = false
) : IOException(message, cause) {
    class Authentication(message: String) : ProviderFailure(message)
    class RateLimited(message: String, val retryAfterMillis: Long?) : ProviderFailure(message, retryable = true)
    open class Timeout(message: String, cause: Throwable? = null) : ProviderFailure(message, cause, retryable = true)
    class Network(message: String, cause: Throwable? = null) : ProviderFailure(message, cause, retryable = true)
    class Server(message: String) : ProviderFailure(message, retryable = true)
    class InvalidResponse(message: String) : ProviderFailure(message)
    class Rejected(message: String) : ProviderFailure(message)
}
