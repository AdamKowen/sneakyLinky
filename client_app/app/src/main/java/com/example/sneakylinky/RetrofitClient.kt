package com.example.sneakylinky

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

// url to server
private const val BASE_URL = "http://10.0.2.2:8000/api/"

private val retrofit = Retrofit.Builder()
    .baseUrl(BASE_URL)
    .addConverterFactory(GsonConverterFactory.create())
    .build()

interface ApiService {
    @POST("check-url/")
    suspend fun checkUrl(@Body request: Map<String, String>): ApiResponse
}

object RetrofitClient {
    val apiService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }
}

data class ApiResponse(
    val status: String,
    val message: String
)