package com.example.utils

import com.example.configuration.RedisConfiguration
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import ru.tinkoff.kora.common.DefaultComponent

interface RedisModule {
    @DefaultComponent
    fun jedisPool(config: RedisConfiguration) = create(config, 16, 2, 8)

    private fun create(config: RedisConfiguration, total: Int, minIdle: Int = total, maxIdle: Int = total) = JedisPool(
        JedisPoolConfig().apply {
            this.maxTotal = total
            this.minIdle = minIdle
            this.maxIdle = maxIdle
        },
        config.host,
        config.port,
        null,
        null
    )

}