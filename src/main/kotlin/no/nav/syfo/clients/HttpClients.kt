package no.nav.syfo.clients

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.network.sockets.SocketTimeoutException
import io.ktor.serialization.jackson.jackson
import no.nav.syfo.Environment
import no.nav.syfo.VaultServiceUser
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.clients.exception.ServiceUnavailableException
import no.nav.syfo.identendring.client.NarmestelederClient
import no.nav.syfo.oppgave.client.OppgaveClient
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.service.PdlPersonService
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import java.net.ProxySelector

class HttpClients(environment: Environment, vaultServiceUser: VaultServiceUser) {
    private val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        engine {
            socketTimeout = 40_000
            connectTimeout = 40_000
            connectionRequestTimeout = 40_000
        }
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                setSerializationInclusion(JsonInclude.Include.NON_NULL)
            }
        }
        expectSuccess = false
        HttpResponseValidator {
            handleResponseExceptionWithRequest { exception, _ ->
                when (exception) {
                    is SocketTimeoutException -> throw ServiceUnavailableException(exception.message)
                }
            }
        }
    }

    private val proxyConfig: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        config()
        engine {
            customizeClient {
                setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
            }
        }
    }

    private val httpClient = HttpClient(Apache, config)
    private val httpClientWithProxy = HttpClient(Apache, proxyConfig)

    private val stsOidcClient =
        StsOidcClient(vaultServiceUser.serviceuserUsername, vaultServiceUser.serviceuserPassword, environment.securityTokenUrl)

    private val accessTokenClientV2 = AccessTokenClientV2(
        environment.aadAccessTokenV2Url,
        environment.clientIdV2,
        environment.clientSecretV2,
        httpClientWithProxy
    )

    private val pdlClient = PdlClient(
        httpClient = httpClient,
        basePath = environment.pdlGraphqlPath,
        graphQlQuery = PdlClient::class.java.getResource("/graphql/getPerson.graphql").readText().replace(Regex("[\n\t]"), ""),
        graphQlQueryAktorids = PdlClient::class.java.getResource("/graphql/getAktorids.graphql").readText().replace(Regex("[\n\t]"), "")
    )

    val pdlService = PdlPersonService(pdlClient, accessTokenClientV2, environment.pdlScope)

    val oppgaveClient = OppgaveClient(environment.oppgavebehandlingUrl, stsOidcClient, httpClient)

    val narmestelederClient = NarmestelederClient(httpClient, accessTokenClientV2, environment.narmestelederUrl, environment.narmestelederScope)
}
