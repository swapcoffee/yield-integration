import com.example.service.ConversionServiceStub
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
import java.util.ArrayDeque


class FarmixLendingService(
    private val httpClient: AsyncHttpClient,
    private val tonlib: Tonlib,
    private val conversionService: ConversionServiceStub,
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
        val res = httpClient.get(
            "https://api.farmix.tg/bff/v1/owner/stakes/all",
            args = mapOf(Pair("force", "false"), Pair("addr", userAddress)),
        ).body()

        val parsed = ObjectMappers.SNAKE_CASE.readValue(res, UserPositionsRes::class.java)

        return parsed.stakes
            .filter { it.stake_usd_amount > 0 }
            .map {
                it.farmixPool to it.stake_usd_amount
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


    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class UserPosition(
        val stakerAddr: String,
        val farmixPool: String,
        val jettonMaster: String,
        val stake_usd_amount: Double,
    )

    private data class UserPositionsRes(
        val stakes: List<UserPosition>
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class JettonMasterResponse(
        val address: String,
        val totalSupply: String
    )


}