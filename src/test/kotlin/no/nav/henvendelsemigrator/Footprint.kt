package no.nav.henvendelsemigrator

import org.openjdk.jol.info.GraphLayout

fun main() {
    val id: Long = 2
    val ingest = Ingest(
        lagHenvendelse(id),
        lagHendelser(id),
        lagArkivpost(id),
        lagVedlegg(id),
    )

    println(GraphLayout.parseInstance(ingest).toFootprint())
}
