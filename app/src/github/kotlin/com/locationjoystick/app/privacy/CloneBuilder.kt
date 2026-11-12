package com.locationjoystick.app.privacy

import android.content.Context
import com.android.apksig.apk.ApkFormatException
import com.locationjoystick.core.common.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

sealed interface CloneBuildResult {
    data class Built(
        val apk: File,
        val record: CloneRecord,
    ) : CloneBuildResult

    data class Failed(
        val reason: String,
    ) : CloneBuildResult
}

/** Rewrites the installed APK into an installable clone. Dex stays untouched, so class names stay. */
@Singleton
class CloneBuilder
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val store: CloneStore,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) {
        suspend fun build(name: String): CloneBuildResult =
            withContext(dispatcher) {
                if (!isValidCloneName(name)) return@withContext CloneBuildResult.Failed("invalid name")
                try {
                    rebuild(name.trim())
                } catch (e: IOException) {
                    CloneBuildResult.Failed(e.message ?: "io error")
                } catch (e: GeneralSecurityException) {
                    CloneBuildResult.Failed(e.message ?: "signing error")
                } catch (e: ApkFormatException) {
                    CloneBuildResult.Failed(e.message ?: "apk format error")
                } catch (e: RuntimeException) {
                    CloneBuildResult.Failed(e.message ?: "build error")
                } catch (e: OutOfMemoryError) {
                    CloneBuildResult.Failed(e.message ?: "out of memory")
                }
            }

        private fun rebuild(name: String): CloneBuildResult {
            val old = store.read()
            val packageId = old?.packageId ?: generatePackageId(Random.Default, context.packageName)
            val record = CloneRecord(packageId, name, old?.hue ?: Random.nextInt(360))
            val signer = store.loadSigner() ?: createSigningKey(packageId)

            val dir = File(context.cacheDir, "privacy-clone").apply { mkdirs() }
            val unsigned = File(dir, "clone-unsigned.apk")
            val signed = File(dir, "clone.apk.tmp")
            try {
                rewriteApk(File(context.applicationInfo.sourceDir), context.packageName, record, unsigned)
                signApk(unsigned, signed, signer.first, signer.second)
            } finally {
                unsigned.delete()
            }
            val apk = File(dir, "clone.apk")
            check(signed.renameTo(apk)) { "cannot replace clone.apk" }
            store.save(record, signer)
            return CloneBuildResult.Built(apk, record)
        }
    }
