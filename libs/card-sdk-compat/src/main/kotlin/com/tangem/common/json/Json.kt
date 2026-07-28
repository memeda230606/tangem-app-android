package com.tangem.common.json

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.tangem.common.card.FirmwareVersion
import com.tangem.common.extensions.hexToBytes
import com.tangem.common.extensions.toHexString
import com.tangem.crypto.hdWallet.DerivationNode
import java.lang.reflect.Type
import java.util.Date

class MoshiJsonConverter(
    adapters: List<Any> = getTangemSdkAdapters(),
    typedAdapters: Map<Type, JsonAdapter<*>> = getTangemSdkTypedAdapters(),
) {
    val moshi: Moshi = Moshi.Builder().apply {
        adapters.forEach { add(it) }
        typedAdapters.forEach { add(it.key, it.value) }
        addLast(KotlinJsonAdapterFactory())
    }.build()

    fun toMap(json: String): Map<String, Any> {
        val type = Map::class.java
        @Suppress("UNCHECKED_CAST")
        return moshi.adapter(type).fromJson(json) as? Map<String, Any> ?: emptyMap()
    }

    fun prettyPrint(value: Any): String {
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            val adapter = moshi.adapter(value::class.java) as JsonAdapter<Any>
            adapter.indent("  ").toJson(value)
        }.getOrElse {
            value.toString()
        }
    }

    companion object {
        fun default(): MoshiJsonConverter = MoshiJsonConverter()

        fun getTangemSdkAdapters(): List<Any> = listOf(
            TangemSdkAdapter.ByteArrayAdapter(),
            TangemSdkAdapter.DateAdapter(),
            TangemSdkAdapter.DerivationNodeAdapter(),
            TangemSdkAdapter.FirmwareVersionAdapter(),
        )

        fun getTangemSdkTypedAdapters(): Map<Type, JsonAdapter<*>> = emptyMap()
    }
}

object TangemSdkAdapter {
    class ByteArrayAdapter {
        @ToJson
        fun toJson(value: ByteArray): String = value.toHexString()

        @FromJson
        fun fromJson(value: String): ByteArray = value.hexToBytes()
    }

    class DateAdapter {
        @ToJson
        fun toJson(value: Date): Long = value.time

        @FromJson
        fun fromJson(value: Long): Date = Date(value)
    }

    class DerivationNodeAdapter {
        @ToJson
        fun toJson(value: DerivationNode): Long = value.getIndex(includeHardened = true)

        @FromJson
        fun fromJson(value: Long): DerivationNode = DerivationNode.fromIndex(value)
    }

    class FirmwareVersionAdapter {
        @ToJson
        fun toJson(value: FirmwareVersion): String = "${value.major}.${value.minor}.${value.patch}"

        @FromJson
        fun fromJson(value: String): FirmwareVersion {
            val parts = value.split(".").mapNotNull(String::toIntOrNull)
            return FirmwareVersion(
                major = parts.getOrNull(0) ?: 0,
                minor = parts.getOrNull(1) ?: 0,
                patch = parts.getOrNull(2) ?: 0,
            )
        }
    }
}
