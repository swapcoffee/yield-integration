package com.example.blockchain.parsers

import com.example.blockchain.TxContext
import org.ton.java.cell.CellSlice
import org.ton.java.tonlib.types.RawMessage
import org.ton.java.tonlib.types.RawTransaction

interface BlockchainRecordParser {

    data class Input(
        val inMessage: RawMessage,
        val opcode: Long,
        val queryID: Long,
        val inSlice: CellSlice,
        val tx: RawTransaction,
    )

    fun validateTx(): Boolean = true

    fun isBelongsTo(context: TxContext, input: Input): Boolean

    suspend fun parse(context: TxContext, input: Input): List<com.example.blockchain.records.BlockchainRecord>

}
