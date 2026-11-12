package co.edu.iub.veterinaria.dto.pago

import jakarta.validation.constraints.NotBlank

data class WompiVerificarRequest(
    @field:NotBlank(message = "La referencia es obligatoria") val referencia: String,
    val idTransaccionWompi: String? = null
)
