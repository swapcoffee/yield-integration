//package com.example.yield.newprotocol
//
//import com.example.yield.blockchain.records.BlockchainRecord
//import com.example.yield.blockchain.parsers.BlockchainRecordParser
//import com.example.yield.blockchain.TxContext
//import ru.tinkoff.kora.common.Component
//
//@Component
//class NewProtocolTradingStatsRecordParser : BlockchainRecordParser {
//    override fun isBelongsTo(context: TxContext, input: BlockchainRecordParser.Input): Boolean {
//        return false
//        TODO("Implement me")
//    }
//
//    override suspend fun parse(context: TxContext, input: BlockchainRecordParser.Input): List<BlockchainRecord> {
//        return emptyList()
//        TODO("Implement me")
//    }
//}