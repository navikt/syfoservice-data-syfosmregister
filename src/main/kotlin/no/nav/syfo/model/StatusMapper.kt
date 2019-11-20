package no.nav.syfo.model

import java.time.LocalDate
import java.time.LocalDateTime

class StatusMapper private constructor() {
    companion object {
        fun mapToSyfoserviceStatus(jsonMap: Map<String, String?>): StatusSyfoService {

            val created = LocalDateTime.parse(
                (jsonMap["CREATED"] ?: error("CREATED timestamp must not be null")).toString().substring(
                    0,
                    19
                )
            )
            val status = jsonMap["STATUS"] ?: error("STATUS must not be null")
            val sendtTilArbeidsgiverDato: LocalDate? = getLocalDate(jsonMap["SENDT_TIL_ARBEIDSGIVER_DATO"])
            val mottakId = jsonMap["MOTTAK_ID"] ?: error("MOTTAK_ID, must not be null")
            return StatusSyfoService(status, mottakId, created, sendtTilArbeidsgiverDato)
        }

        private fun getLocalDate(localDate: String?): LocalDate? {
            return when (localDate) {
                null -> null
                else -> LocalDate.parse(localDate)
            }
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
            val createdDate = statusSyfoService.createdTimestmap.toLocalDate()
            val sendtDate = statusSyfoService.sendTilArbeidsgiverDate ?: createdDate
            return when {
                sendtDate.isAfter(createdDate) -> sendtDate.atTime(12, 0)
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
