package com.example.dto.yield

data class YieldTradingStatistics(
    val apr: Double, // multiplied by 100, i.e. 100% APR and so on....
    val boostApr: Double, // у стонфи нету буста, есть фарминг, но там надо залочить отдельной транзакцией
    val lpApr: Double,
    val volumeUsd: Double,
    val feesUsd: Double,
    val tvlUsd: Double
) {
    companion object {
        val EMPTY = YieldTradingStatistics(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    }
}