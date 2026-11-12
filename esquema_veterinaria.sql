-- ==========================================================
-- BASE DE DATOS: VETERINARIA
-- Version corregida contra Requisitos Funcionales (RF-001 a RF-026)
--
-- Cambios de esta version:
--   - usuario_rol (N:M) reemplaza el id_rol unico en usuario -> RF-002, RF-010
--   - calificacion: tabla nueva -> RF-008, RF-024
--   - password_reset_token: tabla nueva -> RF-004
--   - historial_mascota: FKs a consulta_medica y servicio_estetica -> RF-008
--   - referencia_pago se mueve de metodo_pago a pago (dato por transaccion)
--   - trigger: solo se puede calificar una cita ATENDIDA -> RF-024
--   - se eliminan las tablas de medicamentos (medicamento, receta,
--     detalle_receta) y el tipo de documento TI
-- ==========================================================

-- TABLA: rol
CREATE TABLE rol (
    id_rol SERIAL PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL UNIQUE,
    descripcion TEXT,
    estado BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- TABLA: modulo
CREATE TABLE modulo (
    id_modulo SERIAL PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL UNIQUE,
    descripcion TEXT,
    estado BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- TABLA: rol_modulo (permisos por rol) -> RF-011
CREATE TABLE rol_modulo (
    id_rol_modulo SERIAL PRIMARY KEY,
    id_rol INTEGER NOT NULL,
    id_modulo INTEGER NOT NULL,
    estado BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rol_modulo_rol FOREIGN KEY (id_rol) REFERENCES rol(id_rol),
    CONSTRAINT fk_rol_modulo_modulo FOREIGN KEY (id_modulo) REFERENCES modulo(id_modulo),
    CONSTRAINT uq_rol_modulo UNIQUE(id_rol, id_modulo)
);

-- TABLA: cliente
CREATE TABLE cliente (
    id_cliente SERIAL PRIMARY KEY,
    tipo_documento VARCHAR(20) NOT NULL,
    numero_documento VARCHAR(30) NOT NULL UNIQUE,
    primer_nombre VARCHAR(80) NOT NULL,
    segundo_nombre VARCHAR(80),
    primer_apellido VARCHAR(80) NOT NULL,
    segundo_apellido VARCHAR(80),
    telefono VARCHAR(20),
    correo VARCHAR(100) UNIQUE,
    direccion VARCHAR(150),
    estado BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_cliente_tipo_documento CHECK(tipo_documento IN ('CC','CE','PASAPORTE')),
    CONSTRAINT chk_cliente_nombres CHECK(LENGTH(TRIM(primer_nombre)) > 0 AND LENGTH(TRIM(primer_apellido)) > 0),
    CONSTRAINT chk_cliente_documento CHECK(LENGTH(TRIM(numero_documento)) > 0)
);

-- TABLA: cargo
CREATE TABLE cargo (
    id_cargo SERIAL PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL UNIQUE,
    descripcion TEXT,
    estado BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- TABLA: empleado (RF-009: requiere cuenta de acceso obligatoria)
CREATE TABLE empleado (
    id_empleado SERIAL PRIMARY KEY,
    id_cargo INTEGER NOT NULL,
    tipo_documento VARCHAR(20) NOT NULL,
    numero_documento VARCHAR(30) NOT NULL UNIQUE,
    primer_nombre VARCHAR(80) NOT NULL,
    segundo_nombre VARCHAR(80),
    primer_apellido VARCHAR(80) NOT NULL,
    segundo_apellido VARCHAR(80),
    telefono VARCHAR(20),
    correo VARCHAR(100) UNIQUE,
    direccion VARCHAR(150),
    fecha_ingreso DATE NOT NULL,
    estado BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_empleado_cargo FOREIGN KEY(id_cargo) REFERENCES cargo(id_cargo),
    CONSTRAINT chk_empleado_tipo_documento CHECK(tipo_documento IN ('CC','CE','PASAPORTE')),
    CONSTRAINT chk_empleado_nombre CHECK(LENGTH(TRIM(primer_nombre)) > 0 AND LENGTH(TRIM(primer_apellido)) > 0)
);

-- TABLA: usuario (asociado a cliente O empleado; roles en usuario_rol) -> RF-002, RF-010
-- correo: correo de ACCESO de la cuenta (independiente del correo de contacto del
-- cliente/empleado; este ultimo puede cambiarse o vaciarse sin afectar el login)
CREATE TABLE usuario (
    id_usuario SERIAL PRIMARY KEY,
    id_cliente INTEGER,
    id_empleado INTEGER,
    nombre_usuario VARCHAR(50) NOT NULL UNIQUE,
    correo VARCHAR(100) UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    estado BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_usuario_cliente FOREIGN KEY(id_cliente) REFERENCES cliente(id_cliente),
    CONSTRAINT fk_usuario_empleado FOREIGN KEY(id_empleado) REFERENCES empleado(id_empleado),
    CONSTRAINT uq_usuario_cliente UNIQUE(id_cliente),
    CONSTRAINT uq_usuario_empleado UNIQUE(id_empleado),
    CONSTRAINT chk_usuario_tipo CHECK(
        (id_cliente IS NOT NULL AND id_empleado IS NULL)
        OR (id_cliente IS NULL AND id_empleado IS NOT NULL)
    )
);

-- TABLA: usuario_rol (N:M usuario-rol) -> RF-002, RF-010
CREATE TABLE usuario_rol (
    id_usuario_rol SERIAL PRIMARY KEY,
    id_usuario INTEGER NOT NULL,
    id_rol INTEGER NOT NULL,
    estado BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_usuario_rol_usuario FOREIGN KEY(id_usuario) REFERENCES usuario(id_usuario),
    CONSTRAINT fk_usuario_rol_rol FOREIGN KEY(id_rol) REFERENCES rol(id_rol),
    CONSTRAINT uq_usuario_rol UNIQUE(id_usuario, id_rol)
);

-- TABLA: password_reset_token -> RF-004
CREATE TABLE password_reset_token (
    id_token SERIAL PRIMARY KEY,
    id_usuario INTEGER NOT NULL,
    token VARCHAR(255) NOT NULL UNIQUE,
    fecha_expiracion TIMESTAMP NOT NULL,
    usado BOOLEAN NOT NULL DEFAULT FALSE,
    estado BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_password_reset_usuario FOREIGN KEY(id_usuario) REFERENCES usuario(id_usuario)
);

-- TABLA: mascota
CREATE TABLE mascota (
    id_mascota SERIAL PRIMARY KEY,
    id_cliente INTEGER NOT NULL,
    nombre VARCHAR(80) NOT NULL,
    especie VARCHAR(50) NOT NULL,
    raza VARCHAR(80),
    sexo VARCHAR(15) NOT NULL,
    fecha_nacimiento DATE,
    peso DECIMAL(5,2),
    observaciones TEXT,
    estado BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mascota_cliente FOREIGN KEY(id_cliente) REFERENCES cliente(id_cliente),
    CONSTRAINT chk_mascota_sexo CHECK(sexo IN ('MACHO','HEMBRA')),
    CONSTRAINT chk_mascota_peso CHECK(peso > 0),
    CONSTRAINT chk_mascota_fecha_nacimiento CHECK(fecha_nacimiento <= CURRENT_DATE),
    CONSTRAINT chk_mascota_nombre CHECK(LENGTH(TRIM(nombre)) > 0)
);

-- TABLA: servicio -> RF-012 (tarifas)
CREATE TABLE servicio (
    id_servicio SERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL UNIQUE,
    descripcion TEXT,
    tipo_servicio VARCHAR(20) NOT NULL,
    precio DECIMAL(10,2) NOT NULL,
    estado BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_servicio_tipo CHECK(tipo_servicio IN ('CONSULTA','ESTETICA','OTRO')),
    CONSTRAINT chk_servicio_precio CHECK(precio >= 0)
);

-- TABLA: cita -> RF-013 a RF-016
CREATE TABLE cita (
    id_cita SERIAL PRIMARY KEY,
    id_mascota INTEGER NOT NULL,
    id_empleado INTEGER NOT NULL,
    id_servicio INTEGER NOT NULL,
    fecha_cita DATE NOT NULL,
    hora_cita TIME NOT NULL,
    motivo VARCHAR(200),
    estado_cita VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    observaciones TEXT,
    estado BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cita_mascota FOREIGN KEY(id_mascota) REFERENCES mascota(id_mascota),
    CONSTRAINT fk_cita_empleado FOREIGN KEY(id_empleado) REFERENCES empleado(id_empleado),
    CONSTRAINT fk_cita_servicio FOREIGN KEY(id_servicio) REFERENCES servicio(id_servicio),
    CONSTRAINT chk_cita_estado CHECK(estado_cita IN ('PENDIENTE','CONFIRMADA','ATENDIDA','CANCELADA')),
    CONSTRAINT uq_cita_empleado_hora UNIQUE(id_empleado, fecha_cita, hora_cita)
);

-- TABLA: consulta_medica -> RF-017
CREATE TABLE consulta_medica (
    id_consulta SERIAL PRIMARY KEY,
    id_cita INTEGER NOT NULL UNIQUE,
    peso DECIMAL(6,2),
    temperatura DECIMAL(4,2),
    sintomas TEXT NOT NULL,
    diagnostico_general TEXT,
    tratamiento_indicado TEXT,
    observaciones TEXT,
    fecha_consulta TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    estado BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_consulta_cita FOREIGN KEY(id_cita) REFERENCES cita(id_cita),
    CONSTRAINT chk_consulta_peso CHECK(peso > 0),
    CONSTRAINT chk_consulta_temperatura CHECK(temperatura > 0)
);

-- TABLA: servicio_estetica -> RF-018, RF-019
CREATE TABLE servicio_estetica (
    id_servicio_estetica SERIAL PRIMARY KEY,
    id_cita INTEGER NOT NULL UNIQUE,
    detalles TEXT,
    observaciones TEXT,
    estado BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_estetica_cita FOREIGN KEY(id_cita) REFERENCES cita(id_cita)
);

-- TABLA: factura -> RF-020, RF-023
CREATE TABLE factura (
    id_factura SERIAL PRIMARY KEY,
    id_cliente INTEGER NOT NULL,
    fecha_factura DATE NOT NULL DEFAULT CURRENT_DATE,
    subtotal DECIMAL(10,2) NOT NULL DEFAULT 0,
    descuento DECIMAL(10,2) NOT NULL DEFAULT 0,
    total DECIMAL(10,2) NOT NULL DEFAULT 0,
    estado_factura VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    estado BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_factura_cliente FOREIGN KEY(id_cliente) REFERENCES cliente(id_cliente),
    CONSTRAINT chk_factura_estado CHECK(estado_factura IN ('PENDIENTE','PAGADA','ANULADA')),
    CONSTRAINT chk_factura_total CHECK(total >= 0)
);

-- TABLA: detalle_factura -> RF-021
CREATE TABLE detalle_factura (
    id_detalle_factura SERIAL PRIMARY KEY,
    id_factura INTEGER NOT NULL,
    id_cita INTEGER NOT NULL,
    id_servicio INTEGER NOT NULL,
    cantidad INTEGER NOT NULL DEFAULT 1,
    precio DECIMAL(10,2) NOT NULL,
    subtotal DECIMAL(10,2) GENERATED ALWAYS AS (cantidad * precio) STORED,
    estado BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_detalle_factura FOREIGN KEY(id_factura) REFERENCES factura(id_factura),
    CONSTRAINT fk_detalle_factura_cita FOREIGN KEY(id_cita) REFERENCES cita(id_cita),
    CONSTRAINT fk_detalle_servicio FOREIGN KEY(id_servicio) REFERENCES servicio(id_servicio),
    CONSTRAINT chk_detalle_factura_cantidad CHECK(cantidad > 0),
    CONSTRAINT chk_detalle_factura_precio CHECK(precio >= 0)
);

-- TABLA: metodo_pago
CREATE TABLE metodo_pago (
    id_metodo_pago SERIAL PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL UNIQUE,
    descripcion TEXT,
    estado BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- TABLA: pago -> RF-022
CREATE TABLE pago (
    id_pago SERIAL PRIMARY KEY,
    id_factura INTEGER NOT NULL,
    id_metodo_pago INTEGER NOT NULL,
    fecha_pago TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    monto DECIMAL(10,2) NOT NULL,
    referencia_pago VARCHAR(100),
    estado BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pago_factura FOREIGN KEY(id_factura) REFERENCES factura(id_factura),
    CONSTRAINT fk_pago_metodo_pago FOREIGN KEY(id_metodo_pago) REFERENCES metodo_pago(id_metodo_pago),
    CONSTRAINT chk_pago_monto CHECK(monto > 0)
);

-- TABLA: transaccion_pago -> intentos de pago en línea con Wompi
CREATE TABLE transaccion_pago (
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

-- TABLA: historial_mascota -> RF-008
CREATE TABLE historial_mascota (
    id_historial SERIAL PRIMARY KEY,
    id_mascota INTEGER NOT NULL,
    id_cita INTEGER NOT NULL,
    id_consulta INTEGER,
    id_servicio_estetica INTEGER,
    tipo_historial VARCHAR(20) NOT NULL,
    resumen TEXT NOT NULL,
    fecha_registro TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    estado BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_historial_mascota FOREIGN KEY(id_mascota) REFERENCES mascota(id_mascota),
    CONSTRAINT fk_historial_cita FOREIGN KEY(id_cita) REFERENCES cita(id_cita),
    CONSTRAINT fk_historial_consulta FOREIGN KEY(id_consulta) REFERENCES consulta_medica(id_consulta),
    CONSTRAINT fk_historial_servicio_estetica FOREIGN KEY(id_servicio_estetica) REFERENCES servicio_estetica(id_servicio_estetica),
    CONSTRAINT chk_historial_tipo CHECK(tipo_historial IN ('MEDICO','ESTETICA')),
    CONSTRAINT chk_historial_consistencia CHECK(
        (tipo_historial = 'MEDICO' AND id_consulta IS NOT NULL AND id_servicio_estetica IS NULL)
        OR (tipo_historial = 'ESTETICA' AND id_servicio_estetica IS NOT NULL AND id_consulta IS NULL)
    )
);

-- TABLA: calificacion -> RF-008, RF-024
CREATE TABLE calificacion (
    id_calificacion SERIAL PRIMARY KEY,
    id_cita INTEGER NOT NULL UNIQUE,
    puntuacion SMALLINT NOT NULL,
    comentario TEXT,
    estado BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_calificacion_cita FOREIGN KEY(id_cita) REFERENCES cita(id_cita),
    CONSTRAINT chk_calificacion_puntuacion CHECK(puntuacion BETWEEN 1 AND 5)
);

-- VISTA: v_usuario_correo (correo de acceso: propio de usuario, o derivado de cliente/empleado) -> RF-002, RF-004
CREATE VIEW v_usuario_correo AS
SELECT u.id_usuario, u.nombre_usuario, COALESCE(u.correo, c.correo, e.correo) AS correo
FROM usuario u
LEFT JOIN cliente c ON u.id_cliente = c.id_cliente
LEFT JOIN empleado e ON u.id_empleado = e.id_empleado;

-- INDICES
CREATE INDEX idx_mascota_cliente ON mascota(id_cliente);
CREATE INDEX idx_cita_mascota ON cita(id_mascota);
CREATE INDEX idx_cita_empleado ON cita(id_empleado);
CREATE INDEX idx_cita_fecha ON cita(fecha_cita);
CREATE INDEX idx_factura_cliente ON factura(id_cliente);
CREATE INDEX idx_pago_factura ON pago(id_factura);
CREATE INDEX idx_transaccion_pago_factura ON transaccion_pago(id_factura);
CREATE INDEX idx_transaccion_pago_wompi ON transaccion_pago(id_transaccion_wompi);
CREATE INDEX idx_historial_mascota ON historial_mascota(id_mascota);
CREATE INDEX idx_usuario_rol_usuario ON usuario_rol(id_usuario);
CREATE INDEX idx_password_reset_usuario ON password_reset_token(id_usuario);
CREATE INDEX idx_calificacion_cita ON calificacion(id_cita);

-- FUNCION: set_updated_at (actualiza updated_at en cada UPDATE) -> RF-026
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- TRIGGERS: updated_at (una por tabla con la columna updated_at)
CREATE TRIGGER trg_rol_updated_at BEFORE UPDATE ON rol FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_modulo_updated_at BEFORE UPDATE ON modulo FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_rol_modulo_updated_at BEFORE UPDATE ON rol_modulo FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_cliente_updated_at BEFORE UPDATE ON cliente FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_cargo_updated_at BEFORE UPDATE ON cargo FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_empleado_updated_at BEFORE UPDATE ON empleado FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_usuario_updated_at BEFORE UPDATE ON usuario FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_usuario_rol_updated_at BEFORE UPDATE ON usuario_rol FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_password_reset_token_updated_at BEFORE UPDATE ON password_reset_token FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_mascota_updated_at BEFORE UPDATE ON mascota FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_servicio_updated_at BEFORE UPDATE ON servicio FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_cita_updated_at BEFORE UPDATE ON cita FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_consulta_medica_updated_at BEFORE UPDATE ON consulta_medica FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_servicio_estetica_updated_at BEFORE UPDATE ON servicio_estetica FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_factura_updated_at BEFORE UPDATE ON factura FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_detalle_factura_updated_at BEFORE UPDATE ON detalle_factura FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_metodo_pago_updated_at BEFORE UPDATE ON metodo_pago FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_pago_updated_at BEFORE UPDATE ON pago FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_transaccion_pago_updated_at BEFORE UPDATE ON transaccion_pago FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_historial_mascota_updated_at BEFORE UPDATE ON historial_mascota FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_calificacion_updated_at BEFORE UPDATE ON calificacion FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- TRIGGER: solo se puede calificar una cita ATENDIDA -> RF-024
CREATE OR REPLACE FUNCTION check_cita_atendida_para_calificar()
RETURNS TRIGGER AS $$
DECLARE
    v_estado_cita VARCHAR(20);
BEGIN
    SELECT estado_cita INTO v_estado_cita
    FROM cita
    WHERE id_cita = NEW.id_cita;

    IF v_estado_cita IS DISTINCT FROM 'ATENDIDA' THEN
        RAISE EXCEPTION
            'Solo se pueden calificar citas con estado ATENDIDA (cita % tiene estado %)',
            NEW.id_cita, v_estado_cita;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_calificacion_cita_atendida
BEFORE INSERT ON calificacion
FOR EACH ROW
EXECUTE FUNCTION check_cita_atendida_para_calificar();

-- ==========================================================
-- MIGRACION PRODUCCION: correo de acceso en usuario
-- Ejecutar SOLO sobre bases existentes (instalaciones nuevas ya
-- incluyen la columna en CREATE TABLE usuario).
-- OJO: el ADD COLUMN ... UNIQUE fallara si ya existen correos
-- duplicados entre cliente y empleado; deduplicar antes.
-- ==========================================================
ALTER TABLE usuario ADD COLUMN correo VARCHAR(100) UNIQUE;

UPDATE usuario u SET correo = c.correo
FROM cliente c
WHERE c.id_cliente = u.id_cliente AND u.correo IS NULL;

UPDATE usuario u SET correo = e.correo
FROM empleado e
WHERE e.id_empleado = u.id_empleado AND u.correo IS NULL;

ALTER VIEW v_usuario_correo RENAME TO v_usuario_correo_old;
CREATE VIEW v_usuario_correo AS
SELECT u.id_usuario, u.nombre_usuario, COALESCE(u.correo, c.correo, e.correo) AS correo
FROM usuario u
LEFT JOIN cliente c ON u.id_cliente = c.id_cliente
LEFT JOIN empleado e ON u.id_empleado = e.id_empleado;
DROP VIEW v_usuario_correo_old;

-- NOTA sobre RF-009 (cuenta de acceso obligatoria por empleado):
-- la regla queda como responsabilidad de la capa de aplicacion: el flujo de
-- "crear empleado" SIEMPRE debe crear tambien su 'usuario' correspondiente
-- (idealmente en una misma transaccion de aplicacion).
