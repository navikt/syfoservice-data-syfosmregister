package no.nav.syfo.model

import java.time.LocalDateTime
import kotlin.collections.ArrayList
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class Mapper private constructor() {
    companion object {

        val log: Logger = LoggerFactory.getLogger("no.nav.syfo.syfoservicedatasyfosmregister")

        fun mapToSyfoserviceStatus(jsonMap: Map<String, String?>): StatusSyfoService {

            val created = LocalDateTime.parse((jsonMap["CREATED"].toString().substring(0, 19)))
            val status = jsonMap["STATUS"] ?: error("STATUS must not be null")
            val sendtTilArbeidsgiverDato: LocalDateTime? = getLocalDate(jsonMap["SENDT_TIL_ARBEIDSGIVER_DATO"])
            val mottakId = jsonMap["MOTTAK_ID"] ?: error("MOTTAK_ID, must not be null")
            return StatusSyfoService(status, mottakId, created, sendtTilArbeidsgiverDato)
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

        fun toStatusEventList(statusSyfoService: StatusSyfoService): List<SykmeldingStatusEvent> {
            val statusEvents = ArrayList<SykmeldingStatusEvent>()

            statusEvents.add(SykmeldingStatusEvent(statusSyfoService.mottakid, statusSyfoService.createdTimestmap, StatusEvent.APEN))

            val statusEvent = mapEvent(statusSyfoService.status)
            val statusTimestamp: LocalDateTime = getStatusTimeStamp(statusEvent, statusSyfoService)

            if (statusEvent != StatusEvent.APEN) {
                statusEvents.add(SykmeldingStatusEvent(statusSyfoService.mottakid, statusTimestamp, statusEvent))
            }

            return statusEvents
        }

        private fun getStatusTimeStamp(statusEvent: StatusEvent, statusSyfoService: StatusSyfoService): LocalDateTime {
            return when (statusEvent) {
                StatusEvent.SENDT -> getSendtTimestamp(statusSyfoService)
                StatusEvent.UTGATT -> getUtgattTimestamp(statusSyfoService)
                StatusEvent.APEN -> getApenTimestamp(statusSyfoService)
                StatusEvent.BEKREFTET -> getSendtTimestamp(statusSyfoService)
                StatusEvent.AVBRUTT -> getSendtTimestamp(statusSyfoService)
                StatusEvent.SLETTET -> getSendtTimestamp(statusSyfoService)
            }
        }

        private fun getApenTimestamp(statusSyfoService: StatusSyfoService): LocalDateTime {
            return statusSyfoService.createdTimestmap
        }

        private fun getUtgattTimestamp(statusSyfoService: StatusSyfoService): LocalDateTime {
            return statusSyfoService.createdTimestmap.plusMonths(3)
        }

        private fun getSendtTimestamp(statusSyfoService: StatusSyfoService): LocalDateTime {
            val createdDateTime = statusSyfoService.createdTimestmap
            val sendtDate = statusSyfoService.sendTilArbeidsgiverDate ?: createdDateTime
            return when {
                sendtDate.isAfter(createdDateTime) -> sendtDate
                else -> statusSyfoService.createdTimestmap.plusHours(1)
            }
        }

        private fun mapEvent(status: String): StatusEvent {
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
    }
}
