package nolambda.stream.realtime

import kotlin.reflect.KClass

interface SimpleLogger {
    fun log(message: () -> String)

    companion object {
        val NOOP = object : SimpleLogger {
            override fun log(message: () -> String) {
            }
        }
    }
}

class SystemOutLogger(
    private val clazz: KClass<*>
) : SimpleLogger {
    override fun log(message: () -> String) {
        println("${clazz.simpleName} -> ${message()}")
    }
}
