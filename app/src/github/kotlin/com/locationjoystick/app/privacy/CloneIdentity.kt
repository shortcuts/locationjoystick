package com.locationjoystick.app.privacy

import kotlin.random.Random

data class CloneRecord(
    val packageId: String,
    val name: String,
    val hue: Int,
)

internal val BANNED_WORDS = listOf("location", "gps", "joystick", "mock")

private val TLDS = listOf("com", "net", "org", "io", "app")
private const val LETTERS = "abcdefghijklmnopqrstuvwxyz"
private const val ALPHANUMERIC = LETTERS + "0123456789"

fun generatePackageId(
    random: Random,
    runningPackage: String,
): String {
    while (true) {
        val segments = List(random.nextInt(1, 3)) { randomSegment(random) }
        val id = (listOf(TLDS.random(random)) + segments).joinToString(".")
        if (id != runningPackage && BANNED_WORDS.none { it in id }) return id
    }
}

private fun randomSegment(random: Random): String =
    LETTERS.random(random).toString() + String(CharArray(random.nextInt(3, 8)) { ALPHANUMERIC.random(random) })

fun isValidCloneName(name: String): Boolean = name.trim().length in 1..30

/** Returns null for values that only share a prefix with [oldPkg], such as class names. */
fun renameWithPackage(
    value: String,
    oldPkg: String,
    newPkg: String,
): String? =
    when {
        value == oldPkg -> newPkg
        value.startsWith("$oldPkg.") -> newPkg + value.substring(oldPkg.length)
        else -> null
    }

fun renameAuthorities(
    authorities: String,
    oldPkg: String,
    newPkg: String,
): String = authorities.split(";").joinToString(";") { renameWithPackage(it, oldPkg, newPkg) ?: it }

// The original #15151C is near neutral, so a pure hue shift would be invisible.
fun backgroundArgb(hue: Int): Int {
    val s = 0.55f
    val v = 0.40f
    val h = (hue.mod(360)) / 60f
    val c = v * s
    val x = c * (1 - kotlin.math.abs(h % 2 - 1))
    val (r, g, b) =
        when (h.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
    val m = v - c

    fun channel(f: Float) = ((f + m) * 255).toInt()
    return (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
}
