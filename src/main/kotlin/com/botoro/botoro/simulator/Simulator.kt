package com.botoro.botoro.simulator

import com.botoro.botoro.client.InfluxDBClient
import com.botoro.botoro.entity.Market
import com.botoro.botoro.logger
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.Instant
import javax.annotation.PostConstruct
import kotlin.system.exitProcess

enum class PositionType {
    BUY, SELL
}

data class Position(
    val type: PositionType,
    val amount: Double,
    val price: Double,
    val time: Instant,
)


@Component
class Simulator {
    private final val log = logger(Simulator)

    var money: Double = 5_000.0 * 20
    var position: Position? = null
    var profit: Int = 0
    var lose: Int = 0
    var current: Int = 1
    var lastBuyTime: Instant = Instant.MIN


    var lastMarket: Market = Market("", 0.0, 0.0, Instant.now())

    @Autowired
    lateinit var influxDBClient: InfluxDBClient

    companion object {
        const val MEAN_INTERVAL_IN_SECONDS: Long = 30
    }


    @PostConstruct
    fun init() {
        GlobalScope.launch {
            delay(600)
            simulate()
        }.start()
    }

    var start: Instant = Instant.parse("2021-03-08T09:31:00Z")
    var stop: Instant = Instant.parse("2021-03-29T22:00:00Z")
//    var stop: Instant = Instant.parse("2021-03-12T22:00:00Z")

    lateinit var timeline: List<Market>
    var i = 10
    private fun simulate() {
        start = Instant.parse("2021-03-19T13:40:21Z")
        stop = Instant.parse("2021-03-19T14:01:03Z")

        timeline = influxDBClient.getTimeline(start, stop)
        val meanTimeline = influxDBClient.getMeanTimeline(start, stop)

        println("Total ${timeline.size}")

        while (i < timeline.size) {
            val market = timeline[i]

            if (meanTimeline[current].time.plusSeconds(MEAN_INTERVAL_IN_SECONDS).equals(market.time) ||
                meanTimeline[current].time.plusSeconds(MEAN_INTERVAL_IN_SECONDS).isBefore(market.time)
            ) {
                current++
            }

            if (position == null && shouldBuy(timeline, i, meanTimeline)) {
                log.info("[${market.time}] Buying @ ${market.buy}")
                buy(market)
            }

            if (position != null && shouldSell(timeline, i, meanTimeline)) {
                log.info("[${market.time}] Selling @ ${market.sell}")
                sell(market)
            }

//            if (i % 100 == 0) {
//                log.info("[$i/${timeline.size}] ${market.time}")
//            }

            lastMarket = market
            i++
        }
//        println("[${(System.currentTimeMillis() - start) / 1000}]")
        println("Total profit [$profit]")
        println("Total losses [$lose]")
        exitProcess(0)
    }

//    private fun getBuyMeanMax(meanTimeline: List<Market>, time: Instant): Market? {
//        return meanTimeline
//            .filter { it.time.isAfter(time.minusSeconds(interval)) }
//            .filter { it.time.isBefore(time) }
//            .maxWithOrNull(Comparator.comparing { it.buy })
//    }

    private fun findLowestMin(meanTimeline: List<Market>, currentTime: Instant): Market? {
        var minusSeconds = currentTime.minusSeconds(120)
        if (lastBuyTime.isAfter(minusSeconds)) {
            minusSeconds = lastBuyTime
        }
        return meanTimeline
            .filter { it.time.isAfter(minusSeconds) }
            .filter { it.time.isBefore(currentTime) }
            .minWithOrNull(Comparator.comparing { it.buy })
    }

