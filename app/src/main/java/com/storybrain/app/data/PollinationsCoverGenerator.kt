package com.storybrain.app.data

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Pollinations.ai cover generator with bounded, validated response handling. */
class PollinationsCoverGenerator(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    fun generate(bookId: String, title: String, seed: Int, onResult: (Result<File>) -> Unit) {
        Thread {
            val result = runCatching { generateBlocking(bookId, title, seed) }
            onResult(result)
        }.start()
    }

    private fun generateBlocking(bookId: String, title: String, seed: Int): File {
        val safeTitle = CoverGenerationPolicy.sanitizeTitle(title)
        val prompt = "elegant minimal novel book cover illustration for \"$safeTitle\", " +
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
                    val declaredLength = body.contentLength()
                    check(
                        CoverGenerationPolicy.isAcceptableImage(response.header("Content-Type"), declaredLength)
                    ) { "封面生成失败（响应类型或大小异常）" }
                    val covers = File(context.filesDir, "covers").apply { mkdirs() }
                    val extension = CoverGenerationPolicy.extensionFor(response.header("Content-Type"))
                    val file = File(covers, CoverGenerationPolicy.fileName(bookId, extension))
                    val temporary = File(covers, ".${file.name}.partial")
                    try {
                        BufferedInputStream(body.byteStream()).use { input ->
                            temporary.outputStream().use { output ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                var total = 0L
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    check(total <= CoverGenerationPolicy.MAX_RESPONSE_BYTES) {
                                        "封面生成失败（响应过大）"
                                    }
                                    output.write(buffer, 0, count)
                                }
                                check(
                                    CoverGenerationPolicy.isAcceptableImage(
                                        response.header("Content-Type"),
                                        total
                                    )
                                ) { "封面生成失败（返回数据异常）" }
                            }
                        }
                        if (!temporary.renameTo(file)) {
                            temporary.delete()
                            error("封面生成失败（无法保存文件）")
                        }
                        return file
                    } catch (error: Throwable) {
                        temporary.delete()
                        throw error
                    }
                }
            } catch (error: Throwable) {
                last = error
                if (attempt == 0) Thread.sleep(2_000L)
            }
        }
        throw last ?: error("封面生成失败")
    }

    companion object {
        fun stableSeed(bookId: String): Int = CoverGenerationPolicy.stableSeed(bookId)
    }
}
