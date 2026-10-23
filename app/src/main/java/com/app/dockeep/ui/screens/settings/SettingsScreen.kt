package com.app.dockeep.ui.screens.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.app.dockeep.ui.MainViewModel
import com.app.dockeep.utils.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onGoBack: () -> Unit) {
    val vm: MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel(viewModelStoreOwner = LocalContext.current as ComponentActivity)
    val theme by vm.theme.collectAsState()
    val context=LocalContext.current
    Scaffold(topBar = { TopAppBar(title = { Text("设置") }, navigationIcon = { com.app.dockeep.ui.components.BackChevron(onGoBack) }) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("匣子 · 简版 0.2.1", style = MaterialTheme.typography.headlineSmall)
            Text("文件保存在应用内部，收纳始终保留外部原文件。无需联网，无需全盘访问权限。")
            HorizontalDivider()
            Text("界面配色", style = MaterialTheme.typography.titleMedium)
            listOf(ThemeMode.LIGHT to "暖白 · 简约暖色", ThemeMode.DARK to "蓝白 · 简约蓝色").forEach { (mode, name) ->
                Row(Modifier.fillMaxWidth().clickable { vm.setAppTheme(mode) }) {
                    RadioButton(selected = (if(theme == ThemeMode.AUTO) ThemeMode.LIGHT else theme) == mode, onClick = { vm.setAppTheme(mode) })
                    Text(name, Modifier.padding(top = 13.dp))
                }
            }
            HorizontalDivider()
            Button(onClick={context.startActivity(android.content.Intent(context,com.app.dockeep.ui.activity.SafetyActivity::class.java))}) { Text("回收站与 ZIP 备份") }
            Text("请保留原文件", style = MaterialTheme.typography.titleMedium)
            Text("卸载应用或清除应用数据会删除匣子内全部文件。请定期将 ZIP 备份保存到外部目录；普通 ZIP 不加密。")
            Text("使用其他应用打开或分享时，对方可读取并保存所选文件。")
            HorizontalDivider()
            Text("基于 mattgdot/Dockeep 修改 · GPL-3.0\n保留原项目版权与许可证；本版完整修改源码随 APK 提供。", style = MaterialTheme.typography.bodySmall)
        }
    }
}
