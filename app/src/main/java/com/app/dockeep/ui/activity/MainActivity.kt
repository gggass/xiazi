package com.app.dockeep.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

import androidx.navigation.compose.rememberNavController
import com.app.dockeep.ui.MainViewModel
import com.app.dockeep.ui.navigation.NavGraph
import com.app.dockeep.ui.theme.DockeepTheme
import com.app.dockeep.utils.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.app.dockeep.CrashReport.clear(this)
        val notice = getSharedPreferences("vault_notice", MODE_PRIVATE)
        if(!notice.getBoolean("read",false)) android.app.AlertDialog.Builder(this)
            .setTitle("欢迎使用匣子简版")
            .setMessage("收纳会保留外部原文件。卸载或清除应用数据会删除匣子里的文件，请保留重要文件的外部副本。可在设置中创建 ZIP 备份。")
            .setPositiveButton("知道了") { _, _ -> notice.edit().putBoolean("read",true).apply() }
            .show()

        setContent {
            val mainVM: MainViewModel = androidx.lifecycle.viewmodel.compose.viewModel(viewModelStoreOwner = androidx.compose.ui.platform.LocalContext.current as ComponentActivity)
            val theme by mainVM.theme.collectAsState()
            val firstStart by mainVM.firstStart
            DockeepTheme(
                darkTheme = when (theme) {
                    ThemeMode.AUTO -> false
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                }
            ) {
                val navController = rememberNavController()
                NavGraph(navController, firstStart)
            }
        }
    }
}
