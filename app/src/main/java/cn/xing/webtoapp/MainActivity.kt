package cn.xing.webtoapp

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import android.util.Log
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.progressindicator.LinearProgressIndicator
import android.widget.RadioButton
import com.google.android.material.textfield.TextInputEditText
import java.io.File

class MainActivity : Activity() {
    private lateinit var urlv: TextInputEditText
    private lateinit var namev: TextInputEditText
    private lateinit var vername: TextInputEditText
    private lateinit var jscb: MaterialCheckBox
    private lateinit var logv: TextView
    private lateinit var gobtn: MaterialButton
    private lateinit var installBtn: MaterialButton
    private lateinit var shareBtn: MaterialButton
    private lateinit var splashText: TextView
    private var outApk: File? = null
    private var publicUriStr: String? = null
    private var mainShown = false
    private lateinit var service: BuildService
    private var lastStage = ""

    private fun log(msg: String) {
        runOnUiThread {
            if (mainShown) {
                logv.text = msg
                val sv = logv.parent.parent as ScrollView
                sv.post { sv.fullScroll(View.FOCUS_DOWN) }
            } else {
                splashText.text = msg
            }
        }
    }

    private fun stage(s: BuildStatus, p: Double) {
        lastStage = s.label
        log(s.withPercent(p))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 黑匣子：任何未捕获崩溃写入 crash.txt，方便离线排查
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                File(getExternalFilesDir(null), "crash.txt")
                    .writeText(Log.getStackTraceString(e))
            } catch (_: Exception) {}
            prev?.uncaughtException(t, e)
        }
        try {
            initUi()
        } catch (e: Throwable) {
            val trace = Log.getStackTraceString(e)
            try {
                File(getExternalFilesDir(null), "crash.txt").writeText(trace)
            } catch (_: Exception) {}
            val tv = TextView(this)
            tv.text = "启动崩溃:\n${e.message}\n详情已写入crash.txt"
            tv.setPadding(40, 80, 40, 40)
            setContentView(tv)
        }
    }

    private fun initUi() {
        service = BuildService(this, { s, p -> stage(s, p) }, { msg -> log(msg) })

        if (service.toolsReady()) {
            showMain()
        } else {
            setContentView(R.layout.activity_splash)
            splashText = findViewById(R.id.splashtext)
            Thread {
                try {
                    service.prepareTools()
                    showMain()
                } catch (e: Exception) {
                    log("出错: ${e.message}")
                }
            }.start()
        }
    }

    private fun showMain() {
        runOnUiThread {
            mainShown = true
            setContentView(R.layout.activity_main)
            urlv = findViewById(R.id.url)
            namev = findViewById(R.id.appname)
            vername = findViewById(R.id.vername)
            jscb = findViewById(R.id.js)
            gobtn = findViewById(R.id.go)
            logv = findViewById(R.id.log)
            installBtn = findViewById(R.id.install)
            shareBtn = findViewById(R.id.share)
            installBtn.setOnClickListener { outApk?.let { install(it) } }
            shareBtn.setOnClickListener { outApk?.let { share(it) } }
            val btReleaseBtn = findViewById<RadioButton>(R.id.bt_release)
            val btDebugBtn = findViewById<RadioButton>(R.id.bt_debug)

            logv.text = getString(R.string.log_idle)

            gobtn.setOnClickListener {
                var url = urlv.text.toString().trim()
                var appname = namev.text.toString().trim()
                // 名称框误填网址的兜底：自动纠正（读屏用户两框易粘错）
                if (appname.matches("^https?://.*".toRegex()) || appname.contains("/")) {
                    val pasted = appname
                    if (url.isEmpty()) url = pasted
                    appname = Uri.parse(if (pasted.matches("^https?://.*".toRegex())) pasted else "https://$pasted")
                        .host ?: "我的应用"
                }
                if (appname.isEmpty()) appname = "我的应用"
                if (url.isEmpty()) { log("请输入网址"); return@setOnClickListener }
                if (!url.matches("^https?://.*".toRegex())) url = "https://$url"
                val vernameS = vername.text.toString().trim().ifEmpty { "1.0" }
                val buildType = if (btReleaseBtn.isChecked) "release" else "debug"
                val stamp = System.currentTimeMillis().toString()
                val project = AppProject(url, appname,
                    "cn.xing.wtalite", vernameS, jscb.isChecked)
                gobtn.isEnabled = false
                installBtn.visibility = View.GONE
                shareBtn.visibility = View.GONE
                Thread {
                    try {
                        outApk = service.build(project, buildType)
                        publicUriStr = service.lastPublicUri
                        log(getString(R.string.phase_done) + "!\n" + outApk!!.absolutePath)
                        runOnUiThread {
                            installBtn.visibility = View.VISIBLE
                            shareBtn.visibility = View.VISIBLE
                        }
                    } catch (e: Exception) {
                        log("出错: ${e.message}")
                    } finally {
                        runOnUiThread { gobtn.isEnabled = true }
                    }
                }.start()
            }
        }
    }

    private fun contentUri(f: File): Uri =
        FileProvider.getUriForFile(this, "$packageName.fileprovider", f)

    private fun install(f: File) {
        try {
            Toast.makeText(this, "正在启动安装…", Toast.LENGTH_SHORT).show()
            if (android.os.Build.VERSION.SDK_INT >= 26 &&
                !packageManager.canRequestPackageInstalls()) {
                startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                Toast.makeText(this, "请允许XWTA安装未知应用，然后重新点安装",
                    Toast.LENGTH_LONG).show()
                return
            }
            val uri = publicUriStr?.let { Uri.parse(it) } ?: contentUri(f)
            val i = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (e: Exception) {
            // FileProvider路径失败时退回MediaStore下载副本
            try {
                val uri = Uri.parse(publicUriStr)
                val i = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
                Toast.makeText(this, "已用下载副本启动安装", Toast.LENGTH_SHORT).show()
            } catch (e2: Exception) {
                Toast.makeText(this, "安装失败: ${e.message} / ${e2.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun share(f: File) {
        try {
            val i = Intent(Intent.ACTION_SEND)
                .setType("application/vnd.android.package-archive")
                .putExtra(Intent.EXTRA_STREAM, contentUri(f))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(i, getString(R.string.btn_share)))
        } catch (e: Exception) {
            Toast.makeText(this, "分享失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
