package no.nav.syfo.model

import no.nav.syfo.objectMapper
import org.postgresql.util.PGobject
import java.time.LocalDateTime

data class Sykmeldingsopplysninger(
    var id: String,
    val pasientFnr: String,
    val pasientAktoerId: String,
    val legeFnr: String,
    val legeAktoerId: String,
    val mottakId: String,
    val legekontorOrgNr: String?,
    val legekontorHerId: String?,
    val legekontorReshId: String?,
    val epjSystemNavn: String,
    val epjSystemVersjon: String,
    var mottattTidspunkt: LocalDateTime,
    val tssid: String?
)

data class Sykmeldingsdokument(
    var id: String,
    var sykmelding: Sykmelding
)

fun Sykmelding.toPGObject() = PGobject().also {
    it.type = "json"
    it.value = objectMapper.writeValueAsString(this)
}
