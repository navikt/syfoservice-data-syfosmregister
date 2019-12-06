package no.nav.syfo.persistering.db.postgres

import no.nav.syfo.model.Sykmeldingsdokument
import no.nav.syfo.model.Sykmeldingsopplysninger

data class SykmeldingDbModel(
    val sykmeldingsopplysninger: Sykmeldingsopplysninger,
    val sykmeldingsdokument: Sykmeldingsdokument
)
