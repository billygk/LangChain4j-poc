package com.github.billygk.AIDemo1

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/threads")
class ThreadController {

    @GetMapping("/info")
    fun getThreadInfo(): String {
        return Thread.currentThread().toString()
    }

}