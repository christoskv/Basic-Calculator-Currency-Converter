package com.interview.simplecalculator

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// Define the base URL for Fixer API
private const val BASE_URL = "https://api.frankfurter.app/"

// Data model for the response
data class ConvertionResult(
    val amount: Double,
    val base: String,
    val date: String,
    val rates: Rates
)

// Define the interface for the API endpoints
interface FrankfurterService {
    @GET("latest")
    suspend fun convertAmount(
        @Query("amount") amount: String,
        @Query("from") from: String,
        @Query("to") to: String
    ): ExchangeRatesResponse
}

// Create a singleton object for the API client
object FrankfurterAPI {
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: FrankfurterService by lazy {
        retrofit.create(FrankfurterService::class.java)
    }
}