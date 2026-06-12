package com.xiaohan.xhsnotegen.data.remote

import com.xiaohan.xhsnotegen.data.remote.dto.GenerateRequestDto
import com.xiaohan.xhsnotegen.data.remote.dto.GenerateResponseDto
import retrofit2.http.Body
import retrofit2.http.POST

interface AiGenerationApi {
    @POST("generate")
    suspend fun generate(@Body request: GenerateRequestDto): GenerateResponseDto
}
