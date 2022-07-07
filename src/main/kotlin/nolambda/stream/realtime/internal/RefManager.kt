package nolambda.stream.realtime.internal

class RefManager {
    private var ref: Int = 0

    /**
     * Return the next message ref, accounting for overflows
     */
    fun makeRef(): String {
        var newRef = ref + 1
        if (newRef < 0) {
            newRef = 0
        }
        ref = newRef

        return ref.toString()
    }
}
