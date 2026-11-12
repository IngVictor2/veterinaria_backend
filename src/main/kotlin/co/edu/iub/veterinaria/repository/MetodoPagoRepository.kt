package co.edu.iub.veterinaria.repository

import co.edu.iub.veterinaria.model.MetodoPago
import org.springframework.data.jpa.repository.JpaRepository

interface MetodoPagoRepository : JpaRepository<MetodoPago, Int> {
    fun findByNombre(nombre: String): MetodoPago?
}
