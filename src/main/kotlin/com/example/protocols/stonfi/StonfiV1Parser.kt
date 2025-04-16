package com.example.protocols.stonfi

import com.example.blockchain.TxContext
import com.example.blockchain.parsers.BlockchainRecordParser
import com.example.blockchain.records.BlockchainRecord
import com.example.blockchain.records.PoolCreatedRecord
import com.example.blockchain.records.TradingStatUpdatedRecord
import com.example.dto.db.LiquidityPool
import com.example.dto.db.PoolStatsTradingVolume
import com.example.dto.yield.YieldPoolFieldsDex
import com.example.dto.yield.YieldProtocols
import com.example.service.ConversionServiceStub
import com.example.utils.ObjectMappers
import com.example.utils.dateToLong
import org.ton.java.address.Address
import org.ton.java.cell.Cell
import org.ton.java.cell.CellSlice
import org.ton.java.tonlib.Tonlib
import org.ton.java.tonlib.types.TvmStackEntrySlice
import ru.tinkoff.kora.common.Component
import java.time.LocalDateTime

@Component
class StonfiV1Parser(
    private val tonlib: Tonlib,
    private val conversionService: ConversionServiceStub
) : BlockchainRecordParser {

    private companion object {
        const val STONFI_V1_SWAP_OPCODE = 0x25938561L
    }

    override fun isBelongsTo(context: TxContext, input: BlockchainRecordParser.Input): Boolean {
        return input.opcode == STONFI_V1_SWAP_OPCODE
    }

    override suspend fun parse(context: TxContext, input: BlockchainRecordParser.Input): List<BlockchainRecord> {
        val records = ArrayList<BlockchainRecord>()
        val poolAddress = input.inMessage.destination.account_address
        if (!context.stonfiKnownPools.contains(poolAddress)) {
            val tokens = extractStonfiV1PoolData(input.inMessage.destination.account_address)
            val newPool = PoolCreatedRecord(
                LiquidityPool(
                    YieldProtocols.STONFI_V1,
                    input.inMessage.destination.account_address,
                    0,
                    Long.MAX_VALUE,
                    ObjectMappers.DEFAULT.writeValueAsString(YieldPoolFieldsDex(tokens.first, tokens.second))
                )
            )
            records.add(newPool)
            context.stonfiKnownPools.add(poolAddress)
        }
        val volume = PoolStatsTradingVolume(
            YieldProtocols.STONFI_V1,
            poolAddress,
            dateToLong(LocalDateTime.now()),
            // this is example of how real parser work
            conversionService.getUsdAmount("", 100.0),
            1,
            ""
        )
        records.add(TradingStatUpdatedRecord(volume))

        return records
    }

    private fun extractStonfiV1PoolData(address: String): Pair<String, String> {
        val res = tonlib.runMethod(Address.of(address), "get_pool_data")
        require(res.exit_code == 0L) { "Failed to get pool data for $address, exit code: ${res.exit_code}" }
        val token0 = extractMasterDataFromWallet(
            CellSlice.beginParse(Cell.fromBocBase64((res.stack[2] as TvmStackEntrySlice).slice.bytes)).loadAddress()
                .toRaw()
        )
        val token1 = extractMasterDataFromWallet(
            CellSlice.beginParse(Cell.fromBocBase64((res.stack[2] as TvmStackEntrySlice).slice.bytes)).loadAddress()
                .toRaw()
        )
        return token0 to token1
    }

    private fun extractMasterDataFromWallet(address: String): String {
        val res = tonlib.runMethod(Address.of(address), "get_wallet_data")
        require(res.exit_code == 0L) { "Failed to get master data for $address, exit code: ${res.exit_code}" }
        val masterAddress =
            CellSlice.beginParse(Cell.fromBocBase64((res.stack[2] as TvmStackEntrySlice).slice.bytes)).loadAddress()
                .toRaw()
        return masterAddress
    }
}