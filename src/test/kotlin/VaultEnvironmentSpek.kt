
import io.kotest.core.spec.style.FunSpec
import no.nav.syfo.VaultServiceUser
import no.nav.syfo.utils.getFileAsString
import org.amshove.kluent.shouldBeEqualTo

object VaultEnvironmentSpek : FunSpec({
    context("Inject vault from file") {
        test("Should create VaultServiceUser objeckt from file") {

            val vaultServiceuser = VaultServiceUser(
                serviceuserPassword = getFileAsString("src/test/resources/password"),
                serviceuserUsername = getFileAsString("src/test/resources/username")
            )

            vaultServiceuser.serviceuserPassword shouldBeEqualTo "1324fesdsdfsdffsfsdfds"
            vaultServiceuser.serviceuserUsername shouldBeEqualTo "srvserviceuser"
        }
    }
})
