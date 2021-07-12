package no.nav.henvendelsemigrator.tasks

import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*

data class ProcessingValue(val henvendelseId: String)
fun Route.debugRoutes(processingTask: ProcessChangesTask) {
    route("internal/debug") {
        post("/{debugId}") {
            val debugId = requireNotNull(call.parameters["debugId"])
            val body: ProcessingValue = call.receive()
            val response = processingTask.processChunk(listOf(body.henvendelseId.toLong()))
            call.respond(response)
        }
    }
}
