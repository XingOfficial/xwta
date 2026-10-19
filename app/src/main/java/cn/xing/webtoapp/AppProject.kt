package cn.xing.webtoapp

/** 待打包项目（对应Flutter版models/app_project.dart） */
data class AppProject(
    val url: String,
    val appName: String,
    val packageName: String,
    val versionName: String,
    val enableJavaScript: Boolean
)
