package co.edu.iub.veterinaria.service

import co.edu.iub.veterinaria.dto.pago.WompiCheckoutResponse
import co.edu.iub.veterinaria.dto.pago.WompiVerificacionResponse
import co.edu.iub.veterinaria.dto.pago.WompiVerificarRequest
import co.edu.iub.veterinaria.exception.InvalidRequestException
import co.edu.iub.veterinaria.exception.ResourceNotFoundException
import co.edu.iub.veterinaria.exception.WompiNotConfiguredException
import co.edu.iub.veterinaria.model.EstadoFactura
import co.edu.iub.veterinaria.model.EstadoTransaccionPago
import co.edu.iub.veterinaria.model.Factura
import co.edu.iub.veterinaria.model.MetodoPago
import co.edu.iub.veterinaria.model.Pago
import co.edu.iub.veterinaria.model.TransaccionPago
import co.edu.iub.veterinaria.repository.FacturaRepository
import co.edu.iub.veterinaria.repository.MetodoPagoRepository
import co.edu.iub.veterinaria.repository.PagoRepository
import co.edu.iub.veterinaria.repository.TransaccionPagoRepository
import co.edu.iub.veterinaria.security.CurrentUserHelper
import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import kotlin.random.Random

@Service
class WompiPagoService(
    private val transaccionPagoRepository: TransaccionPagoRepository,
    private val pagoRepository: PagoRepository,
    private val facturaRepository: FacturaRepository,
    private val metodoPagoRepository: MetodoPagoRepository,
    private val wompiClient: WompiClient,
    private val currentUserHelper: CurrentUserHelper,
    @Value("\${app.wompi.currency:COP}") private val moneda: String
) {
    private val log = LoggerFactory.getLogger(WompiPagoService::class.java)

    @Transactional
    fun crearCheckout(idFactura: Int, authentication: Authentication): WompiCheckoutResponse {
        if (!wompiClient.estaConfigurado()) {
            throw WompiNotConfiguredException(wompiClient.diagnosticoConfiguracion())
        }

        val factura = facturaRepository.findById(idFactura)
            .orElseThrow { ResourceNotFoundException("Factura no encontrada") }
        validarPropietario(factura, authentication)

        if (factura.estadoFactura != EstadoFactura.PENDIENTE) {
            throw InvalidRequestException("La factura no está pendiente de pago")
        }
        if (factura.total <= BigDecimal.ZERO) {
            throw InvalidRequestException("La factura no tiene un valor válido para pagar")
        }

        val montoCentavos = factura.total
            .multiply(BigDecimal(100))
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
        val referencia = "VET-${factura.idFactura}-${System.currentTimeMillis()}-${Random.nextInt(1000, 9999)}"

        val transaccion = TransaccionPago().apply {
            this.factura = factura
            this.referencia = referencia
            this.montoCentavos = montoCentavos
            this.estadoTransaccion = EstadoTransaccionPago.PENDIENTE
        }
        transaccionPagoRepository.save(transaccion)

        val cliente = factura.cliente
        return WompiCheckoutResponse(
            idTransaccion = transaccion.idTransaccionPago!!,
            referencia = referencia,
            montoCentavos = montoCentavos,
            moneda = moneda,
            llavePublica = wompiClient.publicKey,
            firmaIntegridad = wompiClient.firmarIntegridad(referencia, montoCentavos, moneda),
            correoCliente = cliente.correo?.takeIf { it.isNotBlank() }
                ?: "cliente${cliente.idCliente}@vetclinic.co",
            nombreCliente = "${cliente.primerNombre} ${cliente.primerApellido}"
        )
    }

    @Transactional
    fun procesarWebhook(payload: JsonNode): Boolean {
        if (!wompiClient.validarEvento(payload)) {
            log.warn("Webhook Wompi rechazado: firma inválida")
            return false
        }
        if (payload.path("event").asText() != "transaction.updated") return true

        val tx = payload.path("data").path("transaction")
        val referencia = tx.path("reference").asText("")
        if (referencia.isBlank()) return true

        val transaccion = transaccionPagoRepository.findByReferencia(referencia)
        if (transaccion == null) {
            log.warn("Webhook Wompi para referencia desconocida: {}", referencia)
            return true
        }

        aplicarEstado(transaccion, tx)
        return true
    }

    @Transactional
    fun verificar(request: WompiVerificarRequest, authentication: Authentication): WompiVerificacionResponse {
        val transaccion = transaccionPagoRepository.findByReferencia(request.referencia)
            ?: throw ResourceNotFoundException("Transacción Wompi no encontrada")
        validarPropietario(transaccion.factura, authentication)

        if (transaccion.estadoTransaccion == EstadoTransaccionPago.PENDIENTE) {
            val idWompi = request.idTransaccionWompi?.takeIf { it.isNotBlank() }
                ?: transaccion.idTransaccionWompi
            val tx = if (idWompi != null) {
                wompiClient.consultarTransaccionPorId(idWompi)
            } else {
                // Respaldo para integraciones antiguas que permiten listar por referencia.
                wompiClient.consultarTransaccionesPorReferencia(transaccion.referencia).firstOrNull()
            }
            if (tx != null) aplicarEstado(transaccion, tx)
        }

        return WompiVerificacionResponse(
            referencia = transaccion.referencia,
            estado = transaccion.estadoTransaccion.name,
            estadoFactura = transaccion.factura.estadoFactura.name,
            mensaje = transaccion.mensaje ?: mensajePorEstado(transaccion.estadoTransaccion),
            idTransaccionWompi = transaccion.idTransaccionWompi,
            montoCentavos = transaccion.montoCentavos
        )
    }

    private fun aplicarEstado(transaccion: TransaccionPago, tx: JsonNode) {
        val estadoWompi = tx.path("status").asText("").uppercase()
        val referenciaWompi = tx.path("reference").asText("")
        if (referenciaWompi.isNotBlank() && referenciaWompi != transaccion.referencia) {
            log.warn(
                "Se ignoró una transacción Wompi con referencia distinta: esperada={}, recibida={}",
                transaccion.referencia,
                referenciaWompi
            )
            return
        }
        if (transaccion.estadoTransaccion == EstadoTransaccionPago.APROBADA && estadoWompi != "APPROVED") {
            log.warn("Se ignoró el estado {} para una transacción ya aprobada: {}", estadoWompi, transaccion.referencia)
            return
        }

        transaccion.idTransaccionWompi = tx.path("id").asText(null)
        transaccion.metodoPagoWompi = tx.path("payment_method_type").asText(null)
        transaccion.mensaje = tx.path("status_message").asText(null)?.take(255)

        when (estadoWompi) {
            "APPROVED" -> {
                if (transaccion.estadoTransaccion == EstadoTransaccionPago.APROBADA) return
                val montoConfirmado = tx.path("amount_in_cents").asLong(-1)
                val monedaConfirmada = tx.path("currency").asText("")
                if (montoConfirmado != transaccion.montoCentavos ||
                    (monedaConfirmada.isNotBlank() && !monedaConfirmada.equals(moneda, ignoreCase = true))
                ) {
                    transaccion.estadoTransaccion = EstadoTransaccionPago.ERROR
                    transaccion.mensaje = "Los datos confirmados por Wompi no coinciden con la factura"
                    transaccionPagoRepository.save(transaccion)
                    return
                }
                transaccion.estadoTransaccion = EstadoTransaccionPago.APROBADA
                registrarPagoAprobado(transaccion)
            }
            "DECLINED" -> transaccion.estadoTransaccion = EstadoTransaccionPago.RECHAZADA
            "VOIDED" -> transaccion.estadoTransaccion = EstadoTransaccionPago.ANULADA
            "ERROR" -> transaccion.estadoTransaccion = EstadoTransaccionPago.ERROR
            "PENDING" -> transaccion.estadoTransaccion = EstadoTransaccionPago.PENDIENTE
            else -> log.warn("Estado Wompi no reconocido: {}", estadoWompi)
        }

        transaccionPagoRepository.save(transaccion)
    }

    private fun registrarPagoAprobado(transaccion: TransaccionPago) {
        val factura = transaccion.factura

        if (!pagoRepository.existsByReferenciaPago(transaccion.referencia)) {
            val metodoWompi = metodoPagoRepository.findByNombre("Wompi")
                ?: metodoPagoRepository.save(
                    MetodoPago().apply {
                        nombre = "Wompi"
                        descripcion = "Pago en línea con Wompi"
                    }
                )

            val pago = Pago().apply {
                this.factura = factura
                this.metodoPago = metodoWompi
                fechaPago = LocalDateTime.now()
                monto = BigDecimal.valueOf(transaccion.montoCentavos).movePointLeft(2)
                referenciaPago = transaccion.referencia
            }
            pagoRepository.save(pago)
        }

        if (factura.estadoFactura == EstadoFactura.PENDIENTE) {
            val totalPagado = pagoRepository.findByFacturaIdFactura(factura.idFactura!!)
                .sumOf { it.monto }
            if (totalPagado >= factura.total && factura.total > BigDecimal.ZERO) {
                factura.estadoFactura = EstadoFactura.PAGADA
                facturaRepository.save(factura)
            }
        }
    }

    private fun validarPropietario(factura: Factura, authentication: Authentication) {
        if (currentUserHelper.esPersonal(authentication)) return

        val idCliente = currentUserHelper.getClienteIdOrNull(authentication)
            ?: throw AccessDeniedException("El usuario no tiene un cliente asociado")
        if (factura.cliente.idCliente != idCliente) {
            throw AccessDeniedException("No tiene permisos para pagar esta factura")
        }
    }

    private fun mensajePorEstado(estado: EstadoTransaccionPago): String = when (estado) {
        EstadoTransaccionPago.PENDIENTE -> "El pago está pendiente de confirmación"
        EstadoTransaccionPago.APROBADA -> "Pago aprobado"
        EstadoTransaccionPago.RECHAZADA -> "El pago fue rechazado"
        EstadoTransaccionPago.ANULADA -> "La transacción fue anulada"
        EstadoTransaccionPago.ERROR -> "Ocurrió un error procesando el pago"
    }
}
