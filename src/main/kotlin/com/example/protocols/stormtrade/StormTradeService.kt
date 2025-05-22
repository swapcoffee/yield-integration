package com.example.protocols.stormtrade

import com.example.api.model.ApiTransactionBoc
import com.example.api.model.ApiTransactionResponse
import com.example.service.ConversionServiceStub
import com.example.service.statuses.StatusHandlerOnReceive
import com.example.service.statuses.StatusObserverServiceStub
import com.example.service.statuses.StatusServiceStub
import com.example.utils.ObjectMappers
import com.example.utils.getLogger
import com.example.utils.http.AsyncHttpClient
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.ton.java.address.Address
import org.ton.java.cell.Cell
import org.ton.java.cell.CellBuilder
import org.ton.java.cell.CellSlice
import org.ton.java.tonlib.Tonlib
import org.ton.java.tonlib.types.TvmStackEntrySlice
import org.ton.java.utils.Utils
import java.math.BigInteger
import java.util.ArrayDeque

class StormTradeService(
    private val httpClient: AsyncHttpClient,
    private val tonlib: Tonlib,
    private val conversionService: ConversionServiceStub,
    private val statusServiceStub: StatusServiceStub,
    private val statusObserverServiceStub: StatusObserverServiceStub,
) {
    
    private val logger = getLogger()

    companion object {
        private const val STORMTRADE_BASE_URL = "https://api5.storm.tg/api"
        
        // StormTrade vault opcodes (from TypeScript VaultOpcodes enum)
        private const val VAULT_PROVIDE_LIQUIDITY_OPCODE = 0xc89a3ee4L
        private const val VAULT_WITHDRAW_LIQUIDITY_OPCODE = 0x595f07bcL // jetton burn
        
        // Standard jetton transfer opcode
        private const val JETTON_TRANSFER_OPCODE = 0xf8a7ea5L
        
        // StormTrade fees (from TypeScript samples)
        private val PROVIDE_LIQUIDITY_MSG_VALUE = Utils.toNano("0.35")
        private val PROVIDE_LIQUIDITY_FORWARD_VALUE = Utils.toNano("0.305")
        private val WITHDRAW_LIQUIDITY_MSG_VALUE = Utils.toNano("0.3")
    }

    /**
     * Gets the liquidity pools configuration from StormTrade API
     */
    suspend fun getLiquiditySources(): List<StormTradeLoaderService.StormTradeLiquiditySource> {
        return try {
            val response = httpClient.get("$STORMTRADE_BASE_URL/config").body()
            val config = ObjectMappers.DEFAULT.readValue(response, StormTradeLoaderService.StormTradeConfigResponse::class.java)
            config.liquiditySources
        } catch (e: Exception) {
            throw RuntimeException("Failed to fetch StormTrade liquidity sources", e)
        }
    }

    // Cache for vaults to avoid repeated API calls
    private var vaultsCache: List<StormTradeLoaderService.StormTradeVault>? = null
    private var vaultsCacheTime: Long = 0
    private val cacheValidityMs = 60_000L // 1 minute cache

    /**
     * Gets vault information for all vaults (with caching)
     */
    suspend fun getVaults(): List<StormTradeLoaderService.StormTradeVault> {
        val now = System.currentTimeMillis()
        if (vaultsCache != null && (now - vaultsCacheTime) < cacheValidityMs) {
            return vaultsCache!!
        }

        return try {
            val response = httpClient.get("$STORMTRADE_BASE_URL/vaults").body()
            val vaults = ObjectMappers.DEFAULT.readValue(response, Array<StormTradeLoaderService.StormTradeVault>::class.java).toList()
            vaultsCache = vaults
            vaultsCacheTime = now
            vaults
        } catch (e: Exception) {
            throw RuntimeException("Failed to fetch StormTrade vaults", e)
        }
    }

    /**
     * Gets vault information for a specific vault address
     */
    suspend fun getVault(vaultAddress: String): StormTradeLoaderService.StormTradeVault? {
        require(vaultAddress.isNotBlank()) { "Vault address cannot be blank" }
        return getVaults().find { it.address == vaultAddress }
    }

    /**
     * Gets user's position in a specific vault
     */
    suspend fun getUserPosition(vaultAddress: String, userAddress: String): StormTradeUserPosition? {
        require(vaultAddress.isNotBlank()) { "Vault address cannot be blank" }
        require(userAddress.isNotBlank()) { "User address cannot be blank" }
        
        return try {
            val encodedVaultAddress = vaultAddress.replace(":", "%3A")
            val encodedUserAddress = userAddress.replace(":", "%3A")
            val response = httpClient.get(
                "$STORMTRADE_BASE_URL/liquidity-providers/$encodedVaultAddress/$encodedUserAddress"
            ).body()
            ObjectMappers.DEFAULT.readValue(response, StormTradeUserPosition::class.java)
        } catch (e: Exception) {
            // User has no position in this vault or API error
            null
        }
    }

    /**
     * Gets all user positions across all vaults
     */
    suspend fun getUserPositions(userAddress: String): List<Pair<String, StormTradeUserPosition>> {
        val vaults = getVaults()
        val positions = mutableListOf<Pair<String, StormTradeUserPosition>>()
        
        for (vault in vaults) {
            val position = getUserPosition(vault.address, userAddress)
            if (position != null && position.supplied.toBigIntegerOrNull()?.let { it > BigInteger.ZERO } == true) {
                positions.add(vault.address to position)
            }
        }
        
        return positions
    }

    /**
     * Gets the total supply of LP tokens for a vault
     */
    suspend fun getTotalSupply(vaultAddress: String): String {
        require(vaultAddress.isNotBlank()) { "Vault address cannot be blank" }
        
        // For StormTrade, we can calculate total supply from vault data
        val vault = getVault(vaultAddress) ?: return "0"
        
        val lockedBalance = vault.lockedBalance.toBigIntegerOrNull() ?: BigInteger.ZERO
        val freeBalance = vault.freeBalance.toBigIntegerOrNull() ?: BigInteger.ZERO
        val totalBalance = lockedBalance + freeBalance
        
        return totalBalance.toString()
    }

    /**
     * Supply TON to a vault (native TON deposits)
     * Based on packNativeProvideLiquidity from TypeScript samples
     */
    suspend fun supplyTon(
        vaultAddress: String,
        userAddress: String,
        amount: BigInteger
    ): ApiTransactionResponse {
        require(vaultAddress.isNotBlank()) { "Vault address cannot be blank" }
        require(userAddress.isNotBlank()) { "User address cannot be blank" }
        require(amount > BigInteger.ZERO) { "Amount must be positive" }
        
        val queryId = statusServiceStub.initializeUserDirectQuery()
        
        // Create native payload (equivalent to packInNativePayload)
        val body = CellBuilder.beginCell()
            .storeUint(VAULT_PROVIDE_LIQUIDITY_OPCODE, 32)
            .storeCoins(amount)
            .endCell()

        val totalValue = PROVIDE_LIQUIDITY_MSG_VALUE.add(amount)

        return ApiTransactionResponse(
            queryId.toString(),
            ApiTransactionBoc(
                body.toBase64(),
                vaultAddress,
                totalValue.toString()
            )
        ).also {
            statusObserverServiceStub.registerToObserve(
                vaultAddress,
                queryId,
                listOf(StatusHandlerOnReceive(true))
            )
        }
    }

    /**
     * Supply jetton to a vault
     * Based on packJettonProvideLiquidity from TypeScript samples
     */
    suspend fun supplyJetton(
        vaultAddress: String,
        userAddress: String,
        jettonAmount: BigInteger,
        jettonMasterAddress: String
    ): ApiTransactionResponse {
        require(vaultAddress.isNotBlank()) { "Vault address cannot be blank" }
        require(userAddress.isNotBlank()) { "User address cannot be blank" }
        require(jettonMasterAddress.isNotBlank()) { "Jetton master address cannot be blank" }
        require(jettonAmount > BigInteger.ZERO) { "Jetton amount must be positive" }
        
        val queryId = statusServiceStub.initializeUserDirectQuery()
        
        // Get user's jetton wallet address
        val userJettonWallet = getUserJettonWalletAddress(jettonMasterAddress, userAddress)
        
        // Create provide liquidity payload (equivalent to packProvideLiquidity())
        val provideLiquidityPayload = CellBuilder.beginCell()
            .storeUint(VAULT_PROVIDE_LIQUIDITY_OPCODE, 32)
            .endCell()
        
        // Create jetton transfer payload (equivalent to packInJettonPayload)
        val body = CellBuilder.beginCell()
            .storeUint(JETTON_TRANSFER_OPCODE, 32) // jetton transfer opcode
            .storeUint(0, 64) // queryId (StormTrade uses 0)
            .storeCoins(jettonAmount)
            .storeAddress(Address.of(vaultAddress)) // destination (vault)
            .storeAddress(Address.of(userAddress)) // response_destination (user's address, not jetton wallet)
            .storeRefMaybe(provideLiquidityPayload) // custom_payload (provide liquidity command)
            .storeCoins(PROVIDE_LIQUIDITY_FORWARD_VALUE) // forward_ton_amount
            .storeRefMaybe(null) // forward_payload (none needed)
            .endCell()

        return ApiTransactionResponse(
            queryId.toString(),
            ApiTransactionBoc(
                body.toBase64(),
                userJettonWallet,
                PROVIDE_LIQUIDITY_MSG_VALUE.toString()
            )
        ).also {
            statusObserverServiceStub.registerToObserve(
                vaultAddress,
                queryId,
                listOf(StatusHandlerOnReceive(true))
            )
        }
    }

    /**
     * Withdraw liquidity from a vault
     * Based on createWithdrawLiquidityTx from TypeScript samples
     */
    suspend fun withdrawLiquidity(
        vaultAddress: String,
        userAddress: String,
        lpAmount: BigInteger
    ): ApiTransactionResponse {
        require(vaultAddress.isNotBlank()) { "Vault address cannot be blank" }
        require(userAddress.isNotBlank()) { "User address cannot be blank" }
        require(lpAmount > BigInteger.ZERO) { "LP amount must be positive" }
        
        val queryId = statusServiceStub.initializeUserDirectQuery()
        
        // Get vault config to find LP jetton master
        val vault = getVault(vaultAddress) ?: throw IllegalArgumentException("Vault not found: $vaultAddress")
        val lpJettonMaster = vault.config.lpJettonMaster
        
        // Get user's LP wallet address
        val userLpWallet = getUserJettonWalletAddress(lpJettonMaster, userAddress)
        
        // Create withdraw liquidity payload (equivalent to packWithdrawLiquidity)
        val body = CellBuilder.beginCell()
            .storeUint(VAULT_WITHDRAW_LIQUIDITY_OPCODE, 32)
            .storeUint(0, 64) // queryId (StormTrade uses 0)
            .storeCoins(lpAmount)
            .storeAddress(Address.of(userAddress))
            .endCell()

        return ApiTransactionResponse(
            queryId.toString(),
            ApiTransactionBoc(
                body.toBase64(),
                userLpWallet, // Send to user's LP wallet, not vault directly
                WITHDRAW_LIQUIDITY_MSG_VALUE.toString()
            )
        ).also {
            statusObserverServiceStub.registerToObserve(
                vaultAddress,
                queryId,
                listOf(StatusHandlerOnReceive(true))
            )
        }
    }

    /**
     * Gets user's jetton wallet address for a given jetton master
     * Based on StonfiV1Service implementation
     */
    suspend fun getUserJettonWalletAddress(jettonMasterAddress: String, userAddress: String): String {
        require(jettonMasterAddress.isNotBlank()) { "Jetton master address cannot be blank" }
        require(userAddress.isNotBlank()) { "User address cannot be blank" }
        
        return try {
            val res = tonlib.runMethod(
                Address.of(jettonMasterAddress),
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
            require(res.exit_code == 0L) { "TON node returned error: ${res.exit_code}" }
            val cs = CellSlice.beginParse(Cell.fromBocBase64((res.stack[0] as TvmStackEntrySlice).slice.bytes))
            cs.loadAddress().toString()
        } catch (e: Exception) {
            throw RuntimeException("Failed to get jetton wallet address for $jettonMasterAddress", e)
        }
    }

    /**
     * Clears the vaults cache
     */
    fun clearCache() {
        vaultsCache = null
        vaultsCacheTime = 0
        logger.debug("StormTrade vaults cache cleared")
    }

    /**
     * Determines if a vault is for native TON or jetton deposits
     */
    private suspend fun isNativeVault(vaultAddress: String): Boolean {
        val vault = getVault(vaultAddress) ?: return false
        return vault.config.asset.name.equals("TON", ignoreCase = true)
    }

    /**
     * Provides liquidity to a vault (automatically detects TON vs jetton)
     */
    suspend fun provideLiquidity(
        vaultAddress: String,
        userAddress: String,
        amount: BigInteger
    ): ApiTransactionResponse {
        require(vaultAddress.isNotBlank()) { "Vault address cannot be blank" }
        require(userAddress.isNotBlank()) { "User address cannot be blank" }
        require(amount > BigInteger.ZERO) { "Amount must be positive" }
        
        return if (isNativeVault(vaultAddress)) {
            supplyTon(vaultAddress, userAddress, amount)
        } else {
            val vault = getVault(vaultAddress) ?: throw IllegalArgumentException("Vault not found: $vaultAddress")
            supplyJetton(vaultAddress, userAddress, amount, vault.config.asset.assetId)
        }
    }

    // Data class for user position API response
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class StormTradeUserPosition(
        val vault: String,
        val trader: String,
        val supplied: String,
        val averageRate: String
    )
} 