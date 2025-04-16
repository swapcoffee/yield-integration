package com.example.protocols.stonfi

import com.example.dto.yield.YieldBoost
import com.example.dto.yield.YieldBoostDex
import com.example.dto.yield.YieldPoolFieldsDex
import com.example.dto.yield.YieldTradingStatistics
import com.example.loader.LoadedData
import com.example.loader.LoaderService
import com.example.repository.PoolsRepository
import com.example.service.ConversionService
import com.example.service.YieldBoostsService
import com.example.service.YieldTradingStatisticsService
import com.example.utils.ObjectMappers
import com.example.utils.dateToLong
import com.example.utils.http.AsyncHttpClient
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.ton.java.address.Address
import org.ton.java.tonlib.Tonlib
import org.ton.java.tonlib.types.TvmStackEntryNumber
import ru.tinkoff.kora.common.Component
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@Component
class StonfiV1LoaderService(
    private val yieldBoostsService: YieldBoostsService,
    private val yieldTradingStatisticsService: YieldTradingStatisticsService,
    private val httpClient: AsyncHttpClient,
    private val repository: PoolsRepository,
    private val tonlib: Tonlib,
    private val conversionService: ConversionService
) : LoaderService {

    companion object {
        private val STONFI_V1 = "stonfi_v1"

        val FARM_QUERY =
            """
            {
              "jsonrpc": "2.0",
              "id": 0,
              "method": "farm.list",
              "params": {}
            }
            """.trimIndent()

    }

    override val workerName: String = "stonfi"
    override val workFrequencyMillis: Long = TimeUnit.MINUTES.toMillis(10)

    override suspend fun doWork() {
        val knownPools = repository.selectLiquidityPoolsByProtocol(STONFI_V1)
            .associateBy { it.poolAddress }
        val now = dateToLong(LocalDateTime.now())
        val prev24H = dateToLong(LocalDateTime.now().minusHours(24))

        val aprs = ObjectMappers.DEFAULT.readValue(
            httpClient.post("https://rpc.ston.fi/", FARM_QUERY).body(),
            FarmResponse::class.java
        )
            .result
            .farms
            .filter { f -> f.status == "operational" }

        val aprMap = aprs.associateBy({ it.poolAddress }, { (it.apy?.toDoubleOrNull() ?: 0.0) * 100.0 })
        val boosts = aprs.asSequence().flatMap { it.rewards.map { jt -> it to jt } }
            .filter {
                it.first.poolAddress != null &&
                        it.first.minterAddress != null &&
                        it.first.minStakeDurationS != null &&
                        it.second.estimatedEndTimestamp != null &&
                        it.second.rewardRate24h != null
            }
            .map {
                YieldBoostDex(
                    it.second.address, // reward token
                    it.second.estimatedEndTimestamp!!, // when ends
                    aprMap[it.first.poolAddress]!!,
                    it.second.rewardRate24h!!,
                    it.first.poolAddress!!, // for which pool
                    it.first.minterAddress!!, // nft collection
                    it.first.minStakeDurationS!!.toLongOrNull()
                )
            }.groupBy {
                it.poolAddress
            }
            .map {
                LoadedData<List<YieldBoost>>(
                    it.key,
                    it.value,
                    true
                )
            }

        val poolToStat = repository.selectPoolsStatsTradingVolume(STONFI_V1, prev24H, now)
            .groupBy { it.poolAddress }
            .filter { knownPools.containsKey(it.key) }
            .map { (k, v) -> k to v.sumOf { it.usdVolumeAmount } }
            .map {
                val pool = knownPools[it.first]!!
                val poolData = ObjectMappers.DEFAULT.readValue(pool.extraData, YieldPoolFieldsDex::class.java)
                val tvl = calculateTvl(it.first, poolData.firstAsset, poolData.secondAsset)
                it.first to YieldTradingStatistics(
                    it.second / tvl * 100.0 + (aprMap[it.first] ?: 0.0),
                    aprMap[it.first] ?: 0.0,
                    it.second / tvl * 100.0,
                    it.second,
                    it.second * 0.01,
                    tvl
                )
            }
            .map {
                LoadedData(it.first, it.second, true)
            }

        yieldTradingStatisticsService.saveData(poolToStat)
        yieldBoostsService.saveData(boosts)
    }

    private fun calculateTvl(address: String, token0: String, token1: String): Double {
        val res = tonlib.runMethod(Address.of(address), "get_pool_data")
        require(res.exit_code == 0L) { "Failed to get pool data for $address, exit code: ${res.exit_code}" }
        val token0amount = (res.stack[0] as TvmStackEntryNumber).number
        val token1amount = (res.stack[0] as TvmStackEntryNumber).number
        return conversionService.getUsdAmount(token0, token0amount) +
                conversionService.getUsdAmount(token1, token1amount)
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class StonfiResponse(
        val result: StonfiResultResponse
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class StonfiResultResponse(
        val pools: List<StonfiPoolResponse>
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class StonfiPoolResponse(
        val address: String,
        @JsonProperty("apy_1d")
        val apy1d: String?,
        val tvl: String?,
        @JsonProperty("volume_24h_usd")
        val volumeUsd: String?,
        val lpFee: String?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class FarmResponse(
        val result: FarmResult,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class FarmResult(
        val farms: List<Farm>
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Farm(
        @JsonProperty("minter_address")
        val minterAddress: String?,
        val version: String?,
        @JsonProperty("pool_address")
        val poolAddress: String?,
        val rewards: List<Reward>,
        val status: String,
        @JsonProperty("min_stake_duration_s")
        val minStakeDurationS: String?,
        @JsonProperty("apy")
        val apy: String?,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Reward(
        val address: String,
        @JsonProperty("status")
        val status: String?,
        @JsonProperty("reward_rate_24h")
        val rewardRate24h: BigInteger?,
        @JsonProperty("estimated_end_timestamp")
        val estimatedEndTimestamp: Instant?
    )
}