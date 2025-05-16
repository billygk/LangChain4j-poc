package com.github.billygk.AIDemo1

import dev.langchain4j.agent.tool.Tool
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.service.AiServices
import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V
import dev.langchain4j.service.spring.AiService
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@AiService
interface Assistant {
    @SystemMessage("You are a Senior Software Engineer, that will answer with great level technical detail.")
    fun softwareEngineer(question: String) : String

    @SystemMessage("You are a Customer service representative, that will answer with a friendly tone.")
    fun customerService(question: String) : String

    @UserMessage("Extract the date and time from the following text: {{text}}")
    fun extractDateTimeFrom(@V("text") text: String): LocalDateTime
}

/**
    --> AI Tools definitions, methods model can call to perform some action.
 */
@Component
class AITools(private val weatherService: WeatherService) {

    private val logger = org.slf4j.LoggerFactory.getLogger(AITools::class.java)

    @Tool("Returns the weather forecast for a given city")
    fun weatherForecast(city: String, localDateTime: LocalDateTime): Double {
        logger.info("--> Getting weather forecast for $localDateTime --> $city")
        // simulating api call to public weather service return random temperature between -10 and 40
        val temperature = weatherService.getTemperature(city)
        logger.info("<-- Weather forecast for $localDateTime --> $city: $temperature")
        return temperature
    }

}

@RestController
class AiController(
    private val assistant: Assistant,
    private val weatherService: WeatherService) {

    @GetMapping("/softwareEngineer")
    fun index(): String {
        val question = "What is a for loop?"
        val response = assistant.softwareEngineer(question)
        return """{"question":"$question","response":"$response"}"""
    }

    @GetMapping("/customerService")
    fun customerService(): String {
        val question = "What is the status of my order?"
        val response = assistant.customerService(question)
        return """{"question":"$question","response":"$response"}"""
    }

    @GetMapping("/extractDateTime")
    fun extractDateTime(): String {
        val question = "It was the 2nd week of the 5th month of the year 2021. 3789 seconds after the 3rd hour of the day."
        val response = assistant.extractDateTimeFrom(question)
        val responseString = response.toString()
        return """{"question":"$question","response":"$responseString"}"""
    }

    @GetMapping("/useToolsSample")
    fun useToolsSample(@RequestParam paramQuestion: String? = null): String {
        val model : ChatLanguageModel = OpenAiChatModel.builder()
            .apiKey("demo")
            .modelName("gpt-4o-mini")
            .logRequests(true)  // springboot settings are set from application.yaml
            .logResponses(true) // springboot settings are set from application.yaml
            .build()

        val aiService = AiServices.builder(Assistant::class.java)
            .tools(AITools(weatherService))
            .chatLanguageModel(model)
            .build()

        val question = paramQuestion ?: "We need the weather temperature for tuesday at noon in New York"
        val response = aiService.customerService(question)
        return """{"question":"$question","response":"$response"}"""
    }

    @GetMapping("/weather", produces = ["json/plain"])
    fun getWeather(@RequestParam("city") city: String): String {
        return weatherService.getWeather(city)
    }


}
