package cn.xing.webtoapp

import java.io.File

/** 生成WebView壳工程的工程文件（对应Flutter版webview_template_materializer.dart） */
object TemplateWriter {

    private val MANIFEST = """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="__PKG__" android:versionCode="__VCODE__" android:versionName="__VNAME__">
  <uses-sdk android:minSdkVersion="24" android:targetSdkVersion="28"/>
  <uses-permission android:name="android.permission.INTERNET"/>
  <application android:label="__APP__" android:icon="@mipmap/ic_launcher"
      android:debuggable="__DEBUG__" android:usesCleartextTraffic="true"
      android:theme="@style/Theme.Material3.DayNight.NoActionBar">
    <activity android:name="cn.xing.wtalite.MainActivityNative" android:exported="true"
        android:configChanges="orientation|keyboardHidden|screenSize">
      <intent-filter>
        <action android:name="android.intent.action.MAIN"/>
        <category android:name="android.intent.category.LAUNCHER"/>
      </intent-filter>
    </activity>
  </application>
</manifest>"""

    private val STRINGS = """<?xml version="1.0" encoding="utf-8"?>
<resources>
  <string name="app_name">__APP__</string>
  <string name="xwta_url">__URL__</string>
  <string name="xwta_js">__JS__</string>
</resources>"""

    private val MAIN_ACTIVITY = """
package cn.xing.wtalite;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;

public class MainActivity extends Activity {
    private WebView wv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        wv = new WebView(this);
        WebSettings s = wv.getSettings();
        int id = getResources().getIdentifier("xwta_url", "string", getPackageName());
        String url = id != 0 ? getString(id) : "https://example.com";
        id = getResources().getIdentifier("xwta_js", "string", getPackageName());
        boolean js = id != 0 && Boolean.parseBoolean(getString(id));
        s.setJavaScriptEnabled(js);
        s.setDomStorageEnabled(true);
        wv.setWebViewClient(new WebViewClient());
        wv.setBackgroundColor(0xFFFFFFFF);
        setContentView(wv);
        wv.loadUrl(url);
    }

    @Override
    public void onBackPressed() {
        if (wv != null && wv.canGoBack()) wv.goBack();
        else super.onBackPressed();
    }
}"""

    private fun esc(v: String): String = v
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "\\'")

    /** 把工程文件写入outputDir（图标由调用方负责拷入res/mipmap） */
    fun write(project: AppProject, outputDir: File, debuggable: Boolean) {
        val resValues = File(outputDir, "res/values").apply { mkdirs() }
        File(outputDir, "res/mipmap").mkdirs()

        writeFile(File(outputDir, "AndroidManifest.xml"),
            MANIFEST.trim().replace("__PKG__", esc(project.packageName))
                .replace("__VNAME__", esc(project.versionName))
                .replace("__VCODE__", "1")
                .replace("__DEBUG__", debuggable.toString())
                .replace("__APP__", esc(project.appName)))
        writeFile(File(resValues, "strings.xml"),
            STRINGS.trim().replace("__APP__", esc(project.appName))
                .replace("__URL__", esc(project.url))
                .replace("__JS__", project.enableJavaScript.toString()))
        writeFile(File(File(outputDir, "app/src/main/java/cn/xing/wtalite"), "MainActivity.java"),
            MAIN_ACTIVITY)
    }

    private fun writeFile(f: File, content: String) {
        f.parentFile?.mkdirs()
        f.writeText(content, Charsets.UTF_8)
    }
}
