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
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,34],application=AliasedFilesApplication::class)
class DualPaneTest {
    @get:Rule val compose=createComposeRule()
    @Test fun sameVaultIndependentLocationsAndReopenRight() {
        val app: Application=RuntimeEnvironment.getApplication()
        val root=VaultFiles.root(app);root.deleteRecursively();root.mkdirs()
        val a=File(root,"待整理").apply{mkdir()};File(a,"left.txt").writeText("left")
        val b=File(root,"图片").apply{mkdir()};File(b,"right.txt").writeText("right")
        compose.setContent { DockeepTheme { NavGraph(rememberNavController(),false) } }
        compose.waitUntil(10000){compose.onAllNodesWithText("待整理").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("展开右侧目录").performClick()
        compose.onNodeWithTag("right-pane").assertExists()
        compose.waitUntil(10000){compose.onAllNodes(hasText("图片") and hasAnyAncestor(hasTestTag("right-pane"))).fetchSemanticsNodes().isNotEmpty() && compose.onAllNodesWithText("正在处理，请稍候…").fetchSemanticsNodes().isEmpty()}
        compose.onNode(hasContentDescription("目录操作") and hasAnyAncestor(hasTestTag("left-pane"))).performClick()
        compose.onNodeWithText("新建文件夹").performClick()
        compose.onNodeWithText("名称").performTextInput("同步目录")
        compose.onNodeWithText("完成").performClick()
        compose.waitUntil(10000){compose.onAllNodesWithText("同步目录").fetchSemanticsNodes().size==2}
        compose.waitUntil(10000){compose.onAllNodes(hasText("图片") and hasAnyAncestor(hasTestTag("right-pane"))).fetchSemanticsNodes().isNotEmpty()}
        compose.onNode(hasText("图片") and hasAnyAncestor(hasTestTag("right-pane"))).performClick()
        compose.waitUntil(10000){compose.onAllNodesWithText("right.txt").fetchSemanticsNodes().isNotEmpty() && compose.onAllNodesWithText("正在处理，请稍候…").fetchSemanticsNodes().isEmpty()}
        compose.onNode(hasText("待整理") and hasAnyAncestor(hasTestTag("left-pane"))).performScrollTo().performClick()
        try {
            compose.waitUntil(10000){compose.onAllNodesWithText("left.txt").fetchSemanticsNodes().isNotEmpty()}
        } catch(e: Throwable) { println(compose.onNodeWithTag("left-pane").printToString()); throw e }
        compose.onNodeWithText("right.txt").assertExists()
        compose.onNodeWithContentDescription("收起右侧目录").performClick()
        compose.onNodeWithTag("right-pane").assertDoesNotExist()
        compose.onNodeWithText("left.txt").assertExists()
        compose.onNodeWithContentDescription("展开右侧目录").performClick()
        compose.waitUntil(10000){compose.onAllNodesWithText("right.txt").fetchSemanticsNodes().isNotEmpty() && compose.onAllNodesWithText("正在处理，请稍候…").fetchSemanticsNodes().isEmpty()}
        assertEquals("left",File(a,"left.txt").readText());assertEquals("right",File(b,"right.txt").readText())
    }
}
