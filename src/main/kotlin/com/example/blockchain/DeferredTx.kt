package com.example.blockchain

interface DeferredTx {

    suspend fun execute()

    suspend fun rollback() {}

}

fun DeferredTx(exec: suspend () -> Unit): DeferredTx = object :
    DeferredTx {
    override suspend fun execute() = exec()
}

fun DeferredTx(
    exec: suspend () -> Unit,
    rollBack: suspend () -> Unit
): DeferredTx = object : DeferredTx {
    override suspend fun execute() = exec()
    override suspend fun rollback() = rollBack()
}
