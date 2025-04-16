package com.example.blockchain.handlers

import com.example.blockchain.DeferredTx
import com.example.blockchain.records.BlockchainRecord
import kotlin.reflect.KClass

abstract class BlockchainRecordHandler<T : BlockchainRecord>(val recordClass: KClass<out T>) {

    abstract fun handle(records: List<T>): List<DeferredTx>

    @Suppress("UNCHECKED_CAST")
    fun handleUnsafe(records: List<BlockchainRecord>) = handle(records as List<T>)

}
