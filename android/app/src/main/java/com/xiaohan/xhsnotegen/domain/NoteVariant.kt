package com.xiaohan.xhsnotegen.domain

data class NoteVariant(
    val styleLabel: String,
    val title: String,
    val body: String,
    val hashtags: List<String>,
    val warnings: List<String> = emptyList(),
)
