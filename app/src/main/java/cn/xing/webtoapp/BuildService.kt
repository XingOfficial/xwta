package cn.xing.webtoapp

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class BuildService(context: Context,
                   private val onStatus: (BuildStatus, Double) -> Unit,
                   private val onLog: (String) -> Unit = {}) {

    private val ctx: Context = context.applicationContext
    private var lastStage = ""

    private fun stage(s: BuildStatus, p: Double) = onStatus(s, p)
    private fun log(msg: String) = onLog(msg)

    private val ASSET_SIZES = mapOf(
        "aapt2.zip" to 5126324L,
        "android.jar.zip" to 24149695L,
    )

    private fun ensureExtracted(asset: String, dest: File, from: Double, to: Double) {
        val marker = File(dest, ".$asset.ok")
        val expected = "v${ASSET_SIZES[asset]}"
        val fresh = marker.exists() && marker.readText() == expected
        if (fresh && dest.isDirectory && dest.list()!!.isNotEmpty()) return
        dest.deleteRecursively()
        dest.mkdirs()
        unzipProgress(asset, dest, from, to)
        marker.writeText(expected)
    }

    fun prepareTools() {
        val tools = File(ctx.filesDir, "tools")
        stage(BuildStatus.PREPARING, 0.02)
        ensureExtracted("aapt2.zip", File(tools, "aapt2"), 0.02, 0.08)
        File(tools, "aapt2/out/aapt2").setExecutable(true, false)
        ensureExtracted("android.jar.zip", File(tools, "ajar"), 0.08, 0.22)
        ensureExtracted("libs-res.zip", File(tools, "libres"), 0.15, 0.22)

        extractAsset("xwta.p12")
        stage(BuildStatus.DONE, 1.0)
    }

    fun toolsReady(): Boolean =
        File(ctx.filesDir, "tools/aapt2/out/aapt2").exists() &&
                File(ctx.filesDir, "tools/ajar/android.jar").exists() &&
                File(ctx.filesDir, "tools/libres").listFiles()?.isNotEmpty() == true

    fun build(project: AppProject, buildType: String): File {
        val p12 = extractAsset("xwta.p12")
        val dexZip = extractAsset("classes-all.dex")

        val stamp = System.currentTimeMillis()
        val workDir = File(File(ctx.filesDir, "builds"), "proj_$stamp")
        val unsigned = ApkAssembler.assemble(ctx, project, workDir, dexZip,
            buildType == "debug") { s, p -> stage(s, p) }

        stage(BuildStatus.PACKAGING, 0.9)
        val outDir = File(ctx.getExternalFilesDir(null), "apk").apply { mkdirs() }

        val outApk = File(outDir, project.appName.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_")
                + "_v${project.versionName}-$buildType-"
                + (System.currentTimeMillis() % 1000000) + ".apk")
        val r = SignHelper.sign(unsigned.absolutePath, outApk.absolutePath,
            p12.absolutePath, "xwta2026")
        if (r != "OK") {
            stage(BuildStatus.FAILED, 0.0)
            throw Exception("签名失败: $r")
        }

        val publicPath = copyToPublicDownloads(outApk)

        stage(BuildStatus.DONE, 1.0)
        if (publicPath != null) log("已复制到下载目录: $publicPath")
        return outApk
    }

    var lastPublicUri: String? = null

    private fun copyToPublicDownloads(src: File): String? {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val cv = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, src.name)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE,
                        "application/vnd.android.package-archive")
                }
                val uri = ctx.contentResolver.insert(
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: return null
                ctx.contentResolver.openOutputStream(uri)?.use { o ->
                    src.inputStream().use { it.copyTo(o) }
                }
                lastPublicUri = uri.toString()
                "/sdcard/Download/${src.name}"
            } else {
                val d = File(android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS), "XWTA").apply { mkdirs() }
                val f = File(d, src.name)
                src.copyTo(f, overwrite = true)
                f.absolutePath
            }
        } catch (e: Exception) { null }
    }

    private fun extractAsset(name: String): File {
        val f = File(ctx.filesDir, name)
        ctx.assets.open(name).use { ins -> ins.copyTo(FileOutputStream(f)) }
        return f
    }

    private fun unzipProgress(name: String, dest: File, from: Double, to: Double) {
        dest.mkdirs()

        var total = 0
        ctx.assets.open(name).use { ins ->
            ZipInputStream(ins).use { zin -> while (zin.nextEntry != null) total++ }
        }
        var done = 0
        ctx.assets.open(name).use { ins ->
            ZipInputStream(ins).use { zin ->
                val buf = ByteArray(65536)
                var e: ZipEntry? = zin.nextEntry
                while (e != null) {
                    val out = File(dest, e.name)
                    if (e.isDirectory) out.mkdirs() else {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { o ->
                            var n: Int
                            while (zin.read(buf).also { n = it } > 0) o.write(buf, 0, n)
                        }
                    }
                    zin.closeEntry()
                    done++
                    stage(BuildStatus.PREPARING, from + (to - from) * done / total.coerceAtLeast(1))
                    e = zin.nextEntry
                }
            }
        }
    }

    private fun applySymlinks(base: File) {
        val f = File(base, "SYMLINKS.txt")
        if (!f.exists()) return
        f.readText().split("\n").forEach { line ->
            if (line.contains("←")) {
                val p = line.split("←")
                if (p.size >= 2) {
                    val link = File(base, p[1].trim())
                    link.delete()
                    link.parentFile?.mkdirs()
                    try {
                        java.nio.file.Files.createSymbolicLink(link.toPath(),
                            link.parentFile.toPath().resolve(p[0].trim()))
                    } catch (e: Exception) {
                        try { ProcessBuilder("ln", "-sf", p[0].trim(), link.absolutePath).start().waitFor() }
                        catch (_: Exception) {}
                    }
                }
            }
        }
        f.delete()
    }

    private fun chmodR(path: File) {
        try { ProcessBuilder("chmod", "-R", "755", path.absolutePath).start().waitFor() }
        catch (_: Exception) {}
    }
}
