import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.syfo"
version = "1.0.0"

val coroutinesVersion = "1.3.3"
val jacksonVersion = "2.9.7"
val kluentVersion = "1.39"
val ktorVersion = "1.3.0"
val logbackVersion = "1.2.3"
val logstashEncoderVersion = "6.1"
val prometheusVersion = "0.5.0"
val spekVersion = "2.0.8"
val micrometerRegistryPrometheusVersion = "1.1.5"
val nimbusjosejwtVersion = "7.5.1"
val spekjunitVersion = "1.1.5"
val hikariVersion = "3.3.1"
val jaxbBasicAntVersion = "1.11.1"
val javaxAnnotationApiVersion = "1.3.2"
val jaxwsToolsVersion = "2.3.1"
val jaxbRuntimeVersion = "2.4.0-b180830.0438"
val javaxJaxwsApiVersion = "2.2.1"
val jaxbApiVersion = "2.4.0-b180830.0359"
val javaxActivationVersion = "1.1.1"
val ojdbc8Version = "19.3.0.0"
val mockkVersion = "1.9.3"
val smCommonVersion = "1.c22544d"
val sykmeldingVersion = "2019.07.29-02-53-86b22e73f7843e422ee500b486dac387a582f2d1"
val fellesformatVersion = "2019.07.30-12-26-5c924ef4f04022bbb850aaf299eb8e4464c1ca6a"
val kithHodemeldingVersion = "2019.07.30-12-26-5c924ef4f04022bbb850aaf299eb8e4464c1ca6a"
val javaTimeAdapterVersion = "1.1.3"
val postgresVersion = "42.2.5"
val vaultJavaDriveVersion = "3.1.0"
val kontrollsystemblokk = "2019.07.29-02-53-86b22e73f7843e422ee500b486dac387a582f2d1"
val infotrygdForespVersion = "2019.07.29-02-53-86b22e73f7843e422ee500b486dac387a582f2d1"
val avroVersion = "1.8.2"
val confluentVersion = "5.0.0"
val syfoAvroSchemasVersion = "c8be932543e7356a34690ce7979d494c5d8516d8"
val legeerklaering = "2019.07.29-02-53-86b22e73f7843e422ee500b486dac387a582f2d1"
val swaggerUiVersion = "3.10.0"
plugins {
    kotlin("jvm") version "1.3.60"
    id("org.jmailen.kotlinter") version "2.1.1"
    id("com.diffplug.gradle.spotless") version "3.24.0"
    id("com.github.johnrengelman.shadow") version "5.2.0"
    id("org.hidetake.swagger.generator") version "2.18.1" apply true
}

val githubUser: String by project
val githubPassword: String by project

repositories {
    mavenCentral()
    jcenter()
    maven(url = "https://dl.bintray.com/kotlin/ktor")
    maven(url = "https://dl.bintray.com/spekframework/spek-dev")
    maven(url = "https://kotlin.bintray.com/kotlinx")
    maven(url = "http://packages.confluent.io/maven/")
    maven(url = "https://repo1.maven.org/maven2/")
    maven {
        url = uri("https://maven.pkg.github.com/navikt/syfosm-common")
        credentials {
            username = githubUser
            password = githubPassword
        }
    }
}

dependencies {
    implementation(kotlin("stdlib"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:$coroutinesVersion")
    implementation ("io.micrometer:micrometer-registry-prometheus:$micrometerRegistryPrometheusVersion")

    implementation ("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-jackson:$ktorVersion")
    implementation ("io.ktor:ktor-jackson:$ktorVersion")
    implementation ("io.ktor:ktor-auth:$ktorVersion")
    implementation ("io.ktor:ktor-auth-jwt:$ktorVersion")

    implementation("no.nav.helse:syfosm-common-rest-sts:$smCommonVersion")

    implementation ("ch.qos.logback:logback-classic:$logbackVersion")
    implementation ("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    implementation ("com.fasterxml.jackson.module:jackson-module-jaxb-annotations:$jacksonVersion")
    implementation ("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation ("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:$jacksonVersion")
    implementation ("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")

    implementation("no.nav.helse:syfosm-common-models:$smCommonVersion")
    implementation("no.nav.helse.xml:sm2013:$sykmeldingVersion")
    implementation("no.nav.helse.xml:xmlfellesformat:$fellesformatVersion")
    implementation("no.nav.helse.xml:kith-hodemelding:$kithHodemeldingVersion")
    implementation("no.nav.helse:syfosm-common-kafka:$smCommonVersion")
    implementation ("no.nav.helse.xml:infotrygd-foresp:$infotrygdForespVersion")
    implementation ("no.nav.helse.xml:kontrollsystemblokk:$kontrollsystemblokk")
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

    implementation ("com.zaxxer:HikariCP:$hikariVersion")
    implementation ("com.oracle.ojdbc:ojdbc8:$ojdbc8Version")
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.bettercloud:vault-java-driver:$vaultJavaDriveVersion")
    implementation("io.confluent:kafka-avro-serializer:$confluentVersion")
    implementation("io.confluent:kafka-streams-avro-serde:$confluentVersion")
    implementation("org.apache.avro:avro:$avroVersion")
    implementation("no.nav.syfo.schemas:syfosmoppgave-avro:$syfoAvroSchemasVersion")

    swaggerUI( "org.webjars:swagger-ui:$swaggerUiVersion")

    testImplementation ("io.mockk:mockk:$mockkVersion")
    testImplementation ("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation ("org.spekframework.spek2:spek-dsl-jvm:$spekVersion")
    testImplementation ("com.nimbusds:nimbus-jose-jwt:$nimbusjosejwtVersion")
    testImplementation ("io.ktor:ktor-server-test-host:$ktorVersion")
    implementation("no.nav.helse.xml:legeerklaering:$legeerklaering")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:$spekVersion") {
        exclude(group = "org.jetbrains.kotlin")
    }
    testRuntimeOnly ("org.spekframework.spek2:spek-runner-junit5:$spekVersion") {
        exclude(group = "org.jetbrains.kotlin")
    }
    testRuntimeOnly ("org.jetbrains.spek:spek-junit-platform-engine:$spekjunitVersion")
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
        kotlinOptions.jvmTarget = "12"
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
            includeEngines("spek2")
        }
        testLogging {
            showStandardStreams = true
        }
    }

    "check" {
        dependsOn("formatKotlin", "generateSwaggerUI")
    }
}
