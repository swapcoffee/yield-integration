package com.example.utils

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule

object ObjectMappers {

    val DEFAULT: ObjectMapper = JsonMapper.builder()
        .configure(MapperFeature.PROPAGATE_TRANSIENT_MARKER, true)
        .build()
        .registerModule(KotlinModule.Builder().build())
        .registerModule(JavaTimeModule())

    val SNAKE_CASE: ObjectMapper = JsonMapper.builder()
        .configure(MapperFeature.PROPAGATE_TRANSIENT_MARKER, true)
        .build()
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .registerModule(KotlinModule.Builder().build())
        .registerModule(JavaTimeModule())

}