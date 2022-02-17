package no.nav.syfo.papirsykmelding

import kotlinx.coroutines.DelicateCoroutinesApi
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

@DelicateCoroutinesApi
class SkjermesForPasientService(private val environment: Environment, private val applicationState: ApplicationState) {

    private val databaseOracle: DatabaseOracle
    private val databasePostgres: DatabasePostgres
    private val sykmeldingIds = emptyList<String>()

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
