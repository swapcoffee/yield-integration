//package com.example.yield.newprotocol
//
//import com.example.yield.loader.LoaderService
//import com.example.yield.repository.PoolsRepository
//import com.example.yield.utils.ObjectMappers
//import com.example.yield.utils.http.AsyncHttpClient
//import com.fasterxml.jackson.annotation.JsonIgnoreProperties
//import ru.tinkoff.kora.common.Component
//import java.util.concurrent.TimeUnit
//
//@Component
//class NewProtocolLoaderService(
//    private val httpClient: AsyncHttpClient,
//    private val repository: PoolsRepository,
//) : LoaderService {
//
//    override val workerName: String = "new-protocol"
//
//    override val workFrequencyMillis: Long = TimeUnit.MINUTES.toMillis(10)
//
//    override suspend fun doWork() {
//        // TODO: Implement
//
//        repository.selectLiquidityPoolsByProtocol("new-protocol")
//        val response = httpClient.get(
//            "https://indexer.swap.coffee/v1/jettons/master/EQCl0S4xvoeGeFGijTzicSA8j6GiiugmJW5zxQbZTUntre-1"
//        ).body()
//        val jettonMaster = ObjectMappers.SNAKE_CASE.readValue(response, JettonMasterResponse::class.java)
//        println("EQCl0S4xvoeGeFGijTzicSA8j6GiiugmJW5zxQbZTUntre-1 ts: ${jettonMaster.totalSupply}")
//    }
//
//    @JsonIgnoreProperties(ignoreUnknown = true)
//    private data class JettonMasterResponse(
//        val address: String,
//        val totalSupply: String
//    )
//}