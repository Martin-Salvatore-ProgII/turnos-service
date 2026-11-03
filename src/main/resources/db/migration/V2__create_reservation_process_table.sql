-- Procesos de reserva (docs/arq/modelo-db.md). Cada fila es un intento de reserva de un usuario,
-- desde que toca "Reservar" hasta su resultado final y una eventual cancelación. Sus estados son
-- los de docs/arq/maquina-estados.md.

CREATE TABLE reservation_process (
    id                      UUID         PRIMARY KEY,
    user_id                 BIGINT       NOT NULL REFERENCES app_user (id),
    status                  TEXT         NOT NULL,
    -- Datos históricos del profesional y del turno, tal como estaban al reservar. No son el catálogo
    -- vigente (Constitución P-05). Los textos vienen de la cátedra y no tienen largo máximo (RNF-09).
    professional_id         BIGINT       NOT NULL,
    professional_first_name TEXT         NOT NULL,
    professional_last_name  TEXT         NOT NULL,
    appointment_date        DATE         NOT NULL,
    start_time              TIME         NOT NULL,
    end_time                TIME         NOT NULL,
    -- Identificadores y datos que informa la cátedra a medida que avanza el proceso (REF §9 a §15).
    hold_id                 TEXT,
    reservation_process_id  TEXT,
    reservation_id          BIGINT,
    expires_at              TIMESTAMPTZ,
    request_event_id        TEXT,
    submitted_event_id      TEXT,
    last_message            TEXT,
    failure_reason          TEXT,
    cancellation_reason     VARCHAR(500),
    cancelled_by            VARCHAR(50),
    created_at              TIMESTAMPTZ  NOT NULL,
    updated_at              TIMESTAMPTZ  NOT NULL,
    confirmed_at            TIMESTAMPTZ,
    cancelled_at            TIMESTAMPTZ,
    -- Aumenta en cada cambio: un cambio hecho sobre una lectura vieja no se guarda (ADR-0068).
    version                 BIGINT       NOT NULL,
    -- Es la key de los eventos Kafka del proceso: con ella se encuentra el proceso de cada evento.
    -- Es NULL hasta que la cátedra crea el hold, y PostgreSQL admite varios NULL en una restricción UNIQUE.
    CONSTRAINT uq_reservation_process_reservation_process_id UNIQUE (reservation_process_id),
    CONSTRAINT ck_reservation_process_status CHECK (status IN ('STARTED', 'HELD', 'AWAITING_REQUEST',
        'AWAITING_PHONE', 'PHONE_SUBMITTED', 'CONFIRMED', 'CANCELLED', 'EXPIRED', 'INVALID', 'FAILED'))
);
