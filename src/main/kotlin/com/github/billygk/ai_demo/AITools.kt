package com.github.billygk.ai_demo

import dev.langchain4j.agent.tool.Tool
import org.springframework.stereotype.Component
import java.time.LocalDateTime

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
