package cn.xing.webtoapp

import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.TreeMap
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** APK v1(JAR)签名器：纯JDK API，无第三方依赖。sign()返回"OK"或错误信息。 */
object SignHelper {

    fun sign(inPath: String, outPath: String, p12Path: String, pass: String): String {
        return try {
            val ks = KeyStore.getInstance("PKCS12")
            ks.load(FileInputStream(p12Path), pass.toCharArray())
            val alias = ks.aliases().nextElement()
            val key = ks.getKey(alias, pass.toCharArray()) as PrivateKey
            val cert = ks.getCertificate(alias) as X509Certificate

            // 读取原APK全部条目（跳过旧META-INF）
            val entries = TreeMap<String, ByteArray>()
            ZipInputStream(FileInputStream(inPath)).use { zin ->
                var e: ZipEntry? = zin.nextEntry
                while (e != null) {
                    if (!e.isDirectory) {
                        val n = e.name
                        if (!n.startsWith("META-INF/")) {
                            entries[n] = zin.readBytes()
                        }
                    }
                    zin.closeEntry()
                    e = zin.nextEntry
                }
            }

            val sha = MessageDigest.getInstance("SHA-256")
            val b64 = Base64.getEncoder()
            val mf = StringBuilder().apply {
                append("Manifest-Version: 1.0\r\n")
                append("Created-By: 1.0 (XWTA)\r\n\r\n")
                entries.forEach { (name, data) ->
                    append("Name: ").append(name).append("\r\n")
                    append("SHA-256-Digest: ").append(b64.encodeToString(sha.digest(data))).append("\r\n\r\n")
                }
            }.toString().toByteArray(StandardCharsets.UTF_8)

            val sf = StringBuilder().apply {
                append("Signature-Version: 1.0\r\n")
                append("Created-By: 1.0 (XWTA)\r\n")
                append("SHA-256-Digest-Manifest: ").append(b64.encodeToString(sha.digest(mf))).append("\r\n\r\n")
                entries.forEach { (name, data) ->
                    append("Name: ").append(name).append("\r\n")
                    append("SHA-256-Digest: ").append(b64.encodeToString(sha.digest(data))).append("\r\n\r\n")
                }
            }.toString().toByteArray(StandardCharsets.UTF_8)

            val sig = Signature.getInstance("SHA256withRSA")
            sig.initSign(key)
            sig.update(sf)
            val encrypted = sig.sign()

            val contentInfo = pkcs7(encrypted, cert)

            ZipOutputStream(FileOutputStream(outPath)).use { zos ->
                put(zos, "META-INF/MANIFEST.MF", mf)
                put(zos, "META-INF/CERT.SF", sf)
                put(zos, "META-INF/CERT.RSA", contentInfo)
                entries.forEach { (name, data) -> put(zos, name, data) }
            }
            "OK"
        } catch (e: Exception) {
            val sw = java.io.StringWriter()
            e.printStackTrace(java.io.PrintWriter(sw))
            "ERR: $e\n$sw"
        }
    }

    // ---- PKCS#7 SignedData (detached, 无signedAttrs) ----
    private fun pkcs7(encryptedDigest: ByteArray, cert: X509Certificate): ByteArray {
        val oidSha256 = byteArrayOf(0x60.toByte(), 0x86.toByte(), 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x01)
        val oidRsa = byteArrayOf(0x2A.toByte(), 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x01)
        val oidData = byteArrayOf(0x2A.toByte(), 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x07, 0x01)
        val oidSignedData = byteArrayOf(0x2A.toByte(), 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x07, 0x02)
        val nullVal = byteArrayOf(0x05, 0x00)

        val digestAlg = seq(tlv(0x06, oidSha256), nullVal)
        val encAlg = seq(tlv(0x06, oidRsa), nullVal)
        val issuerAndSerial = seq(cert.issuerX500Principal.encoded, tlv(0x02, cert.serialNumber.toByteArray()))
        val signerInfo = seq(
            tlv(0x02, byteArrayOf(0x01)),
            issuerAndSerial,
            digestAlg,
            encAlg,
            tlv(0x04, encryptedDigest)
        )
        val signedData = seq(
            tlv(0x02, byteArrayOf(0x01)),
            set(digestAlg),
            seq(tlv(0x06, oidData)),
            tlv(0xA0, cert.encoded),
            set(signerInfo)
        )
        return seq(tlv(0x06, oidSignedData), tlv(0xA0, signedData))
    }

    // ---- DER工具 ----
    private fun seq(vararg parts: ByteArray) = tlv(0x30, concat(*parts))
    private fun set(vararg parts: ByteArray) = tlv(0x31, concat(*parts))
    private fun tlv(tag: Int, content: ByteArray): ByteArray {
        val o = ByteArrayOutputStream()
        o.write(tag)
        val len = content.size
        if (len < 0x80) {
            o.write(len)
        } else {
            var n = 0
            var v = len
            while (v > 0) { v = v shr 8; n++ }
            o.write(0x80 or n)
            for (i in n - 1 downTo 0) o.write((len shr (8 * i)) and 0xFF)
        }
        o.write(content, 0, content.size)
        return o.toByteArray()
    }
    private fun concat(vararg parts: ByteArray): ByteArray {
        val len = parts.sumOf { it.size }
        val r = ByteArray(len)
        var off = 0
        for (p in parts) { System.arraycopy(p, 0, r, off, p.size); off += p.size }
        return r
    }
    private fun put(zos: ZipOutputStream, name: String, data: ByteArray) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(data, 0, data.size)
        zos.closeEntry()
    }
}
