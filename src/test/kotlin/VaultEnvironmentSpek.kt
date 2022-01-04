import no.nav.syfo.VaultServiceUser
import no.nav.syfo.utils.getFileAsString
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.xdescribe

object VaultEnvironmentSpek : Spek({
    xdescribe("Inject vault from file") {
        it("Should create VaultServiceUser objeckt from file") {

            val vaultServiceuser = VaultServiceUser(
                serviceuserPassword = getFileAsString("src/test/resources/password"),
                serviceuserUsername = getFileAsString("src/test/resources/username")
            )

            vaultServiceuser.serviceuserPassword shouldBeEqualTo "1324fesdsdfsdffsfsdfds"
            vaultServiceuser.serviceuserUsername shouldBeEqualTo "srvserviceuser"
        }
    }
})
