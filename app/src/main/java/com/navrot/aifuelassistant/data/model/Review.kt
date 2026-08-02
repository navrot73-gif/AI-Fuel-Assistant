package com.navrot.aifuelassistant.data.model

data class Review(
    val id: Long,
    val stationId: Long,
    val author: String,
    val rating: Int, // 1-5
    val text: String,
    val date: String,
    val likes: Int = 0
)