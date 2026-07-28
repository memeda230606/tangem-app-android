package com.tangem.crypto.hdWallet

sealed class DerivationNode {
    abstract val index: Long

    val isHardened: Boolean
        get() = this is Hardened

    abstract fun getIndex(includeHardened: Boolean = true): Long

    data class Hardened(override val index: Long) : DerivationNode() {
        override fun getIndex(includeHardened: Boolean): Long = if (includeHardened) index + HARDENED_OFFSET else index
    }

    data class NonHardened(override val index: Long) : DerivationNode() {
        override fun getIndex(includeHardened: Boolean): Long = index
    }

    companion object {
        const val HARDENED_OFFSET = 0x80000000L

        fun fromIndex(index: Long): DerivationNode {
            return if (index >= HARDENED_OFFSET) {
                Hardened(index - HARDENED_OFFSET)
            } else {
                NonHardened(index)
            }
        }
    }
}

data class DerivationPath(
    val path: List<DerivationNode>,
) {
    constructor(rawPath: String) : this(parse(rawPath))
    constructor(rawPath: String, nodes: List<DerivationNode>) : this(nodes)

    val nodes: List<DerivationNode>
        get() = path

    val rawPath: String = nodes.joinToString(separator = "/", prefix = "m") { node ->
        when (node) {
            is DerivationNode.Hardened -> "/${node.getIndex(includeHardened = false)}'"
            is DerivationNode.NonHardened -> "/${node.getIndex(includeHardened = false)}"
        }.removePrefix("/")
    }

    companion object {
        private fun parse(rawPath: String): List<DerivationNode> {
            return rawPath.split("/")
                .dropWhile { it == "m" || it.isBlank() }
                .map { item ->
                    val hardened = item.endsWith("'")
                    val value = item.removeSuffix("'").toLong()
                    if (hardened) DerivationNode.Hardened(value) else DerivationNode.NonHardened(value)
                }
        }
    }
}
