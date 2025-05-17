package com.github.billygk.ai_demo

import com.fasterxml.jackson.databind.JsonNode
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.util.UriComponentsBuilder

@Service
class WeatherService(
    private val appRestClient: RestClient
) {

    private val logger = org.slf4j.LoggerFactory.getLogger(WeatherService::class.java)

    @Value("\${openweathermap.api-key}")
    private lateinit var apiKey: String // Replace with your actual API key
    private val baseUrl = "https://api.openweathermap.org/data/2.5/weather"

    /**
     * Fetches the raw weather data as a JSON string for a given city from the OpenWeatherMap API.
     *
     * @param city The name of the city for which to fetch weather data.
     * @return A JsonNode representing the JSON response from the weather API.
     * @throws CityNotFoundException if the city is not found by the API.
     * @throws InvalidApiKeyException if the API key is invalid or unauthorized.
     * @throws WeatherServiceException for other errors encountered during the API call or if the response body is null.
     */
    fun getWeather(city: String): JsonNode {
        // $baseUrl?q=$city&units=metric&appid=$apiKey
        val uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
            .queryParam("q", city)
            .queryParam("units", "metric")
            .queryParam("appid", apiKey)
            .toUriString()
        try {
            val responseEntity = appRestClient.get()
                .uri(uri)
                .retrieve()
                .body(JsonNode::class.java)
            return responseEntity ?: throw WeatherServiceException("Received null body from weather service for city '$city'")

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

    /**
     * Retrieves the current temperature in Celsius for a given city.
     * It first calls `getWeather` to fetch the raw weather data, then parses the JSON
     * to extract the temperature.
     *
     * @param city The name of the city for which to get the temperature.
     * @return The current temperature in Celsius as a Double.
     * @throws CityNotFoundException if the city is not found by the API (propagated from `getWeather`).
     * @throws InvalidApiKeyException if the API key is invalid (propagated from `getWeather`).
     * @throws WeatherServiceException if there's an error fetching or parsing the weather data,
     *                                 or if the temperature information is missing or malformed in the response.
     */
    fun getTemperature(city: String): Double {

        try {
            val jsonNode: JsonNode = getWeather(city)

            val mainNode = jsonNode.get("main")
            if (mainNode == null || mainNode.isNull) {
                logger.error("Weather data for city '$city' is missing 'main' object. Response: $jsonNode")
                throw WeatherServiceException("Weather data for city '$city' is missing 'main' object.")
            }

            val tempNode = mainNode.get("temp")
            if (tempNode == null || tempNode.isNull) {
                logger.error("Weather data for city '$city' is missing 'temp' field in 'main' object. Response: $jsonNode")
                throw WeatherServiceException("Weather data for city '$city' is missing 'temp' field in 'main' object.")
            }

            if (!tempNode.isNumber) {
                logger.error("Temperature field for city '$city' is not a number. Value: '${tempNode.asText()}'. Response: $jsonNode")
                throw WeatherServiceException("Temperature field for city '$city' is not a number.")
            }
            return tempNode.asDouble()
        } catch (e: com.fasterxml.jackson.core.JsonProcessingException) {
            logger.error("Error parsing weather JSON for city '$city': ", e)
            throw WeatherServiceException("Error parsing weather data for city '$city'.")
        } catch (e: WeatherServiceException) {
            throw e
        } catch (e: Exception) { // Catch any other unexpected error during parsing
            logger.error("Unexpected error processing temperature for city '$city': ", e)
            throw WeatherServiceException("Unexpected error processing temperature for city '$city'.")
        }
    }

}

// Add these custom exceptions
class CityNotFoundException(message: String) : RuntimeException(message)
class InvalidApiKeyException(message: String) : RuntimeException(message)
class WeatherServiceException(message: String) : RuntimeException(message)