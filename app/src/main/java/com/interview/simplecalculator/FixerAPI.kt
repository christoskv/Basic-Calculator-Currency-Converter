package com.interview.simplecalculator

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// Define the base URL for Fixer API
private const val BASE_URL = "https://data.fixer.io/api/"

// Your Fixer API Key
private const val API_KEY = "d4d9234bd566bff1408d03809bdd89c2"

// Data model for the response
data class ExchangeRatesResponse(
    val success: Boolean,
    val rates: Map<String, Double>,
    val base: String
)

// Define the interface for the API endpoints
interface FixerService {
    @GET("latest")
    suspend fun getExchangeRates(
        @Query("access_key") accessKey: String = API_KEY,
        @Query("base") baseCurrency: String,
        @Query("symbols") targetCurrencies: String = "EUR,USD,GBP,JPY,TRY,CHF"
    ): ExchangeRatesResponse
}

// Create a singleton object for the API client
object FixerAPI {
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: FixerService by lazy {
        retrofit.create(FixerService::class.java)
    }
}

// Cache data structure to store exchange rates and the timestamp of the last fetch
data class CachedExchangeRates(
    val rates: Map<String, Double>,
    val timestamp: Long
)