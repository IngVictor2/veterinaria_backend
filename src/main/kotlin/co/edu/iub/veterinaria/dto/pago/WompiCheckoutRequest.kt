package co.edu.iub.veterinaria.dto.pago

import jakarta.validation.constraints.NotNull

data class WompiCheckoutRequest(
    @field:NotNull val idFactura: Int
)
