package com.github.billygk.ai_demo

import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.service.AiServices
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

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
        return weatherService.getWeather(city).toString()
    }


}
