package co.edu.iub.veterinaria.controller

import co.edu.iub.veterinaria.dto.pago.PagoRequest
import co.edu.iub.veterinaria.dto.pago.PagoResponse
import co.edu.iub.veterinaria.dto.pago.WompiCheckoutRequest
import co.edu.iub.veterinaria.dto.pago.WompiCheckoutResponse
import co.edu.iub.veterinaria.dto.pago.WompiVerificacionResponse
import co.edu.iub.veterinaria.dto.pago.WompiVerificarRequest
import co.edu.iub.veterinaria.service.PagoService
import co.edu.iub.veterinaria.service.WompiPagoService
import com.fasterxml.jackson.databind.JsonNode
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/pagos")
class PagoController(
    private val pagoService: PagoService,
    private val wompiPagoService: WompiPagoService
) {
    @GetMapping
    fun listar(): List<PagoResponse> = pagoService.listar()

    @GetMapping("/factura/{idFactura}")
    fun listarPorFactura(@PathVariable idFactura: Int): List<PagoResponse> =
        pagoService.listarPorFactura(idFactura)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun registrar(@Valid @RequestBody request: PagoRequest): PagoResponse =
        pagoService.registrar(request)

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    fun crearCheckoutWompi(
        @Valid @RequestBody request: WompiCheckoutRequest,
        authentication: Authentication
    ): WompiCheckoutResponse =
        wompiPagoService.crearCheckout(request.idFactura, authentication)

    @PostMapping("/wompi/webhook")
    fun webhookWompi(@RequestBody payload: JsonNode): ResponseEntity<Map<String, String>> {
        val valido = wompiPagoService.procesarWebhook(payload)
        return if (valido) {
            ResponseEntity.ok(mapOf("status" to "ok"))
        } else {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("status" to "firma inválida"))
        }
    }

    @PostMapping("/wompi/verificar")
    fun verificarPagoWompi(
        @Valid @RequestBody request: WompiVerificarRequest,
        authentication: Authentication
    ): WompiVerificacionResponse =
        wompiPagoService.verificar(request, authentication)
}
