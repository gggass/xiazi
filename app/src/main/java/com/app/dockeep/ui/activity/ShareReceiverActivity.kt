package com.app.dockeep.ui.activity

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.app.dockeep.data.files.FilesRepositoryImpl
import com.app.dockeep.data.files.VaultFiles
import com.app.dockeep.utils.Helper.extractUris
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "正在收纳…请保持页面打开"; setPadding(32,80,32,32) })
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                val uris = intent.extractUris(); require(uris.isNotEmpty()) { "没有可读取的文件" }
                val repo = FilesRepositoryImpl(applicationContext)
                val root = VaultFiles.uri(applicationContext, VaultFiles.root(applicationContext))
                val pending = repo.createDirectoryIfMissing(root)
                repo.copyFilesToFolder(pending, uris, false)
            } }
            Toast.makeText(this@ShareReceiverActivity, result.fold({ "已收纳，原文件保留" }, { "收纳未全部完成：${it.message}" }), Toast.LENGTH_LONG).show()
            startActivity(Intent(this@ShareReceiverActivity, MainActivity::class.java))
            finish()
        }
    }
}
private suspend fun FilesRepositoryImpl.createDirectoryIfMissing(root: android.net.Uri): android.net.Uri =
    listFilesInDirectory(root).firstOrNull { it.isFolder && it.name == "待整理" }?.uri ?: createDirectory(root, "待整理")
