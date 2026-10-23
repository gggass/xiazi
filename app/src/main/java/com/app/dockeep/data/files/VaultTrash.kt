package com.app.dockeep.data.files

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.UUID

/** Moving between vault and trash uses same-filesystem rename; original bytes never get copied or overwritten. */
class VaultTrash(private val context: Context) {
    data class Entry(val id: String, val originalPath: String, val deletedAt: Long, val payload: File, val bytes: Long) {
        val name get() = originalPath.substringAfterLast('/')
    }
    private fun root() = File(context.filesDir,"vault-trash").canonicalFile.apply { mkdirs() }
    private fun bucket(id: String): File {
        require(id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))) { "无效回收站项目" }
        return File(root(),id)
    }
    fun entries(): List<Entry> = synchronized(VaultFiles) {
        root().listFiles().orEmpty().filter { it.isDirectory }.mapNotNull { dir ->
            val payload = File(dir,"content")
            if(!payload.exists()) return@mapNotNull null
            val meta = runCatching { JSONObject(File(dir,"entry.json").readText()) }.getOrNull()
            val path = meta?.optString("originalPath")?.takeIf { it.isNotBlank() } ?: "恢复的文件-${dir.name}"
            Entry(dir.name,path,meta?.optLong("deletedAt",dir.lastModified()) ?: dir.lastModified(),payload,
                runCatching { size(payload) }.getOrDefault(0))
        }.sortedByDescending { it.deletedAt }
    }
    fun move(uri: Uri): Entry = synchronized(VaultFiles) {
        val source = VaultFiles.file(context,uri)
        val vault = VaultFiles.root(context)
        require(source != vault && source.exists()) { "文件不存在或不能移除根目录" }
        val id = UUID.randomUUID().toString()
        val dir = bucket(id).apply { check(mkdir()) { "无法创建回收站项目" } }
        val meta = JSONObject().put("version",1).put("originalPath",source.relativeTo(vault).invariantSeparatorsPath)
            .put("deletedAt",System.currentTimeMillis())
        try {
            FileOutputStream(File(dir,"entry.json")).use { it.write(meta.toString().toByteArray()); it.fd.sync() }
            check(source.renameTo(File(dir,"content"))) { "移入回收站失败，原文件保留" }
        } catch(e: Exception) { if(!File(dir,"content").exists()) safeDelete(dir); throw e }
        Entry(id,meta.getString("originalPath"),meta.getLong("deletedAt"),File(dir,"content"),0)
    }
    fun restore(id: String): File = synchronized(VaultFiles) {
        val dir = bucket(id)
        val payload = File(dir,"content")
        require(payload.exists() && payload.canonicalPath.startsWith(dir.canonicalPath+File.separator)) { "回收站内容不可用" }
        val meta = runCatching { JSONObject(File(dir,"entry.json").readText()) }.getOrNull()
        val relative = meta?.optString("originalPath")?.takeIf { validRelative(it) } ?: "恢复的文件-$id"
        val vault = VaultFiles.root(context)
        val requested = File(vault,relative).canonicalFile
        require(requested.path.startsWith(vault.path+File.separator)) { "恢复路径越界" }
        val parent = requireNotNull(requested.parentFile)
        check(parent.isDirectory || parent.mkdirs()) { "原文件夹不可用，请先处理同名文件" }
        val target = VaultFiles.unique(parent,requested.name)
        check(payload.renameTo(target)) { "恢复失败，回收站内容保留" }
        // Once committed, a leftover metadata folder must not turn a successful restore into a failure.
        runCatching { safeDelete(dir) }
        target
    }
    fun permanentlyDelete(id: String) = synchronized(VaultFiles) { safeDelete(bucket(id)) }
    private fun validRelative(path: String) = path.isNotBlank() && !path.startsWith('/') &&
        path.split('/').all { runCatching { VaultFiles.validName(it) }.isSuccess }
    private fun size(file: File): Long {
        if(Files.isSymbolicLink(file.toPath())) return 0
        return if(file.isDirectory) file.listFiles().orEmpty().sumOf { size(it) } else file.length()
    }
    private fun safeDelete(file: File) {
        if(Files.isSymbolicLink(file.toPath())) { Files.deleteIfExists(file.toPath()); return }
        if(file.isDirectory) file.listFiles().orEmpty().forEach { safeDelete(it) }
        if(file.exists()) check(file.delete()) { "部分内容未能删除，请重试" }
    }
}
