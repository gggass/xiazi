package com.app.dockeep

import com.app.dockeep.ui.navigation.FolderRoute
import org.junit.Assert.*
import org.junit.Test

class FolderRouteTest {
    @Test fun uriAndTitleWithSpecialCharactersRoundTrip() {
        val title = "待整理 % # ? | 空格"
        val uri = "content://com.pocketvault.app.files/file/%E5%BE%85%E6%95%B4%E7%90%86%2Fa%20%25%23%3F%7C"
        assertEquals(title,FolderRoute.decode(FolderRoute.encode(title)))
        assertEquals(uri,FolderRoute.decodeUri(FolderRoute.encode(uri)))
        assertTrue(FolderRoute.create(uri,title).matches(Regex("files/[A-Za-z0-9_-]+/[A-Za-z0-9_-]+")))
    }
    @Test fun oldSavedNavigationArgumentsRemainReadable() {
        assertEquals("待整理",FolderRoute.decode("待整理"))
        assertEquals("content://a/file/%E5",FolderRoute.decodeUri("content://a/file/|E5"))
        assertEquals("",FolderRoute.decodeUri(""))
    }
}
