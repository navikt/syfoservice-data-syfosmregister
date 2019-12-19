package no.nav.syfo.model

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class Mapper private constructor() {
    companion object {

        val log: Logger = LoggerFactory.getLogger("no.nav.syfo.syfoservicedatasyfosmregister")

        fun mapToSyfoserviceStatus(jsonMap: Map<String, String?>): StatusSyfoService {

            val created = LocalDateTime.parse((jsonMap["CREATED"].toString().substring(0, 19)))
            val status = jsonMap["STATUS"] ?: error("STATUS must not be null")
            val sendtTilArbeidsgiverDato: LocalDateTime? = getLocalDate(jsonMap["SENDT_TIL_ARBEIDSGIVER_DATO"])
            val sykmeldingId = jsonMap["MELDING_ID"] ?: error("MELDING_ID, must not be null")
            return StatusSyfoService(status, sykmeldingId, created, sendtTilArbeidsgiverDato)
        }

        private fun getLocalDate(localDate: String?): LocalDateTime? {
            return when (localDate) {
                null -> null
                else -> LocalDateTime.parse(localDate.substring(0, 19))
            }
        }

        fun mapToUpdateEvent(jsonMap: Map<String, String?>): UpdateEvent {
            val created = LocalDateTime.parse((jsonMap["CREATED"].toString().substring(0, 19)))
            val mottakId = jsonMap["MOTTAK_ID"] ?: error("MOTTAK_ID, must not be null")
            val meldingId: String = jsonMap["MELDING_ID"] ?: error("MELDIGN_ID, must not be null")
            return UpdateEvent(sykmeldingId = meldingId, created = created, mottakId = mottakId)
        }

        fun getStatusTimeStamp(statusEvent: StatusEvent, statusSyfoService: SykmeldingStatusTopicEvent): LocalDateTime {
            return when (statusEvent) {
                StatusEvent.SENDT -> getSendtTimestamp(statusSyfoService)
                StatusEvent.UTGATT -> getUtgattTimestamp(statusSyfoService)
                StatusEvent.APEN -> getApenTimestamp(statusSyfoService)
                StatusEvent.BEKREFTET -> getSendtTimestamp(statusSyfoService)
                StatusEvent.AVBRUTT -> getSendtTimestamp(statusSyfoService)
                StatusEvent.SLETTET -> getSendtTimestamp(statusSyfoService)
            }
        }

        private fun getApenTimestamp(statusSyfoService: SykmeldingStatusTopicEvent): LocalDateTime {
            return statusSyfoService.created
        }

        private fun getUtgattTimestamp(statusSyfoService: SykmeldingStatusTopicEvent): LocalDateTime {
            return statusSyfoService.created.plusMonths(3)
        }

        private fun getSendtTimestamp(statusSyfoService: SykmeldingStatusTopicEvent): LocalDateTime {
            val createdDateTime = statusSyfoService.created
            val sendtDate = statusSyfoService.sendtTilArbeidsgiverDato ?: createdDateTime
            return when {
                sendtDate.isAfter(createdDateTime) -> sendtDate
                else -> statusSyfoService.created.plusHours(1)
            }
        }

        fun mapEvent(status: String): StatusEvent {
            return when (status.toUpperCase()) {
                "NY" -> StatusEvent.APEN
                "UTGAATT" -> StatusEvent.UTGATT
                "AVBRUTT" -> StatusEvent.AVBRUTT
                "SENDT" -> StatusEvent.SENDT
                "BEKREFTET" -> StatusEvent.BEKREFTET
                "TIL_SENDING" -> StatusEvent.SENDT
                "SLETTET" -> StatusEvent.SLETTET
                else -> error("STATUS is not valid")
            }
        }


        fun mapToSykmeldingStatusTopicEvent(
            sykmeldingStatusMap: Map<String, Any?>,
            kafkaTimestamp: Long
        ): SykmeldingStatusTopicEvent {
            val kafakTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(kafkaTimestamp), ZoneId.systemDefault()).minusMinutes(2)
            val status = mapEvent(sykmeldingStatusMap["STATUS"].toString())
            val sykmeldingId = sykmeldingStatusMap["MELDING_ID"].toString()
            val harFravaer = getNullSafeBoolen(sykmeldingStatusMap["HAR_FRAVAER"])
            return SykmeldingStatusTopicEvent(
                sykmeldingId = sykmeldingId,
                created = LocalDateTime.parse(sykmeldingStatusMap["CREATED"].toString().substring(0, 19)),
                status = status,
                kafkaTimestamp = kafakTime,
                harForsikring = getNullSafeBoolen(sykmeldingStatusMap["HAR_FORSIKRING"]),
                harFravaer = harFravaer,
                arbeidssituasjon = sykmeldingStatusMap["ARBEIDSSITUASJON"]?.toString(),
                sendtTilArbeidsgiverDato = getNullSafeTimestamp(sykmeldingStatusMap["SENDT_TIL_ARBEIDSGIVER_DATO"]?.toString()),
                arbeidsgiver = getArbeidsgiverStatus(sykmeldingStatusMap, status, sykmeldingId),
                fravarsPeriode = getFravaersPeriode(sykmeldingStatusMap, harFravaer)
            )
        }

        fun getFravaersPeriode(
            sykmeldingStatusMap: Map<String, Any?>,
            harFravaer: Boolean?
        ): List<FravarsPeriode>? {
            if(harFravaer == null || !harFravaer) {
                return null
            }
            val fravaer: HashMap<String, Any> = sykmeldingStatusMap["FRAVAER"] as HashMap<String, Any>
            if (fravaer != null) {
                return (fravaer["rows"] as List<Map<String, String>>).map { FravarsPeriode(fom = LocalDate.parse(it["FOM"]), tom = LocalDate.parse(it["TOM"])) }
            }
            return null
        }

        private fun getArbeidsgiverStatus(
            sykmeldingStatusMap: Map<String, Any?>,
            statusEvent: StatusEvent,
            sykmeldingId: String
        ): ArbeidsgiverStatus? {
            return when (statusEvent) {
                StatusEvent.SENDT -> ArbeidsgiverStatus(
                    sykmeldingId = sykmeldingId,
                    juridiskOrgnummer = sykmeldingStatusMap["JURIDISK_ORGNUMMER"]?.toString(),
                    orgnummer = getOrgnummer(sykmeldingStatusMap, sykmeldingId),
                    orgnavn = sykmeldingStatusMap["NAVN"].toString()
                )
                else -> null
            }
        }

        private fun getOrgnummer(sykmeldingStatusMap: Map<String, Any?>, sykmeldingId: String): String {
            val orgnummer_1 = sykmeldingStatusMap["ORGNUMMER_1"]?.toString()
            val orgnummer = sykmeldingStatusMap["ORGNUMMER"].toString()
            if (orgnummer_1 != null && orgnummer != orgnummer_1) {
                log.warn("orgnummer er ikke lik: {}, {}, sykmeldingId {}", orgnummer, orgnummer_1)
                return orgnummer_1
            } else return orgnummer
        }

        private fun getNullSafeTimestamp(timestamp: String?): LocalDateTime? {
            return when (timestamp) {
                null -> null
                else -> LocalDateTime.parse(timestamp.substring(0, 19))
            }
        }

        private fun getNullSafeBoolen(any: Any?): Boolean? {
            return when (any) {
                null -> null
                else -> any as Boolean
            }
        }
    }
}
