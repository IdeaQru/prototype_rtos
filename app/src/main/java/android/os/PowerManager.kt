package android.os

class PowerManager {
    companion object {
        const val PARTIAL_WAKE_LOCK = 1
    }

    @Suppress("UNUSED_PARAMETER")
    fun newWakeLock(flags: Int, tag: String): WakeLock = WakeLock(tag)

    class WakeLock(val tag: String) {
        var isHeld: Boolean = false
            private set

        fun acquire() {
            isHeld = true
        }

        fun release() {
            isHeld = false
        }
    }
}
