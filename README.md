# XWTA · 网页转APK

网页转APK工具（Android原生/Kotlin）。直拼APK路线：
aapt2 + 预编译dex + v1签名全部内置，全程离线，秒级出包。

## 功能
- 输入网址生成WebView壳App（标准壳/增强壳可选）
- Debug/Release构建类型（debuggable开关）
- R8混淆（可选，首次勾选时按需下载R8运行时，四镜像fallback）
- 生成后一键安装/分享

## 构建
- AGP 9.3.1 / Gradle 9.6.1 / Kotlin 2.3.21（AGP内建，无kotlin插件行）
- CodeOnTheGo等端侧IDE可直接打开构建；CI见.github/workflows

## License
MIT
