package com.example.blockchain.handlers

import com.example.blockchain.DeferredTx
import com.example.blockchain.records.TradingStatUpdatedRecord
import com.example.repository.PoolsRepository
import ru.tinkoff.kora.common.Component

@Component
class TradingStatRecordHandler(
    private val repository: PoolsRepository
) : BlockchainRecordHandler<TradingStatUpdatedRecord>(TradingStatUpdatedRecord::class) {
    override fun handle(records: List<TradingStatUpdatedRecord>): List<DeferredTx> {
        if (records.isEmpty()) {
            return emptyList()
        }
        return records.map { record ->
            DeferredTx {
                repository.insertPoolsStatsTradingVolume(record.item)
            }
        }
    }
}