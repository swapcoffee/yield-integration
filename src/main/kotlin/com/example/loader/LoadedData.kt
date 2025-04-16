package com.example.loader

data class LoadedData<T>(
    val id: String,
    val data: T,
    val needRewrite: Boolean
)