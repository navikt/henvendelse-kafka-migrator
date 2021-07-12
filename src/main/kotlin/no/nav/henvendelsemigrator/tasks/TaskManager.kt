package no.nav.henvendelsemigrator.tasks

import kotlinx.coroutines.*
import no.nav.henvendelsemigrator.infrastructure.health.Healthcheck
import java.time.LocalDateTime

interface Task {
    val name: String
    val description: String
    suspend fun start(): Unit
    suspend fun stop(): Unit
    fun isRunning(): Boolean
    fun status(): TaskStatus
    fun toHealtchCheck(): Healthcheck
}
data class TaskStatus(
    val name: String,
    val description: String,
    val startingTime: LocalDateTime?,
    val endTime: LocalDateTime?,
    val isRunning: Boolean,
    val isDone: Boolean,
    val processed: Int
)
class TaskManager(vararg tasks: Task) {
    val taskmap: Map<String, Task> = tasks.associateBy { it.name }
}
