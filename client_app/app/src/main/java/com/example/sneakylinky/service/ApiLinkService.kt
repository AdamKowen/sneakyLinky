package com.example.sneakylinky.service

import retrofit2.http.Body
import retrofit2.http.POST

interface ApiLinkService {
    @POST("check-url/")
    suspend fun checkUrl(@Body request: Map<String, String>): ApiResponse
}