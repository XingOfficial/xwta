# XWTA · 网页转原生应用

把网页变成**真原生Android应用**——不是WebView套壳：HTML解析后用原生控件渲染，读屏逐段丝滑导航。

## 核心特性
- **原生渲染**：标题/正文/图片/列表/表格/表单全部映射为原生控件（RecyclerView按需加载，长页滚动与页长解耦），链接点击应用内续读，带返回键历史栈
- **智能回退**：JS动态页自动检测并切换内置WebView加载，全程不跳出应用
- **全离线构建**：aapt2 + 预编译dex + v1签名全部内置，手机上秒级出包，无需电脑
- **应用内闭环**：生成、安装、分享、文件下载（apk/zip/pdf/文档等直达下载目录）、POST/GET表单
- **读屏友好**：原生TextView逐段导航、文本可选中复制，为TalkBack用户打造
- **崩溃可溯**：黑匣子双写公开下载目录，闪退日志随手可得

## 使用
输入网址、应用名、版本号 → 点生成 → 授权安装 → 打开即用。

## 下载
[Release v1.18](https://github.com/XingOfficial/xwta/releases/download/v1.18/xwta-1.18.apk)

## 构建
- AGP 9.3.1 / Gradle 9.6.1 / Kotlin 2.3.21（AGP内建，无kotlin插件行）
- CodeOnTheGo等端侧IDE可直接打开构建；CI见.github/workflows

## License
MIT
