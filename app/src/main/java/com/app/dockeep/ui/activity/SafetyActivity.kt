package com.app.dockeep.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.app.dockeep.ui.MainViewModel
import com.app.dockeep.ui.SafetyViewModel
import com.app.dockeep.ui.components.BackChevron
import com.app.dockeep.ui.theme.DockeepTheme
import com.app.dockeep.utils.Helper.humanReadableSize
import com.app.dockeep.utils.ThemeMode

class SafetyActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        setContent {
            val vm: SafetyViewModel=androidx.lifecycle.viewmodel.compose.viewModel()
            val themes: MainViewModel=androidx.lifecycle.viewmodel.compose.viewModel()
            val state by vm.state.collectAsState()
            val theme by themes.theme.collectAsState()
            var backupDialog by remember { mutableStateOf(false) }
            var includeTrash by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
            var backupAfterChoice by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
            var deleteDialog by remember { mutableStateOf(false) }
            var deleteId by remember { mutableStateOf<String?>(null) }
            val folderPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                if(uri!=null && vm.setDirectory(uri) && backupAfterChoice) vm.createBackup(includeTrash)
            }
            val zipPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null) vm.inspectSource(uri) }
            DockeepTheme(darkTheme=theme!=ThemeMode.LIGHT) {
                BackHandler(enabled=state.busy) { }
                Scaffold(topBar={ TopAppBar(title={ Text("回收站与备份") },navigationIcon={ BackChevron { if(!state.busy) finish() } }) }) { padding ->
                    LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        item {
                            Text("ZIP 备份",style=MaterialTheme.typography.titleLarge)
                            Text("备份会校验内容；恢复放进独立文件夹，不覆盖已有文件。ZIP 不加密。",style=MaterialTheme.typography.bodyMedium)
                        }
                        item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Button(onClick={ backupDialog=true },enabled=!state.busy) { Text("创建备份") }
                            OutlinedButton(onClick={ zipPicker.launch(arrayOf("*/*")) },enabled=!state.busy) { Text("从 ZIP 恢复") }
                        } }
                        item { TextButton(onClick={ backupAfterChoice=false; folderPicker.launch(null) },enabled=!state.busy) { Text(if(vm.directory==null) "选择备份保存目录" else "更换备份保存目录") } }
                        item { HorizontalDivider(); Text("回收站",style=MaterialTheme.typography.titleLarge)
                            Text("${state.entries.size} 项 · ${humanReadableSize(state.entries.sumOf { it.bytes })}\n不会自动清理。恢复遇到同名文件会安全重命名。") }
                        if(state.entries.isNotEmpty()) item { Row {
                            TextButton(onClick={ vm.restoreAll() },enabled=!state.busy) { Text("全部恢复") }
                            TextButton(onClick={ deleteId=null; deleteDialog=true },enabled=!state.busy) { Text("清空回收站") }
                        } }
                        if(state.entries.isEmpty()) item { Text("回收站是空的",style=MaterialTheme.typography.bodyMedium) }
                        items(state.entries,key={it.id}) { entry -> Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text(entry.name,style=MaterialTheme.typography.titleMedium)
                                Text("原位置：${entry.originalPath}\n${humanReadableSize(entry.bytes)}",style=MaterialTheme.typography.bodySmall)
                                Row {
                                    TextButton(onClick={vm.restore(entry.id)},enabled=!state.busy) { Text("恢复") }
                                    TextButton(onClick={deleteId=entry.id;deleteDialog=true},enabled=!state.busy) { Text("永久删除") }
                                }
                            }
                        } }
                        item { Spacer(Modifier.height(16.dp)); Text("大文件操作请保持此页打开。卸载或清除应用数据仍会删除仓库与回收站。",style=MaterialTheme.typography.bodySmall) }
                    }
                }
                if(backupDialog) AlertDialog(onDismissRequest={backupDialog=false},title={Text("创建 ZIP 备份")},text={ Column {
                    Text("备份全部仓库文件。请保存到你选择的目录。")
                    Row { Checkbox(checked=includeTrash,onCheckedChange={includeTrash=it}); Text("包含回收站",Modifier.padding(top=14.dp)) }
                } },confirmButton={TextButton(onClick={backupDialog=false;if(vm.directory==null) {backupAfterChoice=true;folderPicker.launch(null)} else vm.createBackup(includeTrash)}) {Text("开始备份")}},dismissButton={TextButton(onClick={backupDialog=false}){Text("取消")}})
                if(state.pending!=null && !state.busy) {
                    val plan=state.pending!!
                    AlertDialog(onDismissRequest={vm.cancelPending()},title={Text("确认独立恢复")},text={Text("${if(plan.pocketVault) "匣子备份" else "普通 ZIP"}\n${plan.files} 个文件，${plan.folders} 个文件夹\n预计解压 ${humanReadableSize(plan.bytes)}\n会创建“导入备份”文件夹，不合并、不覆盖。")},confirmButton={TextButton(onClick={vm.restorePending()}){Text("恢复")}},dismissButton={TextButton(onClick={vm.cancelPending()}){Text("取消")}})
                }
                if(deleteDialog) AlertDialog(onDismissRequest={deleteDialog=false},title={Text(if(deleteId==null) "清空回收站？" else "永久删除？")},text={Text("这一步会永久删除所选回收站内容，无法撤销。")},confirmButton={TextButton(onClick={deleteDialog=false;vm.delete(deleteId)}){Text("永久删除")}},dismissButton={TextButton(onClick={deleteDialog=false}){Text("取消")}})
                if(state.busy) AlertDialog(onDismissRequest={},title={Text("正在处理")},text={Column { Text(state.progress); Spacer(Modifier.height(12.dp)); LinearProgressIndicator(Modifier.fillMaxWidth()); Text("请保持此页打开") }},confirmButton={},dismissButton={TextButton(onClick={vm.cancel()}){Text("取消任务")}})
                if(!state.message.isNullOrEmpty() && !state.busy) AlertDialog(onDismissRequest={vm.clearMessage()},title={Text("操作结果")},text={Text(state.message.orEmpty())},confirmButton={TextButton(onClick={vm.clearMessage()}){Text("知道了")}})
            }
        }
    }
}

