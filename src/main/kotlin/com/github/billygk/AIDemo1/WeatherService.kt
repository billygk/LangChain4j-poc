package com.github.billygk.AIDemo1

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException

@Service
class WeatherService(private val webClient: WebClient) {

    private val logger = org.slf4j.LoggerFactory.getLogger(WeatherService::class.java)

    private val apiKey = "" // Replace with your actual API key
    private val baseUrl = "http://api.openweathermap.org/data/2.5/weather"

    fun getWeather(city: String): Mono<String> {
        val uri = "$baseUrl?q=$city&units=metric&appid=$apiKey"

        return webClient.get()
            .uri(uri)
            .retrieve()
            .onStatus({ status -> status.is4xxClientError || status.is5xxServerError }) { response ->
                when (response.statusCode()) {
                    HttpStatus.NOT_FOUND -> Mono.error(CityNotFoundException("City '$city' not found"))
                    HttpStatus.UNAUTHORIZED -> Mono.error(InvalidApiKeyException("Invalid API key"))
                    else -> Mono.error(WeatherServiceException("Error fetching weather data: ${response.statusCode()}"))
                }
            }
            .bodyToMono(String::class.java)
            .onErrorResume(WebClientResponseException::class.java) { ex ->
                Mono.error(WeatherServiceException("Error fetching weather data: ${ex.message}"))
            }
    }


    fun getTemperature(city: String): Double {


        val objectMapper = ObjectMapper()
        return getWeather(city)
            .map { response ->
                val jsonNode: JsonNode = objectMapper.readTree(response)
                jsonNode.get("main").get("temp").asDouble()
            }
            .block() ?: run {
                logger.error("Error fetching temperature for city '$city'")
                throw WeatherServiceException("Error fetching temperature for city '$city'")
            }
    }

}

// Add these custom exceptions
class CityNotFoundException(message: String) : RuntimeException(message)
class InvalidApiKeyException(message: String) : RuntimeException(message)
class WeatherServiceException(message: String) : RuntimeException(message)