package com.example.blockchain.handlers

import com.example.blockchain.DeferredTx
import com.example.blockchain.records.PoolCreatedRecord
import com.example.repository.PoolsRepository
import ru.tinkoff.kora.common.Component

@Component
class PoolCreatedRecordHandler(
    private val repository: PoolsRepository
) : BlockchainRecordHandler<PoolCreatedRecord>(PoolCreatedRecord::class) {
    override fun handle(records: List<PoolCreatedRecord>): List<DeferredTx> {
        if (records.isEmpty()) {
            return emptyList()
        }
        return records.map { record ->
            DeferredTx {
                repository.insertLiquidityPool(record.item)
            }
        }
    }
}