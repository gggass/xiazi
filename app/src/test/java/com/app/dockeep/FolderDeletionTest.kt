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
@Config(sdk=[28,34],application=AliasedFilesApplication::class)
class FolderDeletionTest {
    @get:Rule val compose=createComposeRule()
    @Test fun deleteFolderThroughMenuMovesItIntoTrash() {
        val app: Application=RuntimeEnvironment.getApplication()
        val root=VaultFiles.root(app); root.deleteRecursively(); root.mkdirs()
        File(app.filesDir,"vault-trash").deleteRecursively()
        File(root,"待整理").mkdirs()
        File(root,"待整理/a.txt").writeText("keep in trash")
        compose.setContent { DockeepTheme { NavGraph(rememberNavController(),false) } }
        compose.waitUntil(10000) { compose.onAllNodesWithText("待整理").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("更多操作").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("移入回收站").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.waitUntil(10000) { compose.onAllNodesWithText("确认").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("确认").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.waitUntil(10000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            com.app.dockeep.data.files.VaultTrash(app).entries().isNotEmpty()
        }
        assert(File(com.app.dockeep.data.files.VaultTrash(app).entries().single().payload,"a.txt").readText()=="keep in trash")
    }
}
