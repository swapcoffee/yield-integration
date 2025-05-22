package com.example.protocols.stormtrade

import com.example.dto.db.LiquidityPool
import com.example.dto.yield.YieldPoolFieldsStormTrade
import com.example.dto.yield.YieldProtocols
import com.example.dto.yield.YieldTradingStatistics
import com.example.loader.LoadedData
import com.example.loader.LoaderService
import com.example.repository.PoolsRepository
import com.example.service.ConversionServiceStub
import com.example.service.YieldBoostsService
import com.example.service.YieldTradingStatisticsService
import com.example.utils.ObjectMappers
import com.example.utils.dateToLong
import com.example.utils.getLogger
import com.example.utils.http.AsyncHttpClient
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import ru.tinkoff.kora.common.Component
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@Component
class StormTradeLoaderService(
    private val yieldBoostsService: YieldBoostsService,
    private val yieldTradingStatisticsService: YieldTradingStatisticsService,
    private val httpClient: AsyncHttpClient,
    private val repository: PoolsRepository,
    private val conversionService: ConversionServiceStub
) : LoaderService {

    companion object {
        private const val STORMTRADE = "stormtrade"
        private const val STORMTRADE_BASE_URL = "https://api5.storm.tg/api"
    }

    private val logger = getLogger()

    override val workerName: String = "stormtrade"
    override val workFrequencyMillis: Long = TimeUnit.MINUTES.toMillis(10)

    override suspend fun doWork() {
        try {
            logger.info("Starting StormTrade data loading...")
            
            val knownPools = repository.selectLiquidityPoolsByProtocol(STORMTRADE)
                .associateBy { it.poolAddress }

            // Fetch liquidity sources configuration
            val config = fetchConfig()
            logger.info("Fetched ${config.liquiditySources.size} liquidity sources from StormTrade")

            // Fetch current vault data
            val vaults = fetchVaults()
            logger.info("Fetched ${vaults.size} vaults from StormTrade")

            // Create pools that don't exist yet
            val newPoolsCreated = createNewPools(config, knownPools)
            if (newPoolsCreated > 0) {
                logger.info("Created $newPoolsCreated new StormTrade pools")
            }

            // Calculate statistics for each vault
            val poolStatistics = calculateVaultStatistics(vaults)
            logger.info("Calculated statistics for ${poolStatistics.size} vaults")

            // Save data
            yieldTradingStatisticsService.saveData(poolStatistics)
            yieldBoostsService.saveData(emptyList()) // StormTrade doesn't have traditional boosts

            logger.info("StormTrade data loading completed successfully")
            
        } catch (e: Exception) {
            logger.error("Error loading StormTrade data: ${e.message}", e)
            // Don't rethrow - we want the loader to continue running
        }
    }

    private suspend fun fetchConfig(): StormTradeConfigResponse {
        val response = httpClient.get("$STORMTRADE_BASE_URL/config").body()
        return ObjectMappers.DEFAULT.readValue(response, StormTradeConfigResponse::class.java)
    }

    private suspend fun fetchVaults(): List<StormTradeVault> {
        val response = httpClient.get("$STORMTRADE_BASE_URL/vaults").body()
        return ObjectMappers.DEFAULT.readValue(response, Array<StormTradeVault>::class.java).toList()
    }

    private suspend fun createNewPools(
        config: StormTradeConfigResponse, 
        knownPools: Map<String, LiquidityPool>
    ): Int {
        var created = 0
        for (liquiditySource in config.liquiditySources) {
            val vaultAddress = liquiditySource.vaultAddress
            if (!knownPools.containsKey(vaultAddress)) {
                val poolData = YieldPoolFieldsStormTrade(
                    asset = liquiditySource.asset.assetId,
                    assetName = liquiditySource.asset.name,
                    decimals = liquiditySource.asset.decimals,
                    lpJettonMaster = liquiditySource.lpJettonMaster,
                    quoteAssetId = liquiditySource.quoteAssetId
                )
                
                val newPool = LiquidityPool(
                    YieldProtocols.STORMTRADE,
                    vaultAddress,
                    0,
                    Long.MAX_VALUE,
                    ObjectMappers.DEFAULT.writeValueAsString(poolData)
                )
                
                repository.insertLiquidityPool(newPool)
                created++
                logger.debug("Created new pool for vault: $vaultAddress (${liquiditySource.asset.name})")
            }
        }
        return created
    }

    private fun calculateVaultStatistics(vaults: List<StormTradeVault>): List<LoadedData<YieldTradingStatistics>> {
        return vaults.map { vault ->
            try {
                // Calculate TVL from vault balances  
                val lockedBalance = vault.lockedBalance.toBigIntegerOrNull()?.toDouble() ?: 0.0
                val freeBalance = vault.freeBalance.toBigIntegerOrNull()?.toDouble() ?: 0.0
                val totalBalance = lockedBalance + freeBalance
                
                // Convert to USD using the asset ID
                val assetId = vault.config.asset.assetId
                val tvlUsd = conversionService.getUsdAmount(assetId, totalBalance)
                
                // Parse APR from vault data
                val yearlyApr = parseApr(vault.apr.year)
                
                val statistics = YieldTradingStatistics(
                    apr = yearlyApr,
                    boostApr = 0.0, // StormTrade doesn't have traditional boosts
                    lpApr = yearlyApr,
                    volumeUsd = 0.0, // Volume tracking would require blockchain parsing
                    feesUsd = 0.0,   // Fee tracking would require blockchain parsing  
                    tvlUsd = tvlUsd
                )
                
                LoadedData(vault.address, statistics, true)
                
            } catch (e: Exception) {
                logger.error("Error calculating statistics for vault ${vault.address}: ${e.message}", e)
                // Return empty statistics for this vault
                LoadedData(vault.address, YieldTradingStatistics.EMPTY, false)
            }
        }
    }

    private fun parseApr(aprString: String): Double {
        return try {
            // Handle potential "NaN" values from API
            if (aprString.equals("NaN", ignoreCase = true)) {
                0.0
            } else {
                aprString.toDoubleOrNull() ?: 0.0
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse APR value: $aprString")
            0.0
        }
    }

    // Data classes for StormTrade API responses (using camelCase to match API)
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class StormTradeConfigResponse(
        val liquiditySources: List<StormTradeLiquiditySource>
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class StormTradeLiquiditySource(
        val asset: StormTradeAsset,
        val vaultAddress: String,
        val quoteAssetId: String,
        val lpJettonMaster: String
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class StormTradeAsset(
        val name: String,
        val decimals: Int,
        val assetId: String
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class StormTradeVault(
        val address: String,
        val rate: String,
        val lockedBalance: String,
        val freeBalance: String,
        val apr: StormTradeApr,
        val predictedRoi: String,
        val config: StormTradeLiquiditySource
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class StormTradeApr(
        val week: String,
        val month: String,
        val year: String
    )
} 