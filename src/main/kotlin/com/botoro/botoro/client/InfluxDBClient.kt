package com.botoro.botoro.client

import com.botoro.botoro.entity.Market
import com.botoro.botoro.logger
import com.botoro.botoro.simulator.PositionType
import com.botoro.botoro.simulator.Simulator
import com.influxdb.annotations.Column
import com.influxdb.annotations.Measurement
import com.influxdb.client.QueryApi
import com.influxdb.query.FluxRecord
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.Instant

@Measurement(name = "market")
data class InternalMarket(
    @Column(name = "id", tag = true) var id: String,
    @Column(name = "price") var price: Double,
    @Column(name = "positionType", tag = true) var positionType: PositionType,
    @Column(name = "time", timestamp = true) var time: Instant,
)

@Component
class InfluxDBClient {
    private final val log = logger(InfluxDBClient)

    @Autowired
    lateinit var queryApi: QueryApi

    companion object

    fun getTimeline(start: Instant, stop: Instant): List<Market> {
        val timeline0 = queryApi.query(queryTimeline(start, stop, PositionType.BUY))[0].records
            .map { fluxRecord -> toInternalMarket(fluxRecord) }
        val timeline1 = queryApi.query(queryTimeline(start, stop, PositionType.SELL))[0].records
            .map { fluxRecord -> toInternalMarket(fluxRecord) }

        if (timeline0.isEmpty() || timeline1.isEmpty()) {
            throw RuntimeException("No timeline data")
        }
        if (timeline0.size != timeline1.size) {
            throw RuntimeException("Timelines with different size")
        }

        return toTimeline(timeline0 = timeline0, timeline1 = timeline1)
    }

    fun getMeanTimeline(start: Instant, stop: Instant): List<Market> {
        val meanTimeline0 = queryApi.query(queryMeanTimeline(start, stop, PositionType.BUY))[0].records
            .map { fluxRecord -> toInternalMarket(fluxRecord) }
        val meanTimeline1 = queryApi.query(queryMeanTimeline(start, stop, PositionType.SELL))[0].records
            .map { fluxRecord -> toInternalMarket(fluxRecord) }

        if (meanTimeline0.isEmpty() || meanTimeline1.isEmpty()) {
            throw RuntimeException("No timeline data")
        }
        if (meanTimeline0.size != meanTimeline1.size) {
            throw RuntimeException("Timelines with different size")
        }

        return toTimeline(timeline0 = meanTimeline0, timeline1 = meanTimeline1)
    }

    private fun toTimeline(timeline0: List<InternalMarket>, timeline1: List<InternalMarket>): List<Market> {
        val timeline: MutableList<Market> = mutableListOf()
        var i = 0
        while (i < timeline0.size) {
            val buy = timeline0[i]
            val sell = timeline1[i]

            if (buy.time != sell.time) {
                throw RuntimeException("Times of two timelines are different")
            }

            val market = Market(
                id = buy.id,
                buy = buy.price,
                sell = sell.price,
                time = buy.time,
            )
            timeline.add(market)
            i++
        }

        return timeline.toList()
    }

    private fun toInternalMarket(record: FluxRecord): InternalMarket {
        return InternalMarket(
            id = record.values["id"].toString(),
            price = record.value.toString().toDouble(),
            positionType = PositionType.valueOf(record.values["positionType"].toString()),
            time = record.time!!,
        )
    }
//    fun toValue(fluxTables: List<FluxTable>): com.botoro.botoro.entity.Market {
//        val mapOfValues: MutableMap<String, String> = mutableMapOf()
//
//        fluxTables.map { fluxTable ->
//            mapOfValues.put(fluxTable.records[0].field.toString(), fluxTable.records[0].value.toString())
//        }
//
//        return com.botoro.botoro.entity.Market()
////        return Market(
////            id = mapOfValues["id"] ?: "",
////            name = mapOfValues["name"] ?: "",
////            fullName = mapOfValues["fullName"] ?: "",
////            buy = mapOfValues["buy"]?.toDouble() ?: 0.0,
////            sell = mapOfValues["sell"]?.toDouble() ?: 0.0,
////            marketOpen = mapOfValues["marketOpen"]?.toBoolean() ?: true,
////            askDiscounted = mapOfValues["askDiscounted"]?.toDouble() ?: 0.0,
////            bidDiscounted = mapOfValues["bidDiscounted"]?.toDouble() ?: 0.0,
////            time = fluxTables[0].records[0].time!!,
////        )
//    }

//    fun findLastMin(start: Instant, stop: Instant): String {
////          |> range(start: time(v: "2021-02-26T15:24:00Z"), stop: time(v: "2021-02-26T15:39:00Z"))
//        return """
//            from(bucket: "myBucket")
//              |> range(start: time(v: "$start"), stop: time(v: "$stop"))
//              |> filter(fn: (r) => r._measurement == "market")
//              |> filter(fn: (r) => r._field == "buy")
//              |> aggregateWindow(every: 30s, fn: mean, createEmpty: false)
//              |> min()
//        """.trimIndent()
//    }

    private fun queryMeanTimeline(start: Instant, stop: Instant, positionType: PositionType): String {
        return """
            from(bucket: "myBucket")
//              |> range(start: v.timeRangeStart, stop: v.timeRangeStop)
              |> range(start: time(v: "$start"), stop: time(v: "$stop"))
              |> filter(fn: (r) => r._measurement == "market")
              |> filter(fn: (r) => r._field == "price")
              |> filter(fn: (r) => r.positionType == "$positionType")
              |> aggregateWindow(every: ${Simulator.MEAN_INTERVAL_IN_SECONDS}s, fn: mean, createEmpty: false)
        """.trimIndent()
    }

//    fun detectFirstValue(): String {
//        return """
//            from(bucket: "myBucket")
//                |> range(start: time(v: "$start"), stop: time(v: "$stop"))
////                |> range(start: -5m)
//                |> filter(fn: (r) => r._measurement == "market")
////                |> filter(fn: (r) => r._field == "marketOpen")
//                |> first()
//        """.trimIndent()
//    }
//
//    fun detectLastValue(): String {
//        return """
//            from(bucket: "myBucket")
//              |> range(start: time(v: "$start"), stop: time(v: "$stop"))
//              |> filter(fn: (r) => r._measurement == "market")
//              |> filter(fn: (r) => r._value == "28")
//              |> last()
//        """.trimIndent()
//    }

    fun queryTimeline(start: Instant, stop: Instant, positionType: PositionType): String {
        return """
            from(bucket: "myBucket")
              |> range(start: time(v: "$start"), stop: time(v: "$stop"))
              |> filter(fn: (r) => r._measurement == "market")
              |> filter(fn: (r) => r.positionType == "$positionType")
//              |> aggregateWindow(every: 1s, fn: mean, createEmpty: false)
        """.trimIndent()
    }

//    fun getCurrentValue(now: Instant): String {
//        return """
//            from(bucket: "myBucket")
//              |> range(start: time(v: "$start"), stop: time(v: "$stop"))
//              |> filter(fn: (r) => r._measurement == "market")
////              |> filter(fn: (r) => r._field == "${PositionType.BUY.toString().toLowerCase()}")
//              |> filter(fn: (r) => r._time == time(v: $now))
//        """.trimIndent()
//    }
}
