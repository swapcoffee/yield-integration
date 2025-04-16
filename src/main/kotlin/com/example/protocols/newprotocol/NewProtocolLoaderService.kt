package com.example.protocols.newprotocol

import com.example.loader.LoaderService
import com.example.repository.PoolsRepository
import com.example.service.YieldBoostsService
import com.example.service.YieldTradingStatisticsService
import com.example.utils.ObjectMappers
import com.example.utils.dateToLong
import com.example.utils.http.AsyncHttpClient
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import ru.tinkoff.kora.common.Component
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit


@Component
class NewProtocolLoaderService(
    private val yieldBoostsService: YieldBoostsService,
    private val yieldTradingStatisticsService: YieldTradingStatisticsService,
    private val httpClient: AsyncHttpClient,
    private val repository: PoolsRepository,
) : LoaderService {

    override val workerName: String = "new-protocol"

    override val workFrequencyMillis: Long = TimeUnit.MINUTES.toMillis(10)

    override suspend fun doWork() {
        // todo: if you use parser, than use request to DB to get all liquidity pools
        val items = repository.selectLiquidityPoolsByProtocol("protocolName")

        // otherwise, use HTTP request to fetch data from your REST API, or our Indexer API
        val response = httpClient.postAsync("https://protocol.name/v1/data", "{}").await()
        val items2 = ObjectMappers.DEFAULT.readValue(response.body(), PoolsList::class.java)

        // Same with metrics
        // Either use:
        val now = dateToLong(LocalDateTime.now())
        val prev24H = dateToLong(LocalDateTime.now().minusHours(24))
        val knownPools = repository.selectLiquidityPoolsByProtocol("protocolName")
            .associateBy { it.poolAddress }
        repository.selectPoolsStatsTradingVolume("protocolName", prev24H, now)
            .groupBy { it.poolAddress }
            .filter { knownPools.containsKey(it.key) }
            .map { (k, v) -> k to v.sumOf { it.usdVolumeAmount } }

        // Or use an HTTP request to fetch data from your REST API or our Indexer API.

        // If you're using a parser and it doesn't provide enough data, feel free to use HTTP requests.
        // For example, if you want to calculate TVL, which depends on the states of some contracts, you can use an HTTP request.

        // Boost or farms always must be loaded from external sources
        val boostResponse = httpClient.postAsync("https://protocol.name/v1/data/boost", "{}").await()
        val boosts = ObjectMappers.DEFAULT.readValue(response.body(), InternalBoostMapperClass::class.java)

        // TODO:
        //  combine all data above to calculate metrics, and write them into
        //  yieldBoostsService
        //  and yieldTradingStatisticsService

        // TODO:
        //  If you're not using parser and receives all pools from API,
        //  then save data into DB:
        //      repository.insertLiquidityPool(...)
        TODO("Implement me")
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PoolsList(
        val pools: List<String>
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class InternalBoostMapperClass(
        val pool: String,
        val bostReward: String
    )

}