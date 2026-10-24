package com.app.dockeep

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.rememberNavController
import com.app.dockeep.data.files.VaultFiles
import com.app.dockeep.ui.navigation.NavGraph
import com.app.dockeep.ui.theme.DockeepTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,34],application=AliasedFilesApplication::class)
class CrossPaneMoveTest {
    @get:Rule val compose=createComposeRule()
    private fun inPane(text: String,pane: String)=compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(pane)))
    private fun ready(text: String,pane: String) {
        compose.waitUntil(10000) {
            compose.onAllNodes(hasText(text) and hasAnyAncestor(hasTestTag(pane))).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithText("正在处理，请稍候…").fetchSemanticsNodes().isEmpty()
        }
    }
    @Test fun selectedFileAndFolderMoveRightWithoutOverwriteAndFileCanMoveBackLeft() {
        val app: Application=RuntimeEnvironment.getApplication()
        val root=VaultFiles.root(app);root.deleteRecursively();root.mkdirs()
        val inbox=File(root,"待整理").apply { mkdir() }
        val target=File(root,"图片").apply { mkdir() }
        File(inbox,"a.txt").writeText("source")
        File(inbox,"目录/子目录").mkdirs();File(inbox,"目录/子目录/data.txt").writeText("nested")
        File(target,"a.txt").writeText("keep")
        File(target,"back.txt").writeText("back")
        compose.setContent { DockeepTheme { NavGraph(rememberNavController(),false) } }
        ready("待整理","left-pane")
        compose.onNodeWithContentDescription("展开右侧目录").performClick()
        ready("图片","right-pane")
        inPane("待整理","left-pane").performClick();ready("a.txt","left-pane")
        inPane("图片","right-pane").performClick();ready("back.txt","right-pane")
        inPane("a.txt","left-pane").performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        inPane("目录","left-pane").performClick()
        compose.onNodeWithText("移到右侧 →").performClick()
        compose.onNodeWithText("取消").performClick()
        assertEquals("source",File(inbox,"a.txt").readText());assertEquals("keep",File(target,"a.txt").readText())
        compose.onNodeWithText("移到右侧 →").performClick()
        compose.onNodeWithText("确认移动").performClick()
        ready("a (1).txt","right-pane")
        assertFalse(File(inbox,"a.txt").exists());assertFalse(File(inbox,"目录").exists())
        assertEquals("source",File(target,"a (1).txt").readText());assertEquals("keep",File(target,"a.txt").readText())
        assertEquals("nested",File(target,"目录/子目录/data.txt").readText())
        inPane("back.txt","right-pane").performSemanticsAction(SemanticsActions.OnLongClick) { it() }
        compose.onNodeWithText("← 移到左侧").performClick();compose.onNodeWithText("确认移动").performClick()
        ready("back.txt","left-pane")
        assertFalse(File(target,"back.txt").exists());assertEquals("back",File(inbox,"back.txt").readText())
    }
}
