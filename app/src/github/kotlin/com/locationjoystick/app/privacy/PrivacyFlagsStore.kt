package com.locationjoystick.app.privacy

import java.io.File
import java.io.StringWriter
import java.util.Properties

data class PrivacyFlags(
    val enabled: Boolean = false,
    val disclosureAccepted: Boolean = false,
)

/** The Privacy Mode switch and its disclosure flag. A file beside the clone store, not [AppSettings], so Play and exports never see it. */
class PrivacyFlagsStore(
    private val file: File,
) {
    fun read(): PrivacyFlags {
        if (!file.exists()) return PrivacyFlags()
        val p = Properties().apply { file.reader(Charsets.UTF_8).use(::load) }
        return PrivacyFlags(
            enabled = p.getProperty("enabled").toBoolean(),
            disclosureAccepted = p.getProperty("disclosureAccepted").toBoolean(),
        )
    }

    fun write(flags: PrivacyFlags) {
        file.parentFile?.mkdirs()
        val p =
            Properties().apply {
                setProperty("enabled", flags.enabled.toString())
                setProperty("disclosureAccepted", flags.disclosureAccepted.toString())
            }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(StringWriter().also { p.store(it, null) }.toString(), Charsets.UTF_8)
        check(tmp.renameTo(file)) { "cannot replace ${file.name}" }
    }
}
