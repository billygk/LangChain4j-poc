package com.github.billygk.ai_demo

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class AppConfig {

    @Bean
    fun appRestClient(): RestClient {
        return RestClient.create()
    }
}