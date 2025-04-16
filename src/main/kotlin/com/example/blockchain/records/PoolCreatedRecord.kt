package com.example.blockchain.records

import com.example.dto.db.LiquidityPool

data class PoolCreatedRecord(val item: LiquidityPool): BlockchainRecord {
}