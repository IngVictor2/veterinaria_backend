package co.edu.iub.veterinaria.repository

import co.edu.iub.veterinaria.model.Pago
import org.springframework.data.jpa.repository.JpaRepository

interface PagoRepository : JpaRepository<Pago, Int> {
    fun findByFacturaIdFactura(idFactura: Int): List<Pago>
    fun existsByReferenciaPago(referenciaPago: String): Boolean
}
