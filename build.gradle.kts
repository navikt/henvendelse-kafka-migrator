val mainClassKt = "no.nav.henvendelsemigrator.MainKt"

plugins {
    application
    kotlin("jvm") version "1.5.20"
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    val ktorVersion = "1.6.1"
    val kafkaVersion = "2.8.0"
    val logbackVersion = "1.2.3"
    val logstashVersion = "5.1"
    val prometheusVersion = "0.4.0"

    implementation(kotlin("stdlib"))
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-jackson:$ktorVersion")
    implementation("io.ktor:ktor-metrics:$ktorVersion")
    implementation("io.ktor:ktor-auth-jwt:$ktorVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.9.9")
    implementation("org.apache.kafka:kafka-clients:$kafkaVersion")

    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")
    implementation("io.prometheus:simpleclient_dropwizard:$prometheusVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashVersion")

    implementation("dev.nohus:AutoKonfig:1.0.0")
    implementation("com.zaxxer:HikariCP:3.2.0")
    implementation("com.oracle.ojdbc:ojdbc8:19.3.0.0")
    implementation("com.github.seratch:kotliquery:1.3.0")

    testImplementation("org.openjdk.jol:jol-core:0.16")
    testImplementation("org.postgresql:postgresql:42.2.23")
}


tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.jar {
    archiveBaseName.set("app")
    manifest {
        attributes["Main-Class"] = mainClassKt
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}