package no.nav.syfo.service

import no.nav.syfo.db.DatabaseInterfacePostgres
import no.nav.syfo.model.Mapper
import no.nav.syfo.model.ShortName
import no.nav.syfo.model.Sporsmal
import no.nav.syfo.model.StatusEvent
import no.nav.syfo.model.Svar
import no.nav.syfo.model.Svartype
import no.nav.syfo.model.SykmeldingStatusEvent
import no.nav.syfo.model.SykmeldingStatusTopicEvent
import no.nav.syfo.objectMapper
import no.nav.syfo.persistering.db.postgres.deleteSykmeldingStatus
import no.nav.syfo.persistering.db.postgres.getStatusesForSykmelding
import no.nav.syfo.persistering.db.postgres.lagreSporsmalOgSvarOgArbeidsgiver
import no.nav.syfo.persistering.db.postgres.oppdaterSykmeldingStatus
import no.nav.syfo.persistering.db.postgres.svarFinnesFraFor

class UpdateStatusService(val database: DatabaseInterfacePostgres) {

    fun updateSykemdlingStatus(sykmeldingStatusTopicEvent: SykmeldingStatusTopicEvent) {

        database.deleteSykmeldingStatus(
            sykmeldingStatusTopicEvent.sykmeldingId,
            sykmeldingStatusTopicEvent.kafkaTimestamp
        )

        val statusInSmRegister = database.getStatusesForSykmelding(sykmeldingStatusTopicEvent.sykmeldingId)

        val statusListe = ArrayList<SykmeldingStatusEvent>()
        statusListe.add(
            SykmeldingStatusEvent(
                sykmeldingStatusTopicEvent.sykmeldingId,
                sykmeldingStatusTopicEvent.created,
                StatusEvent.APEN
            )
        )
        if (statusInSmRegister.isEmpty()) {
            insertLatestStatusFromSyfoService(sykmeldingStatusTopicEvent, statusListe)
        } else {
            if (statusInSmRegister.first().event != StatusEvent.APEN) {
                database.oppdaterSykmeldingStatus(statusListe)
            }
        }
    }

    private fun insertLatestStatusFromSyfoService(
        sykmeldingStatusTopicEvent: SykmeldingStatusTopicEvent,
        statusListe: ArrayList<SykmeldingStatusEvent>
    ) {
        when (sykmeldingStatusTopicEvent.status) {
            StatusEvent.SENDT -> insertSendStatus(sykmeldingStatusTopicEvent, statusListe)
            StatusEvent.BEKREFTET -> insertBekreftetStatus(sykmeldingStatusTopicEvent, statusListe)
            StatusEvent.APEN -> insertApenStatus(statusListe)
            else -> insertStatus(sykmeldingStatusTopicEvent, statusListe)
        }
    }

    private fun insertApenStatus(
        statusListe: java.util.ArrayList<SykmeldingStatusEvent>
    ) {
        database.oppdaterSykmeldingStatus(statusListe)
    }

    private fun insertStatus(
        sykmeldingStatusTopicEvent: SykmeldingStatusTopicEvent,
        statusListe: java.util.ArrayList<SykmeldingStatusEvent>
    ) {
        statusListe.add(
            SykmeldingStatusEvent(
                sykmeldingStatusTopicEvent.sykmeldingId,
                Mapper.getStatusTimeStamp(sykmeldingStatusTopicEvent.status, sykmeldingStatusTopicEvent),
                sykmeldingStatusTopicEvent.status
            )
        )
        database.oppdaterSykmeldingStatus(statusListe)
    }

    private fun insertBekreftetStatus(
        sykmeldingStatusTopicEvent: SykmeldingStatusTopicEvent,
        statusListe: java.util.ArrayList<SykmeldingStatusEvent>
    ) {
        statusListe.add(
            SykmeldingStatusEvent(
                sykmeldingStatusTopicEvent.sykmeldingId,
                sykmeldingStatusTopicEvent.sendtTilArbeidsgiverDato!!,
                StatusEvent.BEKREFTET
            )
        )

        if (!database.svarFinnesFraFor(sykmeldingStatusTopicEvent.sykmeldingId)) {
            val sporsmals: List<Sporsmal> = getSporsmalOgSvarForBekreft(sykmeldingStatusTopicEvent)
            database.lagreSporsmalOgSvarOgArbeidsgiver(sporsmals, null)
        }
        database.oppdaterSykmeldingStatus(statusListe)
    }

