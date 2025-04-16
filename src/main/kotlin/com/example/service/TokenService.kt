package com.example.service

import com.example.api.model.ApiToken
import com.example.api.model.ApiTokenAddress
import com.example.api.model.ApiTokenMetadata
import ru.tinkoff.kora.common.Component

@Component
// This is internal service, which build ApiToken for passed token
class TokenService {
    fun toApiModel(token: String): ApiToken {
        return ApiToken(
            ApiTokenAddress("TON", token),
            ApiTokenMetadata("", "", 0, true)
        )
    }
}