package co.edu.iub.veterinaria.service

import co.edu.iub.veterinaria.dto.pago.PagoRequest
import co.edu.iub.veterinaria.dto.pago.PagoResponse
import co.edu.iub.veterinaria.exception.InvalidRequestException
import co.edu.iub.veterinaria.exception.ResourceNotFoundException
import co.edu.iub.veterinaria.model.EstadoFactura
import co.edu.iub.veterinaria.model.Pago
import co.edu.iub.veterinaria.repository.FacturaRepository
import co.edu.iub.veterinaria.repository.MetodoPagoRepository
import co.edu.iub.veterinaria.repository.PagoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class PagoService(
    private val pagoRepository: PagoRepository,
    private val facturaRepository: FacturaRepository,
    private val metodoPagoRepository: MetodoPagoRepository
) {
    @Transactional(readOnly = true)
    fun listar(): List<PagoResponse> =
        pagoRepository.findAll().map { toResponse(it) }

    @Transactional(readOnly = true)
    fun listarPorFactura(idFactura: Int): List<PagoResponse> =
        pagoRepository.findByFacturaIdFactura(idFactura).map { toResponse(it) }

    @Transactional
    fun registrar(request: PagoRequest): PagoResponse {
        val factura = facturaRepository.findById(request.idFactura)
            .orElseThrow { ResourceNotFoundException("Factura no encontrada") }
        val metodoPago = metodoPagoRepository.findById(request.idMetodoPago)
            .orElseThrow { ResourceNotFoundException("Método de pago no encontrado") }
        if (metodoPago.nombre == "Wompi") {
            throw InvalidRequestException("Los pagos con Wompi deben realizarse desde el checkout en línea")
        }

        val pago = Pago().apply {
            this.factura = factura
            this.metodoPago = metodoPago
            monto = request.monto
            referenciaPago = request.referenciaPago
        }
        pagoRepository.save(pago)

        val totalPagado = pagoRepository.findByFacturaIdFactura(factura.idFactura!!)
            .sumOf { it.monto }
        if (totalPagado >= factura.total && factura.total > BigDecimal.ZERO) {
            factura.estadoFactura = EstadoFactura.PAGADA
            facturaRepository.save(factura)
        }

        return toResponse(pago)
    }

    private fun toResponse(p: Pago) = PagoResponse(
        idPago = p.idPago!!,
        idFactura = p.factura.idFactura!!,
        idMetodoPago = p.metodoPago.idMetodoPago!!,
        nombreMetodoPago = p.metodoPago.nombre,
        fechaPago = p.fechaPago,
        monto = p.monto,
        referenciaPago = p.referenciaPago,
        createdAt = p.createdAt,
        updatedAt = p.updatedAt
    )
}
