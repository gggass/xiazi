package com.app.dockeep.data.files

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.nio.file.Files
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.*

/** Versioned ZIP plus per-file SHA-256, verified before publishing or exposing a restored directory. */
class VaultBackup(private val context: Context) {
    data class Item(val name: String, val directory: Boolean, val bytes: Long, val crc: Long, val hash: String?, val modified: Long = 0)
    data class Plan(val items: List<Item>, val bytes: Long, val pocketVault: Boolean, val manifest: String?) {
        val files get() = items.count { !it.directory && !(pocketVault && it.name == "manifest.json") }
        val folders get() = items.count { it.directory }
    }
    companion object {
        const val RESTORE_MARKER = ".pocketvault-restore.json"
        private const val RESERVE = 16L * 1024 * 1024
        private const val MAX_BYTES = 64L * 1024 * 1024 * 1024
        private const val MAX_ENTRIES = 20000
    }
    fun build(output: File, includeTrash: Boolean, tick: (String) -> Unit = {}): Plan = synchronized(VaultFiles) {
        val records = JSONArray()
        val trash = JSONArray()
        try {
            FileOutputStream(output).use { raw ->
                ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
                    fun add(file: File, name: String, boundary: File) {
                        tick("正在备份：$name")
                        require(!Files.isSymbolicLink(file.toPath()) &&
                            (file.canonicalFile == boundary || file.canonicalPath.startsWith(boundary.path+File.separator))) { "不支持备份符号链接" }
                        require(records.length() < MAX_ENTRIES-1) { "文件过多，请分批整理后备份" }
                        validateName(name)
                        if(file.isDirectory) {
                            zip.putNextEntry(ZipEntry(name.trimEnd('/')+"/")); zip.closeEntry()
                            file.listFiles().orEmpty().filterNot { it.name.startsWith(".import-") }.sortedBy { it.name }.forEach {
                                add(it,name.trimEnd('/')+"/"+it.name,boundary)
                            }
                        } else {
                            val digest = MessageDigest.getInstance("SHA-256")
                            var bytes = 0L
                            zip.putNextEntry(ZipEntry(name).apply { time = file.lastModified() })
                            file.inputStream().buffered().use { input ->
                                val buffer = ByteArray(256*1024); var last = System.nanoTime()
                                while(true) {
                                    val n = input.read(buffer); if(n < 0) break
                                    zip.write(buffer,0,n); digest.update(buffer,0,n); bytes += n
                                    check(output.parentFile!!.usableSpace > RESERVE) { "空间不足，备份未完成" }
                                    if(System.nanoTime()-last > 200_000_000) { tick("正在备份：$name"); last = System.nanoTime() }
                                }
                            }
                            zip.closeEntry()
                            records.put(JSONObject().put("path",name).put("size",bytes).put("sha256",hex(digest.digest())).put("modifiedAt",file.lastModified()))
                        }
                    }
                    val vault = VaultFiles.root(context)
                    add(vault,"文件",vault)
                    if(includeTrash) VaultTrash(context).entries().forEach { entry ->
                        add(entry.payload,"回收站/${entry.id}/content",entry.payload.canonicalFile)
                        trash.put(JSONObject().put("id",entry.id).put("originalPath",entry.originalPath).put("deletedAt",entry.deletedAt))
                    }
                    val manifest = JSONObject().put("format","PocketVault").put("version",1).put("createdAt",System.currentTimeMillis())
                        .put("includesTrash",includeTrash).put("files",records).put("trash",trash).toString().toByteArray(Charsets.UTF_8)
                    check(manifest.size <= 16*1024*1024) { "备份索引过大" }
                    zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest); zip.closeEntry()
                    zip.finish(); zip.flush(); raw.fd.sync()
                }
            }
            tick("正在校验备份…")
            val plan = inspect(output)
            verify(output,plan,tick)
            plan
        } catch(e: Exception) { output.delete(); throw e }
    }
    fun inspect(archive: File): Plan {
        ZipFile(archive).use { zip ->
            val raw=mutableListOf<ZipEntry>()
            val enumeration=zip.entries()
            while(enumeration.hasMoreElements()) { require(raw.size<MAX_ENTRIES) { "ZIP 项目过多" }; raw += enumeration.nextElement() }
            val names = mutableSetOf<String>()
            var total = 0L
            raw.forEach { e ->
                validateName(e.name)
                require(names.add(e.name.trimEnd('/'))) { "ZIP 存在重复或冲突路径" }
                require(e.size >= 0 && e.size <= MAX_BYTES) { "ZIP 文件大小无效或过大" }
                if(!e.isDirectory) total = Math.addExact(total,e.size)
                require(total <= MAX_BYTES) { "ZIP 解压总量超过限制" }
            }
            require(total <= 512L*1024*1024 || total / archive.length().coerceAtLeast(1) <= 1000) { "ZIP 压缩比异常，已阻止解压" }
            val manifestEntry = zip.getEntry("manifest.json")
            val manifestText = if(manifestEntry != null && manifestEntry.size in 0..(16L*1024*1024))
                zip.getInputStream(manifestEntry).use { String(readBounded(it,16*1024*1024),Charsets.UTF_8) } else null
            val manifest = manifestText?.let { runCatching { JSONObject(it) }.getOrNull() }
            val pocket = manifest?.optString("format") == "PocketVault"
            require(pocket || raw.none { it.name.trimEnd('/') == RESTORE_MARKER }) { "ZIP 使用了匣子保留文件名" }
            val hashes = mutableMapOf<String,Pair<Long,String>>()
            val modified = mutableMapOf<String,Long>()
            if(pocket) {
                require(manifest!!.getInt("version") == 1) { "暂不支持这个备份版本" }
                val records = manifest.getJSONArray("files")
                require(records.length() <= MAX_ENTRIES) { "备份索引过大" }
                for(i in 0 until records.length()) {
                    val r = records.getJSONObject(i); val path = r.getString("path"); val hash = r.getString("sha256")
                    require(hash.matches(Regex("[0-9a-f]{64}")) && !hashes.containsKey(path)) { "备份索引损坏" }
                    hashes[path] = r.getLong("size") to hash
                    modified[path] = r.optLong("modifiedAt",0)
                }
                require(hashes.keys == raw.filter { !it.isDirectory && it.name != "manifest.json" }.map { it.name }.toSet()) { "备份文件与索引不一致" }
                require(raw.all { it.name == "manifest.json" || it.name.trimEnd('/') == "文件" || it.name.startsWith("文件/") || it.name.startsWith("回收站/") }) { "备份目录格式异常" }
            }
            val items = raw.map { e ->
                val record = hashes[e.name]
                if(record != null) require(record.first == e.size) { "备份文件大小与索引不一致" }
                Item(e.name,e.isDirectory,e.size,e.crc,record?.second,modified[e.name]?.takeIf { it>0 } ?: e.time)
            }
            return Plan(items,total,pocket,if(pocket) manifestText else null)
        }
    }
    fun restore(archive: File, plan: Plan, tick: (String) -> Unit = {}): File = synchronized(VaultFiles) {
        val vault = VaultFiles.root(context)
        require(plan.bytes + RESERVE < vault.usableSpace) { "空间不足，需要至少 ${plan.bytes} 字节可用空间" }
        val stage = File(context.filesDir,".restore-${UUID.randomUUID()}").canonicalFile
        check(stage.mkdir()) { "无法创建恢复临时目录" }
        var committed = false
        try {
            ZipFile(archive).use { zip ->
                for(item in plan.items) {
                    tick("正在恢复：${item.name}")
                    val target = File(stage,item.name).canonicalFile
                    require(target.path.startsWith(stage.path+File.separator)) { "ZIP 路径越界" }
                    if(item.directory) {
                        require(!target.exists() || target.isDirectory) { "ZIP 目录冲突" }
                        check(target.isDirectory || target.mkdirs()) { "无法创建文件夹" }
                    } else {
                        require(!target.exists()) { "ZIP 文件路径冲突" }
                        check(target.parentFile!!.isDirectory || target.parentFile!!.mkdirs()) { "无法创建文件夹" }
                        FileOutputStream(target).use { out ->
                            zip.getInputStream(zip.getEntry(item.name)).use { input -> copyVerified(input,out,item,tick) }
                            out.fd.sync()
                        }
                        if(item.modified>0) target.setLastModified(item.modified)
                    }
                }
            }
            if(plan.pocketVault) {
                val manifest = File(stage,"manifest.json")
                check(manifest.renameTo(File(stage,RESTORE_MARKER))) { "恢复索引保存失败" }
            } else FileOutputStream(File(stage,RESTORE_MARKER)).use { it.write("{\"format\":\"ImportedZIP\"}".toByteArray()); it.fd.sync() }
            tick("正在提交已校验的恢复…")
            val name = "导入备份-"+SimpleDateFormat("yyyyMMdd-HHmmss",Locale.ROOT).format(Date())+"-"+UUID.randomUUID().toString().take(6)
            val target = VaultFiles.unique(vault,name)
            check(stage.renameTo(target)) { "恢复提交失败，已有仓库保持不变" }
            committed = true
            target
        } finally { if(!committed) stage.deleteRecursively() }
    }
    private fun verify(archive: File, plan: Plan, tick: (String) -> Unit) {
        ZipFile(archive).use { zip -> plan.items.filterNot { it.directory }.forEach { item ->
            tick("正在校验：${item.name}")
            zip.getInputStream(zip.getEntry(item.name)).use { copyVerified(it,null,item,tick) }
        } }
    }
    private fun copyVerified(input: InputStream, output: OutputStream?, item: Item, tick: (String) -> Unit) {
        val digest = MessageDigest.getInstance("SHA-256"); val crc = CRC32(); var bytes = 0L
        val buffer = ByteArray(256*1024); var last = System.nanoTime()
        while(true) {
            val n = input.read(buffer); if(n < 0) break
            bytes += n
            require(bytes <= item.bytes) { "ZIP 实际解压大小异常" }
            if(output != null) require(VaultFiles.root(context).usableSpace > RESERVE) { "空间不足，恢复未完成" }
            output?.write(buffer,0,n); digest.update(buffer,0,n); crc.update(buffer,0,n)
            if(System.nanoTime()-last > 200_000_000) { tick("正在处理：${item.name}"); last = System.nanoTime() }
        }
        require(bytes == item.bytes && crc.value == item.crc) { "ZIP 内容损坏：${item.name}" }
        if(item.hash != null) require(hex(digest.digest()) == item.hash) { "文件校验失败：${item.name}" }
    }
    private fun validateName(name: String) {
        require(name.isNotBlank() && name.length <= 4096 && !name.startsWith('/') && !name.contains('\\') && !name.contains('\u0000')) { "ZIP 路径无效" }
        require(!name.matches(Regex("^[A-Za-z]:.*"))) { "ZIP 不能使用绝对路径" }
        val parts = name.trimEnd('/').split('/')
        require(parts.size <= 64 && parts.all { it.isNotBlank() && it != "." && it != ".." && it.length <= 180 && (it == RESTORE_MARKER || !it.startsWith('.')) }) { "ZIP 路径越界、过深或包含不支持的隐藏路径" }
    }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun readBounded(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while(true) { val n = input.read(buffer); if(n < 0) break; require(output.size()+n <= limit) { "备份索引过大" }; output.write(buffer,0,n) }
        return output.toByteArray()
    }
}
