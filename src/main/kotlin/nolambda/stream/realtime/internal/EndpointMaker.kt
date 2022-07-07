package nolambda.stream.realtime.internal

class EndpointMaker(
    private val baseEndpoint: String,
    private val params: Map<String, String>
) {
    fun make(): String {
        val paramLine = StringBuilder()

        params.forEach { (key, value) ->
            if (paramLine.isEmpty()) {
                paramLine.append(baseEndpoint)
                paramLine.append("?")
            } else {
                paramLine.append("&")
            }
            paramLine.append("$key=$value")
        }
        return paramLine.toString()
    }
}
