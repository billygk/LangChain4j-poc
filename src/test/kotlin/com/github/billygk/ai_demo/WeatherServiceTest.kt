package com.github.billygk.ai_demo

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.*
import io.mockk.InternalPlatformDsl.toStr
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals // Use JUnit 5's assertEquals
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.util.UriComponentsBuilder
import java.nio.charset.StandardCharsets

class WeatherServiceTest {

    @MockK
    private lateinit var mockRestClient: RestClient

    // Mocks for the RestClient fluent API
    @MockK
    private lateinit var mockRequestHeadersUriSpec: RestClient.RequestHeadersUriSpec<*>
    @MockK
    private lateinit var mockRequestHeadersSpec: RestClient.RequestHeadersSpec<*>
    @MockK
    private lateinit var mockResponseSpec: RestClient.ResponseSpec

    private lateinit var weatherService: WeatherService // Real instance for getWeather tests

    private val objectMapper = ObjectMapper()
    private val testApiKey = "test-api-key"
    private val baseUrl = "https://api.openweathermap.org/data/2.5/weather"

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        weatherService = WeatherService(mockRestClient)
        // Set the private apiKey field using reflection
        val field = WeatherService::class.java.getDeclaredField("apiKey")
        field.isAccessible = true
        field.set(weatherService, testApiKey)

