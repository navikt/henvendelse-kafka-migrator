package no.nav.henvendelsemigrator.infrastructure

import dev.nohus.autokonfig.AutoKonfig
import dev.nohus.autokonfig.clear
import dev.nohus.autokonfig.withEnvironmentVariables
import dev.nohus.autokonfig.withSystemProperties

open class AutoKonfigAware {
    init {
        AutoKonfig
            .clear()
            .withSystemProperties()
            .withEnvironmentVariables()
    }
}
