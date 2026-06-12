package com.xiaohan.xhsnotegen.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.xiaohan.xhsnotegen.domain.NoteVariant

data class NoteVariantDto(
    @SerializedName("style_label") val styleLabel: String,
    @SerializedName("title") val title: String,
    @SerializedName("body") val body: String,
    @SerializedName("hashtags") val hashtags: List<String>,
    @SerializedName("warnings") val warnings: List<String> = emptyList(),
)

data class GenerateResponseDto(
    @SerializedName("variants") val variants: List<NoteVariantDto>,
)

fun GenerateResponseDto.toDomain(): List<NoteVariant> =
    variants.map { NoteVariant(it.styleLabel, it.title, it.body, it.hashtags, it.warnings) }