    private fun insertSendStatus(
        sykmeldingStatusTopicEvent: SykmeldingStatusTopicEvent,
        statusListe: MutableList<SykmeldingStatusEvent>
    ) {
        statusListe.add(
            SykmeldingStatusEvent(
                sykmeldingStatusTopicEvent.sykmeldingId,
                sykmeldingStatusTopicEvent.sendtTilArbeidsgiverDato!!,
                StatusEvent.SENDT
            )
        )
        if (!database.svarFinnesFraFor(sykmeldingStatusTopicEvent.sykmeldingId)) {
            val sporsmalOgSvar: List<Sporsmal> = getSporsmalOgSvarForSend(sykmeldingStatusTopicEvent)
            database.lagreSporsmalOgSvarOgArbeidsgiver(sporsmalOgSvar, sykmeldingStatusTopicEvent.arbeidsgiver)
        }
        database.oppdaterSykmeldingStatus(statusListe)
    }

    private fun getSporsmalOgSvarForSend(sykmeldingStatusTopicEvent: SykmeldingStatusTopicEvent): List<Sporsmal> {
        val sporsmals = ArrayList<Sporsmal>()

        val arbeidssituasjon = Sporsmal(
            "Jeg er sykmeldt fra",
            ShortName.ARBEIDSSITUASJON,
            Svar(
                sykmeldingId = sykmeldingStatusTopicEvent.sykmeldingId,
                svartype = Svartype.ARBEIDSSITUASJON,
                svar = "ARBEIDSTAKER",
                sporsmalId = null
            )
        )

        sporsmals.add(arbeidssituasjon)

        return sporsmals
    }

    private fun getSporsmalOgSvarForBekreft(sykmeldingStatusTopicEvent: SykmeldingStatusTopicEvent): List<Sporsmal> {
        val sporsmals = ArrayList<Sporsmal>()

        if (sykmeldingStatusTopicEvent.arbeidssituasjon != null) {
            val arbeidssituasjon = Sporsmal(
                "Jeg er sykmeldt fra",
                ShortName.ARBEIDSSITUASJON,
                Svar(
                    sykmeldingId = sykmeldingStatusTopicEvent.sykmeldingId,
                    svartype = Svartype.ARBEIDSSITUASJON,
                    svar = sykmeldingStatusTopicEvent.arbeidssituasjon,
                    sporsmalId = null
                )
            )
            sporsmals.add(arbeidssituasjon)
        }

        if (sykmeldingStatusTopicEvent.harForsikring != null) {
            sporsmals.add(
                Sporsmal(
                    "Har du forsikring som gjelder de første 16 dagene av sykefraværet?",
                    ShortName.FORSIKRING,
                    Svar(
                        sykmeldingStatusTopicEvent.sykmeldingId,
                        null,
                        Svartype.JA_NEI,
                        getJaNeiSvar(sykmeldingStatusTopicEvent.harForsikring)
                    )
                )
            )
        }
        if (sykmeldingStatusTopicEvent.harFravaer != null) {
            sporsmals.add(
                Sporsmal(
                    "Brukte du egenmelding eller noen annen sykmelding før datoen denne sykmeldingen gjelder fra?",
                    ShortName.FRAVAER,
                    Svar(
                        sykmeldingStatusTopicEvent.sykmeldingId,
                        null,
                        Svartype.JA_NEI,
                        getJaNeiSvar(sykmeldingStatusTopicEvent.harFravaer)
                    )
                )
            )
        }
        if (!sykmeldingStatusTopicEvent.fravarsPeriode.isNullOrEmpty()) {
            sporsmals.add(
                Sporsmal(
                    "Hvilke dager var du borte fra jobb før datoen sykmeldingen gjelder fra?",
                    ShortName.PERIODE,
                    Svar(
                        sykmeldingStatusTopicEvent.sykmeldingId,
                        null,
                        Svartype.PERIODER,
                        objectMapper.writeValueAsString(sykmeldingStatusTopicEvent.fravarsPeriode)
                    )
                )
            )
        }
        return sporsmals
    }

    private fun getJaNeiSvar(svar: Boolean): String {
        return when (svar) {
            true -> "JA"
            false -> "NEI"
        }
    }
}
