package no.nav.henvendelsemigrator.introspect

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlin.reflect.KClass

abstract class IntrospectionTask<INPUT : Any, OUTPUT : Any>(
    val name: String,
    val description: String,
    val inputExample: INPUT
) {
    abstract fun action(input: INPUT): OUTPUT

    companion object {
        operator fun <INPUT : Any, OUTPUT : Any> invoke(
            name: String,
            description: String,
            inputExample: INPUT,
            task: (input: INPUT) -> OUTPUT
        ) = object : IntrospectionTask<INPUT, OUTPUT>(name, description, inputExample) {
            override fun action(input: INPUT): OUTPUT {
                return task(input)
            }
        }
    }
}

fun Route.introspectRoutes(vararg tasks: IntrospectionTask<*, *>) {
    val taskmap: Map<String, IntrospectionTask<Any, Any>> = tasks
        .map { it as IntrospectionTask<Any, Any> }
        .associateBy { it.name }

    route("introspect") {
        route("{taskid}") {
            post("run") {
                val taskId = requireNotNull(call.parameters["taskid"])
                val task: IntrospectionTask<Any, Any>? = taskmap[taskId]
                when (task) {
                    null -> call.respond(HttpStatusCode.NotFound, "Task not found '$taskId'")
                    else -> {
                        val taskInputType: KClass<*> = task.inputExample::class
                        val body = call.receive(taskInputType)
                        call.respond(HttpStatusCode.OK, task.action(body))
                    }
                }
            }
        }
        get {
            call.respond(taskmap)
        }
    }
}
