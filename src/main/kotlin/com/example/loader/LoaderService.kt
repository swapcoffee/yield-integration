package com.example.loader

interface LoaderService {

    val workerName: String

    val workFrequencyMillis: Long

    suspend fun doWork()
}