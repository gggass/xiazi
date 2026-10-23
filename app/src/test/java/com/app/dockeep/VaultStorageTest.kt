package com.app.dockeep

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.app.dockeep.data.files.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VaultStorageTest {
    private lateinit var app: Application
    private lateinit var source: File
    private lateinit var repo: FilesRepositoryImpl
    private lateinit var root: Uri
    private var advertisedSize = -1L
    private var failRead = false
    private val input = Uri.parse("content://test.source/item")
    @Before fun setup() {
        app = RuntimeEnvironment.getApplication()
        VaultFiles.root(app).deleteRecursively()
        source = File(app.cacheDir, "source.dat").apply { writeBytes(ByteArray(8193) { (it % 251).toByte() }) }
        advertisedSize = source.length(); failRead = false
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun getType(uri: Uri) = "application/octet-stream"
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?) =
                MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply { addRow(arrayOf("样本.dat",advertisedSize)) }
            override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
                if(failRead) throw java.io.FileNotFoundException("模拟拒绝读取")
                return ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = throw AssertionError("绝不能删除来源")
        }
        provider.attachInfo(app,ProviderInfo().apply { authority = "test.source"; exported = false })
        ShadowContentResolver.registerProviderInternal("test.source",provider)
        repo = FilesRepositoryImpl(app)
        root = VaultFiles.uri(app,VaultFiles.root(app))
    }
    @Test fun importPreservesBytesAndOriginalAndAvoidsNameCollision() = runBlocking {
        repo.copyFilesToFolder(root,listOf(input),false)
        repo.copyFilesToFolder(root,listOf(input),false)
        val files = repo.listFilesInDirectory(root)
        assertEquals(2,files.size)
        assertEquals(2,files.map { it.name }.distinct().size)
        files.forEach { assertArrayEquals(source.readBytes(),VaultFiles.file(app,it.uri).readBytes()) }
        assertTrue(source.exists())
    }
    @Test fun sizeMismatchDoesNotPublishPartialFile() = runBlocking {
        advertisedSize += 1
        val error = runCatching { repo.copyFilesToFolder(root,listOf(input),false) }.exceptionOrNull()
        assertNotNull(error); assertTrue(error!!.message!!.contains("文件大小变化"))
        assertTrue(repo.listFilesInDirectory(root).isEmpty())
        assertEquals(0,VaultFiles.root(app).listFiles()!!.size)
        assertTrue(source.exists())
    }
    @Test fun failedReadPreservesExistingVaultFile() = runBlocking {
        repo.copyFilesToFolder(root,listOf(input),false)
        failRead = true
        assertTrue(runCatching { repo.copyFilesToFolder(root,listOf(input),false) }.isFailure)
        assertEquals(1,repo.listFilesInDirectory(root).size)
        assertTrue(source.exists())
    }
    @Test fun canonicalDirectoryThroughFilesDirAliasStaysInVault() = runBlocking {
        // Android may expose /data/user/0 while canonicalFile resolves /data/data.
        val alias = File(app.cacheDir,"files-alias")
        java.nio.file.Files.deleteIfExists(alias.toPath())
        java.nio.file.Files.createSymbolicLink(alias.toPath(),app.filesDir.toPath())
        val aliased = object : android.content.ContextWrapper(app) {
            override fun getFilesDir(): File = alias
        }
        val aliasedRepo = FilesRepositoryImpl(aliased)
        val rootUri = VaultFiles.uri(aliased,VaultFiles.root(aliased))
        val expected = File(app.filesDir,"vault/待整理").apply { mkdirs() }.canonicalFile
        File(expected,"photo.jpg").writeBytes(byteArrayOf(1,2,3))
        val folder = aliasedRepo.listFilesInDirectory(rootUri).single()
        assertEquals(expected,VaultFiles.file(aliased,folder.uri))
        assertEquals("photo.jpg",aliasedRepo.listFilesInDirectory(folder.uri).single().name)
    }
    @Test fun traversalAndForeignAuthorityAreRejected() {
        assertTrue(runCatching { VaultFiles.file(app,Uri.Builder().scheme("content").authority(app.packageName+".files").appendPath("file").appendPath("../outside").build()) }.isFailure)
        assertTrue(runCatching { VaultFiles.file(app,input) }.isFailure)
        listOf("../a", ".", "..", "a/b", "a\\b", "").forEach { assertTrue(runCatching { VaultFiles.validName(it) }.isFailure) }
    }
    @Test fun symlinkOutsideVaultIsStillRejected() {
        val outside = File(app.cacheDir,"outside-vault").apply { mkdirs() }
        val link = File(VaultFiles.root(app),"link")
        java.nio.file.Files.createSymbolicLink(link.toPath(),outside.toPath())
        val uri = Uri.Builder().scheme("content").authority(app.packageName+".files").appendPath("file").appendPath("link").build()
        assertTrue(runCatching { VaultFiles.file(app,uri) }.isFailure)
        assertTrue(runCatching { VaultFiles.uri(app,link) }.isFailure)
    }
    @Test fun renameConflictPreservesBothFiles() = runBlocking {
        val folder = repo.createDirectory(root,"资料")
        val one = File(VaultFiles.file(app,folder),"one.txt").apply { writeText("one") }
        val two = File(one.parentFile,"two.txt").apply { writeText("two") }
        assertTrue(runCatching { repo.renameDocument(VaultFiles.uri(app,one),"two.txt") }.isFailure)
        assertEquals("one",one.readText()); assertEquals("two",two.readText())
    }
    @Test fun mainActivityStartsWithoutStoragePermission() {
        val controller = org.robolectric.Robolectric.buildActivity(com.app.dockeep.ui.activity.MainActivity::class.java).setup()
        assertNotNull(controller.get().window.decorView)
        with(com.app.dockeep.utils.Helper) {
            controller.get().shareDocument(listOf(input,Uri.parse("content://test.source/second")),listOf("text/plain","text/plain"))
        }
        val chooser = org.robolectric.Shadows.shadowOf(app).nextStartedActivity
        @Suppress("DEPRECATION")
        val outgoing = chooser.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)!!
        assertTrue(outgoing.flags and android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(2,outgoing.clipData!!.itemCount)
        controller.pause().stop().destroy()
    }
    @Test fun shareIntentCollectsStreamsAndDeduplicatesClipData() {
        val second = Uri.parse("content://test.source/second")
        val share = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE)
            .putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, arrayListOf(input,second))
        share.clipData = android.content.ClipData.newRawUri("",input)
        with(com.app.dockeep.utils.Helper) { assertEquals(listOf(input,second),share.extractUris()) }
    }
    @Test fun externalProviderOnlyAllowsReadOfSelectedFile() {
        val file = File(VaultFiles.root(app),"read.txt").apply { writeText("private") }
        val provider = VaultProvider()
        provider.attachInfo(app,ProviderInfo().apply { authority = app.packageName+".files"; exported = false; grantUriPermissions = true })
        assertTrue(runCatching { provider.openFile(VaultFiles.uri(app,file),"rw") }.isFailure)
        provider.openFile(VaultFiles.uri(app,file),"r").use { assertNotNull(it) }
    }
}
