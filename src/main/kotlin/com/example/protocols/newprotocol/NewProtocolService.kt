package com.example.protocols.newprotocol

import com.example.api.model.ApiTransactionBoc
import com.example.api.model.ApiTransactionResponse
import com.example.service.ConversionServiceStub
import com.example.service.statuses.StatusHandlerOnReceive
import com.example.service.statuses.StatusObserverServiceStub
import com.example.service.statuses.StatusServiceStub
import com.example.utils.http.AsyncHttpClient
import org.ton.java.address.Address
import org.ton.java.cell.Cell
import org.ton.java.cell.CellBuilder
import org.ton.java.cell.CellSlice
import org.ton.java.tonlib.Tonlib
import org.ton.java.tonlib.types.*
import org.ton.java.utils.Utils
import java.math.BigInteger
import java.util.ArrayDeque

/**
 * You need to implement this class by yourself.
 * Refer to YieldService, and StonfiV1Service to understand how these parts are interconnected with each other.
 */
class NewProtocolService(
    private val httpClient: AsyncHttpClient,
    private val tonlib: Tonlib,
    private val conversionService: ConversionServiceStub,
    private val statusServiceStub: StatusServiceStub,
    private val statusObserverServiceStub: StatusObserverServiceStub,
) {

    suspend fun getDetailedInfoAboutPool(pool: String): Any /*Return any object you want*/ {
        // TODO:
        //  fetch latest data of this pool
        // For example, if yor pool if jetton, you may use total supply and get it from Indexer API
        // If this pool is non standart contract, use tonlib.runGetMethod
        TODO()
    }

    suspend fun getUserPositions(userAddress: String): List<Any> /*Return any object you want*/ {
        // TODO:
        //  fetch all pools of your protocol, where user has any liquidity
        // For example, if yor pool if jetton, you may use /v1/accounts/{address}/balance from Indexer API
        TODO()
    }

    suspend fun getUserPosition(poolAddress: String, userAddress: String): Any /*Return any object you want*/ {
        // TODO:
        //  returns latest user data in this pool
        TODO()
    }

    suspend fun doAction_1_ForUser(
        poolAddress: String,
        userAddress: String,
        /*Pass here any data, which helps you to build transaction*/
    ): List<ApiTransactionResponse> {
        // TODO:
        //  this method must build transaction to be signed and sent by user to the TON blockchain.
        //  To get queryId use statusServiceStub.initializeUserDirectQuery()
        //  After transaction build, register it in statusObserverServiceStub

        // You may do any request to any sources
        // For example:
        // you may run tonlib.runMethod on poolAddress with "get_wallet_address" to extract jettonWallet address

        /*
        val userAddressEncoded = ParseRunResult.serializeTvmElement(
            TvmStackEntrySlice.builder()
                .slice(
                    TvmSlice.builder()
                        .bytes(
                            Utils.bytesToHex(
                                CellBuilder.beginCell().storeAddress(Address.of(userAddress)).endCell().toBoc()
                            )
                        )
                        .build()
                )
                .build()
        )

        val res = tonlib.runMethod(
            Address.of(poolAddress),
            "get_wallet_address",
            ArrayDeque<String?>().apply {
                add(userAddressEncoded)
            }
        )
        require(res.exit_code == 0L) { "Failed to run getMethod, exit code: ${res.exit_code}" }
        val cs = CellSlice.beginParse(Cell.fromBocBase64((res.stack[0] as TvmStackEntrySlice).slice.bytes))
        val jettonWallet = cs.loadAddress()
        val queryId = statusServiceStub.initializeUserDirectQuery()
         */

        // In this example, you must build message to jettonWallet
        // And after that register it in tracing system
        /*
            statusObserverServiceStub.registerToObserve(
                    poolAddress,
                    queryId,
                    listOf(StatusHandlerOnReceive(true))
            )
         */

        TODO()
    }

    // TODO: add another actions if needed

}