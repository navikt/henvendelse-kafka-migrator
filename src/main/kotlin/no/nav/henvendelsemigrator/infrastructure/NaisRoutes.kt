package no.nav.henvendelsemigrator.infrastructure

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.exporter.common.TextFormat
import no.nav.henvendelsemigrator.infrastructure.health.Healthcheck

fun Route.naisRoutes(
    readinessCheck: () -> Boolean,
    livenessCheck: () -> Boolean = { true },
    healthchecks: List<Healthcheck> = emptyList(),
    collectorRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry
) {
    route("internal") {
        get("/isAlive") {
            if (livenessCheck()) {
                call.respondText("Alive")
            } else {
                call.respondText("Not alive", status = HttpStatusCode.InternalServerError)
            }
        }

        get("/isReady") {
            if (readinessCheck()) {
                call.respondText("Ready")
            } else {
                call.respondText("Not ready", status = HttpStatusCode.InternalServerError)
            }
        }

        get("/metrics") {
            val names = call.request.queryParameters.getAll("name[]")?.toSet() ?: setOf()
            call.respondTextWriter(ContentType.parse(TextFormat.CONTENT_TYPE_004)) {
                TextFormat.write004(this, collectorRegistry.filteredMetricFamilySamples(names))
            }
        }

        get("healthchecks") {
            call.respond(healthchecks.map { it.check() })
        }
    }
}
