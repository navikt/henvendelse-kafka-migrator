package no.nav.henvendelsemigrator.utils

object EnvUtils {
    fun getRequiredProperty(propertyName: String): String {
        return requireNotNull(System.getProperty(propertyName, System.getenv(propertyName))) {
            "Mangler property: $propertyName"
        }
    }
}