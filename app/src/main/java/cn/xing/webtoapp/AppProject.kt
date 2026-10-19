package cn.xing.webtoapp

data class AppProject(
    val url: String,
    val appName: String,
    val packageName: String,
    val versionName: String,
    val enableJavaScript: Boolean
)
