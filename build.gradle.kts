import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.syfo"
version = "1.0.0"

val coroutinesVersion = "1.6.0"
val jacksonVersion = "2.13.1"
val kluentVersion = "1.68"
val ktorVersion = "1.6.7"
val logbackVersion = "1.2.10"
val logstashEncoderVersion = "7.0.1"
val prometheusVersion = "0.14.1"
val nimbusjosejwtVersion = "9.18"
val hikariVersion = "5.0.1"
val jaxbBasicAntVersion = "1.11.1"
val javaxAnnotationApiVersion = "1.3.2"
val jaxwsToolsVersion = "2.3.1"
val jaxbRuntimeVersion = "2.4.0-b180830.0438"
val javaxJaxwsApiVersion = "2.2.1"
val jaxbApiVersion = "2.4.0-b180830.0359"
val javaxActivationVersion = "1.1.1"
val ojdbc8Version = "19.3.0.0"
val mockkVersion = "1.12.2"
val smCommonVersion = "1.a92720c"
val sykmeldingVersion = "2019.07.29-02-53-86b22e73f7843e422ee500b486dac387a582f2d1"
val fellesformatVersion = "2019.07.30-12-26-5c924ef4f04022bbb850aaf299eb8e4464c1ca6a"
val kithHodemeldingVersion = "2019.07.30-12-26-5c924ef4f04022bbb850aaf299eb8e4464c1ca6a"
val javaTimeAdapterVersion = "1.1.3"
val postgresVersion = "42.3.2"
val vaultJavaDriveVersion = "3.1.0"
val kontrollsystemblokk = "2019.07.29-02-53-86b22e73f7843e422ee500b486dac387a582f2d1"
val infotrygdForespVersion = "2019.07.29-02-53-86b22e73f7843e422ee500b486dac387a582f2d1"
val avroVersion = "1.8.2"
val confluentVersion = "6.2.2"
val syfoAvroSchemasVersion = "c8be932543e7356a34690ce7979d494c5d8516d8"
val legeerklaering = "2019.07.29-02-53-86b22e73f7843e422ee500b486dac387a582f2d1"
val swaggerUiVersion = "4.1.3"
val kotlinVersion = "1.6.0"
val googleCloudStorageVersion = "2.3.0"
val pale2CommonVersion = "1.a40bf1a"
val kotestVersion = "5.1.0"

plugins {
    kotlin("jvm") version "1.6.0"
    id("org.jmailen.kotlinter") version "3.6.0"
    id("com.diffplug.spotless") version "5.16.0"
    id("com.github.johnrengelman.shadow") version "7.0.0"
    id("org.hidetake.swagger.generator") version "2.18.2" apply true
}

val githubUser: String by project
val githubPassword: String by project

repositories {
    maven {
        url = uri("https://maven.pkg.github.com/navikt/syfosm-common")
        credentials {
            username = githubUser
            password = githubPassword
        }
    }
    mavenCentral()
    maven(url = "https://packages.confluent.io/maven/")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:$coroutinesVersion")
    implementation("io.prometheus:simpleclient_hotspot:$prometheusVersion")
    implementation("io.prometheus:simpleclient_common:$prometheusVersion")

    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-jackson:$ktorVersion")
    implementation("io.ktor:ktor-jackson:$ktorVersion")
    implementation("io.ktor:ktor-auth:$ktorVersion")
    implementation("io.ktor:ktor-auth-jwt:$ktorVersion")

    implementation("no.nav.helse:syfosm-common-rest-sts:$smCommonVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    implementation("com.fasterxml.jackson.module:jackson-module-jaxb-annotations:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    implementation("no.nav.helse:syfosm-common-models:$smCommonVersion")
    implementation("no.nav.helse.xml:sm2013:$sykmeldingVersion")
    implementation("no.nav.helse.xml:xmlfellesformat:$fellesformatVersion")
    implementation("no.nav.helse.xml:kith-hodemelding:$kithHodemeldingVersion")
    implementation("no.nav.helse:syfosm-common-kafka:$smCommonVersion")
    implementation("no.nav.helse.xml:infotrygd-foresp:$infotrygdForespVersion")
    implementation("no.nav.helse.xml:kontrollsystemblokk:$kontrollsystemblokk")
    implementation("no.nav.helse:syfosm-common-diagnosis-codes:$smCommonVersion")
    implementation("com.migesok:jaxb-java-time-adapters:$javaTimeAdapterVersion")

    implementation("javax.xml.ws:jaxws-api:$javaxJaxwsApiVersion")
    implementation("javax.annotation:javax.annotation-api:$javaxAnnotationApiVersion")
    implementation("javax.xml.bind:jaxb-api:$jaxbApiVersion")
    implementation("org.glassfish.jaxb:jaxb-runtime:$jaxbRuntimeVersion")
    implementation("javax.activation:activation:$javaxActivationVersion")
    implementation("com.sun.xml.ws:jaxws-tools:$jaxwsToolsVersion") {
        exclude(group = "com.sun.xml.ws", module = "policy")
    }

    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("com.oracle.ojdbc:ojdbc8:$ojdbc8Version")
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.bettercloud:vault-java-driver:$vaultJavaDriveVersion")
    implementation("io.confluent:kafka-avro-serializer:$confluentVersion")
    implementation("io.confluent:kafka-streams-avro-serde:$confluentVersion")
    implementation("org.apache.avro:avro:$avroVersion")
    implementation("no.nav.syfo.schemas:syfosmoppgave-avro:$syfoAvroSchemasVersion")
    implementation("no.nav.helse.xml:legeerklaering:$legeerklaering")
    implementation("com.google.cloud:google-cloud-storage:$googleCloudStorageVersion")
    implementation("no.nav.syfo:pale-2-common-models:$pale2CommonVersion")
    swaggerUI( "org.webjars:swagger-ui:$swaggerUiVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation("com.nimbusds:nimbus-jose-jwt:$nimbusjosejwtVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-property:$kotestVersion")
}

swaggerSources {
    create("macgyver").apply {
        setInputFile(file("api/oas3/macgyver-api.yaml"))
    }
}


tasks {
    withType<Jar> {
        manifest.attributes["Main-Class"] = "no.nav.syfo.BootstrapKt"
    }

    create("printVersion") {
        doLast {
            println(project.version)
        }
    }
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    withType<org.hidetake.gradle.swagger.generator.GenerateSwaggerUI> {
        outputDir = File(buildDir.path + "/resources/main/api")
    }

    withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
        transform(com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer::class.java) {
            setPath("META-INF/cxf")
            include("bus-extensions.txt")
        }
        dependsOn("generateSwaggerUI")
    }

    withType<Test> {
        useJUnitPlatform {
        }
        testLogging {
            showStandardStreams = true
        }
    }

    "check" {
        dependsOn("formatKotlin", "generateSwaggerUI")
    }
}
