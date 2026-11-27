package com.locationjoystick.app.privacy

import java.io.File
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.Properties

/** One clone: its identity, signing key and certificate in [dir]. Android Keystore cannot export the key to apksig. */
class CloneStore(
    private val dir: File,
) {
    private val props = File(dir, "clone.properties")
    private val key = File(dir, "key.pk8")
    private val cert = File(dir, "cert.der")
    private val originFile = File(dir, "origin.txt")

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

    /** SHA-256 digests of the original's release signers. Only a copy stores them, to check its own updates. */
    fun origin(): Set<String> =
        if (originFile.exists()) {
            originFile
                .readText()
                .split(",")
                .filter { it.isNotEmpty() }
                .toSet()
        } else {
            emptySet()
        }

    /** Stores what [encodePayload] wrote. Returns false and stores nothing for an incomplete payload. */
    fun importPayload(bytes: ByteArray): Boolean {
        val p = Properties().apply { load(bytes.inputStream().reader(Charsets.UTF_8)) }
        val decoder = Base64.getDecoder()
        val record =
            CloneRecord(
                packageId = p.getProperty("packageId") ?: return false,
                name = p.getProperty("name") ?: return false,
                hue = p.getProperty("hue")?.toIntOrNull() ?: return false,
            )
        val origin = p.getProperty("origin")?.takeIf { it.isNotEmpty() } ?: return false
        val privateKey =
            KeyFactory.getInstance("RSA").generatePrivate(
                PKCS8EncodedKeySpec(
                    decoder.decode(
                        p.getProperty("key") ?: return false,
                    ),
                ),
            )
        val certificate =
            CertificateFactory
                .getInstance("X.509")
                .generateCertificate(decoder.decode(p.getProperty("cert") ?: return false).inputStream()) as X509Certificate
        save(record, privateKey to certificate)
        atomicWrite(originFile, origin.toByteArray(Charsets.UTF_8))
        return true
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

fun encodePayload(
    record: CloneRecord,
    signer: Pair<PrivateKey, X509Certificate>,
    origin: Set<String>,
): ByteArray {
    val encoder = Base64.getEncoder()
    val p =
        Properties().apply {
            setProperty("packageId", record.packageId)
            setProperty("name", record.name)
            setProperty("hue", record.hue.toString())
            setProperty("key", encoder.encodeToString(signer.first.encoded))
            setProperty("cert", encoder.encodeToString(signer.second.encoded))
            setProperty("origin", origin.joinToString(","))
        }
    return java.io
        .StringWriter()
        .also { p.store(it, null) }
        .toString()
        .toByteArray(Charsets.UTF_8)
}

fun certDigest(encoded: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(encoded).joinToString("") { "%02x".format(it) }
