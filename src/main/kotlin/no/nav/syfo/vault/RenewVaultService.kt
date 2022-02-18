package no.nav.syfo.vault

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.db.VaultCredentialService
import org.slf4j.LoggerFactory

@DelicateCoroutinesApi
class RenewVaultService(private val vaultCredentialService: VaultCredentialService, private val applicationState: ApplicationState) {

    private val log = LoggerFactory.getLogger(RenewVaultService::class.java)

    fun startRenewTasks() {
        GlobalScope.launch {
            try {
                Vault.renewVaultTokenTask(applicationState)
            } catch (e: Exception) {
                log.error("Noe gikk galt ved fornying av vault-token", e.message)
            } finally {
                applicationState.ready = false
            }
        }

        GlobalScope.launch {
            try {
                vaultCredentialService.runRenewCredentialsTask(applicationState)
            } catch (e: Exception) {
                log.error("Noe gikk galt ved fornying av vault-credentials", e.message)
            } finally {
                applicationState.ready = false
            }
        }
    }
}
