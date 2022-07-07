package nolambda.stream.realtime

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import nolambda.stream.realtime.realtime.internal.RetryTimer

class RetryTimerSpec : StringSpec({

    "retry function should stop at max delay" {
        val backOff = RetryTimer.createRetryFunction(maxDelayInMs = 5000)
        backOff(500) shouldBe 5000
    }

    "retry function should return first delay on tries == 1" {
        val backOff = RetryTimer.createRetryFunction()
        backOff(1) shouldBe 1000
    }

    "retry function should return first delay * 4 for tries == 3" {
        val backOff = RetryTimer.createRetryFunction()
        backOff(3) shouldBe 4000
    }
})
