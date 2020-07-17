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
import no.nav.syfo.persistering.db.postgres.updateSvangerskap
import no.nav.syfo.utils.getFileAsString
import no.nav.syfo.vault.RenewVaultService

class SvangerskapService(environment: Environment, applicationState: ApplicationState) {

    private val databasePostgres: DatabasePostgres
    private val databaseOracle: DatabaseOracle
    private val tilOppdatering = listOf(
        "04466b4f-e651-4ae6-9d53-6570227ad466"
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
        tilOppdatering.forEach { sykmeldingId ->
            val result = databaseOracle.getSykmeldingsDokument(sykmeldingId)

            if (result.rows.isNotEmpty()) {
                log.info("updating sykmelding dokument with sykmelding id {}", sykmeldingId)
                val document = result.rows.first()
                if (document != null) {
                    document.medisinskVurdering.isSvangerskap = false
                    databaseOracle.updateDocument(document, sykmeldingId)
                }
                databasePostgres.updateSvangerskap(sykmeldingId, false)
            } else {
                log.info("Could not find sykmelidng with id {}", sykmeldingId)
            }
        }
    }
}
