-- ============================================================================
-- V7 — Logo del membrete de la firma
-- Fija branding.logoUrl al recurso del classpath donde la firma deja su logo.
-- El exportador (LogoResolver) lo incrusta en el encabezado del DOCX/PDF cuando
-- la plantilla NO tiene documento modelo propio. Si el recurso no existe, el
-- membrete se estampa solo con texto (comportamiento tolerante a fallos).
--
-- Colocar la imagen en: backend/src/main/resources/branding/logo.png
-- ============================================================================

UPDATE tenant
SET branding = jsonb_set(branding, '{logoUrl}', '"classpath:branding/logo.png"'::jsonb)
WHERE tenant_id = 'estufuturo-legal';
