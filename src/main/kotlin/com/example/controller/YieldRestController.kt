package com.example.controller

import com.example.api.controller.YieldAggregatorApiDelegate
import com.example.api.controller.YieldAggregatorApiResponses
import com.example.api.model.*
import com.example.dto.yield.mapToYieldProtocols
import com.example.service.YieldService
import com.example.service.statuses.StatusServiceStub
import ru.tinkoff.kora.common.Component

@Component
class YieldRestController(
    private val yieldService: YieldService,
    private val statusServiceStub: StatusServiceStub
) : YieldAggregatorApiDelegate {
    override suspend fun getYieldSearchResponse(
        blockchains: List<ApiSupportedBlockchain>?,
        providers: List<ApiSupportedYields>?,
        trusted: Boolean?,
        withActiveBoosts: Boolean?,
        recentlyCreated: Boolean?,
        withLiquidityFrom: String?,
        searchText: String?,
        order: ApiPoolSortOrder?,
        descendingOrder: Boolean?,
        inGroups: Boolean?,
        size: Int?,
        page: Int?
    ): YieldAggregatorApiResponses.GetYieldSearchResponseApiResponse {
        val providersNormalized = providers?.map { it.mapToYieldProtocols() } ?: emptyList()
        val pools = if (withLiquidityFrom == null) {
            yieldService.getAllPools(providersNormalized)
        } else {
            yieldService.getAllPoolsForUser(providersNormalized, withLiquidityFrom)
        }

        return YieldAggregatorApiResponses.GetYieldSearchResponseApiResponse.GetYieldSearchResponse200ApiResponse(
            listOf(
                ApiYieldSearchResponse(pools.size, pools)
            )
        )
    }

    override suspend fun getYieldDetails(poolAddress: String): YieldAggregatorApiResponses.GetYieldDetailsApiResponse {
        return YieldAggregatorApiResponses.GetYieldDetailsApiResponse.GetYieldDetails200ApiResponse(
            yieldService.getPool(poolAddress)
        )
    }

    override suspend fun getYieldUserDetails(
        poolAddress: String,
        userAddress: String
    ): YieldAggregatorApiResponses.GetYieldUserDetailsApiResponse {
        val res = yieldService.getUserDetails(poolAddress, userAddress)
        return YieldAggregatorApiResponses.GetYieldUserDetailsApiResponse.GetYieldUserDetails200ApiResponse(res)
    }

    override suspend fun interactYieldPoolUser(
        poolAddress: String,
        userAddress: String,
        apiYieldInteractionRequest: ApiYieldInteractionRequest
    ): YieldAggregatorApiResponses.InteractYieldPoolUserApiResponse {
        val bocs = yieldService.processUserRequest(poolAddress, userAddress, apiYieldInteractionRequest.requestData)
        return YieldAggregatorApiResponses
            .InteractYieldPoolUserApiResponse
            .InteractYieldPoolUser200ApiResponse(bocs)
    }

    override suspend fun getYieldPoolInteractionStatus(queryId: List<String>): YieldAggregatorApiResponses.GetYieldPoolInteractionStatusApiResponse {
        val statuses = queryId.map {
            statusServiceStub.getStatus(it.toLong())
        }
        return YieldAggregatorApiResponses
            .GetYieldPoolInteractionStatusApiResponse
            .GetYieldPoolInteractionStatus200ApiResponse(statuses)
    }

}