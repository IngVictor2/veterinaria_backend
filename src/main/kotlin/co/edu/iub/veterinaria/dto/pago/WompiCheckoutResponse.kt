package co.edu.iub.veterinaria.dto.pago

data class WompiCheckoutResponse(
    val idTransaccion: Int,
    val referencia: String,
    val montoCentavos: Long,
    val moneda: String,
    val llavePublica: String,
    val firmaIntegridad: String,
    val correoCliente: String,
    val nombreCliente: String
)
