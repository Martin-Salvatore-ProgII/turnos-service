-- Eventos Kafka de reservas ya procesados (ADR-0008). La entrega de Kafka es al menos una vez: el
-- eventId identifica un evento y su clave primaria hace que registrarlo dos veces sea imposible, aun
-- con dos copias llegando a la vez (ADR-0070).
--
-- Se purga por antigüedad (processed_at) en cada ciclo de la reconciliación. Un evento viejo que se
-- repitiera después de purgado es inofensivo: un estado final no se reabre y un disparador sin
-- transición desde el estado actual se ignora (ADR-0023).
--
-- Son datos propios: la base sí valida su contenido.

CREATE TABLE processed_event (
    event_id     TEXT        PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_processed_event_processed_at ON processed_event (processed_at);
