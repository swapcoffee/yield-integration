package com.example.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.ton.java.cell.Cell
import org.ton.java.cell.CellSlice
import org.ton.java.tonlib.types.RawMessage
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import java.time.LocalDateTime

fun dateToLong(date: LocalDateTime): Long {
    return date.year * 1_00_00_00L + date.monthValue * 1_00_00L + date.dayOfMonth * 1_00L + date.hour
}

inline fun <reified T> T.getLogger(): Logger = LoggerFactory.getLogger(T::class.java)

fun deserializeMessageToSlice(msg: RawMessage): CellSlice? {
    val data = msg.msg_data
    return try {
        CellSlice.beginParse(Cell.fromBocBase64(data.body))
    } catch (t: Throwable) {
        null
    }
}

fun <T> JedisPool.work(block: Jedis.() -> T): T {
    return this.resource.use { jedis ->
        block(jedis)
    }
}