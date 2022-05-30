package no.nav.syfo.pdl.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import no.nav.syfo.pdl.client.model.GetAktoridsRequest
import no.nav.syfo.pdl.client.model.GetAktoridsResponse
import no.nav.syfo.pdl.client.model.GetAktoridsVariables
import no.nav.syfo.pdl.client.model.GetPersonRequest
import no.nav.syfo.pdl.client.model.GetPersonVariables
import no.nav.syfo.pdl.client.model.PdlResponse
import no.nav.syfo.pdl.model.GraphQLResponse

class PdlClient(
    private val httpClient: HttpClient,
    private val basePath: String,
    private val graphQlQuery: String,
    private val graphQlQueryAktorids: String
) {

    private val temaHeader = "TEMA"
    private val tema = "SYM"

    suspend fun getPerson(fnr: String, token: String): GraphQLResponse<PdlResponse> {
        val getPersonRequest = GetPersonRequest(query = graphQlQuery, variables = GetPersonVariables(ident = fnr))
        return getGraphQLResponse(getPersonRequest, token)
    }

    suspend fun getFnrs(aktorids: List<String>, token: String): GetAktoridsResponse {
        val getAktoridsRequest = GetAktoridsRequest(query = graphQlQueryAktorids, variables = GetAktoridsVariables(identer = aktorids))
        return getGraphQLResponse(getAktoridsRequest, token)
    }

    private suspend inline fun <reified R> getGraphQLResponse(graphQlBody: Any, token: String): R {
        return httpClient.post(basePath) {
            setBody(graphQlBody)
            header(HttpHeaders.Authorization, "Bearer $token")
            header(temaHeader, tema)
            header(HttpHeaders.ContentType, "application/json")
        }.body()
    }
}
