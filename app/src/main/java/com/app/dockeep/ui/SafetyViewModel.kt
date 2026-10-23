package com.app.dockeep.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.app.dockeep.data.files.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

class SafetyViewModel(app: Application) : AndroidViewModel(app) {
    data class State(val entries: List<VaultTrash.Entry> = emptyList(), val busy: Boolean = false, val progress: String = "", val message: String? = null, val pending: VaultBackup.Plan? = null)
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private val context = app.applicationContext
    private val trash = VaultTrash(context)
    private val backup = VaultBackup(context)
    private val prefs = context.getSharedPreferences("vault_safety",0)
    private var job: Job? = null
    private var pendingFile: File? = null
    val directory: String? get() = prefs.getString("backup_dir",null)
    init { refresh() }
    fun clearMessage() { mutable.update { it.copy(message=null) } }
    fun setDirectory(uri: Uri): Boolean = try {
        context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        prefs.edit().putString("backup_dir",uri.toString()).apply(); mutable.update { it.copy(message="备份目录已保存") }; true
    } catch(e: Exception) { mutable.update { it.copy(message="无法保存目录授权，请选择支持读写的本地目录：${e.message}") }; false }
    fun refresh() { if(!mutable.value.busy) viewModelScope.launch { val entries = withContext(Dispatchers.IO) { trash.entries() }; mutable.update { it.copy(entries=entries) } } }
    private fun task(label: String, work: suspend (tick: (String) -> Unit) -> String) {
        if(mutable.value.busy) return
        mutable.update { it.copy(busy=true,progress=label,message=null) }
        job = viewModelScope.launch {
            try {
                val message = withContext(Dispatchers.IO) {
                    val operation = coroutineContext
                    work { text -> operation.ensureActive(); mutable.update { it.copy(progress=text) } }
                }
                mutable.update { it.copy(message=message) }
            } catch(e: CancellationException) { mutable.update { it.copy(message="已取消。仓库内文件保留，未完成恢复不会显示为成功。") } }
            catch(e: Exception) { mutable.update { it.copy(message="操作未完成：${e.message}。仓库内文件保留。") } }
            finally {
                withContext(NonCancellable) {
                    val entries = withContext(Dispatchers.IO) { trash.entries() }
                    mutable.update { it.copy(busy=false,progress="",entries=entries) }
                }
            }
        }
    }
    fun cancel() { job?.cancel() }
    fun restore(id: String) = task("正在恢复…") { _ -> "已恢复：${trash.restore(id).name}" }
    fun restoreAll() = task("正在恢复回收站…") { tick ->
        var ok=0; val failed=mutableListOf<String>()
        trash.entries().forEach { e -> tick("正在恢复：${e.name}"); try { trash.restore(e.id); ok++ } catch(x: Exception) { failed += e.name } }
        "已恢复 $ok 项" + if(failed.isEmpty()) "" else "，${failed.size} 项未完成并保留在回收站：${failed.take(3).joinToString()}"
    }
    fun delete(id: String?) = task("正在永久删除…") { tick ->
        val targets = if(id == null) trash.entries() else trash.entries().filter { it.id == id }
        var ok=0; var failed=0
        targets.forEach { e -> tick("正在删除：${e.name}"); try { trash.permanentlyDelete(e.id); ok++ } catch(x: Exception) { failed++ } }
        "已永久删除 $ok 项" + if(failed==0) "" else "，$failed 项未完全删除，请检查回收站"
    }
    fun createBackup(includeTrash: Boolean) = task("正在创建备份…") { tick ->
        val dir = directory?.let { DocumentFile.fromTreeUri(context,Uri.parse(it)) }
        require(dir != null && dir.exists() && dir.canWrite()) { "备份目录授权失效，请更换备份目录" }
        val local = File.createTempFile("vault-backup-",".zip",context.cacheDir)
        var created: DocumentFile? = null
        try {
            backup.build(local,includeTrash,tick)
            tick("正在保存备份…")
            val name = "匣子备份-"+SimpleDateFormat("yyyyMMdd-HHmmss",Locale.ROOT).format(Date())+"-"+UUID.randomUUID().toString().take(8)+".zip"
            require(dir.findFile(name)==null) { "备份名称冲突，请重试" }
            created = requireNotNull(dir.createFile("application/zip",name)) { "无法创建备份文件" }
            val expected = MessageDigest.getInstance("SHA-256")
            local.inputStream().use { input -> requireNotNull(context.contentResolver.openOutputStream(created!!.uri,"w")).use { output ->
                val buffer=ByteArray(256*1024); var last=System.nanoTime()
                while(true) { val n=input.read(buffer); if(n<0) break; output.write(buffer,0,n); expected.update(buffer,0,n)
                    if(System.nanoTime()-last>200_000_000) { tick("正在保存备份…"); last=System.nanoTime() } }
                output.flush()
            } }
            tick("正在回读校验备份…")
            val actual = MessageDigest.getInstance("SHA-256")
            requireNotNull(context.contentResolver.openInputStream(created!!.uri)).use { input ->
                val buffer=ByteArray(256*1024); var last=System.nanoTime()
                while(true) { val n=input.read(buffer); if(n<0) break; actual.update(buffer,0,n)
                    if(System.nanoTime()-last>200_000_000) { tick("正在回读校验备份…"); last=System.nanoTime() } }
            }
            require(expected.digest().contentEquals(actual.digest())) { "备份副本校验失败" }
            "备份成功：$name\nZIP 未加密，请妥善保管。"
        } catch(e: Exception) { runCatching { created?.delete() }; throw e }
        finally { local.delete() }
    }
    fun inspectSource(uri: Uri) = task("正在读取 ZIP…") { tick ->
        cancelPending()
        val temp = File.createTempFile("vault-import-",".zip",context.cacheDir)
        var keep=false
        try {
            requireNotNull(context.contentResolver.openInputStream(uri)).use { input -> temp.outputStream().use { output ->
                val buffer=ByteArray(256*1024); var count=0L; var last=System.nanoTime()
                while(true) { val n=input.read(buffer); if(n<0) break; count+=n
                    require(count<=8L*1024*1024*1024 && context.cacheDir.usableSpace>16L*1024*1024) { "ZIP 太大或剩余空间不足" }
                    output.write(buffer,0,n)
                    if(System.nanoTime()-last>200_000_000) { tick("正在读取 ZIP…"); last=System.nanoTime() }
                }
            } }
            tick("正在检查 ZIP…")
            val plan=backup.inspect(temp)
            require(plan.bytes+16L*1024*1024 < VaultFiles.root(context).usableSpace) { "解压空间不足" }
            pendingFile=temp; keep=true
            mutable.update { it.copy(pending=plan) }
            ""
        } finally { if(!keep) temp.delete() }
    }
    fun cancelPending() { pendingFile?.delete(); pendingFile=null; mutable.update { it.copy(pending=null) } }
    fun restorePending() {
        val source=pendingFile ?: return
        val plan=mutable.value.pending ?: return
        mutable.update { it.copy(pending=null) }
        task("正在独立恢复…") { tick ->
            try { "恢复完成：${backup.restore(source,plan,tick).name}\n请回首页查看独立文件夹；已有文件未覆盖。备份中的回收站保留在该文件夹内。" }
            finally { source.delete(); pendingFile=null }
        }
    }
    override fun onCleared() { pendingFile?.delete(); super.onCleared() }
}
