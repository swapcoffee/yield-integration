package com.example.blockchain

import com.example.blockchain.handlers.BlockchainRecordHandler
import com.example.blockchain.parsers.BlockchainRecordParser
import com.example.dto.yield.YieldProtocols
import com.example.repository.PoolsRepository
import com.example.utils.getLogger
import kotlinx.coroutines.runBlocking
import org.ton.java.cell.CellSlice
import org.ton.java.tonlib.Tonlib
import org.ton.java.tonlib.types.BlockIdExt
import org.ton.java.tonlib.types.RawTransaction
import ru.tinkoff.kora.application.graph.All
import ru.tinkoff.kora.common.Component
import ru.tinkoff.kora.common.annotation.Root
import java.util.concurrent.*

// Note: this is example, never use it in production
@Root
@Component
class TxLoader(
    private val repository: PoolsRepository,
    private val parsers: All<BlockchainRecordParser>,
    handlers: All<BlockchainRecordHandler<*>>,
    private val tonlib: Tonlib
) {

    private val logger = getLogger()

    private val recordHandlers = handlers.groupBy { it.recordClass }

    private var lastMasterBlock: BlockIdExt? = null

    // shard -> seqno
    private val latestWorkchainShards: MutableMap<Long, Long> = ConcurrentHashMap()

    private val threadPool = Executors.newFixedThreadPool(10)

    private val txContext = TxContext()

    init {
        logger.info("Parsers: {}", parsers)
        logger.info("Handlers: {}", handlers)
        val thread = Thread {
            beforeStart()
            while (true) {
                try {
                    runBlocking {
                        handleNextBlock()
                    }
                } catch (e: Exception) {
                    logger.error("Error in handleNextBlock: ${e.message}", e)
                }
            }
        }
        thread.start()
    }

    fun beforeStart() {
        txContext.stonfiKnownPools =
            repository.selectAllLiquidityPools()
                .filter { it.protocol == YieldProtocols.STONFI_V1 }
                .map { it.poolAddress }
                .toMutableSet()
    }

    suspend fun handleNextBlock() {
        if (lastMasterBlock == null) {
            lastMasterBlock = tonlib.last.last
        }
        val localLastMasterBlock = lastMasterBlock!!

        val latestMasterBlock = tonlib.last.last
        if (localLastMasterBlock.seqno == latestMasterBlock.seqno) {
            return
        }
        val nextMasterBlock = tonlib.lookupBlock(localLastMasterBlock.seqno + 1, -1, 0L, 0)
        val txs = loadTxsForShard(nextMasterBlock).sortedBy { it.transaction_id.lt }
        logger.info("We are at: ${localLastMasterBlock.seqno}, known: ${latestMasterBlock.seqno}, dist: ${latestMasterBlock.seqno - localLastMasterBlock.seqno}, loaded Txs: ${txs.size}")
        lastMasterBlock = nextMasterBlock

        val recordsMap = parseTxs(txs).groupBy { it::class }
        val deferredTxs = mutableListOf<com.example.blockchain.DeferredTx>()
        for ((recordsKey, records) in recordsMap) {
            deferredTxs.addAll(recordHandlers[recordsKey]?.flatMap { it.handleUnsafe(records) } ?: emptyList())
        }
        for (d in deferredTxs) {
            d.execute()
        }
    }

    private fun loadTxsForShard(masterBlock: BlockIdExt): List<RawTransaction> {
        val shards = tonlib.getShards(masterBlock)
        val futures = mutableListOf<Future<List<RawTransaction>>>()
        for (shard in shards.shards) {
            val task: Callable<List<RawTransaction>> = Callable {
                val allTxs = mutableListOf<RawTransaction>()
                val prevSeqNo = latestWorkchainShards[shard.shard]
                if (prevSeqNo == null) {
                    latestWorkchainShards[shard.shard] = shard.seqno
                    return@Callable emptyList<RawTransaction>()
                }
                if (prevSeqNo == shard.seqno) {
                    return@Callable emptyList<RawTransaction>()
                }
                for (i in prevSeqNo + 1..shard.seqno) {
                    val wcBlock = tonlib.lookupBlock(i, shard.workchain, shard.shard, 0)
                    val txs = tonlib.getBlockTransactionsExt(wcBlock, 1_000, null)
                    allTxs.addAll(txs.transactions)
                }
                latestWorkchainShards[shard.shard] = shard.seqno
                allTxs
            }
            val future = threadPool.submit(task)
            futures.add(future)
        }
        return futures.map { it.get() }.flatten()
    }

    private suspend fun parseTxs(txs: List<RawTransaction>): List<com.example.blockchain.records.BlockchainRecord> {
        val records = mutableListOf<com.example.blockchain.records.BlockchainRecord>()
        for (tx in txs) {
            val inSlice = com.example.utils.deserializeMessageToSlice(tx.in_msg) ?: continue
            if (inSlice.restBits < 96) {
                continue
            }
            val opcode = loadOpcode(inSlice)
            val queryID = loadQueryId(inSlice)
            val input = BlockchainRecordParser.Input(tx.in_msg, opcode, queryID, inSlice, tx)
            for (parser in parsers) {
                if (parser.isBelongsTo(txContext, input)) {
                    try {
                        records.addAll(parser.parse(txContext, input))
                    } catch (t: Throwable) {
                        logger.error("Could not parse record of tx ${tx.transaction_id}", t)
                    }
                }
            }
        }
        return records
    }

    private fun loadOpcode(slice: CellSlice) = slice.loadUint(32).toLong()

    private fun loadQueryId(slice: CellSlice) = slice.loadUint(64).toLong()
}