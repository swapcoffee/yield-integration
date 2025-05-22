import com.example.dto.db.LiquidityPool
import com.example.dto.yield.YieldPoolFieldsFarmixLending
import com.example.dto.yield.YieldProtocols
import com.example.dto.yield.YieldTradingStatistics
import com.example.loader.LoadedData
import com.example.loader.LoaderService
import com.example.repository.PoolsRepository
import com.example.service.ConversionServiceStub
import com.example.service.YieldBoostsService
import com.example.service.YieldTradingStatisticsService
import com.example.utils.ObjectMappers
import com.example.utils.http.AsyncHttpClient
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.ton.java.tonlib.Tonlib
import ru.tinkoff.kora.common.Component
import java.util.concurrent.TimeUnit

@Component
class FarmixLengingLoaderService(
    private val yieldBoostsService: YieldBoostsService,
    private val yieldTradingStatisticsService: YieldTradingStatisticsService,
    private val httpClient: AsyncHttpClient,
    private val repository: PoolsRepository,
    private val tonlib: Tonlib,
    private val conversionService: ConversionServiceStub
) : LoaderService {

    override val workerName: String = "farmix_lending"

    override val workFrequencyMillis: Long = TimeUnit.MINUTES.toMillis(5)

    override suspend fun doWork() {

        val poolsRaw = ObjectMappers.DEFAULT.readValue(
            httpClient.get("https://api.farmix.tg/bff/v1/pool/staker/all").body(),
            LendingPoolsRes::class.java
        )
            .pools

        // We suppose that pool if validTillUtcSeconds is in the past then the pool is considered disabled and will not be shown
        val pools = poolsRaw.map { p ->
            LiquidityPool(
                YieldProtocols.FARMIX_V1_LENDING,
                p.addr,
                0,
                if ((p.inited ?: false) && !(p.disabled ?: true) && !(p.deprecated ?: true) && !(p.paused ?: true)) Long.MAX_VALUE else 0,
                ObjectMappers.DEFAULT.writeValueAsString(YieldPoolFieldsFarmixLending(p.underlyingJettonAddr))
            )
        }

        val stats = poolsRaw
            .filter { p -> (p.inited ?: false) && !(p.disabled ?: true) && !(p.deprecated ?: true) && !(p.paused ?: true) }
            .map {
                // TODO(latter fees and volumes will be added)
                Pair(it.addr, YieldTradingStatistics(
                    (it.realAprPr ?: 0.0) * 100.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    (it.tvlUsd ?: 0.0),
                ))
            }
            .map {
                LoadedData(it.first, it.second, true)
            }


        // TODO(would be better to use transaction here)
        for (p in pools) {
            repository.insertLiquidityPool(p)
        }

        yieldTradingStatisticsService.saveData(stats);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class LendingPoolsRes(
        val pools: List<LendingPool>
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class LendingPool(
        val addr: String,
        @JsonProperty("tvl_usd")
        val tvlUsd: Double?,
        val paused: Boolean?,
        val disabled: Boolean?,
        val deprecated: Boolean?,
        val inited: Boolean?,
        @JsonProperty("real_apr_pr")
        val realAprPr: Double?,
        @JsonProperty("target_jetton_master_addr")
        val underlyingJettonAddr: String
    )
}
