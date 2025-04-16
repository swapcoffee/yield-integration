package com.example.protocols.newprotocol

import com.example.blockchain.TxContext
import com.example.blockchain.parsers.BlockchainRecordParser
import com.example.blockchain.records.BlockchainRecord
import com.example.service.ConversionServiceStub
import org.ton.java.tonlib.Tonlib
import ru.tinkoff.kora.common.Component

@Component
class NewProtocolRecordParser(
    private val tonlib: Tonlib,
    private val conversionService: ConversionServiceStub
) : BlockchainRecordParser {

    private companion object {

    }

    override fun isBelongsTo(context: TxContext, input: BlockchainRecordParser.Input): Boolean {
        // NOTE:
        // Please do not use any queries to the database or any other external services in this context.

        // TODO: Implement a method that verifies whether the transaction belongs to our protocol.
        //  The method could use a pattern like: input.tx.address.accountAddress === "your-fabric-address"
        //  Or, it could check the opcode: input.opcode === CREATE_POOL
        //  Alternatively, it could use any other validation logic you see fit.
        return false
    }

    override suspend fun parse(context: TxContext, input: BlockchainRecordParser.Input): List<BlockchainRecord> {

        // TODO: Implement a method to extract the required data for saving in the database and later use.
        //  In this block, we guarantee that if check isBelongsTo is true, you can trust that you are processing exactly what is needed.
        //  For example, you could extract the liquidity pool address from the transaction, or any other relevant data.

        TODO()
    }
}