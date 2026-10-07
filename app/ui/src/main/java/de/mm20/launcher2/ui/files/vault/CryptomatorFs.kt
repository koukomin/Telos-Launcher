package de.mm20.launcher2.ui.files.vault

import android.net.Uri
import de.mm20.launcher2.ui.files.Fs
import de.mm20.launcher2.ui.files.FsEntry
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/** The address of something inside an unlocked vault: vlt://<vault folder, encoded>!/<path inside> */
object VaultPath {
    private const val PREFIX = "vlt://"
    fun isVault(p: String) = p.startsWith(PREFIX)
    fun vaultOf(p: String): String = Uri.decode(p.removePrefix(PREFIX).substringBefore('!'))
    fun innerOf(p: String): String = "/" + p.substringAfter('!', "").trim('/')
    fun isRoot(p: String) = isVault(p) && innerOf(p) == "/"
    fun build(vault: String, inner: String) = PREFIX + Uri.encode(vault) + "!/" + inner.trim('/')

    /** Whether this folder is a Cryptomator vault */
    fun isVaultFolder(dir: File) = File(dir, "masterkey.cryptomator").isFile && File(dir, "d").isDirectory
}

/** The keys of the vaults that are unlocked right now. They live in memory only and are gone when the process ends. */
object VaultSessions {
    private val open = ConcurrentHashMap<String, VaultCrypto>()
    fun isUnlocked(vault: String) = open.containsKey(vault)
    fun lock(vault: String) { open.remove(vault) }
    fun lockAll() = open.clear()

    /** Throws [WrongPasswordException] when the password is wrong */
    fun unlock(vault: String, password: String) {
        val dir = File(vault)
        val key = File(dir, "masterkey.cryptomator").readText()
        val config = File(dir, "vault.cryptomator").takeIf { it.isFile }?.readText()
        open[vault] = VaultCrypto.unlock(key, config, password)
    }

    fun crypto(vault: String): VaultCrypto = open[vault] ?: throw IOException("The vault is locked")
}

private class VaultItem(val name: String, val isDir: Boolean, val physical: File, val dirId: String?)

/** A Cryptomator vault (format 7 and 8) on the phone's storage, read only. */
class CryptomatorFs(private val vault: String) : Fs {
    override val isRoot = false
    override val isRemote = true // never copied as plain files
    private val root = File(vault)
    private val crypto get() = VaultSessions.crypto(vault)

    private fun folderOf(dirId: String) = File(root, crypto.directoryPath(dirId))

    private fun readText(f: File) = f.readText().trim()

    private fun items(dirId: String): List<VaultItem> {
        val folder = folderOf(dirId)
        val files = folder.listFiles() ?: return emptyList()
        val out = ArrayList<VaultItem>()
        for (f in files) {
            val n = f.name
            when {
                n.endsWith(".c9r") && f.isDirectory -> {
                    val dirFile = File(f, "dir.c9r")
                    if (dirFile.isFile) {
                        val name = crypto.decryptName(n.removeSuffix(".c9r"), dirId) ?: continue
                        out += VaultItem(name, true, f, readText(dirFile))
                    }
                }
                n.endsWith(".c9r") -> {
                    val name = crypto.decryptName(n.removeSuffix(".c9r"), dirId) ?: continue
                    out += VaultItem(name, false, f, null)
                }
                n.endsWith(".c9s") && f.isDirectory -> {
                    val full = File(f, "name.c9s").takeIf { it.isFile }?.let { readText(it) } ?: continue
                    val name = crypto.decryptName(full.removeSuffix(".c9r"), dirId) ?: continue
                    val dirFile = File(f, "dir.c9r")
                    val contents = File(f, "contents.c9r")
                    when {
                        dirFile.isFile -> out += VaultItem(name, true, f, readText(dirFile))
                        contents.isFile -> out += VaultItem(name, false, contents, null)
                    }
                }
            }
        }
        return out
    }

    private fun segments(path: String) = VaultPath.innerOf(path).trim('/').split('/').filter { it.isNotEmpty() }

    /** The ID of the folder at [path] */
    private fun dirIdOf(path: String): String {
        var id = ""
        for (s in segments(path)) {
            val item = items(id).firstOrNull { it.name == s && it.isDir } ?: throw IOException("Folder not found in the vault")
            id = item.dirId ?: throw IOException("Folder not found in the vault")
        }
        return id
    }

    private fun itemAt(path: String): VaultItem? {
        val segs = segments(path)
        if (segs.isEmpty()) return null
        val parentId = dirIdOf(VaultPath.build(vault, segs.dropLast(1).joinToString("/")))
        return items(parentId).firstOrNull { it.name == segs.last() }
    }

    override fun list(path: String): List<FsEntry> {
        val c = crypto
        return items(dirIdOf(path)).map {
            FsEntry(
                path = VaultPath.build(vault, (segments(path) + it.name).joinToString("/")),
                name = it.name,
                isDir = it.isDir,
                size = if (it.isDir) -1 else c.cleartextSize(it.physical.length()),
                modified = it.physical.lastModified(),
            )
        }
    }

    override fun mkdir(parent: String, name: String) = false
    override fun createFile(parent: String, name: String) = false
    override fun rename(path: String, newName: String) = false
    override fun delete(path: String) = false
    override fun copy(src: String, dstDir: String, newName: String) = false
    override fun move(src: String, dstDir: String, newName: String) = false
    override fun chmod(path: String, mode: String) = false
    override fun exists(path: String) = segments(path).isEmpty() || runCatching { itemAt(path) != null }.getOrDefault(false)

    override fun totalSize(path: String): Long {
        val segs = segments(path)
        if (segs.isNotEmpty()) {
            val item = itemAt(path) ?: return 0
            if (!item.isDir) return crypto.cleartextSize(item.physical.length())
        }
        return list(path).sumOf { if (it.isDir) totalSize(it.path) else it.size }
    }

    override fun openRead(path: String): InputStream {
        val item = itemAt(path)?.takeIf { !it.isDir } ?: throw IOException("Not found in the vault")
        return crypto.decrypting(item.physical.inputStream().buffered())
    }

    companion object {
        fun of(vault: String) = CryptomatorFs(vault)
    }
}
