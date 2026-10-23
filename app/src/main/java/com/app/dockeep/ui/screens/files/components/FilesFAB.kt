package com.app.dockeep.ui.screens.files.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable

@Composable
fun FilesFAB(
    onClick: () -> Unit
) {
    ExtendedFloatingActionButton(onClick = onClick, icon = { Icon(Icons.Default.Add, null) }, text = { Text("收纳文件") })
}
