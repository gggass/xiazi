package com.app.dockeep

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class RetiredCrashReportTest {
    @Test fun removesLegacyReportWithoutTouchingVaultOrWritingNewReport() {
        val app: Application=RuntimeEnvironment.getApplication()
        val report=File(app.filesDir,"last-error.txt").apply{writeText("old error")}
        val kept=File(com.app.dockeep.data.files.VaultFiles.root(app),"keep.txt").apply{writeText("data")}
        CrashReport.clear(app);assertFalse(report.exists());assertEquals("data",kept.readText())
        CrashReport.record(app,IllegalArgumentException("test"));assertFalse(report.exists())
    }
}
