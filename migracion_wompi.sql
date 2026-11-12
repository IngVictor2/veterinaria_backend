-- Migración: pagos en línea con Wompi (sandbox)
-- Ejecutar una sola vez sobre la base de datos Neon.
-- Es idempotente: se puede ejecutar varias veces sin duplicar datos.

CREATE TABLE IF NOT EXISTS transaccion_pago (
    id_transaccion_pago SERIAL PRIMARY KEY,
    id_factura INTEGER NOT NULL,
    referencia VARCHAR(120) NOT NULL UNIQUE,
    monto_centavos BIGINT NOT NULL,
    estado_transaccion VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    id_transaccion_wompi VARCHAR(120),
    metodo_pago_wompi VARCHAR(40),
    mensaje VARCHAR(255),
    estado BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transaccion_pago_factura FOREIGN KEY(id_factura) REFERENCES factura(id_factura),
    CONSTRAINT chk_transaccion_pago_estado CHECK(estado_transaccion IN ('PENDIENTE','APROBADA','RECHAZADA','ANULADA','ERROR')),
    CONSTRAINT chk_transaccion_pago_monto CHECK(monto_centavos > 0)
);

CREATE INDEX IF NOT EXISTS idx_transaccion_pago_factura ON transaccion_pago(id_factura);
CREATE INDEX IF NOT EXISTS idx_transaccion_pago_wompi ON transaccion_pago(id_transaccion_wompi);

INSERT INTO metodo_pago (nombre, descripcion)
SELECT 'Wompi', 'Pago en línea con Wompi'
WHERE NOT EXISTS (SELECT 1 FROM metodo_pago WHERE nombre = 'Wompi');
