package com.app.dockeep.ui.activity

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.app.dockeep.data.files.VaultFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lightweight read-only preview. Never executes HTML or SVG scripts. */
class PreviewActivity : ComponentActivity() {
    private var pdf: PdfRenderer? = null
    private var bitmap: Bitmap? = null
    private var page = 0
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window,false)
        val blue = getSharedPreferences("vault_ui",MODE_PRIVATE).getString("theme","") != "LIGHT"
        val background = if(blue) Color.rgb(247,249,253) else Color.rgb(250,247,241)
        val root = LinearLayout(this).apply { id = com.app.dockeep.R.id.preview_root; orientation = LinearLayout.VERTICAL; setBackgroundColor(background) }
        setContentView(root)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsetsIgnoringVisibility(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
            // Before API 30, compat navigation-bar inference can be clamped by the
            // decor root's stable insets. This read-only screen has no IME: retain
            // the platform system-window values as a lower bound on older devices.
            @Suppress("DEPRECATION")
            if (android.os.Build.VERSION.SDK_INT < 30) {
                view.setPadding(maxOf(bars.left,insets.systemWindowInsetLeft),maxOf(bars.top,insets.systemWindowInsetTop),maxOf(bars.right,insets.systemWindowInsetRight),maxOf(bars.bottom,insets.systemWindowInsetBottom))
            } else view.setPadding(bars.left,bars.top,bars.right,bars.bottom)
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(root)
        androidx.core.view.WindowCompat.getInsetsController(window,root).apply {
            isAppearanceLightStatusBars=true; isAppearanceLightNavigationBars=true
        }
        fun dp(value: Int) = (value*resources.displayMetrics.density).toInt()
        val toolbar = LinearLayout(this).apply { id=com.app.dockeep.R.id.preview_toolbar; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(4),0,dp(12),0) }
        val back = ImageButton(this).apply {
            id=com.app.dockeep.R.id.preview_back
            setImageResource(com.app.dockeep.R.drawable.ic_back_chevron)
            contentDescription="返回"; setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { finish() }
        }
        toolbar.addView(back,LinearLayout.LayoutParams(dp(48),dp(48)))
        val title = TextView(this).apply { id=com.app.dockeep.R.id.preview_title; textSize=16f; setTextColor(Color.rgb(48,46,44)); maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.MIDDLE }
        toolbar.addView(title,LinearLayout.LayoutParams(0,-2,1f))
        root.addView(toolbar,LinearLayout.LayoutParams(-1,dp(56)))
        val file = runCatching { VaultFiles.file(this, requireNotNull(intent.data)) }.getOrNull()
        if(file == null || !file.isFile) { title.text="文件不可用"; return }
        title.text=file.name
        val message = TextView(this).apply { text = "正在读取…" }; root.addView(message)
        lifecycleScope.launch {
            try {
                if(intent.type == "application/pdf") {
                    pdf = PdfRenderer(android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY))
                    root.removeView(message)
                    val image = ImageView(this@PreviewActivity).apply { adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER }
                    root.addView(image, LinearLayout.LayoutParams(-1,0,1f))
                    val controls = LinearLayout(this@PreviewActivity).apply { gravity = Gravity.CENTER }
                    val label = TextView(this@PreviewActivity)
                    fun showPage() {
                        val renderer = pdf ?: return
                        renderer.openPage(page).use { p ->
                            val w = 1000; val h = (w.toDouble() * p.height / p.width).toInt().coerceIn(1,3000)
                            val next = Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
                            next.eraseColor(Color.WHITE); p.render(next,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            image.setImageBitmap(next); bitmap?.recycle(); bitmap = next
                        }
                        label.text = " ${page + 1} / ${renderer.pageCount} "
                    }
                    controls.addView(Button(this@PreviewActivity).apply { text = "上一页"; setOnClickListener { if(page > 0) { page--; showPage() } } })
                    controls.addView(label)
                    controls.addView(Button(this@PreviewActivity).apply { text = "下一页"; setOnClickListener { if(page + 1 < (pdf?.pageCount ?: 0)) { page++; showPage() } } })
                    root.addView(controls); showPage()
                } else if(intent.type?.startsWith("image/") == true) {
                    val result = withContext(Dispatchers.IO) {
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.path, opts)
                        var sample = 1
                        while(opts.outWidth / sample > 1800 || opts.outHeight / sample > 1800) sample *= 2
                        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
                    }
                    requireNotNull(result) { "该图片格式暂不支持内置预览，请使用其他应用打开" }
                    bitmap = result; root.removeView(message)
                    root.addView(ImageView(this@PreviewActivity).apply { setImageBitmap(result); scaleType = ImageView.ScaleType.FIT_CENTER }, LinearLayout.LayoutParams(-1,0,1f))
                } else {
                    val text = withContext(Dispatchers.IO) {
                        file.inputStream().use { String(it.takeBytes(1024 * 1024), Charsets.UTF_8) } + if(file.length() > 1024*1024) "\n\n（简版仅显示前 1MB）" else ""
                    }
                    root.removeView(message)
                    root.addView(ScrollView(this@PreviewActivity).apply { addView(TextView(this@PreviewActivity).apply { this.text = text; textSize = 15f; setTextIsSelectable(true); setPadding(8,8,8,24) }) }, LinearLayout.LayoutParams(-1,0,1f))
                }
            } catch (e: Exception) { message.text = "无法预览：${e.message}"; if(message.parent == null) root.addView(message) }
        }
    }
    override fun onDestroy() { pdf?.close(); bitmap?.recycle(); super.onDestroy() }
}

private fun java.io.InputStream.takeBytes(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(8192)
    while(out.size() < limit) { val n = read(buf,0,minOf(buf.size,limit-out.size())); if(n < 0) break; out.write(buf,0,n) }
    return out.toByteArray()
}

