package com.example.loader

import com.example.utils.getLogger
import kotlinx.coroutines.runBlocking
import ru.tinkoff.kora.application.graph.All
import ru.tinkoff.kora.common.Component
import ru.tinkoff.kora.common.annotation.Root
import java.util.concurrent.Executors
import kotlin.math.max

@Root
@Component
class LoaderServiceHolder(
    loaderServices: All<LoaderService>,
) {
    private val executors = Executors.newFixedThreadPool(10)

    private val logger = getLogger()

    init {
        loaderServices.forEach { service ->
            executors.submit {
                while (true) {
                    try {
                        runBlocking {
                            service.doWork()
                        }
                    } catch (e: Exception) {
                        logger.error("Failed to handle ${service.workerName} work: ${e.message}", e)
                    }
                    Thread.sleep(service.workFrequencyMillis)
                }
            }
        }
    }
}