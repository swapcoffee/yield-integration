package com.example.configuration

import ru.tinkoff.kora.config.common.annotation.ConfigSource

@ConfigSource("redis")
data class RedisConfiguration(
    val host: String,
    val port: Int
)