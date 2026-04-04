package com.phantombeats.data.remote.dto

import com.google.gson.annotations.SerializedName

data class StreamUrlDto(
    @SerializedName("url")
    val url: String
)
