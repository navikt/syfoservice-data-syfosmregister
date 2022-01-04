package no.nav.syfo.service

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.log
import no.nav.syfo.model.Behandlingsutfall
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.db.postgres.oppdaterBehandlingsutfall
import java.nio.file.Paths

class MaskerBehandlingsutfallService(
    private val databasePostgres: DatabaseInterfacePostgres
) {

    fun maskerBehandlingsutfall() {
        val behandlingsutfallIds = objectMapper.readValue<List<String>>(Paths.get("/var/run/secrets/nais.io/vault/behandlingsutfall.json").toFile())
        log.info("Skal oppdatere ${behandlingsutfallIds.size} behandlingsutfall")

        behandlingsutfallIds.forEach {
            databasePostgres.oppdaterBehandlingsutfall(Behandlingsutfall(id = it, behandlingsutfall = ValidationResult(Status.MANUAL_PROCESSING, emptyList())))
        }
        log.info("Ferdig med alle behandlingsutfall-idene")
    }
}
