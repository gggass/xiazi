package com.app.dockeep.data.files

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File

/** Only files below filesDir/vault may ever be addressed by a granted URI. */
object VaultFiles {
    fun root(context: Context): File = File(context.filesDir, "vault").canonicalFile.apply { mkdirs() }
    fun uri(context: Context, file: File): Uri {
        // Android's /data/user/0 alias and /data/data may denote the same directory.
        // Compute relative paths only after resolving BOTH sides; otherwise a normal
        // child can turn into a ../../ path and be rejected on the next read.
        val root = root(context)
        val canonical = file.canonicalFile
        require(canonical == root || canonical.path.startsWith(root.path + File.separator)) { "无效路径" }
        return Uri.Builder().scheme("content")
            .authority(context.packageName + ".files").appendPath("file")
            .appendPath(canonical.relativeTo(root).path).build()
    }
    fun file(context: Context, uri: Uri): File {
        require(uri.authority == context.packageName + ".files" && uri.pathSegments.firstOrNull() == "file") { "无效文件地址" }
        val root = root(context).canonicalFile
        val result = File(root, uri.pathSegments.getOrElse(1) { "" }).canonicalFile
        require(result == root || result.path.startsWith(root.path + File.separator)) { "无效路径" }
        return result
    }
    fun validName(name: String): String {
        require(name.isNotBlank() && name != "." && name != ".." && !name.startsWith(".") && name.length <= 180 && name.none { it == '/' || it == '\\' || it.code < 32 }) { "文件名为空、过长或包含不支持的字符" }
        return name
    }
    fun unique(parent: File, name: String): File {
        validName(name)
        var f = File(parent, name); var i = 1
        val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
        while (f.exists()) { f = File(parent, name.take(dot) + " ($i)" + name.drop(dot)); i++ }
        return f
    }
    fun mime(file: File): String = if (file.isDirectory) "vnd.android.document/directory" else
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
}
