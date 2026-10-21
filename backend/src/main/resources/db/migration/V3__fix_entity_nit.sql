-- ============================================================================
-- V3 — Corrección de NIT heredados no verificados
-- En el seed original (V2, portado del sistema Python) tres entidades comparten
-- el NIT 800.197.268-4 (DIAN, Supersalud, Superfinanciera), lo cual es
-- incorrecto: ese NIT corresponde a la DIAN. No se fabrican NIT: se marcan los
-- no verificados para que la firma los corrija con la fuente oficial (RUT/DIAN).
-- ============================================================================

UPDATE entities
SET nit = 'POR VERIFICAR',
    notes = notes || ' [NIT pendiente de verificar contra fuente oficial: el seed heredado reutilizaba el NIT de la DIAN.]'
WHERE entity_id IN ('supersalud-001', 'superfinanciera-001')
  AND nit = '800.197.268-4';
