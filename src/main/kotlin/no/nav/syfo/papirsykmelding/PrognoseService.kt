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
import no.nav.syfo.persistering.db.postgres.updateErIkkeIArbeid
import no.nav.syfo.utils.getFileAsString
import no.nav.syfo.vault.RenewVaultService

class PrognoseService(environment: Environment, applicationState: ApplicationState) {

    private val databasePostgres: DatabasePostgres
    private val databaseOracle: DatabaseOracle
    private val sykmeldingIds = listOf("f0763775-15ec-44ae-bbc9-732ac2134675")
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

    fun setErIkkeIArbeidToNull() {
        sykmeldingIds.forEach { sykmeldingId ->
            val result = databaseOracle.getSykmeldingsDokument(sykmeldingId)

            if (result.rows.isNotEmpty()) {

                val document = result.rows.first()
                if (document != null) {
                    document.prognose.erIkkeIArbeid = null
                    databaseOracle.updateDocument(document, sykmeldingId)
                }
                val updated = databasePostgres.updateErIkkeIArbeid(sykmeldingId, null)
                log.info("updating {} sykmelding dokument with sykmelding id {}", updated, sykmeldingId)
            } else {
                log.info("Could not find sykmelidng with id {}", sykmeldingId)
            }
        }
    }
}
