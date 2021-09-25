package com.nakoradio.geoleg

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate

@SpringBootApplication
class GeolegApplication

fun main(args: Array<String>) {
    runApplication<GeolegApplication>(*args)
}

@Configuration
internal class AppConfig {

    @Value("\${redis.host}")
    lateinit var redisHost: String

    @Value("\${redis.port}")
    lateinit var redisPort: Integer

    @Value("\${redis.password}")
    lateinit var redisPassword: String

    @Bean
    fun redisConnectionFactory(): LettuceConnectionFactory {
        val conf = RedisStandaloneConfiguration(redisHost, redisPort.toInt())
        conf.setPassword(redisPassword)
        return LettuceConnectionFactory(conf)
    }

    @Bean
    fun redisTemplate(): StringRedisTemplate {
        val template = StringRedisTemplate()
        template.setConnectionFactory(redisConnectionFactory())
        return template
    }

    @Bean
    fun testRedisConnection(redisTemplate: StringRedisTemplate): String {
        redisTemplate.opsForValue().get("/foo")
        println("Redis connection initialized successfully")
        return "redisOk"
    }
}
