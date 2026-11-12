package co.edu.iub.veterinaria.dto.auth

import co.edu.iub.veterinaria.model.TipoDocumento
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.AssertTrue

data class RegisterRequest(
    @field:NotNull val tipoDocumento: TipoDocumento,
    @field:NotBlank val numeroDocumento: String,
    @field:NotBlank val primerNombre: String,
    val segundoNombre: String? = null,
    @field:NotBlank val primerApellido: String,
    val segundoApellido: String? = null,
    @field:NotBlank
    @field:Pattern(regexp = "[0-9]{7,15}", message = "El teléfono debe contener entre 7 y 15 dígitos, sin letras ni símbolos")
    val telefono: String,
    val direccion: String? = null,
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank @field:Size(min = 8) val password: String
) {
    @AssertTrue(message = "La cédula de ciudadanía debe contener de 1 a 10 dígitos; la de extranjería, de 1 a 7. El pasaporte admite de 1 a 30 letras y números, sin espacios ni símbolos")
    fun isDocumentoValido(): Boolean = when (tipoDocumento) {
        TipoDocumento.CC -> numeroDocumento.matches(Regex("[0-9]{1,10}"))
        TipoDocumento.CE -> numeroDocumento.matches(Regex("[0-9]{1,7}"))
        TipoDocumento.PASAPORTE -> numeroDocumento.matches(Regex("[a-zA-Z0-9]{1,30}"))
    }
}
