package com.example.dto.db

import com.example.dto.yield.YieldProtocols

data class LiquidityPool(
    val protocol: YieldProtocols,
    val poolAddress: String,
    val validFromUtcSeconds: Long,
    val validTillUtcSeconds: Long,
    val extraData: String
)