package no.nav.syfo.model

import no.nav.syfo.objectMapper
import org.postgresql.util.PGobject

data class Behandlingsutfall(
    val id: String,
    val behandlingsutfall: ValidationResult
)

fun ValidationResult.toPGObject() = PGobject().also {
    it.type = "json"
    it.value = objectMapper.writeValueAsString(this)
}
