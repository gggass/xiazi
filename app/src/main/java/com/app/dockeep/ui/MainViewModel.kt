package com.app.dockeep.ui

import android.app.Activity
import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.app.dockeep.data.files.FilesRepository
import com.app.dockeep.data.preferences.DataStoreRepository
import com.app.dockeep.model.DocumentItem
import com.app.dockeep.utils.Constants.CONTENT_PATH_KEY
import com.app.dockeep.utils.Constants.DELETE_KEY
import com.app.dockeep.utils.Constants.FIRST_START_KEY
import com.app.dockeep.utils.Constants.SORT_TYPE_KEY
import com.app.dockeep.utils.Constants.THEME_KEY
import com.app.dockeep.utils.Helper.extractUris
import com.app.dockeep.utils.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class MainViewModel constructor(
    application: Application
) : AndroidViewModel(application) {
    companion object {
        val vaultRevision = MutableStateFlow(0L)
        val movingFiles = MutableStateFlow(false)
    }
    private fun vaultChanged() { vaultRevision.value += 1 }
    private val prefRepo: DataStoreRepository = com.app.dockeep.data.preferences.DataStoreRepositoryImpl(application)
    private val filesRepo: FilesRepository = com.app.dockeep.data.files.FilesRepositoryImpl(application)

   // var files by mutableStateOf(listOf<DocumentItem>())
    private val files = MutableStateFlow(listOf<DocumentItem>())
    val filess = files.asStateFlow()
   // val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    var folders = mutableStateOf(listOf<Pair<String, Uri>>())
    val loading = mutableStateOf(false)
    val fileError = mutableStateOf<String?>(null)
    var launched = false

    private val _theme = MutableStateFlow(ThemeMode.AUTO)
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    val firstStart = mutableStateOf(true)
    val deleteOriginal = mutableStateOf(true)

    init {
        getAppTheme()
        getFirstStart()
        getDeleteOriginal()

        // The visible pane loads its own location. An automatic root read here
        // can finish after that read and overwrite a newly opened directory.
    }

    suspend fun getContentPathUri(): String = com.app.dockeep.data.files.VaultFiles.uri(getApplication(), com.app.dockeep.data.files.VaultFiles.root(getApplication())).toString()

    fun getSortType(): String = runBlocking { prefRepo.getString(SORT_TYPE_KEY) ?: "名称升序" }

    fun setSortType(type: String) {
        viewModelScope.launch {
            prefRepo.putString(SORT_TYPE_KEY, type)
        }
    }

    fun getAppTheme() = runBlocking {
        prefRepo.getString(THEME_KEY)?.let { _theme.value = ThemeMode.valueOf(it) }
        getApplication<Application>().getSharedPreferences("vault_ui",0).edit().putString("theme",_theme.value.name).apply()
    }

    fun setAppTheme(th: ThemeMode) {
        viewModelScope.launch {
            _theme.value = th
            getApplication<Application>().getSharedPreferences("vault_ui",0).edit().putString("theme",th.name).apply()
            prefRepo.putString(THEME_KEY, th.name)
        }
    }

    fun getFirstStart() =
        runBlocking { firstStart.value = false }

    fun setFirstStart(bool: Boolean) {
        viewModelScope.launch {
            prefRepo.putBool(FIRST_START_KEY, bool)
            firstStart.value = bool
        }
    }

    fun getDeleteOriginal() =
        runBlocking { deleteOriginal.value = false }

    fun setDeleteOriginal(bool: Boolean) {
        viewModelScope.launch {
            prefRepo.putBool(DELETE_KEY, bool)
            deleteOriginal.value = bool
        }
    }

    suspend fun rootExists(): Boolean =
        filesRepo.pathExists(getContentPathUri()?.toUri() ?: Uri.EMPTY)

    private suspend fun resolveFolderUri(folderUri: String): Uri {
        val uriString = folderUri.ifBlank { getContentPathUri() ?: "" }
        return uriString.toUri()
    }

    private suspend fun setLoading(boolean: Boolean) {
        withContext(Dispatchers.Main) {
            loading.value = boolean
        }
    }

    private suspend fun displayMessage(message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(getApplication<Application>(), message, Toast.LENGTH_SHORT).show()
        }
    }

    fun setContentPathUri(result: ActivityResult) {
        if (result.resultCode != Activity.RESULT_OK) return
        val uri = result.data?.data ?: return
        launched = true
        viewModelScope.launch {
            filesRepo.setRootLocation(uri).toString().let {
                prefRepo.putString(CONTENT_PATH_KEY, it)
                setFirstStart(false)
                loadFiles(getContentPathUri() ?: "")
            }
        }
    }

    suspend fun loadFiles(folderUri: String = "") {
        try {
            if (files.value.isEmpty()) setLoading(true)
            val folder = resolveFolderUri(folderUri)
            val fileList = withContext(Dispatchers.IO) { filesRepo.listFilesInDirectory(folder) }
            withContext(Dispatchers.Main) {
                fileError.value = null
                files.value = fileList
                sortFiles(getSortType())
            }
            val folderList = withContext(Dispatchers.IO) {
                filesRepo.listAllDirectories(getContentPathUri().toUri())
            }
            withContext(Dispatchers.Main) { folders.value = folderList }
        } catch(e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch(e: Exception) {
            com.app.dockeep.CrashReport.record(getApplication(),e)
            withContext(Dispatchers.Main) {
                files.value = emptyList()
                fileError.value = "文件夹暂时无法读取：${e.message ?: "未知错误"}。文件仍保留在匣子中。"
            }
        } finally {
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) { loading.value = false }
        }
    }


    fun loadAndCopyFiles(folderUri: String = "", result: ActivityResult) {
        if (result.resultCode != Activity.RESULT_OK) return

        viewModelScope.launch(Dispatchers.IO) {
            setLoading(true)
            val uris = result.data?.extractUris() ?: return@launch
            val folder = if(folderUri.isBlank()) {
                val root = resolveFolderUri("")
                filesRepo.listFilesInDirectory(root).firstOrNull { it.isFolder && it.name == "待整理" }?.uri ?: filesRepo.createDirectory(root, "待整理")
            } else resolveFolderUri(folderUri)
            try { filesRepo.copyFilesToFolder(folder, uris, false); displayMessage("已收纳，外部原文件保留") } catch (e: Exception) { displayMessage("收纳未全部完成：${e.message}") }
            vaultChanged()
            loadFiles(resolveFolderUri(folderUri).toString())
        }
    }

    fun searchFiles(query: String, parent: String) {
        viewModelScope.launch {
            if (query.isNotBlank()) {
                val result = withContext(Dispatchers.IO) {
                    filesRepo.searchFiles(
                        query, getContentPathUri()?.toUri() ?: Uri.EMPTY
                    )
                }
                withContext(Dispatchers.Main) {
                    files.value = result.toList()
                }

            } else {
                loadFiles(parent)
            }
        }
    }

    fun createFolder(parent: String = "", name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val folder = resolveFolderUri(parent)
            try { filesRepo.createDirectory(folder, name) } catch(e: Exception) { displayMessage(e.message ?: "新建失败"); return@launch }
            vaultChanged()
            loadFiles(folder.toString())
            displayMessage("已新建")
        }
    }

    fun deleteFiles(folder: String, uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            setLoading(true)
            var ok=0; var failed=0
            try {
                val context=getApplication<Application>()
                val selected=uris.map { com.app.dockeep.data.files.VaultFiles.file(context,it) }.distinctBy { it.path }
                val top=selected.filter { f -> selected.none { p -> p!=f && f.path.startsWith(p.path+java.io.File.separator) } }
                top.forEach { f ->
                    try { filesRepo.deleteDocument(com.app.dockeep.data.files.VaultFiles.uri(context,f)); ok++ }
                    catch(e: Exception) { failed++ }
                }
                vaultChanged()
                loadFiles(folder)
                displayMessage("已移入回收站 $ok 项" + if(failed==0) "" else "，$failed 项未完成并保留原位")
            } catch(e: Exception) { displayMessage("未能移入回收站：${e.message}") }
            finally { setLoading(false) }
        }
    }

    fun zipFiles(folder: String, uris: List<Uri>, name:String) {
        viewModelScope.launch(Dispatchers.IO) {
            setLoading(true)
            val root = resolveFolderUri(folder)
            filesRepo.createArchive(root, "$name.zip",uris)
            loadFiles(folder)
            displayMessage("Archived successfully")
        }
    }

    fun sortFiles(type: String) {

        setSortType(type)

        when (type) {
            "名称升序" -> {
                files.value =
                    files.value.sortedWith(compareByDescending<DocumentItem> { it.isFolder }.thenBy { it.name.lowercase() }).toList()
            }

            "名称降序" -> {
                files.value =
                    files.value.sortedWith(compareByDescending<DocumentItem> { it.isFolder }.thenByDescending { it.name.lowercase() }).toList()
            }

            "大小从大到小" -> {
                files.value =
                    files.value.sortedWith(compareByDescending<DocumentItem> { it.isFolder }.thenByDescending { it.size  }).toList()
            }

            "大小从小到大" -> {
                files.value =
                    files.value.sortedWith(compareByDescending<DocumentItem> { it.isFolder }.thenBy { it.size  }).toList()
            }

            "时间从新到旧" -> {
                files.value =
                    files.value.sortedWith(compareByDescending<DocumentItem> { it.isFolder }.thenByDescending {  it.date  }).toList()
            }

            "时间从旧到新" -> {
                files.value =
                    files.value.sortedWith(compareByDescending<DocumentItem> { it.isFolder }.thenBy { it.date }).toList()
            }
        }
    }

    fun renameFile(folder: String = "", doc: Uri, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try { filesRepo.renameDocument(doc, name) } catch(e: Exception) { displayMessage(e.message ?: "重命名失败"); return@launch }
            vaultChanged()
            loadFiles(folder)
            displayMessage("已重命名")
        }
    }

    fun moveAcross(sourceFolder: String, uris: List<Uri>, destination: String) {
        if(uris.isEmpty() || !movingFiles.compareAndSet(false,true)) return
        val selected=uris.toList()
        loading.value=true
        viewModelScope.launch {
            try {
                val result=withContext(Dispatchers.IO) {
                    val context=getApplication<Application>()
                    val current=com.app.dockeep.data.files.VaultFiles.file(context,resolveFolderUri(sourceFolder))
                    val sources=selected.map { com.app.dockeep.data.files.VaultFiles.file(context,it) }
                    require(sources.none { it==current || current.path.startsWith(it.path+java.io.File.separator) }) {
                        "当前目录位于所选文件夹内，请先返回上级目录后移动"
                    }
                    val target=com.app.dockeep.data.files.VaultFiles.file(context,resolveFolderUri(destination))
                    com.app.dockeep.data.files.VaultMover(com.app.dockeep.data.files.VaultFiles.root(context),
                        com.app.dockeep.data.files.VaultFiles).move(sources,target)
                }
                val renamed=result.moved.count { it.renamed }
                val message=if(result.moved.isEmpty() && result.failures.isEmpty() && result.skipped.isNotEmpty())
                    "已在目标目录，无需移动"
                else buildString {
                    append("已移动 ${result.moved.size} 项")
                    if(renamed>0) append("，$renamed 项同名已自动改名")
                    if(result.skipped.isNotEmpty()) append("，${result.skipped.size} 项已在目标目录")
                    if(result.failures.isNotEmpty()) append("；${result.failures.size} 项未完成并保留原位：${result.failures.first().source.name}（${result.failures.first().reason}）")
                }
                displayMessage(message)
            } catch(e: kotlinx.coroutines.CancellationException) { throw e }
            catch(e: Exception) { displayMessage("移动未完成：${e.message}") }
            finally {
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) {
                    vaultChanged()
                    loading.value=false
                    movingFiles.value=false
                }
            }
        }
    }

    fun importFiles(
        uris: List<Uri>,
        folderUri: String = "",
        load: Boolean = true,
        remove: Boolean = false,
        src: String = ""
    ) {

        viewModelScope.launch(Dispatchers.IO) {
            setLoading(true)
            val folder = resolveFolderUri(folderUri)

            filesRepo.copyFilesToFolder(folder, uris)
            if (remove) uris.forEach { filesRepo.deleteDocument(it) }

            val src = resolveFolderUri(src)

            setLoading(false)

            if (load) loadFiles(folderUri)
            if (remove) loadFiles(src.toString())
        }
    }
}
