package com.app.dockeep.ui.screens.files.components

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesTopBar(title: String, displayBackIcon: Boolean, scrollBehaviour: TopAppBarScrollBehavior, onCreateFolder:()->Unit, onGoBack: () -> Unit, onNavigateSettings:()->Unit, onSort:()->Unit,
                compact: Boolean=false, onToggleRight: (() -> Unit)?=null, rightExpanded: Boolean=false) {
    var menu by remember { mutableStateOf(false) }
    Column {
    TopAppBar(scrollBehavior=scrollBehaviour,
        colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background,
            scrolledContainerColor=MaterialTheme.colorScheme.background,
            actionIconContentColor=MaterialTheme.colorScheme.onSurfaceVariant,
            navigationIconContentColor=MaterialTheme.colorScheme.primary),
        title={ if(!compact) Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis) },
        navigationIcon={ if(displayBackIcon) com.app.dockeep.ui.components.BackChevron(onGoBack) },
        actions={
            if(onToggleRight!=null) IconButton(onClick=onToggleRight) {
                Icon(if(rightExpanded) Icons.Outlined.CloseFullscreen else Icons.Outlined.ViewWeek,
                    if(rightExpanded) "收起右侧目录" else "展开右侧目录",
                    tint=if(rightExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(compact) {
                IconButton(onClick={menu=true}) { Icon(Icons.Outlined.MoreVert,"目录操作") }
                DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                    DropdownMenuItem(text={Text("新建文件夹")},onClick={menu=false;onCreateFolder()})
                    DropdownMenuItem(text={Text("排序")},onClick={menu=false;onSort()})
                    DropdownMenuItem(text={Text("设置")},onClick={menu=false;onNavigateSettings()})
                }
            } else {
                IconButton(onClick=onCreateFolder) { Icon(Icons.Outlined.CreateNewFolder,"新建文件夹") }
                IconButton(onClick=onNavigateSettings) { Icon(Icons.Outlined.Settings,"设置") }
                IconButton(onClick=onSort) { Icon(Icons.AutoMirrored.Outlined.Sort,"排序") }
            }
        })
    if(compact) Text(title,modifier=Modifier.fillMaxWidth().padding(start=8.dp,end=8.dp,bottom=8.dp),
        style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}

