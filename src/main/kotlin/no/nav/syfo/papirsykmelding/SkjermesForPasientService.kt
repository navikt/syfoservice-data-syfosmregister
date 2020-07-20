package no.nav.syfo.papirsykmelding

import no.nav.syfo.Environment
import no.nav.syfo.VaultConfig
import no.nav.syfo.VaultCredentials
import no.nav.syfo.aksessering.db.oracle.getSykmeldingsDokument
import no.nav.syfo.aksessering.db.oracle.updateDocument
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.DatabaseOracle
import no.nav.syfo.db.DatabasePostgres
import no.nav.syfo.db.VaultCredentialService
import no.nav.syfo.log
import no.nav.syfo.persistering.db.postgres.updateSkjermesForPasient
import no.nav.syfo.utils.getFileAsString
import no.nav.syfo.vault.RenewVaultService

class SkjermesForPasientService(private val environment: Environment, private val applicationState: ApplicationState) {

    private val databaseOracle: DatabaseOracle
    private val databasePostgres: DatabasePostgres
    private val sykmeldingIds = listOf(
        "b1ff3f43-85f2-4c04-874f-443173d6d349",
        "ce657e35-ec31-4a36-a2b0-a30fa37951bc",
        "f2196233-8f0a-4748-9b0f-3a7b6b151b6f"
    )

    init {
        val vaultConfig = VaultConfig(
            jdbcUrl = getFileAsString("/secrets/syfoservice/config/jdbc_url")
        )
        val syfoserviceVaultSecrets = VaultCredentials(
            databasePassword = getFileAsString("/secrets/syfoservice/credentials/password"),
            databaseUsername = getFileAsString("/secrets/syfoservice/credentials/username")
        )
        val vaultCredentialService = VaultCredentialService()
        RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()

        databasePostgres = DatabasePostgres(environment, vaultCredentialService)
        databaseOracle = DatabaseOracle(vaultConfig, syfoserviceVaultSecrets)
    }

    fun start() {
        sykmeldingIds.forEach { sykmeldingId ->
            val result = databaseOracle.getSykmeldingsDokument(sykmeldingId)

            if (result.rows.isNotEmpty()) {

                val document = result.rows.first()
                if (document != null) {
                    document.medisinskVurdering.isSkjermesForPasient = false
                    databaseOracle.updateDocument(document, sykmeldingId)
                }
                val updated = databasePostgres.updateSkjermesForPasient(sykmeldingId, false)
                log.info("updating {} sykmelding dokument with sykmelding id {}", updated, sykmeldingId)
            } else {
                log.info("Could not find sykmelidng with id {}", sykmeldingId)
            }
        }
    }
}
