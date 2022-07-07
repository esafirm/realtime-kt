package nolambda.stream.realtime.internal

import java.util.Timer
import kotlin.concurrent.schedule

typealias TimerCallback = () -> Unit
typealias TimerCalculation = (tries: Int) -> Long

class RetryTimer(
    val calculation: TimerCalculation = createRetryFunction(),
    val callback: TimerCallback
) {

    var timer: Timer? = null
    var tries = 0

    /**
     * Cancels any previous timer and reset tries
     */
    fun reset() {
        tries = 0
        timer?.cancel()
    }

    /**
     * Cancels any previous scheduleTimeout and schedules callback
     */
    fun scheduleTimeout() {
        timer?.cancel()
        timer = Timer().also {
            val delay = calculation(tries + 1)

            it.schedule(delay) {
                tries += 1
                callback()
            }
        }
    }

    companion object {

        // Need to limit doubling to avoid overflow, this limit gives 1 million times the first delay
        private const val MAX_SHIFT = 20;

        /**
         * Generate an exponential backoff function with first and max delays
         */
        fun createRetryFunction(
            firstDelayInMs: Int = 1000,
            maxDelayInMs: Long = 10_000
        ): TimerCalculation {
            return { tries: Int ->
                val shiftAmount = if ((tries - 1) > MAX_SHIFT) MAX_SHIFT else tries - 1
                val delay = firstDelayInMs.shl(shiftAmount).toLong()
                if (delay > maxDelayInMs) maxDelayInMs else delay
            }
        }
    }
}
