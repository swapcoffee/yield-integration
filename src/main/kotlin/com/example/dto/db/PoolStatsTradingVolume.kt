package com.example.dto.db

import com.example.dto.yield.YieldProtocols

data class PoolStatsTradingVolume(
    val protocol: YieldProtocols,
    val poolAddress: String,
    val tradingDate: Long,
    val usdVolumeAmount: Double,
    val interactionCount: Int,
    val extraData: String
)