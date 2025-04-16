package com.example.utils

import ru.tinkoff.kora.common.Component
import ru.tinkoff.kora.common.Context
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.http.common.body.HttpBody
import ru.tinkoff.kora.http.server.common.HttpServerInterceptor
import ru.tinkoff.kora.http.server.common.HttpServerModule
import ru.tinkoff.kora.http.server.common.HttpServerRequest
import ru.tinkoff.kora.http.server.common.HttpServerResponse
import java.util.concurrent.CompletionException
import java.util.concurrent.CompletionStage

@Tag(HttpServerModule::class)
@Component
class HttpServerHandler : HttpServerInterceptor {

    private val logger = getLogger()

    override fun intercept(
        context: Context,
        request: HttpServerRequest,
        chain: HttpServerInterceptor.InterceptChain
    ): CompletionStage<HttpServerResponse> {
        val body = String(request.body().asInputStream().readAllBytes())
        logger.info("${request.method()} ${request.path()} ${request.queryParams()} >> $body")
        return chain.process(context, request)
            .handle { result, ex ->
                if (result != null) {
                    logger.info("${request.method()} ${request.path()} << OK")
                }
                if (ex != null) {
                    val source = (ex as? CompletionException)?.cause ?: ex
                    logger.error(source.stackTraceToString())
                    return@handle HttpServerResponse.of(
                        500,
                        HttpBody.json("{\"error\": \"Something went wrong\"}")
                    )
                }
                return@handle result
            }
    }
}
