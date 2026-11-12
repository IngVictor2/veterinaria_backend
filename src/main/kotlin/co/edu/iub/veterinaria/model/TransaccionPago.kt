package co.edu.iub.veterinaria.model

import jakarta.persistence.*

@Entity
@Table(name = "transaccion_pago")
class TransaccionPago : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_transaccion_pago")
    var idTransaccionPago: Int? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_factura", nullable = false)
    lateinit var factura: Factura

    @Column(nullable = false, unique = true, length = 120)
    lateinit var referencia: String

    @Column(name = "monto_centavos", nullable = false)
    var montoCentavos: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_transaccion", nullable = false, length = 20)
    var estadoTransaccion: EstadoTransaccionPago = EstadoTransaccionPago.PENDIENTE

    @Column(name = "id_transaccion_wompi", length = 120)
    var idTransaccionWompi: String? = null

    @Column(name = "metodo_pago_wompi", length = 40)
    var metodoPagoWompi: String? = null

    @Column(length = 255)
    var mensaje: String? = null
}

enum class EstadoTransaccionPago {
    PENDIENTE, APROBADA, RECHAZADA, ANULADA, ERROR
}
