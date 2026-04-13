package com.capstone.focus

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.openfeign.EnableFeignClients


@EnableFeignClients
@SpringBootApplication
class FocusApplication

fun main(args: Array<String>) {
	runApplication<FocusApplication>(*args)
}
