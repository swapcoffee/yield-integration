package com.example.blockchain

data class TxContext(
    var stonfiKnownPools: MutableSet<String> = mutableSetOf()
)