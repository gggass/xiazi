package com.app.dockeep.ui.screens.files.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.dockeep.model.DocumentItem
import com.app.dockeep.utils.Helper.getTimeAgo
import com.app.dockeep.utils.Helper.humanReadableSize

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FileListItem(
    item: DocumentItem,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    isSelected: Boolean,
    selectionMode: Boolean,
    addToSelected: (DocumentItem) -> Unit,
    removeFromSelected: (DocumentItem) -> Unit,
    onLongClick: () -> Unit,
    compact: Boolean = false
) {
    if(compact) {
        CompactFileItem(item,onClick,onMoreClick,isSelected,selectionMode,
            { if(isSelected) removeFromSelected(item) else addToSelected(item) },onLongClick)
        return
    }
    ListItem(
        colors = if(isSelected)  ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(0.3f)
        ) else ListItemDefaults.colors(),
        headlineContent = {
            Text(
                item.name,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                fontSize = TextUnit(16f, TextUnitType.Sp),
                fontWeight = if (item.isFolder) FontWeight.Bold else FontWeight.Normal
            )
        },
        supportingContent = {
            Text(
                if (item.isFolder) "文件夹" else runCatching { "${humanReadableSize(item.size?.coerceAtLeast(0) ?: 0)} • ${getTimeAgo(item.date ?: 0)}" }.getOrDefault("文件"),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                fontSize = TextUnit(12f, TextUnitType.Sp),
            )
        },
        leadingContent = {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    if(item.isFolder) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null
                        )
                    } else {
                        var ext = item.mimeType.substringAfter("/")
                        if(ext.length > 5 || ext.isEmpty()) ext = item.name.substringAfterLast(".")
                        Text(
                            text = ext,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

        },
        trailingContent = {
            if (selectionMode) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
            } else {
                IconButton(onClick = onMoreClick) { Icon(Icons.Default.MoreVert, "更多操作") }
            }
        },
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .padding(2.dp)
            .combinedClickable(
                onClick = {
                    if (selectionMode) {
                        if (isSelected) {
                            removeFromSelected(item)
                        } else {
                            addToSelected(item)
                        }
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    if (selectionMode) {
                        if (isSelected) {
                            removeFromSelected(item)
                        } else {
                            addToSelected(item)
                        }
                    } else {
                        onLongClick()
                    }
                },
            ),
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CompactFileItem(item: DocumentItem,onClick:()->Unit,onMore:()->Unit,
                            selected:Boolean,selectionMode:Boolean,onSelect:()->Unit,onLong:()->Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.background(if(selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick={if(selectionMode) onSelect() else onClick()},onLongClick={if(selectionMode) onSelect() else onLong()})
            .padding(start=8.dp,top=4.dp,bottom=4.dp),
        verticalAlignment=Alignment.CenterVertically) {
        Icon(if(item.isFolder) Icons.Default.Folder else androidx.compose.material.icons.Icons.Outlined.InsertDriveFile,
            null,Modifier.size(20.dp))
        androidx.compose.foundation.layout.Column(Modifier.weight(1f).padding(start=6.dp)) {
            Text(item.name,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=14.sp)
            Text(if(item.isFolder) "文件夹" else runCatching { humanReadableSize(item.size?:0) }.getOrDefault("文件"),
                maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(selectionMode) Icon(if(selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,null,Modifier.padding(12.dp).size(20.dp))
        else IconButton(onClick=onMore) { Icon(Icons.Default.MoreVert,"更多操作",Modifier.size(20.dp)) }
    }
}
