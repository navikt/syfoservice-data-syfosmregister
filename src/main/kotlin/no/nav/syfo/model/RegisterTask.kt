package no.nav.syfo.model

data class RegisterTask(
    val produceTask: ProduceTask,
    val registerJournal: RegisterJournal?
)

data class ProduceTask(
    val messageId: String,
    val aktoerId: String?,
    val tildeltEnhetsnr: String?,
    val opprettetAvEnhetsnr: String?,
    val behandlesAvApplikasjon: String?,
    val orgnr: String?,
    val beskrivelse: String,
    val temagruppe: String?,
    val tema: String?,
    val behandlingstema: String?,
    val oppgavetype: String?,
    val behandlingstype: String?,
    val mappeId: Int?,
    val aktivDato: String?,
    val fristFerdigstillelse: String?,
    val prioritet: PrioritetType?,
    val metadata: Map<String, String>?
)

enum class PrioritetType {
    HOY, NORM, LAV
}

data class RegisterJournal(
    val messageId: String?,
    val sakId: String?,
    val journalpostId: String?,
    val journalpostKilde: String?
)
