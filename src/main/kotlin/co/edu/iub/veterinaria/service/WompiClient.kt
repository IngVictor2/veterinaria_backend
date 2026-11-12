package co.edu.iub.veterinaria.service

import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.security.MessageDigest

@Component
class WompiClient(
    @Value("\${app.wompi.base-url:https://sandbox.wompi.co/v1}") baseUrl: String,
    @Value("\${app.wompi.public-key:}") val publicKey: String,
    @Value("\${app.wompi.private-key:}") private val privateKey: String,
    @Value("\${app.wompi.integrity-secret:}") private val integritySecret: String,
    @Value("\${app.wompi.events-secret:}") private val eventsSecret: String
) {
    private val log = LoggerFactory.getLogger(WompiClient::class.java)

    private val restClient: RestClient

    init {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(10_000)
            setReadTimeout(15_000)
        }
        restClient = RestClient.builder()
            .baseUrl(baseUrl.trimEnd('/'))
            .requestFactory(requestFactory)
            .build()

        log.info(
            "Wompi config: baseUrl={}, publicKeySandbox={}, privateKeySandbox={}, integritySecret={}, eventsSecret={}",
            baseUrl.trimEnd('/'),
            publicKey.startsWith("pub_test_"),
            privateKey.startsWith("prv_test_"),
            integritySecret.isNotBlank(),
            eventsSecret.isNotBlank()
        )
    }

    fun estaConfigurado(): Boolean =
        publicKey.startsWith("pub_test_") &&
            privateKey.startsWith("prv_test_") &&
            integritySecret.isNotBlank()

    fun diagnosticoConfiguracion(): String {
        val problemas = mutableListOf<String>()
        if (!publicKey.startsWith("pub_test_")) {
            problemas += "WOMPI_PUBLIC_KEY debe existir y empezar con pub_test_ (${describirPrefijo(publicKey)})"
        }
        if (!privateKey.startsWith("prv_test_")) {
            problemas += "WOMPI_PRIVATE_KEY debe existir y empezar con prv_test_ (${describirPrefijo(privateKey)})"
        }
        if (integritySecret.isBlank()) {
            problemas += "WOMPI_INTEGRITY_SECRET está vacío"
        }
        return problemas.joinToString("; ")
    }

    fun webhookConfigurado(): Boolean = eventsSecret.isNotBlank()

    fun firmarIntegridad(referencia: String, montoCentavos: Long, moneda: String): String =
        sha256("$referencia$montoCentavos$moneda$integritySecret")

    fun validarEvento(payload: JsonNode): Boolean {
        if (!webhookConfigurado()) return false

        val signature = payload.path("signature")
        val checksum = signature.path("checksum").asText("")
        val properties = signature.path("properties")
        if (checksum.isBlank() || !properties.isArray || properties.size() == 0) return false

        val data = payload.path("data")
        val valores = properties.map { resolverPropiedad(data, it.asText()) ?: return false }
        val timestamp = payload.path("timestamp").asLong(0)
        val cadena = valores.joinToString("") + timestamp + eventsSecret

        return checksum.equals(sha256(cadena), ignoreCase = true)
    }

    fun consultarTransaccionPorId(idTransaccion: String): JsonNode? {
        return try {
            val response = restClient.get()
                .uri("/transactions/{id}", idTransaccion)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $privateKey")
                .retrieve()
                .body(JsonNode::class.java)

            response?.path("data")?.takeIf { it.isObject }
        } catch (ex: RestClientResponseException) {
            log.warn(
                "Wompi respondió {} al consultar la transacción {}: {}",
                ex.statusCode,
                idTransaccion,
                ex.responseBodyAsString
            )
            null
        } catch (ex: RestClientException) {
            log.warn("No se pudo consultar la transacción {} en Wompi: {}", idTransaccion, ex.message)
            null
        }
    }

    fun consultarTransaccionesPorReferencia(referencia: String): List<JsonNode> {
        return try {
            val response = restClient.get()
                .uri { builder ->
                    builder.path("/transactions")
                        .queryParam("reference", referencia)
                        .build()
                }
                .header(HttpHeaders.AUTHORIZATION, "Bearer $privateKey")
                .retrieve()
                .body(JsonNode::class.java)

            val data = response?.path("data")
            if (data != null && data.isArray) data.map { it } else emptyList()
        } catch (ex: RestClientResponseException) {
            log.warn(
                "Wompi respondió {} al consultar la referencia {}: {}",
                ex.statusCode,
                referencia,
                ex.responseBodyAsString
            )
            emptyList()
        } catch (ex: RestClientException) {
            log.warn("No se pudo consultar la referencia {} en Wompi: {}", referencia, ex.message)
            emptyList()
        }
    }

    private fun describirPrefijo(valor: String): String =
        if (valor.isBlank()) "vacío" else "valor actual empieza con ${valor.take(9)}"

    private fun resolverPropiedad(root: JsonNode, path: String): String? {
        var node: JsonNode = root
        for (part in path.split('.')) {
            node = node.path(part)
        }
        return if (node.isMissingNode || node.isNull) null else node.asText()
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
