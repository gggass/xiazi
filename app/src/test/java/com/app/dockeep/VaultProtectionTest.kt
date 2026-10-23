package com.app.dockeep

import android.app.Application
import com.app.dockeep.data.files.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class VaultProtectionTest {
    private lateinit var app: Application
    private lateinit var vault: File
    private lateinit var trash: VaultTrash
    private lateinit var backup: VaultBackup
    @Before fun setup() {
        app=RuntimeEnvironment.getApplication()
        vault=VaultFiles.root(app); vault.deleteRecursively(); vault.mkdirs()
        File(app.filesDir,"vault-trash").deleteRecursively()
        app.filesDir.listFiles().orEmpty().filter { it.name.startsWith(".restore-") }.forEach { it.deleteRecursively() }
        trash=VaultTrash(app); backup=VaultBackup(app)
    }
    private fun source(name: String, text: String="original"): File = File(vault,name).apply { parentFile!!.mkdirs(); writeText(text) }
    private fun zip(vararg files: Pair<String,ByteArray>): File = File.createTempFile("test-zip-",".zip",app.cacheDir).apply {
        ZipOutputStream(outputStream()).use { z -> files.forEach { (name,bytes) -> z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry() } }
    }
    private fun restored(): List<File> = vault.listFiles().orEmpty().filter { it.name.startsWith("导入备份-") }
    private fun stages(): List<File> = app.filesDir.listFiles().orEmpty().filter { it.name.startsWith(".restore-") }
    @Test fun trashRestorePreservesBytesAndRenamesOnCollision() {
        val original=source("资料/a.txt","private bytes")
        val entry=trash.move(VaultFiles.uri(app,original))
        assertFalse(original.exists()); assertEquals(1,trash.entries().size)
        source("资料/a.txt","newer")
        val result=trash.restore(entry.id)
        assertEquals("private bytes",result.readText()); assertEquals("a (1).txt",result.name)
        assertEquals("newer",original.readText()); assertTrue(trash.entries().isEmpty())
    }
    @Test fun wholeFolderRoundTripPreservesNestedStructure() {
        val a=source("目录/子目录/a.bin","abc"); source("目录/b.txt","def")
        val entry=trash.move(VaultFiles.uri(app,File(vault,"目录")))
        assertFalse(a.exists()); assertTrue(trash.entries().single().payload.isDirectory)
        trash.restore(entry.id)
        assertEquals("abc",a.readText()); assertEquals("def",File(vault,"目录/b.txt").readText())
    }
    @Test fun permanentDeleteIsScopedToTrash() {
        val keep=source("keep.txt","keep")
        val item=trash.move(VaultFiles.uri(app,source("remove.txt")))
        trash.permanentlyDelete(item.id)
        assertTrue(trash.entries().isEmpty()); assertEquals("keep",keep.readText())
        assertTrue(runCatching { trash.permanentlyDelete("../vault") }.isFailure)
    }
    @Test fun missingTrashMetadataStillPreservesAndRestoresPayload() {
        val entry=trash.move(VaultFiles.uri(app,source("metadata.txt","saved")))
        File(entry.payload.parentFile,"entry.json").delete()
        assertEquals(1,trash.entries().size)
        assertEquals("saved",trash.restore(entry.id).readText())
    }
    @Test fun fullBackupAndIndependentRestorePreserveFilesTrashAndExistingVault() {
        source("照片/原图.jpg","photo"); File(vault,"空文件夹").mkdirs()
        val entry=trash.move(VaultFiles.uri(app,source("删除的文件.txt","trash data")))
        val archive=File(app.cacheDir,"full.zip")
        backup.build(archive,true)
        ZipFile(archive).use { z -> assertNotNull(z.getEntry("文件/照片/原图.jpg")); assertNotNull(z.getEntry("回收站/${entry.id}/content")) }
        source("照片/原图.jpg","new original")
        val result=backup.restore(archive,backup.inspect(archive))
        assertEquals("photo",File(result,"文件/照片/原图.jpg").readText())
        assertEquals("trash data",File(result,"回收站/${entry.id}/content").readText())
        assertTrue(File(result,"文件/空文件夹").isDirectory)
        assertTrue(File(result,VaultBackup.RESTORE_MARKER).isFile)
        assertEquals("new original",File(vault,"照片/原图.jpg").readText())
        assertEquals(1,trash.entries().size)
    }
    @Test fun backupCanExcludeTrashAndCanBeBackedUpAgainAfterRestore() {
        source("a.txt","hello")
        trash.move(VaultFiles.uri(app,source("b.txt","hidden")))
        val archive=File(app.cacheDir,"without-trash.zip"); backup.build(archive,false)
        ZipFile(archive).use { assertTrue(it.entries().asSequence().none { e -> e.name.startsWith("回收站/") }) }
        backup.restore(archive,backup.inspect(archive))
        backup.build(File(app.cacheDir,"again.zip"),true)
        assertTrue(File(app.cacheDir,"again.zip").length()>0)
    }
    @Test fun ordinaryZipRestoresSeparatelyAndNeverOverwrites() {
        val original=source("a.txt","keep")
        val archive=zip("a.txt" to "imported".toByteArray(),"dir/b.txt" to "b".toByteArray())
        val one=backup.restore(archive,backup.inspect(archive)); val two=backup.restore(archive,backup.inspect(archive))
        assertNotEquals(one,two); assertEquals("keep",original.readText()); assertEquals("imported",File(one,"a.txt").readText())
    }
    @Test fun traversalAbsoluteAndConflictingPathsAreRejected() {
        for(path in listOf("../escape.txt","/absolute","..\\outside","a/../../bad","C:/bad")) {
            val archive=zip(path to byteArrayOf(1))
            assertTrue(path,runCatching { backup.inspect(archive) }.isFailure)
        }
        val conflict=zip("a/b.txt" to byteArrayOf(1),"a" to byteArrayOf(2))
        assertTrue(runCatching { backup.restore(conflict,backup.inspect(conflict)) }.isFailure)
        assertTrue(restored().isEmpty()); assertTrue(stages().isEmpty())
    }
    @Test fun incorrectHashFailsWithoutPublishingOrChangingExistingFiles() {
        val keep=source("keep.txt","keep")
        val manifest=JSONObject().put("format","PocketVault").put("version",1).put("files",JSONArray().put(
            JSONObject().put("path","文件/a.txt").put("size",3).put("sha256","0".repeat(64))))
        val archive=zip("文件/a.txt" to "abc".toByteArray(),"manifest.json" to manifest.toString().toByteArray())
        assertTrue(runCatching { backup.restore(archive,backup.inspect(archive)) }.isFailure)
        assertEquals("keep",keep.readText()); assertTrue(restored().isEmpty()); assertTrue(stages().isEmpty())
    }
    @Test fun corruptedZipCrcFailsAndCleansItsOwnStagingDirectory() {
        val payload="UNIQUE_RAW_PAYLOAD_abc123".toByteArray()
        val archive=File(app.cacheDir,"bad-crc.zip")
        ZipOutputStream(archive.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("a.txt").apply { method=ZipEntry.STORED; size=payload.size.toLong(); compressedSize=size; crc=CRC32().apply { update(payload) }.value })
            z.write(payload); z.closeEntry()
        }
        val bytes=archive.readBytes(); val offset=bytes.indices.first { i -> i+payload.size<=bytes.size && bytes.copyOfRange(i,i+payload.size).contentEquals(payload) }
        bytes[offset]=(bytes[offset].toInt() xor 1).toByte(); archive.writeBytes(bytes)
        assertTrue(runCatching { backup.restore(archive,backup.inspect(archive)) }.isFailure)
        assertTrue(restored().isEmpty()); assertTrue(stages().isEmpty())
    }
    @Test fun cancellationNeverPublishesHalfRestoredFolder() {
        source("keep.txt","keep")
        val archive=zip("a.txt" to "a".toByteArray(),"b.txt" to "b".toByteArray())
        var count=0
        assertTrue(runCatching { backup.restore(archive,backup.inspect(archive)) { if(++count==2) throw kotlinx.coroutines.CancellationException() } }.isFailure)
        assertTrue(restored().isEmpty()); assertTrue(stages().isEmpty()); assertEquals("keep",File(vault,"keep.txt").readText())
    }
    @Test fun interruptedBackupDoesNotDeleteVaultOrTrash() {
        val keep=source("keep.txt","keep"); val entry=trash.move(VaultFiles.uri(app,source("trash.txt","trash")))
        val archive=File(app.cacheDir,"cancelled.zip")
        assertTrue(runCatching { backup.build(archive,true) { throw kotlinx.coroutines.CancellationException() } }.isFailure)
        assertFalse(archive.exists()); assertEquals("keep",keep.readText()); assertTrue(entry.payload.exists())
    }
    @Test fun suspiciousExpansionIsRejectedBeforeExtracting() {
        val archive=zip("large.txt" to byteArrayOf(1))
        val bytes=archive.readBytes()
        val index=bytes.indices.first { i -> i+4<bytes.size && bytes[i]==0x50.toByte() && bytes[i+1]==0x4b.toByte() && bytes[i+2]==1.toByte() && bytes[i+3]==2.toByte() }
        // Central directory claims 1 GiB, but the archive is tiny.
        for(i in 0..3) bytes[index+24+i]=((1L shl 30) shr (8*i)).toByte()
        archive.writeBytes(bytes)
        assertTrue(runCatching { backup.inspect(archive) }.isFailure); assertTrue(restored().isEmpty())
    }
}
