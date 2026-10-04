package com.assistant.app.data.update

data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null,
) : Comparable<SemanticVersion> {

    override fun compareTo(other: SemanticVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)
        if (preRelease == null && other.preRelease != null) return 1
        if (preRelease != null && other.preRelease == null) return -1
        if (preRelease != null && other.preRelease != null) {
            return comparePreRelease(preRelease, other.preRelease)
        }
        return 0
    }

    companion object {
        fun parse(version: String): SemanticVersion? {
            val trimmed = version.trim().removePrefix("v").removePrefix("V")
            if (trimmed.isEmpty()) return null
            val withoutBuild = trimmed.substringBefore('+')
            val hyphenSplit = withoutBuild.split('-', limit = 2)
            val versionNumbers = hyphenSplit[0].split('.')
            val major = versionNumbers.getOrNull(0)?.toIntOrNull() ?: return null
            val minor = versionNumbers.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = versionNumbers.getOrNull(2)?.toIntOrNull() ?: 0
            val preRelease = hyphenSplit.getOrNull(1)?.takeIf { it.isNotBlank() }
            return SemanticVersion(major, minor, patch, preRelease)
        }

        fun isNewer(latest: String, current: String): Boolean {
            val latestParsed = parse(latest) ?: return false
            val currentParsed = parse(current) ?: return false
            return latestParsed > currentParsed
        }
    }
}

private fun comparePreRelease(left: String, right: String): Int {
    val leftIds = left.split('.')
    val rightIds = right.split('.')
    for (i in 0 until maxOf(leftIds.size, rightIds.size)) {
        val l = leftIds.getOrNull(i) ?: return -1
        val r = rightIds.getOrNull(i) ?: return 1
        val lNum = l.toIntOrNull()
        val rNum = r.toIntOrNull()
        val cmp = when {
            lNum != null && rNum != null -> lNum.compareTo(rNum)
            lNum != null -> -1
            rNum != null -> 1
            else -> l.compareTo(r)
        }
        if (cmp != 0) return cmp
    }
    return 0
}
