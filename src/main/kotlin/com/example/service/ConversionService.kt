package com.example.service

import ru.tinkoff.kora.common.Component
import java.math.BigInteger

// This is method definitions, not a real implementation
@Component
class ConversionService {

    fun getUsdAmount(token: String, amount: Double): Double {
        return amount * 1.0
    }

    fun getUsdAmount(token: String, amount: BigInteger): Double {
        return amount.toDouble() / 1e9 * 1.0
    }
}