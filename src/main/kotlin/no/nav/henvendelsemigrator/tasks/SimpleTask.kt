package no.nav.henvendelsemigrator.tasks

import kotlinx.coroutines.*
import no.nav.henvendelsemigrator.infrastructure.health.HealthcheckResult
import java.time.LocalDateTime

abstract class SimpleTask : Task {
    private var process: Job? = null
    protected var startingTime: LocalDateTime? = null
    protected var endTime: LocalDateTime? = null

    abstract suspend fun runTask()
    override suspend fun start() {
        if (process != null) throw IllegalStateException("Task $name is already running")
        val healthcheck = this.toHealtchCheck().check()
        if (healthcheck is HealthcheckResult.Error) {
            throw healthcheck.throwable
        }

        withContext(Dispatchers.IO) {
            startingTime = LocalDateTime.now()
            endTime = null
            println("Starting $name ${LocalDateTime.now()}")
            // Launching in globalScope as to not force ktor to wait for the jobs completions
            process = GlobalScope.launch {
                reset()
                runTask()
                endTime = LocalDateTime.now()
            }
            println("Started $name ${LocalDateTime.now()}")
        }
    }

    override suspend fun stop() {
        if (process == null) throw IllegalStateException("Task $name is not running")
        process?.cancel()
        process = null
    }

    override fun isRunning(): Boolean = process != null
}
