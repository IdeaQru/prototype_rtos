package android.os

import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue

class Looper private constructor() {
    companion object {
        private val mainLooper = Looper()
        fun getMainLooper(): Looper = mainLooper
    }
}

class Message {
    var what: Int = 0
    var obj: Any? = null
    var target: Handler? = null

    fun sendToTarget() {
        target?.sendMessage(this)
    }
}

open class Handler(val looper: Looper = Looper.getMainLooper()) {
    private val queue = LinkedBlockingQueue<Message>()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Handler-EventLoop-Thread").apply { isDaemon = true }
    }

    init {
        executor.submit {
            while (!Thread.currentThread().isInterrupted) {
                try {
                    val msg = queue.take()
                    handleMessage(msg)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }
    }

    fun obtainMessage(what: Int): Message {
        return Message().apply {
            this.what = what
            this.target = this@Handler
        }
    }

    fun obtainMessage(what: Int, obj: Any?): Message {
        return Message().apply {
            this.what = what
            this.obj = obj
            this.target = this@Handler
        }
    }

    fun sendMessage(msg: Message): Boolean {
        return queue.offer(msg)
    }

    open fun handleMessage(msg: Message) {}
}

object Build {
    object VERSION {
        const val SDK_INT: Int = 34
    }
    object VERSION_CODES {
        const val M = 23
        const val O = 26
        const val UPSIDE_DOWN_CAKE = 34
    }
}

interface IBinder
