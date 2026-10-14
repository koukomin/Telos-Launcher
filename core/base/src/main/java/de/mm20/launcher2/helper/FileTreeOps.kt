package de.mm20.launcher2.helper

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.SimpleFileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.Path

/** Permanent deletion unlinks symbolic links without visiting their targets. */
object FileTreeOps {
    fun delete(file: File, checkpoint: () -> Unit = {}) {
        val path = file.absoluteFile.toPath().normalize()
        require(path.parent != null) { "Cannot delete the filesystem root" }
        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                checkpoint(); Files.delete(file); return FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                checkpoint(); if (exc != null) throw exc
                Files.delete(dir); return FileVisitResult.CONTINUE
            }
        })
    }
    fun size(file: File, checkpoint: () -> Unit = {}): Long {
        var bytes = 0L
        Files.walkFileTree(file.toPath(), object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                checkpoint(); return FileVisitResult.CONTINUE
            }
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                checkpoint(); if (attrs.isRegularFile) bytes = Math.addExact(bytes, attrs.size())
                return FileVisitResult.CONTINUE
            }
        })
        return bytes
    }
}
