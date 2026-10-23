package com.app.dockeep.ui.navigation

import com.app.dockeep.utils.Constants.FILES_ROUTE
import java.util.Base64

/** Opaque path arguments: no nested URI slashes, percent escapes or locale-specific text. */
object FolderRoute {
    private const val PREFIX = "pv1_"
    fun encode(value: String): String = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
    fun decode(value: String): String = if(value.startsWith(PREFIX))
        String(Base64.getUrlDecoder().decode(value.removePrefix(PREFIX)), Charsets.UTF_8) else value
    fun decodeUri(value: String): String = if(value.startsWith(PREFIX)) decode(value) else value.replace('|','%')
    fun create(uri: String, title: String) = "$FILES_ROUTE/${encode(title)}/${encode(uri)}"
}
