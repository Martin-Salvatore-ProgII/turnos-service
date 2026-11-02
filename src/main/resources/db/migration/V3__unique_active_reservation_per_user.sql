-- Un usuario tiene como máximo un proceso de reserva activo (ADR-0024). Lo garantiza la base y no una
-- consulta previa: con dos pedidos simultáneos, los dos consultarían antes de que cualquiera guarde
-- (ADR-0070). El índice es parcial: solo cuenta los estados no finales, así que un proceso terminado
-- no impide reservar de nuevo. Los estados son los no finales de docs/arq/maquina-estados.md.

CREATE UNIQUE INDEX uq_reservation_process_active_user ON reservation_process (user_id)
    WHERE status IN ('STARTED', 'HELD', 'AWAITING_REQUEST', 'AWAITING_PHONE', 'PHONE_SUBMITTED');
