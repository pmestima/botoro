package com.botoro.botoro.conf

import com.influxdb.LogLevel
import com.influxdb.client.InfluxDBClient
import com.influxdb.client.InfluxDBClientFactory
import com.influxdb.client.QueryApi
import com.influxdb.client.domain.HealthCheck
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class InfluxConfig {

    var url: String = "http://localhost:8086"
    val token = "_V83W0oxokkuNdWja-FL8y9LRLguyiWCN7r-st9aVbS2tE_EBSQnozHr0AgDuhLN0N60RjC_0N0edu5tAT-zLw=="
    val org = "myOrg"
    val bucket = "myBucket"

    fun influxDB(): InfluxDBClient {
        val influxDBClient: InfluxDBClient = InfluxDBClientFactory.create(url, token.toCharArray(), org, bucket)

        if (influxDBClient.health().status != HealthCheck.StatusEnum.PASS) {
            throw RuntimeException("InfluxDB not connected")
        }

        influxDBClient.logLevel = LogLevel.NONE
        return influxDBClient
    }

    @Bean
    fun queryApi(): QueryApi {
        return influxDB().queryApi;
    }
}
