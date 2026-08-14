package com.storybrain.app.data

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Pollinations.ai 免费文生图封面。
 * GET https://image.pollinations.ai/prompt/{prompt}?width=..&height=..&seed=..&nologo=true
 * seed 由 bookId 稳定派生（同一本书重复生成结果一致），"重新生成"传随机 seed。
 */
class PollinationsCoverGenerator(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    /** 后台线程生成，结果通过回调返回（成功=封面文件）。 */
    fun generate(bookId: String, title: String, seed: Int, onResult: (Result<File>) -> Unit) {
        Thread {
            val result = runCatching { generateBlocking(bookId, title, seed) }
            onResult(result)
        }.start()
    }

    private fun generateBlocking(bookId: String, title: String, seed: Int): File {
        val prompt = "elegant minimal novel book cover illustration for \"$title\", " +
            "fantasy literary style, muted colors, no text, no letters, no words"
        val url = "https://image.pollinations.ai/prompt/${URLEncoder.encode(prompt, "UTF-8")}" +
            "?width=768&height=1024&seed=$seed&nologo=true&model=flux"
        val request = Request.Builder().url(url).get().build()
        var last: Throwable? = null
        repeat(2) { attempt ->
            try {
                client.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "封面生成失败（HTTP ${response.code}）" }
                    val body = response.body ?: error("封面生成失败（空响应）")
                    val bytes = body.bytes()
                    check(bytes.size > 1024) { "封面生成失败（返回数据异常）" }
                    val covers = File(context.filesDir, "covers").apply { mkdirs() }
                    val file = File(covers, "$bookId.jpg")
                    file.writeBytes(bytes)
                    return file
                }
            } catch (error: Throwable) {
                last = error
                if (attempt == 0) Thread.sleep(2_000L)
            }
        }
        throw last ?: error("封面生成失败")
    }

    companion object {
        /** 由 bookId 稳定派生 seed：同一本书的默认封面固定。 */
        fun stableSeed(bookId: String): Int =
            MessageDigest.getInstance("SHA-256")
                .digest(bookId.toByteArray())
                .fold(0) { acc, byte -> acc * 31 + byte } and 0x7fffffff
    }
}
