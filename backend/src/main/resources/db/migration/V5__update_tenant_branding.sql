-- ============================================================================
-- V5 — Identidad real de la firma (TUFUTURO — Abogados en Pensiones)
-- Actualiza el tenant sembrado en V2 (genérico "EstuFuturo S.A.S.") con los
-- datos reales de contacto y branding que aparecen en las minutas de la firma:
-- oficinas de Medellín y Pereira, correos, línea WhatsApp y sitio web.
--
-- Notas de mapeo:
--  - branding es JSONB y usa claves camelCase (el record Branding se serializa
--    con Jackson sin estrategia snake_case).
--  - headerText/footerText solo se usan en el modo de exportación "branded"
--    (sin .docx modelo). Cuando la plantilla tiene membrete propio, el pie real
--    viene del footer del .docx modelo, no de estos campos.
--  - tenant_id ('estufuturo-legal') es la PK y NO se modifica.
-- ============================================================================

UPDATE tenant
SET name = 'TUFUTURO — Abogados en Pensiones',
    branding = jsonb_build_object(
        'logoUrl',      '',
        'headerText',   'TUFUTURO — Abogados en Pensiones',
        'footerText',   'Cra. 46 N.º 52-36 Oficina 501 Edificio Vicente Uribe Rendón Tel. 514-11-87 Medellín | '
                        || 'Calle 20 N.º 6-30 Oficina 905 Edificio Banco Ganadero Tel. 333-33-01 Pereira | '
                        || 'www.estufuturo.com.co | Línea WhatsApp 310-665-81-93',
        'primaryColor', '#1a1a1a',
        'nit',          'POR VERIFICAR',
        'address',      'Cra. 46 N.º 52-36 Oficina 501, Edificio Vicente Uribe Rendón, Medellín',
        'phone',        '514-11-87',
        'email',        'gerencia@estufuturo.com.co',
        'website',      'www.estufuturo.com.co'
    )
WHERE tenant_id = 'estufuturo-legal';
