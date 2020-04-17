package no.nav.syfo.application

import io.ktor.server.engine.ApplicationEngine

class ApplicationServer(private val applicationServer: ApplicationEngine, private val applicationState: ApplicationState) {
    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            this.applicationServer.stop(10_000, 10_000)
        })
    }

    fun start() {
        applicationServer.start(false)
        applicationState.alive = true
    }
}
