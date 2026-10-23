package com.app.dockeep

import android.app.Application
import android.content.Intent
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.app.dockeep.data.files.VaultFiles
import com.app.dockeep.ui.activity.PreviewActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,34])
class PreviewLayoutTest {
    @Test fun chevronIsSmallAndToolbarAvoidsSystemBars() {
        val app: Application=RuntimeEnvironment.getApplication()
        val file=File(VaultFiles.root(app),"很长的图片文件名称.txt").apply { writeText("hello") }
        val controller=Robolectric.buildActivity(PreviewActivity::class.java,Intent(app,PreviewActivity::class.java).setDataAndType(VaultFiles.uri(app,file),"text/plain")).setup()
        val activity=controller.get()
        val root=activity.findViewById<android.view.View>(R.id.preview_root)
        ViewCompat.dispatchApplyWindowInsets(root,WindowInsetsCompat.Builder().setStableInsets(Insets.of(0,32,0,24)).setSystemWindowInsets(Insets.of(0,32,0,24)).setInsets(WindowInsetsCompat.Type.systemBars(),Insets.of(0,32,0,24)).build())
        assertEquals(32,root.paddingTop); assertEquals(24,root.paddingBottom)
        val back=activity.findViewById<ImageButton>(R.id.preview_back)
        assertEquals("返回",back.contentDescription)
        assertTrue(back.layoutParams.width<=48*activity.resources.displayMetrics.density+1)
        assertEquals(file.name,activity.findViewById<TextView>(R.id.preview_title).text.toString())
        back.performClick(); assertTrue(activity.isFinishing)
        controller.pause().stop().destroy()
    }
}
