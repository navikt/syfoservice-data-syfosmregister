package no.nav.syfo.narmesteleder

import no.nav.syfo.log
import no.nav.syfo.narmesteleder.kafkamodel.Leder
import no.nav.syfo.narmesteleder.kafkamodel.NlResponse
import no.nav.syfo.narmesteleder.kafkamodel.Sykmeldt
import no.nav.syfo.pdl.service.PdlPersonService
import java.time.OffsetDateTime
import java.time.ZoneOffset

class NarmesteLederMappingService(private val pdlPersonService: PdlPersonService) {
    suspend fun mapSyfoServiceNarmesteLederTilNlResponse(syfoServiceNarmesteLeder: SyfoServiceNarmesteLeder): NlResponse {
        val fnrs = pdlPersonService.getFnrs(listOf(syfoServiceNarmesteLeder.aktorId, syfoServiceNarmesteLeder.nlAktorId), syfoServiceNarmesteLeder.id.toString())
        val sykmeldtFnr = fnrs[syfoServiceNarmesteLeder.aktorId]
        val lederFnr = fnrs[syfoServiceNarmesteLeder.nlAktorId]
        if (sykmeldtFnr == null && lederFnr == null) {
            log.error("Mangler fnr for sykmeldt og leder! NL-id: ${syfoServiceNarmesteLeder.id}")
            throw IllegalStateException("Mangler fnr for sykmeldt og leder! NL-id: ${syfoServiceNarmesteLeder.id}")
        } else if (sykmeldtFnr == null) {
            log.error("Mangler fnr for sykmeldt! NL-id: ${syfoServiceNarmesteLeder.id}")
            throw IllegalStateException("Mangler fnr for sykmeldt! NL-id: ${syfoServiceNarmesteLeder.id}")
        } else if (lederFnr == null) {
            log.error("Mangler fnr for leder! NL-id: ${syfoServiceNarmesteLeder.id}")
            throw IllegalStateException("Mangler fnr for leder! NL-id: ${syfoServiceNarmesteLeder.id}")
        }
        return NlResponse(
            orgnummer = syfoServiceNarmesteLeder.orgnummer,
            utbetalesLonn = syfoServiceNarmesteLeder.agForskutterer,
            leder = Leder(
                fnr = lederFnr,
                mobil = syfoServiceNarmesteLeder.nlTelefonnummer,
                epost = syfoServiceNarmesteLeder.nlEpost,
                fornavn = null,
                etternavn = null
            ),
            sykmeldt = Sykmeldt(
                fnr = sykmeldtFnr,
                navn = null
            ),
            aktivFom = OffsetDateTime.of(syfoServiceNarmesteLeder.aktivFom.atStartOfDay(), ZoneOffset.UTC),
            aktivTom = syfoServiceNarmesteLeder.aktivTom?.let { OffsetDateTime.of(syfoServiceNarmesteLeder.aktivTom.atStartOfDay(), ZoneOffset.UTC) }
        )
    }
}
