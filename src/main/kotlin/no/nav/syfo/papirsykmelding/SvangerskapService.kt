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
        "7391a086-d9f2-486f-a2f2-692e04bdce18",
        "7ac41be5-1418-4c77-8bd5-e06af646ab7d",
        "bc37f602-3cf5-4440-969a-63a4c4f58547",
        "e71b6402-0e94-4489-b28d-a4185039534e",
        "26a85e6e-e63c-4c29-b3e6-122ed7e86fb5",
        "52f315f0-0910-4967-9c97-ca88a45c35d9",
        "40a7ebf7-41d6-4f84-9d6c-41645a61a89d",
        "766cbd74-1cc2-4bcf-9df1-0ba3df7a623b",
        "3615e3cb-6769-42a1-b114-59629ac74ca8",
        "edf1cfc0-b19c-4cff-8ce1-3ba49b6030f5",
        "e1209293-a520-4a0d-ab05-7b3ad6186e06",
        "29cdd32e-c720-4c63-8581-176ae58f3a02",
        "c28ba64d-2ff2-42a7-8b89-6012977af901",
        "c702b1bb-4949-4327-850a-60fc13ce1adb",
        "39ba04b8-67b7-4324-82f8-ab87b7e19eea"
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

                val document = result.rows.first()
                if (document != null) {
                    document.medisinskVurdering.isSvangerskap = false
                    databaseOracle.updateDocument(document, sykmeldingId)
                }
                val updated = databasePostgres.updateSvangerskap(sykmeldingId, false)
                log.info("updating {} sykmelding dokument with sykmelding id {}", updated, sykmeldingId)
            } else {
                log.info("Could not find sykmelidng with id {}", sykmeldingId)
            }
        }
    }
}
