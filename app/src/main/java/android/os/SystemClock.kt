package android.os

/**
 * JVM Stub untuk android.os.SystemClock
 * Memberikan waktu monotonic berbasis hardware osilator (System.nanoTime)
 * yang kompatibel penuh dengan API Android asli.
 */
object SystemClock {
    fun elapsedRealtime(): Long = System.nanoTime() / 1_000_000L
    fun elapsedRealtimeNanos(): Long = System.nanoTime()
    fun uptimeMillis(): Long = System.nanoTime() / 1_000_000L
}
