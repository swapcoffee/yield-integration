package com.example.service.statuses

import com.example.api.model.ApiTxOperationStatus
import ru.tinkoff.kora.common.Component

@Component
class StatusServiceStub {

    fun getStatus(
        baseQueryId: Long
    ): ApiTxOperationStatus {
        return ApiTxOperationStatus.SUCCEEDED
    }

    fun initializeUserDirectQuery(): Long {
        return 100
    }

}
