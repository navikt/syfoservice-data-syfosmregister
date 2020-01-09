package no.nav.syfo.persistering.db.postgres

import no.nav.syfo.model.Behandlingsutfall
import no.nav.syfo.model.Sykmeldingsdokument
import no.nav.syfo.model.Sykmeldingsopplysninger

data class SykmeldingDbModel(
    val sykmeldingsopplysninger: Sykmeldingsopplysninger,
    val sykmeldingsdokument: Sykmeldingsdokument?
)

data class SykmeldingBehandlingsutfallDbModel(
    val sykmeldingsopplysninger: Sykmeldingsopplysninger,
    val behandlingsutfall: Behandlingsutfall?
)
