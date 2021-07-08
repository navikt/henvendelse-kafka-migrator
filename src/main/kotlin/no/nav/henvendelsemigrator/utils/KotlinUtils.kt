package no.nav.henvendelsemigrator.utils

import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.concurrent.timerTask

inline fun Timer.schedule(delay: Duration, period: Duration, crossinline action: TimerTask.() -> Unit): TimerTask {
    val task = timerTask(action)
    schedule(task, delay.toMillis(), period.toMillis())
    return task
}

val Int.minutes get() = Duration.of(this.toLong(), ChronoUnit.MINUTES)

fun String.toUUID(): UUID {
    return try {
        UUID.fromString(this)
    } catch (exception: IllegalArgumentException) {
        UUID.nameUUIDFromBytes(this.toByteArray())
    }
}
