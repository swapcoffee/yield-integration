package com.example.service.statuses

import com.example.blockchain.parsers.BlockchainRecordParser
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(
    use = JsonTypeInfo.Id.CLASS,
    include = JsonTypeInfo.As.PROPERTY,
    property = "@type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = StatusHandlerOnReceive::class),
    JsonSubTypes.Type(value = StatusHandlerOnEmptyOutputEvents::class),
)
abstract class StatusHandler(val resolveAsSuccess: Boolean) {

    abstract fun isApplicable(input: BlockchainRecordParser.Input): Boolean
}

class StatusHandlerOnReceive(resolveAsSuccess: Boolean) : StatusHandler(resolveAsSuccess) {
    override fun isApplicable(input: BlockchainRecordParser.Input): Boolean {
        return true
    }
}

data class StatusHandlerOnEmptyOutputEvents(
    val messageSenderAddress: String,
) : StatusHandler(false) {
    override fun isApplicable(input: BlockchainRecordParser.Input): Boolean {
        return messageSenderAddress == input.inMessage.source.account_address && input.tx.out_msgs.isEmpty()
    }
}

