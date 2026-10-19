package cn.xing.webtoapp

/** 构建阶段（对应Flutter版models/build_status.dart） */
enum class BuildStatus(val label: String, val startProgress: Double) {
    IDLE("等待开始", 0.0),
    PREPARING("解压构建环境", 0.05),
    GENERATING("生成工程文件", 0.15),
    COMPILING("编译中", 0.4),
    PACKAGING("打包中", 0.8),
    DONE("完成", 1.0),
    FAILED("失败", 0.0);

    fun withPercent(p: Double): String = "$label ${(p * 100).toInt()}%"
}
