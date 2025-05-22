package com.example.dto.yield

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@type")
@JsonSubTypes(
    JsonSubTypes.Type(value = YieldPoolFieldsDex::class, name = "dex"),
    JsonSubTypes.Type(value = YieldPoolFieldsStormTrade::class, name = "stormtrade"),
)
interface YieldPoolFields

@JsonIgnoreProperties(ignoreUnknown = true)
data class YieldPoolFieldsDex(
    val firstAsset: String,
    val secondAsset: String,
    // extra data, like fees, pool type, and so on.
) : YieldPoolFields

@JsonIgnoreProperties(ignoreUnknown = true)
data class YieldPoolFieldsStormTrade(
    val asset: String,
    val assetName: String,
    val decimals: Int,
    val lpJettonMaster: String,
    val quoteAssetId: String
) : YieldPoolFields