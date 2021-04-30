package no.nav.syfo.pdl.client.model

data class GetAktoridsResponse(
    val data: ResponseData,
    val errors: List<ResponseError>?
)

data class ResponseData(
    val hentIdenterBolk: List<HentIdenterBolk>?
)

data class HentIdenterBolk(
    val ident: String,
    val identer: List<PdlIdent>?,
    val code: String
)

data class PdlIdent(val ident: String, val gruppe: String)
