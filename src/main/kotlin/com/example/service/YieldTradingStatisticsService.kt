package com.example.service

import com.example.dto.yield.YieldTradingStatistics
import com.example.loader.LoadedData
import com.example.utils.ObjectMappers
import com.example.utils.work
import redis.clients.jedis.JedisPool
import redis.clients.jedis.params.SetParams
import ru.tinkoff.kora.common.Component

@Component
class YieldTradingStatisticsService(
    private val jedisPool: JedisPool
) {

    private companion object {
        fun poolStatKey(poolAddress: String): String = "yield.s.$poolAddress"
        const val EXPIRATION_SECONDS = 60 * 90L // 90 minutes
        val cacheExpirationParamForce: SetParams = SetParams().ex(EXPIRATION_SECONDS)
        val cacheExpirationParam: SetParams = SetParams().nx().ex(EXPIRATION_SECONDS)
    }

    fun saveData(items: List<LoadedData<YieldTradingStatistics>>) {
        jedisPool.work {
            items.forEach {
                val param = if (it.needRewrite) {
                    cacheExpirationParamForce
                } else {
                    cacheExpirationParam
                }
                val key = poolStatKey(it.id)
                set(
                    key,
                    ObjectMappers.DEFAULT.writeValueAsString(it.data),
                    param
                )
            }
        }
    }

    fun getData(keys: List<String>): List<YieldTradingStatistics?> {
        return jedisPool.work {
            keys.map { k ->
                get(poolStatKey(k))
            }
        }.map {
            if (it != null) ObjectMappers.DEFAULT.readValue(it, YieldTradingStatistics::class.java) else null
        }
    }

    fun getData(key: String): YieldTradingStatistics? {
        return jedisPool.work {
            get(poolStatKey(key))
        }?.let {
            ObjectMappers.DEFAULT.readValue(it, YieldTradingStatistics::class.java)
        }
    }
}