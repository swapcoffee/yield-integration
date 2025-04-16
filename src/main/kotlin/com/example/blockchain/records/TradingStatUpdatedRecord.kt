package com.example.blockchain.records

import com.example.dto.db.PoolStatsTradingVolume

data class TradingStatUpdatedRecord(val item: PoolStatsTradingVolume) : BlockchainRecord