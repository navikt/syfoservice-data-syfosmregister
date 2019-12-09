import io.mockk.clearAllMocks
import io.mockk.mockkClass
import no.nav.syfo.db.DatabaseInterfaceOracle
import no.nav.syfo.kafka.SykmeldingKafkaProducer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object SykmeldingServiceSpek : Spek({

    val database = mockkClass(DatabaseInterfaceOracle::class)
    val sykmeldingKafkaProducer = mockkClass(SykmeldingKafkaProducer::class)

    beforeEachTest {
        clearAllMocks()
    }

    describe("Tester SykmeldingServiceSpek") {

//        it("Skal hente ut alle sykmeldinger", timeout = 1000000000L) {
//
//            every { sykmeldingKafkaProducer.publishToKafka(any()) } returns Unit
//            mockkStatic("no.nav.syfo.aksessering.db.oracle.SyfoServiceQueriesKt")
//            every { database.hentSykmeldingerSyfoService(0, 10) } returns DatabaseResult(
//                10,
//                0.until(10).map { HashMap<String, String>() })
//            every { database.hentSykmeldingerSyfoService(10, 10) } returns DatabaseResult(
//                20,
//                0.until(10).map { HashMap<String, String>() })
//            every { database.hentSykmeldingerSyfoService(20, 10) } returns DatabaseResult(
//                21,
//                0.until(1).map { HashMap<String, String>() })
//            every { database.hentSykmeldingerSyfoService(21, 10) } returns DatabaseResult(
//                21,
//                emptyList()
//            )
//            every { database.hentAntallSykmeldingerSyfoService() } returns listOf(
//                AntallSykmeldinger(
//                    "21"
//                )
//            )
//            val sykmeldingService = HentSykmeldingerFraSyfoServiceService(sykmeldingKafkaProducer, database, 10)
//            sykmeldingService.run() shouldEqual 21
//            verify(exactly = 1) { database.hentSykmeldingerSyfoService(0, 10) }
//            verify(exactly = 1) { database.hentSykmeldingerSyfoService(10, 10) }
//            verify(exactly = 1) { database.hentSykmeldingerSyfoService(20, 10) }
//            verify(exactly = 1) { database.hentSykmeldingerSyfoService(21, 10) }
//            // verify(exactly = 21) { sykmeldingKafkaProducer.publishToKafka(any()) }
//        }
//
//        it("Skal hente ut alle sykmeldinger 2") {
//            every { sykmeldingKafkaProducer.publishToKafka(any()) } returns Unit
//            mockkStatic("no.nav.syfo.aksessering.db.oracle.SyfoServiceQueriesKt")
//            every { database.hentSykmeldingerSyfoService(any(), any()) } returns DatabaseResult(
//                8,
//                0.until(8).map { HashMap<String, String>() })
//            every { database.hentAntallSykmeldingerSyfoService() } returns listOf(
//                AntallSykmeldinger(
//                    "8"
//                )
//            )
//            every { database.hentSykmeldingerSyfoService(8, 10) } returns DatabaseResult(
//                8,
//                emptyList()
//            )
//            val sykmeldingService = HentSykmeldingerFraSyfoServiceService(sykmeldingKafkaProducer, database, 10)
//            sykmeldingService.run() shouldEqual 8
//            verify(exactly = 1) { database.hentSykmeldingerSyfoService(0, 10) }
//            verify(exactly = 1) { database.hentSykmeldingerSyfoService(8, 10) }
//            // verify(exactly = 8) { sykmeldingKafkaProducer.publishToKafka(any()) }
//        }
    }
})
