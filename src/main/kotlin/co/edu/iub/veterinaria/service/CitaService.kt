package co.edu.iub.veterinaria.service

import co.edu.iub.veterinaria.dto.cita.*
import co.edu.iub.veterinaria.exception.DuplicateResourceException
import co.edu.iub.veterinaria.exception.InvalidRequestException
import co.edu.iub.veterinaria.exception.InvalidStatusTransitionException
import co.edu.iub.veterinaria.exception.ResourceNotFoundException
import co.edu.iub.veterinaria.model.*
import co.edu.iub.veterinaria.repository.*
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Service
class CitaService(
    private val citaRepository: CitaRepository,
    private val mascotaRepository: MascotaRepository,
    private val empleadoRepository: EmpleadoRepository,
    private val servicioRepository: ServicioRepository,
    private val consultaMedicaRepository: ConsultaMedicaRepository,
    private val servicioEsteticaRepository: ServicioEsteticaRepository,
    private val historialMascotaRepository: HistorialMascotaRepository,
    private val facturaService: FacturaService
) {

    companion object {
        private val DURACION_MINUTOS = mapOf(
            TipoServicio.CONSULTA to 30,
            TipoServicio.ESTETICA to 60,
            TipoServicio.OTRO to 120
        )
        private val HORA_INICIO_JORNADA = LocalTime.of(7, 0)
        private val HORA_FIN_JORNADA = LocalTime.of(18, 0)
        private val HORA_FIN_JORNADA_DOMINGO = LocalTime.of(13, 0)
        private val ZONA_HORARIA = ZoneId.of("America/Bogota")
        private val FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    }

    @Transactional(readOnly = true)
    fun listar(): List<CitaResponse> = citaRepository.findAll().map { toResponse(it) }

    @Transactional(readOnly = true)
    fun buscarPorId(id: Int, idCliente: Int? = null): CitaResponse {
        val cita = citaRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Cita no encontrada") }
        if (idCliente != null && cita.mascota.cliente.idCliente != idCliente) {
            throw AccessDeniedException("No tiene permisos para ver esta cita")
        }
        return toResponse(cita)
    }

    @Transactional(readOnly = true)
    fun listarPorCliente(idCliente: Int): List<CitaResponse> =
        citaRepository.findByMascotaClienteIdCliente(idCliente).map { toResponse(it) }

    @Transactional(readOnly = true)
    fun listarPorMascota(idMascota: Int, idCliente: Int? = null): List<CitaResponse> {
        if (idCliente != null) {
            val mascota = mascotaRepository.findById(idMascota)
                .orElseThrow { ResourceNotFoundException("Mascota no encontrada") }
            if (mascota.cliente.idCliente != idCliente) {
                throw AccessDeniedException("No tiene permisos para ver las citas de esta mascota")
            }
        }
        return citaRepository.findByMascotaIdMascota(idMascota).map { toResponse(it) }
    }

    @Transactional(readOnly = true)
    fun listarPorEmpleado(idEmpleado: Int): List<CitaResponse> =
        citaRepository.findByEmpleadoIdEmpleado(idEmpleado).map { toResponse(it) }

    @Transactional(readOnly = true)
    fun listarPorFecha(fecha: LocalDate): List<CitaResponse> =
        citaRepository.findByFechaCita(fecha).map { toResponse(it) }

    @Transactional(readOnly = true)
    fun listarBloquesOcupados(fecha: LocalDate): List<BloqueOcupadoResponse> =
        citaRepository.findByFechaCita(fecha)
            .filter { it.estadoCita != EstadoCita.CANCELADA && it.empleado != null }
            .map {
                val empleado = it.empleado!!
                BloqueOcupadoResponse(
                    horaInicio = it.horaCita,
                    duracionMinutos = DURACION_MINUTOS[it.servicio.tipoServicio] ?: 60,
                    tipoServicio = it.servicio.tipoServicio,
                    idEmpleado = empleado.idEmpleado!!,
                    nombreEmpleado = "${empleado.primerNombre} ${empleado.primerApellido}"
                )
            }

    private val transicionesValidas = mapOf(
        EstadoCita.PENDIENTE to setOf(EstadoCita.CONFIRMADA, EstadoCita.CANCELADA),
        EstadoCita.CONFIRMADA to setOf(EstadoCita.ATENDIDA, EstadoCita.CANCELADA),
        EstadoCita.ATENDIDA to emptySet(),
        EstadoCita.CANCELADA to emptySet()
    )

    @Transactional
    fun crear(request: CitaRequest, idCliente: Int? = null): CitaResponse {
        val mascota = mascotaRepository.findById(request.idMascota)
            .orElseThrow { ResourceNotFoundException("Mascota no encontrada") }
        if (idCliente != null && mascota.cliente.idCliente != idCliente) {
            throw AccessDeniedException("No tiene permisos para agendar una cita con esta mascota")
        }
        val empleado = request.idEmpleado?.let { idEmpleado ->
            empleadoRepository.findById(idEmpleado)
                .orElseThrow { ResourceNotFoundException("Empleado no encontrado") }
        }
        val servicio = servicioRepository.findById(request.idServicio)
            .orElseThrow { ResourceNotFoundException("Servicio no encontrado") }

        if (empleado != null) {
            val duracion = DURACION_MINUTOS[servicio.tipoServicio] ?: 60
            if (existeConflictoHorario(request.idEmpleado!!, request.fechaCita, request.horaCita, duracion)) {
                throw DuplicateResourceException("El empleado ya tiene una cita en ese horario")
            }
        }

        val cita = Cita().apply {
            this.mascota = mascota
            this.empleado = empleado
            this.servicio = servicio
            fechaCita = request.fechaCita
            horaCita = request.horaCita
            motivo = request.motivo
            observaciones = request.observaciones
        }
        return toResponse(citaRepository.save(cita))
    }

    @Transactional
    fun reprogramar(id: Int, request: CitaRequest): CitaResponse {
        val cita = citaRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Cita no encontrada") }
        if (cita.estadoCita == EstadoCita.ATENDIDA || cita.estadoCita == EstadoCita.CANCELADA) {
            throw InvalidRequestException("No se puede reprogramar una cita ${cita.estadoCita}")
        }
        val duracion = DURACION_MINUTOS[cita.servicio.tipoServicio] ?: 60
        val idEmpleado = request.idEmpleado ?: cita.empleado?.idEmpleado
            ?: throw InvalidRequestException("Se requiere un empleado para reprogramar la cita")
        if (existeConflictoHorario(
                idEmpleado, request.fechaCita, request.horaCita, duracion, excludeId = id
            )
        ) {
            throw DuplicateResourceException("El empleado ya tiene una cita en ese horario")
        }
        cita.fechaCita = request.fechaCita
        cita.horaCita = request.horaCita
        cita.motivo = request.motivo
        cita.observaciones = request.observaciones
        return toResponse(citaRepository.save(cita))
    }

    @Transactional
    fun confirmar(id: Int, request: CitaRequest): CitaResponse {
        val cita = citaRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Cita no encontrada") }
        if (cita.estadoCita == EstadoCita.ATENDIDA || cita.estadoCita == EstadoCita.CANCELADA) {
            throw InvalidRequestException("No se puede confirmar una cita ${cita.estadoCita}")
        }
        if (request.idMascota != cita.mascota.idMascota) {
            throw InvalidRequestException("La mascota de la cita no puede cambiarse")
        }
        val idEmpleado = request.idEmpleado
            ?: throw InvalidRequestException("Se requiere asignar un empleado para confirmar la cita")
        val empleado = empleadoRepository.findById(idEmpleado)
            .orElseThrow { ResourceNotFoundException("Empleado no encontrado") }
        val servicio = servicioRepository.findById(request.idServicio)
            .orElseThrow { ResourceNotFoundException("Servicio no encontrado") }

        val duracion = DURACION_MINUTOS[servicio.tipoServicio] ?: 60
        if (existeConflictoHorario(
                idEmpleado, request.fechaCita, request.horaCita, duracion, excludeId = id
            )
        ) {
            throw DuplicateResourceException("El empleado ya tiene una cita en ese horario")
        }

        cita.empleado = empleado
        cita.servicio = servicio
        cita.fechaCita = request.fechaCita
        cita.horaCita = request.horaCita
        cita.motivo = request.motivo
        cita.observaciones = request.observaciones
        cita.estadoCita = EstadoCita.CONFIRMADA
        return toResponse(citaRepository.save(cita))
    }

    @Transactional
    fun cambiarEstado(id: Int, nuevoEstado: EstadoCita) {
        val cita = citaRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Cita no encontrada") }
        val permitidos = transicionesValidas[cita.estadoCita]
            ?: throw InvalidStatusTransitionException("Estado actual inválido: ${cita.estadoCita}")
            if (nuevoEstado !in permitidos) {
            throw InvalidStatusTransitionException(
                "No se puede cambiar de ${cita.estadoCita} a $nuevoEstado. " +
                "Transiciones permitidas: ${permitidos.joinToString(", ")}"
            )
        }
        if (nuevoEstado == EstadoCita.ATENDIDA) {
            validarDiaDeAtencion(cita)
        }
        cita.estadoCita = nuevoEstado
        citaRepository.save(cita)
        when (nuevoEstado) {
            EstadoCita.ATENDIDA -> facturaService.generarPorAtencion(cita)
            EstadoCita.CANCELADA -> facturaService.anularPorCita(cita.idCita!!)
            else -> {}
        }
    }

    @Transactional
    fun cancelar(id: Int) {
        cambiarEstado(id, EstadoCita.CANCELADA)
    }

    @Transactional
    fun registrarConsulta(request: ConsultaRequest) {
        cambiarEstado(request.idCita, EstadoCita.ATENDIDA)
        val cita = citaRepository.findById(request.idCita)
            .orElseThrow { ResourceNotFoundException("Cita no encontrada") }

        if (consultaMedicaRepository.findByCitaIdCita(request.idCita) != null) {
            throw DuplicateResourceException("Esta cita ya tiene una consulta médica registrada")
        }

        val consulta = ConsultaMedica().apply {
            this.cita = cita
            peso = request.peso
            temperatura = request.temperatura
            sintomas = request.sintomas
            diagnosticoGeneral = request.diagnosticoGeneral
            tratamientoIndicado = request.tratamientoIndicado
            observaciones = request.observaciones
        }
        consultaMedicaRepository.save(consulta)

        val historial = HistorialMascota().apply {
            mascota = cita.mascota
            this.cita = cita
            this.consulta = consulta
            tipoHistorial = TipoHistorial.MEDICO
            resumen = "Consulta médica: ${request.sintomas}"
        }
        historialMascotaRepository.save(historial)
    }

    @Transactional
    fun registrarEstetica(request: EsteticaRequest) {
        cambiarEstado(request.idCita, EstadoCita.ATENDIDA)
        val cita = citaRepository.findById(request.idCita)
            .orElseThrow { ResourceNotFoundException("Cita no encontrada") }

        if (servicioEsteticaRepository.findByCitaIdCita(request.idCita) != null) {
            throw DuplicateResourceException("Esta cita ya tiene un servicio estético registrado")
        }

        val estetica = ServicioEstetica().apply {
            this.cita = cita
            detalles = request.detalles
            observaciones = request.observaciones
        }
        servicioEsteticaRepository.save(estetica)

        val historial = HistorialMascota().apply {
            mascota = cita.mascota
            this.cita = cita
            this.servicioEstetica = estetica
            tipoHistorial = TipoHistorial.ESTETICA
            resumen = "Servicio estético: ${request.detalles ?: "Sin detalles"}"
        }
        historialMascotaRepository.save(historial)
    }

    private fun validarDiaDeAtencion(cita: Cita) {
        val hoy = LocalDate.now(ZONA_HORARIA)
        if (cita.fechaCita != hoy) {
            throw InvalidRequestException(
                "No se puede marcar como atendida una cita programada para el " +
                    "${cita.fechaCita.format(FORMATO_FECHA)}. Solo se permite el día de la cita."
            )
        }
    }

    private fun existeConflictoHorario(
        idEmpleado: Int,
        fecha: LocalDate,
        horaInicio: LocalTime,
        duracionMinutos: Int,
        excludeId: Int? = null
    ): Boolean {
        if (horaInicio.isBefore(HORA_INICIO_JORNADA)) {
            throw InvalidRequestException("La cita debe iniciar a partir de las 7:00 AM")
        }

        val horaFin = horaInicio.plusMinutes(duracionMinutos.toLong())
        val esDomingo = fecha.dayOfWeek == DayOfWeek.SUNDAY
        val horaFinJornada = if (esDomingo) HORA_FIN_JORNADA_DOMINGO else HORA_FIN_JORNADA
        if (horaFin.isAfter(horaFinJornada)) {
            val rango = if (esDomingo) "7:00 AM - 1:00 PM" else "7:00 AM - 6:00 PM"
            throw InvalidRequestException(
                "La cita excede el horario laboral ($rango). " +
                "La hora maxima de inicio es las ${horaFinJornada.minusMinutes(duracionMinutos.toLong())}."
            )
        }

        return citaRepository
            .findByEmpleadoIdEmpleadoAndFechaCitaAndEstadoCitaNot(idEmpleado, fecha, EstadoCita.CANCELADA)
            .any { cita ->
                if (excludeId != null && cita.idCita == excludeId) return@any false
                val duracionExistente = DURACION_MINUTOS[cita.servicio.tipoServicio] ?: 60
                val finExistente = cita.horaCita.plusMinutes(duracionExistente.toLong())
                horaInicio.isBefore(finExistente) && horaFin.isAfter(cita.horaCita)
            }
    }

    private fun toResponse(c: Cita) = CitaResponse(
        idCita = c.idCita!!,
        idMascota = c.mascota.idMascota!!,
        nombreMascota = c.mascota.nombre,
        idCliente = c.mascota.cliente.idCliente!!,
        nombreCliente = "${c.mascota.cliente.primerNombre} ${c.mascota.cliente.primerApellido}",
        idEmpleado = c.empleado?.idEmpleado,
        nombreEmpleado = c.empleado?.let { "${it.primerNombre} ${it.primerApellido}" },
        idServicio = c.servicio.idServicio!!,
        nombreServicio = c.servicio.nombre,
        tipoServicio = c.servicio.tipoServicio,
        duracionMinutos = DURACION_MINUTOS[c.servicio.tipoServicio] ?: 60,
        fechaCita = c.fechaCita,
        horaCita = c.horaCita,
        motivo = c.motivo,
        estadoCita = c.estadoCita,
        observaciones = c.observaciones,
        createdAt = c.createdAt,
        updatedAt = c.updatedAt
    )
}