    //
    private fun findLastMax(meanTimeline: List<Market>, currentTime: Instant): Market? {
        return meanTimeline
//            .filter { it.time.isAfter(currentTime.minusSeconds(30)) }
            .filter { it.time.isAfter(position!!.time) }
            .filter { it.time.isBefore(currentTime) }
            .maxWithOrNull(Comparator.comparing { it.sell })
    }
//
//    private fun getSellMeanMin(meanTimeline: List<Market>, time: Instant): Market? {
//        return meanTimeline
//            .filter { it.time.isAfter(time.minusSeconds(interval)) }
//            .filter { it.time.isBefore(time) }
//            .minWithOrNull(Comparator.comparing { it.sell })
//    }

    private fun currentMean(mean: Market, timeline: List<Market>, i: Int, currentTime: Instant): Market {
        val a = timeline[i]
        val b = timeline[i - 5]
        val c = timeline[i - 10]
        val buy = (a.buy + b.buy + c.buy + mean.buy) / 4
        val sell = (a.sell + b.sell + c.sell + mean.sell) / 4
        return Market("mean", buy, sell, currentTime)
    }

    private fun shouldBuy(timeline: List<Market>, i: Int, meanTimeline: List<Market>): Boolean {
        val market = timeline[i]
        val lastMin = meanTimeline[current]
        val currentMean = currentMean(lastMin, timeline, i, market.time)

        if (!areTimesInInterval(market, lastMin.time, currentMean.time)) {
            return false
        }

        val lowestMin = findLowestMin(meanTimeline, market.time) ?: return false

//        println("${meanTimeline[current].buy} ${market.buy} - ${currentMean.buy} ${buyMeanMin.buy}")

        fun isSteepCurve(): Boolean {
            if (currentMean.buy - lastMin.buy > 15) {
                return true
            }
            return false
        }

        fun isSlowGrowth(): Boolean {
            return false
        }

//        if (isSteepCurve() || (market.buy - avgNow.buy > 5 && avgNow.buy > meanTimeline[current].buy && meanTimeline[current].buy > meanTimeline[current - 1].buy
//            && market.buy - buyMeanMin.buy > 10)) {
//            return true
//        }

        return isSteepCurve() || isSlowGrowth()

        return false
    }

    private fun areTimesInInterval(market: Market, time: Instant, time1: Instant): Boolean {
        if (lastMarket.time.plusSeconds(600).isBefore(market.time)) {
            return false
        }
        // verificar se 300 posicoes atras as horas estao correctas
        val j: Int = if (i > 300) i - 300 else 0
        if (timeline[j].time.plusSeconds(600).isBefore(market.time)) {
            return false
        }

        return true
    }

    private fun positiveGrowthFor(ticks: Int): Boolean {
        var x = i - ticks
        while (x < i) {
            val m1 = timeline[x]
            val m2 = timeline[x + 1]
            x++
            if (m2.buy < m1.buy) {
                return false
            }
        }
        return true
    }

    private fun shouldSell(timeline: List<Market>, i: Int, meanTimeline: List<Market>): Boolean {
        val market = timeline[i]
        val lastMax = meanTimeline[current]
        val currentMean = currentMean(meanTimeline[current], timeline, i, market.time)

        val highestPriceFromPosition = findLastMax(meanTimeline, market.time)

        if (market.time.isBefore(position!!.time.plusSeconds(10))) {
            return false
        }

        if (market.sell < currentMean.sell && currentMean.sell < meanTimeline[current].sell && lastMax.sell - market.sell > 11.5) {
//            println("lastMax [${lastMax.sell}]")
            return true
        }
        return false
    }

    private fun buy(market: Market) {
        position = Position(PositionType.BUY, money, market.buy, market.time)
//        val totalUnits = money / currentMarket.buy
//        println("Bought $totalUnits units")
    }

    private fun sell(market: Market) {
        val totalUnits = money / position!!.price
        val pnl = (totalUnits * market.sell - money).toInt()
        println("PnL: $pnl")

        if (pnl > 0) {
            profit += pnl
        } else {
            lose += pnl
        }
        println("Profit: ${profit + lose}")
        position = null
        lastBuyTime = market.time
    }

}
