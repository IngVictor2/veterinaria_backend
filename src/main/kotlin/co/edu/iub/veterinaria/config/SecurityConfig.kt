package co.edu.iub.veterinaria.config

import co.edu.iub.veterinaria.security.JwtAuthenticationFilter
import co.edu.iub.veterinaria.security.ModuleAuthorizationManager
import co.edu.iub.veterinaria.security.RestAccessDeniedHandler
import co.edu.iub.veterinaria.security.RestAuthenticationEntryPoint
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val objectMapper: ObjectMapper,
    private val moduleAuthorizationManager: ModuleAuthorizationManager,
    @Value("\${app.cors.allowed-origins}") private val allowedOrigins: List<String>
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOrigins = this@SecurityConfig.allowedOrigins
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            exposedHeaders = listOf("Authorization")
            allowCredentials = true
            maxAge = 3600L
        }
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors(Customizer.withDefaults())
            .sessionManagement {
                it.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            .exceptionHandling {
                it.authenticationEntryPoint(RestAuthenticationEntryPoint(objectMapper))
                it.accessDeniedHandler(RestAccessDeniedHandler(objectMapper))
            }
            .authorizeHttpRequests {
                it
                    .requestMatchers("/").permitAll()
                    .requestMatchers("/auth/register", "/auth/login", "/auth/solicitar-recuperacion", "/auth/reset-password").permitAll()
                    .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()

                    .requestMatchers(HttpMethod.GET, "/admin/usuarios").access(moduleAuthorizationManager.hasAnyModule("CLIENTES", "USUARIOS"))
                    .requestMatchers(HttpMethod.PATCH, "/admin/usuarios/*/estado").access(moduleAuthorizationManager.hasAnyModule("CLIENTES", "USUARIOS"))
                    .requestMatchers("/admin/**").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/empleados", "/empleados/{id}").authenticated()
                    .requestMatchers(HttpMethod.POST, "/empleados/**").access(moduleAuthorizationManager.hasModule("USUARIOS"))
                    .requestMatchers(HttpMethod.PUT, "/empleados/**").access(moduleAuthorizationManager.hasModule("USUARIOS"))
                    .requestMatchers(HttpMethod.PATCH, "/empleados/**").access(moduleAuthorizationManager.hasModule("USUARIOS"))
                    .requestMatchers(HttpMethod.DELETE, "/empleados/**").access(moduleAuthorizationManager.hasModule("USUARIOS"))
                    .requestMatchers(HttpMethod.GET, "/servicios/**").authenticated()
                    .requestMatchers(HttpMethod.POST, "/servicios/**").access(moduleAuthorizationManager.hasModule("TARIFAS"))
                    .requestMatchers(HttpMethod.PUT, "/servicios/**").access(moduleAuthorizationManager.hasModule("TARIFAS"))
                    .requestMatchers(HttpMethod.DELETE, "/servicios/**").access(moduleAuthorizationManager.hasModule("TARIFAS"))

                    .requestMatchers("/clientes/me").authenticated()
                    .requestMatchers("/clientes/**").access(moduleAuthorizationManager.hasModule("CLIENTES"))

                    .requestMatchers("/mascotas/mis-mascotas").authenticated()
                    .requestMatchers(HttpMethod.GET, "/mascotas/{id}", "/mascotas/cliente/{idCliente}").authenticated()
                    .requestMatchers(HttpMethod.DELETE, "/mascotas/**").access(moduleAuthorizationManager.hasAnyModule("CLIENTES", "USUARIOS"))
                    .requestMatchers("/mascotas/**").access(moduleAuthorizationManager.hasModule("MASCOTAS"))

                    .requestMatchers("/citas/mis-citas").authenticated()
                    .requestMatchers(HttpMethod.POST, "/citas").authenticated()
                    .requestMatchers(HttpMethod.GET, "/citas/{id}", "/citas/cliente/{idCliente}", "/citas/mascota/{idMascota}", "/citas/bloques/{fecha}").authenticated()
                    .requestMatchers("/citas/**").access(moduleAuthorizationManager.hasModule("CITAS"))

                    .requestMatchers("/facturas/mis-facturas").authenticated()
                    .requestMatchers(HttpMethod.GET, "/facturas/{id}", "/facturas/cliente/{idCliente}").authenticated()
                    .requestMatchers(HttpMethod.POST, "/facturas/**").access(moduleAuthorizationManager.hasModule("FACTURACION"))
                    .requestMatchers("/facturas/**").access(moduleAuthorizationManager.hasModule("FACTURACION"))

                    .requestMatchers(HttpMethod.POST, "/pagos/wompi/webhook").permitAll()
                    .requestMatchers(HttpMethod.POST, "/pagos/checkout", "/pagos/wompi/verificar").authenticated()
                    .requestMatchers(HttpMethod.POST, "/pagos").hasAnyRole("ADMIN", "RECEPCIONISTA")
                    .requestMatchers("/pagos/**").access(moduleAuthorizationManager.hasModule("FACTURACION"))
                    .requestMatchers(HttpMethod.GET, "/historial/mascota/{idMascota}").authenticated()
                    .requestMatchers("/historial/**").access(moduleAuthorizationManager.hasModule("HISTORIAL"))
                    .requestMatchers("/calificaciones/**").authenticated()

                    .anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }
}