        // Common mocking for RestClient chain
        every { mockRestClient.get() } returns mockRequestHeadersUriSpec
        // Allow any String URI for flexibility, but verify specific URI in tests
        every { mockRequestHeadersUriSpec.uri(any<String>()) } returns mockRequestHeadersSpec
        every { mockRequestHeadersSpec.retrieve() } returns mockResponseSpec
    }

    @AfterEach
    fun tearDown() {
        unmockkAll() // Clears all MockK mocks and recorded calls
    }

    private fun buildExpectedUri(city: String): String {
        return UriComponentsBuilder.fromHttpUrl(baseUrl)
            .queryParam("q", city)
            .queryParam("units", "metric")
            .queryParam("appid", testApiKey)
            .toUriString()
    }

    @Nested
    inner class GetWeatherTests {

        @Test
        fun `should return JsonNode on successful API call`() {
            val city = "London"
            val expectedUri = buildExpectedUri(city)
            val mockJsonNode = objectMapper.createObjectNode().put("key", "value")

            every { mockResponseSpec.body(JsonNode::class.java) } returns mockJsonNode

            val result = weatherService.getWeather(city)

            assertEquals(mockJsonNode, result)
            verify { mockRestClient.get() }
            verify { mockRequestHeadersUriSpec.uri(expectedUri) }
            verify { mockRequestHeadersSpec.retrieve() }
            verify { mockResponseSpec.body(JsonNode::class.java) }
        }

        @Test
        fun `should throw CityNotFoundException on 404 error`() {
            val city = "UnknownCity"
            val expectedUri = buildExpectedUri(city)
            val exception = HttpClientErrorException.create(
                HttpStatus.NOT_FOUND,
                "Not Found",
                HttpHeaders(),
                ByteArray(0),
                StandardCharsets.UTF_8
            )

            every { mockResponseSpec.body(JsonNode::class.java) } throws exception

            val thrown = assertThrows<CityNotFoundException> {
                weatherService.getWeather(city)
            }
            assertEquals("City '$city' not found", thrown.message)
            verify { mockRequestHeadersUriSpec.uri(expectedUri) }
        }

        @Test
        fun `should throw InvalidApiKeyException on 401 error`() {
            val city = "London"
            val expectedUri = buildExpectedUri(city)
            val exception = HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                HttpHeaders(),
                ByteArray(0),
                StandardCharsets.UTF_8
            )

            every { mockResponseSpec.body(JsonNode::class.java) } throws exception

            val thrown = assertThrows<InvalidApiKeyException> {
                weatherService.getWeather(city)
            }
            assertEquals("Invalid API key", thrown.message)
            verify { mockRequestHeadersUriSpec.uri(expectedUri) }
        }

        @Test
        fun `should throw WeatherServiceException on other HttpClientError`() {
            val city = "London"
            val expectedUri = buildExpectedUri(city)
            val status = HttpStatus.BAD_REQUEST
            val statusText = "Custom Bad Request" // This is the statusText part of the message
            // For HttpClientErrorException, e.message is "statusCode statusText"
            val exceptionMessageInError = "${status.value()} $statusText"
            val exception = HttpClientErrorException.create(
                status,
                statusText,
                HttpHeaders(),
                ByteArray(0),
                StandardCharsets.UTF_8
            )

            every { mockResponseSpec.body(JsonNode::class.java) } throws exception

            val thrown = assertThrows<WeatherServiceException> {
                weatherService.getWeather(city)
            }
            assertEquals("Error fetching weather for city '$city': $exceptionMessageInError", thrown.message)
            verify { mockRequestHeadersUriSpec.uri(expectedUri) }
        }

        @Test
        fun `should throw WeatherServiceException on HttpServerError`() {
            val city = "London"
            val expectedUri = buildExpectedUri(city)
            val responseBody = "Server Error Body Content"
            val status = HttpStatus.INTERNAL_SERVER_ERROR
            // For HttpServerErrorException, e.statusCode is HttpStatusCode, e.responseBodyAsString is used
            val exception = HttpServerErrorException.create(
                status,
                "Internal Server Error Status Text", // This status text is not directly used by SUT's message
                HttpHeaders(),
                responseBody.toByteArray(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
            )

            every { mockResponseSpec.body(JsonNode::class.java) } throws exception

            val thrown = assertThrows<WeatherServiceException> {
                weatherService.getWeather(city)
            }
            // SUT uses e.statusCode (which stringifies to "VALUE NAME", e.g. "500 INTERNAL_SERVER_ERROR")
            // and e.responseBodyAsString
            assertEquals("Server error fetching weather data for '$city': $status $responseBody", thrown.message)
            verify { mockRequestHeadersUriSpec.uri(expectedUri) }
        }

        @Test
        fun `should throw WeatherServiceException on RestClientException`() {
            val city = "London"
            val expectedUri = buildExpectedUri(city)
            val errorMessage = "Network connectivity issue"
            val exception = RestClientException(errorMessage)

            every { mockResponseSpec.body(JsonNode::class.java) } throws exception

            val thrown = assertThrows<WeatherServiceException> {
                weatherService.getWeather(city)
            }
            assertEquals("Error fetching weather data for city '$city': $errorMessage", thrown.message)
            verify { mockRequestHeadersUriSpec.uri(expectedUri) }
        }

        @Test
        fun `should throw WeatherServiceException if API returns null body`() {
            val city = "London"
            val expectedUri = buildExpectedUri(city)

            every { mockResponseSpec.body(JsonNode::class.java) } returns null

            val thrown = assertThrows<WeatherServiceException> {
                weatherService.getWeather(city)
            }
            assertEquals("Received null body from weather service for city '$city'", thrown.message)
            verify { mockRequestHeadersUriSpec.uri(expectedUri) }
        }
    }

    @Nested
    inner class GetTemperatureTests {

        private lateinit var spiedWeatherService: WeatherService

        @BeforeEach
        fun setUpSpy() {
            // weatherService is already initialized from outer setUp with mockRestClient
            // We spy on this already configured instance to mock its getWeather method.
            spiedWeatherService = spyk(weatherService, recordPrivateCalls = true)
        }

        @Test
        fun `should return temperature on success`() {
            val city = "Paris"
            val temperature = 15.5
            val jsonResponse = objectMapper.createObjectNode().set<JsonNode>(
                "main",
                objectMapper.createObjectNode().put("temp", temperature)
            )

            every { spiedWeatherService.getWeather(city) } returns jsonResponse

            val result = spiedWeatherService.getTemperature(city)

            assertEquals(temperature, result, 0.001) // Delta for double comparison
            verify { spiedWeatherService.getWeather(city) }
        }

        @Test
        fun `when getWeather throws CityNotFoundException should re-wrap and throw WeatherServiceException`() {
            val city = "NonExistent"
            every { spiedWeatherService.getWeather(city) } throws CityNotFoundException("City '$city' not found")

            val thrown = assertThrows<WeatherServiceException> {
                spiedWeatherService.getTemperature(city)
            }
            // This tests the current behavior of getTemperature's generic catch (e: Exception) block
            assertEquals("Unexpected error processing temperature for city '$city'.", thrown.message)
        }

        @Test
        fun `when getWeather throws InvalidApiKeyException should re-wrap and throw WeatherServiceException`() {
            val city = "London"
            every { spiedWeatherService.getWeather(city) } throws InvalidApiKeyException("Invalid API key")

            val thrown = assertThrows<WeatherServiceException> {
                spiedWeatherService.getTemperature(city)
            }
            assertEquals("Unexpected error processing temperature for city '$city'.", thrown.message)
        }

        @Test
        fun `should throw WeatherServiceException if main node is missing`() {
            val city = "TestCity"
            val jsonResponse = objectMapper.createObjectNode().put("coord", "data") // No "main" node

            every { spiedWeatherService.getWeather(city) } returns jsonResponse

            val thrown = assertThrows<WeatherServiceException> {
                spiedWeatherService.getTemperature(city)
            }
            assertEquals("Weather data for city '$city' is missing 'main' object.", thrown.message)
        }

        @Test
        fun `should throw WeatherServiceException if main node is null`() {
            val city = "TestCity"
            val jsonResponse = objectMapper.createObjectNode().set<JsonNode>("main", objectMapper.nullNode())

            every { spiedWeatherService.getWeather(city) } returns jsonResponse

            val thrown = assertThrows<WeatherServiceException> {
                spiedWeatherService.getTemperature(city)
            }
            assertEquals("Weather data for city '$city' is missing 'main' object.", thrown.message)
        }

        @Test
        fun `should throw WeatherServiceException if temp node is missing in main`() {
            val city = "TestCity"
            val jsonResponse = objectMapper.createObjectNode().set<JsonNode>(
                "main",
                objectMapper.createObjectNode().put("pressure", 1012) // No "temp" node
            )
            every { spiedWeatherService.getWeather(city) } returns jsonResponse
            val thrown = assertThrows<WeatherServiceException> {
                spiedWeatherService.getTemperature(city)
            }
            assertEquals("Weather data for city '$city' is missing 'temp' field in 'main' object.", thrown.message)
        }

        @Test
        fun `should throw WeatherServiceException if temp node is null in main`() {
            val city = "TestCity"
            val jsonResponse = objectMapper.createObjectNode().set<JsonNode>(
                "main",
                objectMapper.createObjectNode().set("temp", objectMapper.nullNode())
            )
            every { spiedWeatherService.getWeather(city) } returns jsonResponse
            val thrown = assertThrows<WeatherServiceException> {
                spiedWeatherService.getTemperature(city)
            }
            assertEquals("Weather data for city '$city' is missing 'temp' field in 'main' object.", thrown.message)
        }

        @Test
        fun `should throw WeatherServiceException if temp node is not a number`() {
            val city = "TestCity"
            val jsonResponse = objectMapper.createObjectNode().set<JsonNode>(
                "main",
                objectMapper.createObjectNode().put("temp", "not-a-number")
            )
            every { spiedWeatherService.getWeather(city) } returns jsonResponse
            val thrown = assertThrows<WeatherServiceException> {
                spiedWeatherService.getTemperature(city)
            }
            assertEquals("Temperature field for city '$city' is not a number.", thrown.message)
        }

        @Test
        fun `should throw WeatherServiceException on JsonProcessingException during parsing`() {
            val city = "Corrupt City"
            val mockJsonNode = mockk<JsonNode>() // This is the overall response node
            every { spiedWeatherService.getWeather(city) } returns mockJsonNode

            val thrown = assertThrows<WeatherServiceException> {
                spiedWeatherService.getTemperature(city)
            }
            assertEquals("Unexpected error processing temperature for city '$city'.", thrown.message)
        }

        @Test
        fun `should throw WeatherServiceException on other unexpected error during parsing`() {
            val city = "ErrorCity"
            val mockJsonNode = mockk<JsonNode>()

            every { spiedWeatherService.getWeather(city) } returns mockJsonNode
            // Simulate an error, e.g., when trying to access "main" node
            every { mockJsonNode.get("main") } throws RuntimeException("Unexpected internal issue")

            val thrown = assertThrows<WeatherServiceException> {
                spiedWeatherService.getTemperature(city)
            }
            assertEquals("Unexpected error processing temperature for city '$city'.", thrown.message)
        }
    }
}