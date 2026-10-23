package com.app.dockeep.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun BackChevron(onClick: () -> Unit) {
    val color=MaterialTheme.colorScheme.onSurface
    IconButton(onClick=onClick,modifier=Modifier.semantics { contentDescription="返回" }) {
        Canvas(Modifier.size(24.dp)) {
            drawPath(Path().apply { moveTo(size.width*.65f,size.height*.2f); lineTo(size.width*.35f,size.height*.5f); lineTo(size.width*.65f,size.height*.8f) },color,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
        }
    }
}
