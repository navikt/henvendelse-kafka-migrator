package no.nav.henvendelsemigrator.tasks

import kotliquery.using
import no.nav.henvendelsemigrator.infrastructure.HealthcheckedDataSource
import no.nav.henvendelsemigrator.infrastructure.health.Healthcheck
import no.nav.henvendelsemigrator.infrastructure.health.HealthcheckResult

class SetupMigrationTableTask(
    val henvendelseDb: HealthcheckedDataSource
) : SimpleTask() {
    override val name: String = "Fase 0 - Setup tabeller"
    override val description: String = """
        Setter opp midlertidige tabeller nødvendig for migreringen 
    """.trimIndent()
    private var isDone: Boolean = false
    private var errored: Boolean = false

    override suspend fun runTask() {
        using(henvendelseDb.getOrThrow().connection) { connection ->
            runCatching {
                val createTable = connection.prepareStatement(
                    """
                    CREATE TABLE migreringmetadata(
                        key VARCHAR(50),
                        value VARCHAR(50)
                    );
                    """.trimIndent()
                )
                val insertDefaultValues = connection.prepareStatement(
                    """
                    insert into migreringmetadata values (
                        'SIST_PROSESSERT_HENDELSE',
                        '9223372036854775807'
                    );
                    """.trimIndent()
                )

                createTable.execute()
                insertDefaultValues.execute()
                connection.commit()
            }
                .onSuccess { isDone = true }
                .onFailure { errored = true }
            println("$name is done")
        }
    }

    override suspend fun reset() {
        isDone = false
        errored = false
    }

    override fun status() = TaskStatus(
        name = name,
        description = description,
        startingTime = startingTime,
        endTime = endTime,
        isRunning = isRunning(),
        isDone = isDone,
        processed = when {
            isDone -> 1
            errored -> -1
            else -> 0
        }
    )

    override fun toHealtchCheck() = Healthcheck {
        HealthcheckResult.Ok(name, 0, "N/A")
    }
}
