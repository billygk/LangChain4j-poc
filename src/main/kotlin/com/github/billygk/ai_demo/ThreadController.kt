package com.github.billygk.ai_demo

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/threads")
class ThreadController {

    /**
     * Retrieves information about the current thread.
     * @return A string representation of the current thread.
     */
    @GetMapping("/info")
    fun getThreadInfo(): String {
        return Thread.currentThread().toString()
    }

}