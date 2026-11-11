package com.locationjoystick.app.privacy

import java.io.File
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Properties

/** One clone: its identity, signing key and certificate in [dir]. Android Keystore cannot export the key to apksig. */
class CloneStore(
    private val dir: File,
) {
    private val props = File(dir, "clone.properties")
    private val key = File(dir, "key.pk8")
    private val cert = File(dir, "cert.der")

    fun read(): CloneRecord? {
        if (!props.exists()) return null
        val p = Properties().apply { props.reader(Charsets.UTF_8).use(::load) }
        return CloneRecord(
            packageId = p.getProperty("packageId") ?: return null,
            name = p.getProperty("name") ?: return null,
            hue = p.getProperty("hue")?.toIntOrNull() ?: return null,
        )
    }

    fun save(
        record: CloneRecord,
        signer: Pair<PrivateKey, X509Certificate>,
    ) {
        dir.mkdirs()
        atomicWrite(key, signer.first.encoded)
        atomicWrite(cert, signer.second.encoded)
        val p =
            Properties().apply {
                setProperty("packageId", record.packageId)
                setProperty("name", record.name)
                setProperty("hue", record.hue.toString())
            }
        val text =
            java.io
                .StringWriter()
                .also { p.store(it, null) }
                .toString()
        atomicWrite(props, text.toByteArray(Charsets.UTF_8))
    }

    fun loadSigner(): Pair<PrivateKey, X509Certificate>? {
        if (!key.exists() || !cert.exists()) return null
        val privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(key.readBytes()))
        val certificate = CertificateFactory.getInstance("X.509").generateCertificate(cert.inputStream()) as X509Certificate
        return privateKey to certificate
    }

    fun clear() {
        dir.deleteRecursively()
    }

    private fun atomicWrite(
        target: File,
        bytes: ByteArray,
    ) {
        val tmp = File(dir, target.name + ".tmp")
        tmp.writeBytes(bytes)
        check(tmp.renameTo(target)) { "cannot replace ${target.name}" }
    }
}
