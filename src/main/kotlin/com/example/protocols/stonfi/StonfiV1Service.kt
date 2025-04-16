package com.example.protocols.stonfi

import com.example.api.model.ApiTransactionBoc
import com.example.api.model.ApiTransactionResponse
import com.example.repository.PoolsRepository
import com.example.service.ConversionService
import com.example.service.statuses.StatusHandlerOnReceive
import com.example.service.statuses.StatusObserverServiceStub
import com.example.service.statuses.StatusServiceStub
import com.example.utils.ObjectMappers
import com.example.utils.http.AsyncHttpClient
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.ton.java.address.Address
import org.ton.java.cell.Cell
import org.ton.java.cell.CellBuilder
import org.ton.java.cell.CellSlice
import org.ton.java.tonlib.Tonlib
import org.ton.java.tonlib.types.TvmStackEntryNumber
import org.ton.java.tonlib.types.TvmStackEntrySlice
import org.ton.java.utils.Utils
import java.math.BigInteger
import java.util.ArrayDeque

class StonfiV1Service(
    private val httpClient: AsyncHttpClient,
    private val repository: PoolsRepository,
    private val tonlib: Tonlib,
    private val conversionService: ConversionService,
    private val statusServiceStub: StatusServiceStub,
    private val statusObserverServiceStub: StatusObserverServiceStub,
) {

    suspend fun getTotalSupply(pool: String): String {
        val response = httpClient.get(
            "https://indexer.swap.coffee/v1/jettons/master/$pool"
        ).body()
        val jettonMaster = ObjectMappers.SNAKE_CASE.readValue(response, JettonMasterResponse::class.java)
        return jettonMaster.totalSupply
    }

    suspend fun getUserPositions(userAddress: String): List<Pair<String, Double>> {
        val response = httpClient.get(
            "https://indexer.swap.coffee/v1/accounts/${userAddress}/balance"
        ).body()
        val jettonMaster = ObjectMappers.SNAKE_CASE.readValue(response, JettonsList::class.java)
        return jettonMaster.jettons.map {
            it.masterAddress to
                    conversionService.getUsdAmount(
                        Address.of(it.masterAddress).toBounceable(), it.value.toDouble()
                    )
        }
    }

    suspend fun getUserPosition(poolAddress: String, userAddress: String): Pair<String, String> {
        var res = tonlib.runMethod(
            Address.of(poolAddress),
            "get_wallet_address",
            ArrayDeque<String?>().apply {
                add(
                    "[[slice, ${
                        Utils.bytesToHex(
                            CellBuilder.beginCell().storeAddress(Address.of(userAddress)).endCell().toBoc()
                        )
                    }]]"
                )
            }
        )
        require(res.exit_code == 0L)
        val cs = CellSlice.beginParse(Cell.fromBocBase64((res.stack[0] as TvmStackEntrySlice).slice.bytes))
        val jettonWallet = cs.loadAddress()
        try {
            res = tonlib.runMethod(
                jettonWallet,
                "get_wallet_data"
            )
            require(res.exit_code == 0L)
            return jettonWallet.toBounceable() to (res.stack[0] as TvmStackEntryNumber).number.toString()
        } catch (t: Throwable) {
            return "0" to "0"
        }
    }

    suspend fun closeUserPosition(
        poolAddress: String,
        userAddress: String,
        lpAmount: BigInteger
    ): ApiTransactionResponse {
        var res = tonlib.runMethod(
            Address.of(poolAddress),
            "get_wallet_address",
            ArrayDeque<String?>().apply {
                add(
                    "[[slice, ${
                        Utils.bytesToHex(
                            CellBuilder.beginCell().storeAddress(Address.of(userAddress)).endCell().toBoc()
                        )
                    }]]"
                )
            }
        )
        require(res.exit_code == 0L)
        val cs = CellSlice.beginParse(Cell.fromBocBase64((res.stack[0] as TvmStackEntrySlice).slice.bytes))
        val jettonWallet = cs.loadAddress()
        val queryId = statusServiceStub.initializeUserDirectQuery()
        val body = CellBuilder.beginCell()
            .storeUint(0x595f07bcL, 32)
            .storeUint(queryId, 64)
            .storeCoins(lpAmount)
            .storeAddress(Address.of(userAddress))
            .storeRefMaybe(null)
            .endCell()

        return ApiTransactionResponse(
            queryId.toString(),
            ApiTransactionBoc(
                body.toBase64(),
                Address.of(jettonWallet).toRaw(),
                Utils.toNano("1").toString()
            )
        ).also {
            statusObserverServiceStub.registerToObserve(
                poolAddress,
                queryId,
                listOf(StatusHandlerOnReceive(true))
            )
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class JettonMasterResponse(
        val address: String,
        val totalSupply: String
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class JettonsList(
        val jettons: List<Jettons>
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class Jettons(
        val masterAddress: String,
        val value: String,
    )
}