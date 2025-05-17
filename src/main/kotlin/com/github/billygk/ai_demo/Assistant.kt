package com.github.billygk.ai_demo

import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.UserMessage
import dev.langchain4j.service.V
import dev.langchain4j.service.spring.AiService
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


