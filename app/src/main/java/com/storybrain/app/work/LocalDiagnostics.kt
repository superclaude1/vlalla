package com.storybrain.app.work

import android.util.Log

/** Privacy-preserving structured diagnostics. Never logs novel text, URLs, or credentials. */
object LocalDiagnostics {
    private const val TAG = "ZhangJing"

    fun event(name: String, vararg fields: Pair<String, Any?>) {
        Log.i(TAG, format(name, fields))
    }

    fun failure(name: String, error: Throwable, vararg fields: Pair<String, Any?>) {
        Log.e(TAG, format(name, fields), error)
    }

    private fun format(name: String, fields: Array<out Pair<String, Any?>>): String = buildString {
        append("event=").append(name)
        fields.forEach { (key, value) -> append(' ').append(key).append('=').append(value) }
    }
}
