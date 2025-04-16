package com.example.dto.yield

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.math.BigInteger
import java.time.Instant

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
@JsonSubTypes(
    JsonSubTypes.Type(value = YieldBoostDex::class, name = "dex"),
)
sealed interface YieldBoost {
    val asset: String
    val endsIn: Instant
    val apr: Double
}

data class YieldBoostDex(
    override val asset: String,
    override val endsIn: Instant,
    override val apr: Double,
    val rewardRatePerDay: BigInteger,
    val poolAddress: String,
    val minterFactory: String?,
    val lockDurationSeconds: Long?,
) : YieldBoost

// TODO: add your own boost if needed,
//  fields from YieldBoost is required for all boosts