package com.example.dto.yield

import com.example.api.model.ApiSupportedYields

// TODO: add your protocol here
enum class YieldProtocols(val value: String) {
    STONFI_V1("stonfi_v1"),
    FARMIX_V1_LENDING("farmix_v1_lending");

    companion object {
        fun resolve(it: String): YieldProtocols {
            for (i in YieldProtocols.entries) {
                if (i.value == it) {
                    return i
                }
            }
            throw IllegalArgumentException("Unknown Yield Protocol: $it")
        }
    }
}

fun YieldProtocols.mapToApi(): ApiSupportedYields {
    when (this) {
        YieldProtocols.STONFI_V1 -> return ApiSupportedYields.STONFI
        YieldProtocols.FARMIX_V1_LENDING -> return ApiSupportedYields.FARMIXLENDING
        else -> throw IllegalArgumentException("Unsupported Yield Protocol: $this")
    }
}

fun ApiSupportedYields.mapToYieldProtocols(): YieldProtocols {
    when (this) {
        ApiSupportedYields.STONFI -> return YieldProtocols.STONFI_V1
        ApiSupportedYields.FARMIXLENDING -> return YieldProtocols.FARMIX_V1_LENDING
        else -> throw IllegalArgumentException("Unsupported Yield Protocol: $this")
    }
}