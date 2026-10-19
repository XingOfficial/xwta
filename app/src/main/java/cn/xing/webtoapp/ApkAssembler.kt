package cn.xing.webtoapp

import java.io.File

/** aapt2编译链接 + dex注入（直拼核心步骤） */
object ApkAssembler {

    fun assemble(ctx: android.content.Context, project: AppProject,
                 workDir: File, dexZip: File, debuggable: Boolean,
                 status: (BuildStatus, Double) -> Unit): File {
        val tools = File(ctx.filesDir, "tools")
        val aapt2 = File(tools, "aapt2/out/aapt2").apply { setExecutable(true, false) }
        val ajar = File(tools, "ajar/android.jar")
        val env = mapOf("LD_LIBRARY_PATH" to File(tools, "aapt2/out").absolutePath)

        status(BuildStatus.GENERATING, 0.15)
        TemplateWriter.write(project, workDir, debuggable)
        copyIcon(ctx, File(workDir, "res"))

        status(BuildStatus.COMPILING, 0.3)
        val resZip = File(workDir, "res.zip")
        runTool(aapt2, env, "compile --dir '${File(workDir, "res").absolutePath}' -o '${resZip.absolutePath}'")

        status(BuildStatus.COMPILING, 0.5)
        val baseApk = File(workDir, "base.apk")
        val libZips = File(ctx.filesDir, "tools/libres").listFiles { f -> f.extension == "zip" }
                ?.map { "'" + it.absolutePath + "'" }?.joinToString(" ") ?: ""
        runTool(aapt2, env, "link -o '${baseApk.absolutePath}' -I '${ajar.absolutePath}'" +
                " --manifest '${File(workDir, "AndroidManifest.xml").absolutePath}'" +
                " --auto-add-overlay '${resZip.absolutePath}' $libZips")

        status(BuildStatus.PACKAGING, 0.7)
        val unsigned = File(workDir, "unsigned.apk")
        ZipMerger.mergeDexZip(baseApk, dexZip, unsigned)
        return unsigned
    }

    private fun copyIcon(ctx: android.content.Context, resDir: File) {
        val dst = File(resDir, "mipmap/ic_launcher.png")
        dst.parentFile?.mkdirs()
        ctx.assets.open("ic_launcher.png").use { ins ->
            dst.outputStream().use { o -> ins.copyTo(o) }
        }
    }

    private fun runTool(exe: File, env: Map<String, String>, args: String) {
        val cmd = mutableListOf(exe.absolutePath)
        // 无shell参与，路径不带引号；strip('')防quote残留
        cmd += args.trim().split("\\s+".toRegex()).map { it.trim('\'') }
        val pb = ProcessBuilder(cmd)
        pb.environment().putAll(env)
        pb.redirectErrorStream(true)
        val p = pb.start()
        val out = StringBuilder()
        p.inputStream.bufferedReader().forEachLine {
            out.append(it).append('\n')
            if (out.length > 8000) return@forEachLine
        }
        val code = p.waitFor()
        if (code != 0) throw Exception("工具执行失败(退出码$code):\n$out")
    }
}
