package com.storybrain.app.data

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

data class CoverDeletionStage(
    val original: File,
    val staged: File,
    val directory: File
)

object CoverFileLifecycle {
    fun coversDirectory(filesDir: File): File = File(filesDir, "covers").apply { mkdirs() }

    fun newTemporaryFile(filesDir: File, bookId: String): File {
        val covers = coversDirectory(filesDir)
        val prefix = ".${CoverGenerationPolicy.fileName(bookId, "tmp").substringBefore('.')}"
        return File(covers, "$prefix-${UUID.randomUUID()}.partial")
    }

    fun publish(temporary: File, destination: File) {
        require(temporary.parentFile?.canonicalFile == destination.parentFile?.canonicalFile) {
            "封面生成失败（临时文件目录异常）"
        }
        Files.move(
            temporary.toPath(),
            destination.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
        )
    }

    fun stageDeletion(filesDir: File, path: String?): CoverDeletionStage? {
        if (path.isNullOrBlank() || !CoverGenerationPolicy.isManagedPath(filesDir, path)) return null
        val original = File(path)
        if (!original.exists()) return null
        val directory = File(coversDirectory(filesDir), ".trash-${UUID.randomUUID()}")
        check(directory.mkdir()) { "无法准备封面回收区" }
        val staged = File(directory, original.name)
        check(original.renameTo(staged)) { "无法暂存书籍封面" }
        return CoverDeletionStage(original, staged, directory)
    }

    fun restoreDeletion(stage: CoverDeletionStage) {
        if (stage.staged.exists()) {
            stage.original.parentFile?.mkdirs()
            check(stage.staged.renameTo(stage.original)) { "无法恢复书籍封面" }
        }
        stage.directory.deleteRecursively()
    }

    fun commitDeletion(stage: CoverDeletionStage?) {
        stage?.directory?.deleteRecursively()
    }

    fun recoverAndCleanup(
        filesDir: File,
        referencedPaths: Set<String>,
        nowMillis: Long = System.currentTimeMillis(),
        orphanGraceMillis: Long = ORPHAN_GRACE_MILLIS
    ) {
        val covers = File(filesDir, "covers")
        if (!covers.exists()) return
        val referenced = referencedPaths.mapNotNull { path ->
            if (CoverGenerationPolicy.isManagedPath(filesDir, path)) runCatching { File(path).canonicalFile }.getOrNull()
            else null
        }.toSet()
        covers.listFiles()?.forEach { entry ->
            if (
                entry.isDirectory &&
                entry.name.startsWith(".trash-") &&
                isOldEnough(entry, nowMillis, orphanGraceMillis)
            ) {
                var allRecovered = true
                entry.listFiles()?.forEach { staged ->
                    val destination = File(covers, staged.name)
                    if (destination.canonicalFile in referenced && !destination.exists()) {
                        if (!staged.renameTo(destination)) allRecovered = false
                    } else if (!staged.deleteRecursively()) {
                        allRecovered = false
                    }
                }
                if (allRecovered) entry.deleteRecursively()
            } else if (entry.name.endsWith(".partial") && isOldEnough(entry, nowMillis, orphanGraceMillis)) {
                entry.deleteRecursively()
            } else if (entry.canonicalFile !in referenced && isOldEnough(entry, nowMillis, orphanGraceMillis)) {
                entry.deleteRecursively()
            }
        }
    }

    fun deleteReplaced(filesDir: File, previousPath: String?, newPath: String) {
        if (previousPath == null || previousPath == newPath) return
        if (CoverGenerationPolicy.isManagedPath(filesDir, previousPath)) {
            File(previousPath).delete()
        }
    }

    fun deleteManaged(filesDir: File, path: String?) {
        if (path != null && CoverGenerationPolicy.isManagedPath(filesDir, path)) {
            File(path).delete()
        }
    }

    private fun isOldEnough(file: File, nowMillis: Long, graceMillis: Long): Boolean =
        nowMillis - file.lastModified() >= graceMillis

    private const val ORPHAN_GRACE_MILLIS = 6L * 60L * 60L * 1_000L
}
