package android.content

import android.app.AlarmManager

open class Context {
    companion object {
        const val ALARM_SERVICE = "alarm"
        const val POWER_SERVICE = "power"
    }

    private val alarmManager by lazy { AlarmManager() }
    private val powerManager by lazy { android.os.PowerManager() }

    open fun getSystemService(name: String): Any? {
        return when (name) {
            ALARM_SERVICE -> alarmManager
            POWER_SERVICE -> powerManager
            else -> null
        }
    }
}

open class Intent(val context: Context? = null, val cls: Class<*>? = null) {
    val extras = HashMap<String, Any>()

    fun putExtra(name: String, value: Any): Intent {
        extras[name] = value
        return this
    }

    fun getLongExtra(name: String, defaultValue: Long): Long {
        return (extras[name] as? Long) ?: defaultValue
    }

    fun getStringExtra(name: String): String? {
        return extras[name] as? String
    }
}

abstract class BroadcastReceiver {
    abstract fun onReceive(context: Context, intent: Intent)
}
