package com.locationjoystick.app.privacy

import com.android.apksig.ApkSigner
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date

private const val DAY_MS = 24L * 60 * 60 * 1000

// No provider is set: BC's "BC" name clashes with the platform's built-in one.
fun createSigningKey(packageId: String): Pair<PrivateKey, X509Certificate> {
    val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val now = System.currentTimeMillis()
    val name = X500Name("CN=$packageId")
    // 30 years: Android checks the signing certificate only at install, and a clone must stay updatable.
    val holder =
        JcaX509v3CertificateBuilder(
            name,
            BigInteger(64, SecureRandom()).abs().add(BigInteger.ONE),
            Date(now - DAY_MS),
            Date(now + 30 * 365 * DAY_MS),
            name,
            pair.public,
        ).build(JcaContentSignerBuilder("SHA256withRSA").build(pair.private))
    return pair.private to JcaX509CertificateConverter().getCertificate(holder)
}

// v2 only: it is supported from API 24, below minSdk 28, so v1 adds nothing.
fun signApk(
    unsigned: File,
    out: File,
    key: PrivateKey,
    cert: X509Certificate,
) {
    ApkSigner
        .Builder(listOf(ApkSigner.SignerConfig.Builder("clone", key, listOf(cert)).build()))
        .setInputApk(unsigned)
        .setOutputApk(out)
        .setV1SigningEnabled(false)
        .setV2SigningEnabled(true)
        .setV3SigningEnabled(false)
        .setMinSdkVersion(28)
        .build()
        .sign()
}
