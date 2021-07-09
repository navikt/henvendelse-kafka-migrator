package no.nav.henvendelsemigrator.tasks

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*

fun Route.taskRoutes(taskManager: TaskManager) {
    route("task") {
        route("{taskid}") {
            post("start") {
                val taskId = requireNotNull(call.parameters["taskid"])
                val task = taskManager.taskmap[taskId]
                when {
                    task == null -> call.respond(HttpStatusCode.NotFound, "Task not found '$taskId'")
                    task.isRunning() -> call.respond(HttpStatusCode.NotAcceptable, "Task already running '$taskId'")
                    else -> {
                        task.start()
                        call.respond(HttpStatusCode.OK, "Started task '$taskId'")
                    }
                }
            }
            post("stop") {
                val taskId = requireNotNull(call.parameters["taskid"])
                val task = taskManager.taskmap[taskId]
                when {
                    task == null -> call.respond(HttpStatusCode.NotFound, "Task not found '$taskId'")
                    !task.isRunning() -> call.respond(HttpStatusCode.NotAcceptable, "Task not running '$taskId'")
                    else -> {
                        task.stop()
                        call.respond(HttpStatusCode.OK, "Stopped task '$taskId'")
                    }
                }
            }
            get("status") {
                val taskId = requireNotNull(call.parameters["taskid"])
                when (val task = taskManager.taskmap[taskId]) {
                    null -> call.respond(HttpStatusCode.NotFound, "Task not found '$taskId'")
                    else -> call.respond(task.status())
                }
            }
        }

        get {
            call.respond(taskManager.taskmap.mapValues { it.value.status() })
        }
    }
}
