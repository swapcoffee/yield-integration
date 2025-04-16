package com.example.utils.http

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.future.asDeferred
import ru.tinkoff.kora.common.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executors

@Component
class AsyncHttpClient : AutoCloseable {

    private val executor = Executors.newFixedThreadPool(10)
    private val client: HttpClient = HttpClient.newBuilder()
        .executor(executor)
        .build()

    suspend fun get(
        path: String,
        args: Map<String, String> = emptyMap(),
        options: Options? = null
    ): HttpResponse<String> {
        val request = request("$path${prepareArgs(args)}", options)
            .GET()
            .header("Content-Type", "application/json")
            .build()
        return send(request).asDeferred().await()
    }

    suspend fun post(
        path: String,
        body: String,
        options: Options? = null
    ): HttpResponse<String> {
        return postAsync(path, body, options).await()
    }

    fun postAsync(
        path: String,
        body: String,
        options: Options? = null
    ): Deferred<HttpResponse<String>> {
        val request = request(path, options)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .header("Content-Type", "application/json")
            .build()
        return send(request).asDeferred()
    }

    suspend fun post(
        path: String,
        body: ByteArray,
        options: Options? = null
    ): HttpResponse<String> {
        val request = request(path, options)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build()
        return send(request).asDeferred().await()
    }

    private fun request(path: String, options: Options?): HttpRequest.Builder {
        val builder = HttpRequest
            .newBuilder(URI.create(path))
            .timeout(options?.timeout ?: Duration.ofMinutes(5))
        options?.headers?.let {
            for ((k, v) in it) {
                builder.header(k, v)
            }
        }
        return builder
    }

    private fun send(request: HttpRequest): CompletionStage<HttpResponse<String>> {
        val future = CompletableFuture<HttpResponse<String>>()
        send0(request, future)
        return future
    }

    private fun send0(request: HttpRequest, future: CompletableFuture<HttpResponse<String>>, retry: Int = 0) {
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply { resp ->
            if (resp.statusCode() !in 200..299) {
                throw RuntimeException("rest api responded with ${resp.statusCode()}: ${resp.body()}")
            }
            resp
        }.whenComplete { result, t ->
            if (t != null) {
                future.completeExceptionally(t)
            } else {
                future.complete(result)
            }
        }
    }

    private fun prepareArgs(args: Map<String, String>) = if (args.isEmpty()) {
        ""
    } else {
        "?${args.entries.joinToString("&") { "${it.key}=${it.value}" }}"
    }

    override fun close() {
        executor.shutdownNow()
    }

    data class Options(
        val timeout: Duration? = null,
        val headers: Map<String, String> = emptyMap()
    )

}