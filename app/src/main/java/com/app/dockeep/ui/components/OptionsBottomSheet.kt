package com.app.dockeep.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import com.app.dockeep.model.DocumentItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionsBottomSheet(
    items: List<DocumentItem>,
    onDismiss: () -> Unit,
    onOpen: (DocumentItem) -> Unit,
    onShare: (List<DocumentItem>) -> Unit,
    onDelete: (List<DocumentItem>) -> Unit,
    onRename: (DocumentItem) -> Unit,
    onMove: (List<DocumentItem>, Boolean) -> Unit,
    onCompress:(List<DocumentItem>) -> Unit,
    onExport: (DocumentItem) -> Unit,
    onOpenExternal: (DocumentItem) -> Unit,
    transferLabel: String? = null,
    transferToRight: Boolean = true,
    onTransfer: ((List<DocumentItem>) -> Unit)? = null
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column {
            if (items.size == 1) {
                if (!items[0].isFolder) {
                    OptionsListItem("导出副本", Icons.Outlined.Share) {
                        onExport(items[0]); onDismiss()
                    }
                    OptionsListItem("使用其他应用打开", Icons.Outlined.OpenWith) {
                        onOpenExternal(items[0]); onDismiss()
                    }
                    OptionsListItem("打开预览", Icons.Outlined.OpenWith) {
                        onOpen(items[0])
                        onDismiss()
                    }
                }
                OptionsListItem("重命名", Icons.Outlined.Edit) {
                    onRename(items[0])
                    onDismiss()
                }
            }
            if (!items.any { it.isFolder }) {
                OptionsListItem("分享", Icons.Outlined.Share) {
                    onShare(items)
                    onDismiss()
                }
            }

            if(transferLabel!=null && onTransfer!=null) {
                OptionsListItem(transferLabel, if(transferToRight) Icons.AutoMirrored.Outlined.ArrowForward else Icons.AutoMirrored.Outlined.ArrowBack) {
                    onTransfer(items); onDismiss()
                }
            }
            OptionsListItem("移入回收站", Icons.Outlined.Delete) { onDelete(items); onDismiss() }
        }
    }
}
