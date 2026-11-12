package co.edu.iub.veterinaria.dto.pago

data class WompiVerificacionResponse(
    val referencia: String,
    val estado: String,
    val estadoFactura: String,
    val mensaje: String,
    val idTransaccionWompi: String?,
    val montoCentavos: Long
)
