package com.squareup.wire

data class Instant(
    val epochSecond: Long,
) : Comparable<Instant> {

    override fun compareTo(other: Instant): Int = epochSecond.compareTo(other.epochSecond)

    fun isAfter(other: Instant): Boolean = this > other

    fun isBefore(other: Instant): Boolean = this < other

    companion object {
        fun now(): Instant = Instant(epochSecond = java.time.Instant.now().epochSecond)

        fun ofEpochSecond(epochSecond: Long): Instant = Instant(epochSecond = epochSecond)
    }
}
