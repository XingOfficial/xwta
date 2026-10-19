package cn.xing.webtoapp

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipMerger {

    fun mergeDexZip(srcApk: File, dexZip: File, dstApk: File) {
        ZipInputStream(FileInputStream(dexZip)).use { dzin ->
            var de: ZipEntry? = dzin.nextEntry
            val dexEntries = mutableListOf<Pair<String, ByteArray>>()
            while (de != null) {
                if (!de.isDirectory) dexEntries.add(de.name to dzin.readBytes())
                de = dzin.nextEntry
            }
            doMerge(srcApk, dstApk, dexEntries)
        }
    }

    private fun doMerge(srcApk: File, dstApk: File, dexEntries: List<Pair<String, ByteArray>>) {
        val buf = ByteArray(65536)
        ZipInputStream(FileInputStream(srcApk)).use { zin ->
            ZipOutputStream(FileOutputStream(dstApk)).use { zout ->
                var e: ZipEntry? = zin.nextEntry
                while (e != null) {
                    if (!e.isDirectory) {
                        zout.putNextEntry(ZipEntry(e.name))
                        var n: Int
                        while (zin.read(buf).also { n = it } > 0) zout.write(buf, 0, n)
                        zout.closeEntry()
                    }
                    zin.closeEntry()
                    e = zin.nextEntry
                }
                for ((name, data) in dexEntries) {
                    zout.putNextEntry(ZipEntry(name))
                    zout.write(data)
                    zout.closeEntry()
                }
            }
        }
    }

    fun merge(srcApk: File, extraDex: File, dexName: String, dstApk: File) {
        ZipInputStream(FileInputStream(srcApk)).use { zin ->
            ZipOutputStream(FileOutputStream(dstApk)).use { zout ->
                val buf = ByteArray(65536)
                var e: ZipEntry? = zin.nextEntry
                while (e != null) {
                    if (!e.isDirectory) {
                        zout.putNextEntry(ZipEntry(e.name))
                        var n: Int
                        while (zin.read(buf).also { n = it } > 0) zout.write(buf, 0, n)
                        zout.closeEntry()
                    }
                    zin.closeEntry()
                    e = zin.nextEntry
                }
                zout.putNextEntry(ZipEntry(dexName))
                extraDex.inputStream().use { ins ->
                    var n: Int
                    while (ins.read(buf).also { n = it } > 0) zout.write(buf, 0, n)
                }
                zout.closeEntry()
            }
        }
    }
}
