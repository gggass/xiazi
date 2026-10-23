package com.app.dockeep

import android.content.Context
import java.io.File

/** Legacy diagnostics retired. Only deletes its own old report, never vault content. */
object CrashReport {
    fun clear(context: Context) { runCatching { File(context.filesDir,"last-error.txt").delete() } }
    @Suppress("UNUSED_PARAMETER")
    fun record(context: Context,error: Throwable) { clear(context) }
}
