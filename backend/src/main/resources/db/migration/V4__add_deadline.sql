-- V4 — Plazo legal de respuesta (deadline) para control de vencimientos.
-- El deadline se calcula al generar el documento según el tipo de plantilla:
-- petición general/pensional = 15 días hábiles, tutela = 10 días hábiles.

ALTER TABLE generated_document
    ADD COLUMN IF NOT EXISTS deadline TIMESTAMPTZ DEFAULT NULL;
