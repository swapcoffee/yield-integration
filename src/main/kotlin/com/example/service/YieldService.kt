package com.example.service

import com.example.api.model.*
import com.example.dto.yield.*
import com.example.protocols.stonfi.StonfiV1Service
import com.example.repository.PoolsRepository
import com.example.utils.ObjectMappers
import com.example.utils.getLogger
import kotlinx.coroutines.runBlocking
import ru.tinkoff.kora.common.Component
import java.math.BigInteger

// Note: this is example implementation
// It's always return all records for specified protocol, and doesn't filter out by any other criteria
// Don't use it in production!
@Component
class YieldService(
    private val repository: PoolsRepository,
    private val yieldBoostsService: YieldBoostsService,
    private val yieldTradingStatisticsService: YieldTradingStatisticsService,
    private val tokenService: TokenService,
    private val stonfiV1Service: StonfiV1Service
    // TODO: add you service here, like stonfiV1Service
) {

    private val logger = getLogger()

    @Volatile
    private var pools: Map<String, PoolHolder>? = null

    init {
        val t = Thread {
            logger.info("YieldService is running")
            while (true) {
                try {
                    runBlocking {
                        reloadData()
                    }
                    Thread.sleep(10_000)
                } catch (e: Exception) {
                    logger.error("Error in YieldService: ${e.message}", e)
                }
            }
        }
        t.start()
    }

    /**
     * Fetches data from local stored pools, and returns all data in brief format.
     * <br>
     * Note: it always returns all data, to simplify integration process.
     * Consider to change this method as you wish.
     */
    fun getAllPools(protocols: List<YieldProtocols>): List<ApiYieldSearchWrapper> {
        val pools = pools ?: return emptyList()
        return pools
            .map {
                val poolHolder = it.value
                val stat = ApiPoolStatistics(
                    poolHolder.stat.tvlUsd,
                    poolHolder.stat.volumeUsd,
                    poolHolder.stat.feesUsd,
                    poolHolder.stat.apr,
                    poolHolder.stat.lpApr,
                    poolHolder.stat.boostApr
                )
                ApiYieldSearchWrapper(stat, mapper(poolHolder))
            }
    }

    /**
     * Returns BRIEF information about all positions for specified user for protocols.
     * <br>
     * You need to modify this function, and call your service to get user positions.
     */
    suspend fun getAllPoolsForUser(protocols: List<YieldProtocols>, userAddress: String): List<ApiYieldSearchWrapper> {
        val pools = pools ?: return emptyList()

        val userPositions = ArrayList<ApiYieldSearchWrapper>()

        val stonfiPositions = if (protocols.contains(YieldProtocols.STONFI_V1)) {
            stonfiV1Service.getUserPositions(userAddress).associateBy { it.first }
        } else {
            emptyMap()
        }.filter { it.key in pools.keys }
            .mapNotNull { (poolAddress, balance) ->
                val poolHolder = pools[poolAddress] ?: return@mapNotNull null
                val stat = ApiPoolStatistics(
                    poolHolder.stat.tvlUsd,
                    balance.second,
                    0.0,
                    poolHolder.stat.apr,
                    poolHolder.stat.lpApr,
                    poolHolder.stat.boostApr
                )
                ApiYieldSearchWrapper(stat, mapper(poolHolder))
            }
        // TODO: implement interaction with your protocol in a same way
        //  implement `getUserPositions` as you wish, you may pass any arguments here
        userPositions.addAll(stonfiPositions)

        return userPositions
    }

    /**
     * Returns detailed information about pool.
     * <br>
     * When this method executed you must provide latest known info.
     */
    suspend fun getPool(poolAddress: String): ApiYieldDetails {
        val pools = pools ?: throw NullPointerException("Not found pool: $poolAddress")
        val poolHolder = pools[poolAddress] ?: throw NullPointerException("Not found pool: $poolAddress")
        val stat = ApiPoolStatistics(
            poolHolder.stat.tvlUsd,
            poolHolder.stat.volumeUsd,
            poolHolder.stat.feesUsd,
            poolHolder.stat.apr,
            poolHolder.stat.lpApr,
            poolHolder.stat.boostApr
        )
        return ApiYieldDetails(
            stat,
            mapperDetail(poolHolder) // <- for readability this method is separated
        )
    }

    private suspend fun mapperDetail(item: PoolHolder): ApiYieldDetailsPool {
        return when (val it = item.poolInfo) {
            is YieldPoolFieldsDex -> {
                val poolInfo = mapper(item) as ApiPool
                ApiYieldDetailsDex(
                    poolInfo,
                    item.boosts.map { boost ->
                        val boostDex = boost as YieldBoostDex
                        ApiBoost(
                            item.poolAddress,
                            tokenService.toApiModel(boostDex.asset),
                            (boostDex.rewardRatePerDay / BigInteger.valueOf(24 * 60 * 60)).toString(),
                            boostDex.endsIn.toEpochMilli(),
                            boostDex.rewardRatePerDay.toBigDecimal(),
                            boostDex.apr.toBigDecimal(),
                            null,
                            null,
                            null,
                            null
                        )
                    },
                    stonfiV1Service.getTotalSupply(item.poolAddress)
                )
            }

            else -> TODO("When you implement new YieldPoolFields_<Protocol>, add it here, and provide latest data to user")
        }
    }

    /**
     * Returns detailed information about user positions in provided pools.
     */
    suspend fun getUserDetails(poolAddress: String, userAddress: String): ApiYieldUserDetails {
        val pools = pools ?: throw NullPointerException("Not found pool: $poolAddress")
        val poolHolder = pools[poolAddress] ?: throw NullPointerException("Not found pool: $poolAddress")

        return if (poolHolder.protocol == YieldProtocols.STONFI_V1) {
            val userData = stonfiV1Service.getUserPosition(poolAddress, userAddress)
            ApiYieldUserDetails(
                ApiYieldUserDetailsDex(
                    userData.first, // user's lp amount
                    userData.second, // user's jetton wallet
                    emptyList() // boosts
                )
            )
        } else {
            // TODO: call yourProtocolService.getUserPosition, which may return any data, which will be mapped into ApiYieldUserDetails
            TODO("Implement me")
        }
    }

    /**
     * POST request.
     * This method must build transaction, register it in our tracing system and returns to user.
     */
    suspend fun processUserRequest(
        poolAddress: String,
        userAddress: String,
        request: ApiYieldInteractionRequestRequestData
    ): List<ApiTransactionResponse> {
        val pools = pools ?: throw NullPointerException("Not found pool: $poolAddress")
        val poolHolder = pools[poolAddress] ?: throw NullPointerException("Not found pool: $poolAddress")

        return if (poolHolder.protocol == YieldProtocols.STONFI_V1) {
            when (request) {
                is ApiDexPoolLiquidityWithdrawalRequest -> listOf(
                    stonfiV1Service.closeUserPosition(
                        poolAddress,
                        userAddress,
                        request.lpAmount.toBigInteger()
                    )
                )

                is ApiDexPoolLiquidityProvisioningRequest -> TODO("Not needed in this example")
                is ApiStonfiFarmRequest -> TODO("Not needed in this example")
                is ApiYieldInteractionRequestDexStonfiWithdrawFromStaking -> TODO("Not needed in this example")
                else -> throw IllegalArgumentException("Unsupported operation")
            }
        } else {
            // TODO if this pool is from your protocol consider to add when(request) like this:
            /*
                when(request) {
                    is YourApiPostRequestCommand1 -> yourProtocolService.buildTransactionForCommand1(poolAddress, userAddress, request)
                    is YourApiPostRequestCommand2 -> yourProtocolService.buildTransactionForCommand2(poolAddress, userAddress, request)
                    else -> throw IllegalArgumentException("Unsupported operation")
                }
            */
            TODO("Implement me")
        }

    }

    // This is internal function, don't modify this
    private suspend fun reloadData() {
        pools = repository.selectAllLiquidityPools().associate {
            val poolData = ObjectMappers.DEFAULT.readValue(it.extraData, YieldPoolFields::class.java)
            it.poolAddress to PoolHolder(
                it.protocol,
                it.poolAddress,
                poolData,
                yieldBoostsService.getData(it.poolAddress) ?: emptyList(),
                yieldTradingStatisticsService.getData(it.poolAddress) ?: YieldTradingStatistics.EMPTY
            )
        }
    }

    private fun mapper(item: PoolHolder): ApiYieldSearchWrapperPool {
        return when (val it = item.poolInfo) {
            is YieldPoolFieldsDex -> ApiPool(
                item.protocol.value,
                item.poolAddress,
                ApiPoolType.PUBLIC,
                ApiAmmType.CONSTANT_PRODUCT,
                listOf(tokenService.toApiModel(it.firstAsset), tokenService.toApiModel(it.secondAsset)),
                listOf(0.0, 0.0),
                ApiPoolFees(0.0),
                null,
                null,
                null
            )

            else -> TODO("Implement your YieldPoolFields, which returns main info about your pool")
        }
    }

    private data class PoolHolder(
        val protocol: YieldProtocols,
        val poolAddress: String,
        val poolInfo: YieldPoolFields,
        val boosts: List<YieldBoost>,
        val stat: YieldTradingStatistics
    )


}