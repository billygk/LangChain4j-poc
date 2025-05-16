package com.github.billygk.AIDemo1

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate

@Service
class WeatherService(private val restTemplate: RestTemplate) {

    private val logger = org.slf4j.LoggerFactory.getLogger(WeatherService::class.java)

    @Value("\${openweathermap.api-key}")
    private val apiKey = "" // Replace with your actual API key
    private val baseUrl = "http://api.openweathermap.org/data/2.5/weather"

    fun getWeather(city: String): String {
        val uri = "$baseUrl?q=$city&units=metric&appid=$apiKey"

        try {
            val responseEntity = restTemplate.getForEntity(uri, String::class.java)
            return responseEntity.body ?: throw WeatherServiceException("Received null body from weather service for city '$city'")
        } catch (e: HttpClientErrorException) {
            when (e.statusCode) {
                HttpStatus.NOT_FOUND -> throw CityNotFoundException("City '$city' not found")
                HttpStatus.UNAUTHORIZED -> {
                    logger.error("Invalid API key.")
                    throw InvalidApiKeyException("Invalid API key")
                }

                else -> {
                    logger.error("Error fetching weather for city '$city': ${e.message}")
                    throw WeatherServiceException("Error fetching weather for city '$city': ${e.message}")
                }
            }
        } catch (e: HttpServerErrorException) {
            logger.error("Server error fetching weather data for '$city': ${e.statusCode} ${e.responseBodyAsString}", e)
            throw WeatherServiceException("Server error fetching weather data for '$city': ${e.statusCode} ${e.responseBodyAsString}")
        } catch (e: RestClientException) {
            logger.error("Error fetching weather data for city '$city': ${e.message}", e)
            throw WeatherServiceException("Error fetching weather data for city '$city': ${e.message}")
        }
    }


    fun getTemperature(city: String): Double {
        val objectMapper = ObjectMapper()
        val responseJson: String
        try {
            responseJson = getWeather(city) // This call can throw CityNotFoundException, InvalidApiKeyException, or WeatherServiceException
        } catch (e: RuntimeException) {
            // Log and re-throw exceptions from getWeather, as they are already specific.
            logger.error("Failed to get weather data for city '$city' before extracting temperature: ${e.message}", e)
            throw e // Re-throw the specific exception (CityNotFoundException, InvalidApiKeyException, etc.)
        }

        try {
            val jsonNode: JsonNode = objectMapper.readTree(responseJson)

            val mainNode = jsonNode.get("main")
            if (mainNode == null || mainNode.isNull) {
                logger.error("Weather data for city '$city' is missing 'main' object. Response: $responseJson")
                throw WeatherServiceException("Weather data for city '$city' is missing 'main' object.")
            }

            val tempNode = mainNode.get("temp")
            if (tempNode == null || tempNode.isNull) {
                logger.error("Weather data for city '$city' is missing 'temp' field in 'main' object. Response: $responseJson")
                throw WeatherServiceException("Weather data for city '$city' is missing 'temp' field in 'main' object.")
            }

            if (!tempNode.isNumber) {
                logger.error("Temperature field for city '$city' is not a number. Value: '${tempNode.asText()}'. Response: $responseJson")
                throw WeatherServiceException("Temperature field for city '$city' is not a number.")
            }
            return tempNode.asDouble()
        } catch (e: com.fasterxml.jackson.core.JsonProcessingException) {
            logger.error("Error parsing weather JSON for city '$city': ${e.message}. Response: $responseJson", e)
            throw WeatherServiceException("Error parsing weather data for city '$city'.")
        } catch (e: WeatherServiceException) {
            // This catches WeatherServiceExceptions thrown by the null/type checks above
            throw e
        } catch (e: Exception) { // Catch any other unexpected error during parsing
            logger.error("Unexpected error processing temperature for city '$city' from JSON: $responseJson. Error: ${e.message}", e)
            throw WeatherServiceException("Unexpected error processing temperature for city '$city'.")
        }
    }

}

// Add these custom exceptions
class CityNotFoundException(message: String) : RuntimeException(message)
class InvalidApiKeyException(message: String) : RuntimeException(message)
class WeatherServiceException(message: String) : RuntimeException(message)