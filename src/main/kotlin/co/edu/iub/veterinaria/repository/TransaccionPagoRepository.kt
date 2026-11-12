package co.edu.iub.veterinaria.repository

import co.edu.iub.veterinaria.model.TransaccionPago
import org.springframework.data.jpa.repository.JpaRepository

interface TransaccionPagoRepository : JpaRepository<TransaccionPago, Int> {
    fun findByReferencia(referencia: String): TransaccionPago?
    fun findByIdTransaccionWompi(idTransaccionWompi: String): TransaccionPago?
}
