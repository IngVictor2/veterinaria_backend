package co.edu.iub.veterinaria.exception

import org.springframework.http.HttpStatus

open class ApiException(
    message: String,
    val status: HttpStatus
) : RuntimeException(message)

class InvalidCredentialsException(message: String) : ApiException(message, HttpStatus.UNAUTHORIZED)
class InvalidStatusTransitionException(message: String) : ApiException(message, HttpStatus.BAD_REQUEST)
class InvalidRequestException(message: String) : ApiException(message, HttpStatus.BAD_REQUEST)

class WompiNotConfiguredException(detalle: String) : ApiException(
    "La pasarela Wompi no está configurada en modo sandbox. Revisa en Render: $detalle",
    HttpStatus.SERVICE_UNAVAILABLE
)
