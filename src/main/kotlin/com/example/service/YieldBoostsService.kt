package com.example.service

import com.example.loader.LoadedData
import com.fasterxml.jackson.core.type.TypeReference
import redis.clients.jedis.JedisPool
import redis.clients.jedis.params.SetParams
import ru.tinkoff.kora.common.Component
import com.example.dto.yield.YieldBoost
import com.example.utils.ObjectMappers
import com.example.utils.work

@Component
class YieldBoostsService(
    private val jedisPool: JedisPool,
) {

    private companion object {
        fun poolBoostKey(poolAddress: String): String = "yield.b.$poolAddress"
        const val EXPIRATION_SECONDS = 60 * 90L // 90 minutes
        val DECODE_TYPE_REFERENCE = object : TypeReference<List<YieldBoost>>() {}
        val cacheExpirationParamForce: SetParams = SetParams().ex(EXPIRATION_SECONDS)
        val cacheExpirationParam: SetParams = SetParams().nx().ex(EXPIRATION_SECONDS)
    }

    fun saveData(items: List<LoadedData<List<YieldBoost>>>) {
        jedisPool.work {
            items.forEach {
                val param = if (it.needRewrite) {
                    cacheExpirationParamForce
                } else {
                    cacheExpirationParam
                }
                val key = poolBoostKey(it.id)
                set(
                    key,
                    ObjectMappers.DEFAULT.writeValueAsString(it.data),
                    param
                )
            }
        }
    }

    fun getData(key: String): List<YieldBoost>? {
        return jedisPool.work {
            get(poolBoostKey(key))
        }?.let {
            ObjectMappers.DEFAULT.readValue(it, DECODE_TYPE_REFERENCE)
        }
    }
}