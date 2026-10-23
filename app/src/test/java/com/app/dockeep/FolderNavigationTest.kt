package com.app.dockeep

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.rememberNavController
import com.app.dockeep.data.files.VaultFiles
import com.app.dockeep.ui.navigation.NavGraph
import com.app.dockeep.ui.theme.DockeepTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34], application = AliasedFilesApplication::class)
class FolderNavigationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun openPendingFolderContainingTwoImageFormats() {
        val app: Application = RuntimeEnvironment.getApplication()
        val root = VaultFiles.root(app)
        root.deleteRecursively(); root.mkdirs()
        val pending = File(root,"待整理").apply { mkdirs() }
        val image = android.graphics.Bitmap.createBitmap(40,30,android.graphics.Bitmap.Config.ARGB_8888)
        image.eraseColor(android.graphics.Color.BLUE)
        File(pending,"照片.jpg").outputStream().use { check(image.compress(android.graphics.Bitmap.CompressFormat.JPEG,90,it)) }
        File(pending,"截图.png").outputStream().use { check(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)) }
        image.recycle()
        compose.setContent { DockeepTheme { NavGraph(rememberNavController(),false) } }
        compose.waitUntil(10000) { compose.onAllNodesWithText("待整理").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("待整理").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("照片.jpg").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("截图.png").assertIsDisplayed()
    }
}

/** Real devices can return a filesDir alias while canonicalFile resolves elsewhere. */
class AliasedFilesApplication : Application() {
    override fun getFilesDir(): File {
        val actual = super.getFilesDir()
        val alias = File(super.getCacheDir(), "ui-files-alias")
        if(!java.nio.file.Files.isSymbolicLink(alias.toPath())) {
            java.nio.file.Files.createSymbolicLink(alias.toPath(),actual.toPath())
        }
        return alias
    }
}
