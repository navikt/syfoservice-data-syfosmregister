package no.nav.syfo.sparenaproxy

import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

const val AKTIVITETSKRAV_8_UKER_TYPE = "8UKER"
const val BREV_39_UKER_TYPE = "39UKER"
const val BREV_4_UKER_TYPE = "4UKER"

data class PlanlagtMeldingDbModel(
    val id: UUID,
    val fnr: String,
    val startdato: LocalDate,
    val type: String,
    val opprettet: OffsetDateTime,
    val sendes: OffsetDateTime,
    val avbrutt: OffsetDateTime? = null,
    val sendt: OffsetDateTime? = null
)

fun ResultSet.toPlanlagtMeldingDbModel(): PlanlagtMeldingDbModel =
    PlanlagtMeldingDbModel(
        id = getObject("id", UUID::class.java),
        fnr = getString("fnr"),
        startdato = getObject("startdato", LocalDate::class.java),
        type = getString("type"),
        opprettet = getTimestamp("opprettet").toInstant().atOffset(ZoneOffset.UTC),
        sendes = getTimestamp("sendes").toInstant().atOffset(ZoneOffset.UTC),
        avbrutt = getTimestamp("avbrutt")?.toInstant()?.atOffset(ZoneOffset.UTC),
        sendt = getTimestamp("sendt")?.toInstant()?.atOffset(ZoneOffset.UTC)
    )
