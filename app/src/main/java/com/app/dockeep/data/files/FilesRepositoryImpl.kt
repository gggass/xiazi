package com.app.dockeep.data.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.app.dockeep.model.DocumentItem
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

class FilesRepositoryImpl(private val context: Context) : FilesRepository {
    private val root get() = VaultFiles.root(context)
    override suspend fun persistUriPermissions(uri: Uri) { /* Import is copied immediately. */ }
    override suspend fun setRootLocation(uri: Uri) = VaultFiles.uri(context, root)
    private fun dir(uri: Uri): File = VaultFiles.file(context, uri).also { require(it.isDirectory) }
    override suspend fun copyFilesToFolder(folderUri: Uri, files: List<Uri>, delete: Boolean) {
        require(!delete) { "简版始终保留原文件" }
        val folder = dir(folderUri)
        val errors = mutableListOf<String>()
        for (uri in files.distinct()) {
            var temp: File? = null
            try {
                var name = "文件-${System.currentTimeMillis()}"
                var size = -1L
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if(c.moveToFirst()) {
                        val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val z = c.getColumnIndex(OpenableColumns.SIZE)
                        if(n >= 0 && !c.isNull(n)) name = c.getString(n)
                        if(z >= 0 && !c.isNull(z)) size = c.getLong(z)
                    }
                }
                // Preserve typical names; reject path traversal rather than trusting provider names.
                VaultFiles.validName(name)
                if(size >= 0) require(root.usableSpace > size + 8L * 1024 * 1024) { "存储空间不足" }
                temp = File(folder, ".import-${UUID.randomUUID()}")
                val digest = MessageDigest.getInstance("SHA-256")
                var copied = 0L
                val input = requireNotNull(context.contentResolver.openInputStream(uri)) { "无法读取" }
                input.use { src -> FileOutputStream(temp).use { out ->
                    val buf = ByteArray(256 * 1024)
                    while(true) {
                        val n = src.read(buf); if(n < 0) break
                        out.write(buf, 0, n); digest.update(buf, 0, n); copied += n
                        require(root.usableSpace > 2L * 1024 * 1024) { "存储空间不足" }
                    }
                    out.fd.sync()
                } }
                require(size < 0 || size == copied) { "文件大小变化，请重试" }
                val saved = MessageDigest.getInstance("SHA-256")
                temp.inputStream().use { src -> val buf = ByteArray(256 * 1024)
                    while(true) { val n = src.read(buf); if(n < 0) break; saved.update(buf, 0, n) }
                }
                require(digest.digest().contentEquals(saved.digest())) { "副本校验失败" }
                synchronized(VaultFiles) {
                    val target = VaultFiles.unique(folder, name)
                    require(temp.renameTo(target)) { "保存失败" }
                }
            } catch (e: Exception) { errors += (e.message ?: "读取失败") }
            finally { temp?.takeIf { it.exists() }?.delete() }
        }
        if(errors.isNotEmpty()) throw IllegalStateException("${errors.size} 个失败：${errors.first()}")
    }
    override suspend fun listFilesInDirectory(folderUri: Uri): List<DocumentItem> = dir(folderUri).listFiles().orEmpty()
        .filterNot { it.name.startsWith(".") }.map { f ->
            val u = VaultFiles.uri(context, f)
            DocumentItem(id = u.toString(), name = f.name, mimeType = VaultFiles.mime(f), isFolder = f.isDirectory, uri = u, size = f.length(), date = f.lastModified())
        }
    override suspend fun createDirectory(parentUri: Uri, folderName: String): Uri = synchronized(VaultFiles) {
        val f = VaultFiles.unique(dir(parentUri), VaultFiles.validName(folderName))
        require(f.mkdir()) { "无法新建文件夹" }; VaultFiles.uri(context, f)
    }
    override suspend fun listAllDirectories(root: Uri, parent: String): List<Pair<String, Uri>> {
        val folder = dir(root)
        return folder.walkTopDown().filter { it.isDirectory && !it.name.startsWith(".") }
            .map { (if(it == this.root) "匣子" else it.relativeTo(this.root).path) to VaultFiles.uri(context, it) }.toList()
    }
    override suspend fun listAllFiles(dir: DocumentFile, parent: String): List<Pair<String, Uri>> = emptyList()
    override suspend fun renameDocument(uri: Uri, name: String) {
        val f = VaultFiles.file(context, uri); require(f != root)
        VaultFiles.validName(name)
        if(f.name == name) return
        val target = File(requireNotNull(f.parentFile), name)
        synchronized(VaultFiles) {
            require(!target.exists()) { "已有同名文件" }
            require(f.renameTo(target)) { "重命名失败" }
        }
    }
    override suspend fun deleteDocument(uri: Uri) { VaultTrash(context).move(uri) }
    override suspend fun pathExists(uri: Uri) = runCatching { VaultFiles.file(context, uri).exists() }.getOrDefault(false)
    override suspend fun moveDocument(uri: Uri, destination: Uri) {
        val result = VaultMover(root,VaultFiles).move(listOf(VaultFiles.file(context,uri)),dir(destination))
        check(result.failures.isEmpty()) { result.failures.first().reason }
    }
    override suspend fun searchFiles(query: String, root: Uri): List<DocumentItem> {
        val result = mutableListOf<DocumentItem>()
        suspend fun visit(uri: Uri) { listFilesInDirectory(uri).forEach {
            val isolated = it.isFolder && java.io.File(VaultFiles.file(context,it.uri),VaultBackup.RESTORE_MARKER).exists()
            if(!isolated) {
                if(it.name.contains(query, ignoreCase = true)) result += it
                if(it.isFolder) visit(it.uri)
            }
        } }
        visit(root); return result
    }
    override suspend fun createArchive(root: Uri, name: String, files: List<Uri>) { throw UnsupportedOperationException("备份将在后续版本加入") }
}
