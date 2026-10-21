-- ============================================================================
-- V2 — Datos semilla (Fase 6)
-- Tenant por defecto, plantillas jurídicas base y entidades destinatarias.
-- Idempotente (ON CONFLICT DO NOTHING). Los usuarios NO se siembran aquí:
-- se crean en AuthService.ensureDefaultUsers() con hash BCrypt al primer arranque.
--
-- Notas de mapeo con el dominio Java:
--  - branding (JSONB) usa claves camelCase porque el record Branding se
--    serializa con Jackson sin estrategia snake_case.
--  - templates.fields es List<String> en el dominio: se siembran las KEYS de
--    los campos. Las etiquetas (label/required) viven en el frontend.
--  - legal_rules es array JSON de strings.
-- ============================================================================

-- --- Tenant (firma) por defecto ---
INSERT INTO tenant (tenant_id, name, system_prompt, legal_rules, branding, active)
VALUES (
    'estufuturo-legal',
    'EstuFuturo S.A.S.',
    'Eres un abogado colombiano senior experto en derecho administrativo, laboral, pensional y constitucional, que trabaja para la firma EstuFuturo S.A.S. Redacta documentos jurídicos formales con lenguaje técnico-jurídico preciso, respetuoso y contundente. Siempre cita las normas aplicables con su número, año y artículo específico. Estructura los documentos con encabezado institucional de EstuFuturo, hechos numerados, fundamentos de derecho y peticiones concretas. Representa con profesionalismo y diligencia los intereses de los clientes de EstuFuturo S.A.S.',
    '["Constitución Política de Colombia de 1991","Ley 1755 de 2015 — Derecho fundamental de petición","Ley 1437 de 2011 — CPACA","Ley 100 de 1993 — Sistema General de Seguridad Social"]'::jsonb,
    '{"logoUrl":"","headerText":"ESTUFUTURO S.A.S.","footerText":"NIT 901.234.567-8 | Bogotá D.C. | Tel: (601) 234-5678 | contacto@estufuturo.com.co","primaryColor":"#1a237e","nit":"901.234.567-8","address":"Bogotá D.C.","phone":"(601) 234-5678","email":"contacto@estufuturo.com.co","website":"www.estufuturo.com.co"}'::jsonb,
    TRUE
)
ON CONFLICT (tenant_id) DO NOTHING;

-- --- Plantillas jurídicas base ---

-- Derecho de Petición General
INSERT INTO templates (template_id, name, description, fields, system_prompt, legal_rules, rag_strict)
VALUES (
    'PETICION-GENERAL-V1',
    'Derecho de Petición General',
    'Solicitudes formales ante cualquier entidad pública o privada que preste servicios públicos.',
    '["cliente_nombre","cedula","direccion","ciudad","telefono","correo","entidad_demandada","cargo_destinatario","objeto_peticion","hechos_crudos"]'::jsonb,
    E'Eres un abogado colombiano senior con 20 años de experiencia en derecho administrativo. Redacta derechos de petición formales, claros y contundentes conforme a la Ley 1755 de 2015 y el artículo 23 de la Constitución Política. El documento debe tener la siguiente estructura obligatoria:\n1. Encabezado con ciudad, fecha y datos del destinatario\n2. Identificación completa del peticionario\n3. Objeto de la petición (claro y preciso en máximo 2 líneas)\n4. Hechos numerados y cronológicos que fundamentan la solicitud\n5. Peticiones concretas numeradas (use ''PRIMERO:'', ''SEGUNDO:'', etc.)\n6. Fundamentos de derecho citando artículos específicos\n7. Notificaciones\n8. Firma\nUsa lenguaje jurídico formal pero comprensible. Nunca uses lenguaje coloquial. Cita textualmente las normas cuando sea pertinente. El tono debe ser respetuoso pero firme. Incluye siempre el término legal de respuesta (15 días hábiles para peticiones generales, 10 para información).',
    '["Artículo 23 Constitución Política de Colombia — Derecho fundamental de petición","Ley 1755 de 2015 — Regula el derecho fundamental de petición","Artículo 14 Ley 1755/2015 — Términos para resolver (15 días hábiles)","Artículo 13 CPACA — Objeto y modalidades del derecho de petición","Artículo 19 Ley 1755/2015 — Peticiones entre autoridades"]'::jsonb,
    TRUE
)
ON CONFLICT (template_id) DO NOTHING;

-- Derecho de Petición — Pensional
INSERT INTO templates (template_id, name, description, fields, system_prompt, legal_rules, rag_strict)
VALUES (
    'PETICION-PENSIONAL-V1',
    'Derecho de Petición — Pensional',
    'Reconocimiento, reliquidación, sustitución pensional e historial laboral ante Colpensiones y fondos privados.',
    '["cliente_nombre","cedula","fecha_nacimiento","direccion","ciudad","telefono","correo","entidad_demandada","tipo_pension","regimen_pensional","semanas_cotizadas","empleadores","hechos_crudos"]'::jsonb,
    E'Eres un abogado colombiano senior con especialización en derecho pensional y seguridad social, con más de 15 años de experiencia en litigios contra Colpensiones y fondos privados de pensiones. Redacta derechos de petición pensionales con rigor técnico y jurídico. El documento debe:\n1. Identificar con precisión el tipo de prestación pensional solicitada (vejez, invalidez, sobrevivientes, reliquidación)\n2. Señalar el régimen pensional aplicable (RPM — Colpensiones o RAIS — fondo privado)\n3. Citar las semanas cotizadas, el IBL (Ingreso Base de Liquidación) y la edad del peticionario cuando sea relevante\n4. Invocar el principio de favorabilidad en materia pensional y la condición más beneficiosa\n5. Referenciar la jurisprudencia de la Corte Constitucional y Corte Suprema de Justicia aplicable\n6. Solicitar de forma subsidiaria el historial de semanas cotizadas y el extracto de cuenta individual\n7. Advertir sobre el derecho a la indexación de la primera mesada pensional\nEstructura obligatoria: encabezado, identificación del peticionario, hechos numerados y cronológicos, fundamentos de derecho, peticiones concretas numeradas, notificaciones. Lenguaje técnico-jurídico preciso.',
    '["Ley 100 de 1993 — Sistema General de Seguridad Social Integral","Acto Legislativo 01 de 2005 — Reforma pensional constitucional","Ley 797 de 2003 — Reforma al sistema general de pensiones","Decreto 758 de 1990 — Régimen de prima media con prestación definida (Acuerdo 049)","Artículo 48 Constitución Política — Derecho irrenunciable a la seguridad social","Sentencia C-258 de 2013 Corte Constitucional — Sostenibilidad financiera del sistema pensional","Ley 1755 de 2015 — Derecho de petición"]'::jsonb,
    TRUE
)
ON CONFLICT (template_id) DO NOTHING;

-- Acción de Tutela
INSERT INTO templates (template_id, name, description, fields, system_prompt, legal_rules, rag_strict)
VALUES (
    'ACCION-TUTELA-V1',
    'Acción de Tutela',
    'Mecanismo constitucional de protección inmediata de derechos fundamentales vulnerados o amenazados.',
    '["cliente_nombre","cedula","direccion","ciudad","telefono","correo","entidad_demandada","representante_accionado","derecho_vulnerado","solicitud_medida_provisional","mecanismos_previos","hechos_crudos"]'::jsonb,
    E'Eres un abogado constitucionalista colombiano senior con profundo conocimiento en acción de tutela y jurisprudencia de la Corte Constitucional. Redacta acciones de tutela técnicamente impecables que maximicen las posibilidades de éxito. El documento debe:\n1. Identificar con precisión el juez competente (juez del lugar de los hechos o del domicilio del accionante)\n2. Individualizar al accionante y al accionado con todos sus datos\n3. Identificar con claridad y precisión el derecho fundamental vulnerado o amenazado\n4. Narrar los hechos de forma cronológica, clara y contundente, destacando la urgencia y la inminencia del perjuicio\n5. Demostrar la vulneración o amenaza del derecho fundamental con argumentos jurídicos sólidos\n6. Invocar la jurisprudencia constitucional relevante de la Corte Constitucional (sentencias T- y SU-)\n7. Formular pretensiones concretas, claras y realizables por el juez\n8. Solicitar medida provisional si hay urgencia manifiesta\n9. Acreditar la subsidiariedad (inexistencia de otro mecanismo eficaz o perjuicio irremediable)\nEstructura: identificación del juez, datos del accionante, datos del accionado, derechos vulnerados, hechos numerados, fundamentos jurídicos, pretensiones, pruebas, juramento, firma. Tono: urgente, técnico, constitucional.',
    '["Artículo 86 Constitución Política de Colombia — Acción de tutela","Decreto 2591 de 1991 — Reglamentación de la acción de tutela","Artículo 10 Decreto 2591/1991 — Legitimidad e interés para actuar","Artículo 13 Decreto 2591/1991 — Personas contra quienes se puede dirigir","Artículo 7 Decreto 2591/1991 — Medidas provisionales para proteger el derecho","Artículo 6 Decreto 2591/1991 — Causales de improcedibilidad","Sentencia T-406 de 1992 Corte Constitucional — Eficacia directa de los derechos fundamentales"]'::jsonb,
    TRUE
)
ON CONFLICT (template_id) DO NOTHING;

-- --- Entidades destinatarias ---
INSERT INTO entities (entity_id, name, nit, address, city, phone, email, legal_rep, entity_type, notes) VALUES
    ('colpensiones-001', 'Colpensiones', '900.073.223-6', 'Carrera 7 No. 33-49', 'Bogotá D.C.', '(601) 307-7050', 'servicioalciudadano@colpensiones.gov.co', 'Gerente General', 'Colpensiones', 'Administradora del Régimen de Prima Media. Atención presencial con cita previa.'),
    ('eps-sanitas-001', 'EPS Sanitas', '800.251.440-2', 'Calle 163 No. 13B-60', 'Bogotá D.C.', '01 8000 912 666', 'servicioalcliente@epssanitas.com.co', 'Presidente EPS Sanitas', 'EPS / Entidad de Salud', 'Línea de atención nacional gratuita 01 8000 912 666.'),
    ('eps-sura-001', 'EPS Sura', '800.088.702-2', 'Carrera 43A No. 5A-113', 'Medellín', '01 8000 051 052', 'servicioalcliente@epssura.com', 'Presidente EPS Sura', 'EPS / Entidad de Salud', 'Línea nacional gratuita 01 8000 051 052.'),
    ('eps-nueva-eps-001', 'Nueva EPS', '900.156.264-2', 'Carrera 7 No. 76-35', 'Bogotá D.C.', '01 8000 910 010', 'servicioalcliente@nuevaeps.com.co', 'Presidente Nueva EPS', 'EPS / Entidad de Salud', 'Línea de atención nacional gratuita 01 8000 910 010.'),
    ('porvenir-001', 'Porvenir S.A. — Fondo de Pensiones', '830.002.397-7', 'Calle 26 No. 68B-70 Torre C', 'Bogotá D.C.', '(601) 744-7000', 'servicioalcliente@porvenir.com.co', 'Presidente Porvenir S.A.', 'Fondo de Pensiones / AFP', 'Fondo de pensiones del Grupo Aval. Régimen de Ahorro Individual con Solidaridad (RAIS).'),
    ('proteccion-001', 'Protección S.A. — Fondo de Pensiones', '800.128.656-8', 'Carrera 43A No. 18-114', 'Medellín', '01 8000 519 519', 'servicioalcliente@proteccion.com.co', 'Presidente Protección S.A.', 'Fondo de Pensiones / AFP', 'Fondo de pensiones del Grupo Sura. Régimen de Ahorro Individual con Solidaridad (RAIS).'),
    ('bancolombia-001', 'Bancolombia S.A.', '890.903.938-8', 'Carrera 48 No. 26-85', 'Medellín', '01 8000 912 345', 'servicioalcliente@bancolombia.com.co', 'Presidente Bancolombia S.A.', 'Banco / Entidad Financiera', 'Banco más grande de Colombia. Defensor del Cliente: defensordelcliente@bancolombia.com.co'),
    ('banco-bogota-001', 'Banco de Bogotá S.A.', '860.034.313-7', 'Calle 36 No. 7-47', 'Bogotá D.C.', '01 8000 912 211', 'servicioalcliente@bancodebogota.com.co', 'Presidente Banco de Bogotá', 'Banco / Entidad Financiera', 'Defensor del Cliente: defensordelcliente@bancodebogota.com.co'),
    ('dian-001', 'DIAN — Dirección de Impuestos y Aduanas Nacionales', '800.197.268-4', 'Carrera 8 No. 6C-38', 'Bogotá D.C.', '601 307-1111', 'webmaster@dian.gov.co', 'Director General DIAN', 'DIAN', 'Entidad adscrita al Ministerio de Hacienda. Atención virtual en www.dian.gov.co'),
    ('min-salud-001', 'Ministerio de Salud y Protección Social', '899.999.034-1', 'Carrera 13 No. 32-76', 'Bogotá D.C.', '(601) 330-5000', 'atencionciudadano@minsalud.gov.co', 'Ministro de Salud y Protección Social', 'Ministerio', 'Ente rector del Sistema General de Seguridad Social en Salud.'),
    ('min-trabajo-001', 'Ministerio del Trabajo', '899.999.026-4', 'Carrera 14 No. 99-33', 'Bogotá D.C.', '(601) 489-3900', 'mintrabajo@mintrabajo.gov.co', 'Ministro del Trabajo', 'Ministerio', 'Inspecciones laborales y conciliaciones en las Direcciones Territoriales.'),
    ('alcaldia-bogota-001', 'Alcaldía Mayor de Bogotá D.C.', '899.999.061-9', 'Carrera 8 No. 10-65', 'Bogotá D.C.', '195', 'alcaldia@bogota.gov.co', 'Alcalde Mayor de Bogotá', 'Alcaldía / Gobernación', 'Línea de atención ciudadana 195. Peticiones en www.bogota.gov.co'),
    ('personeria-bogota-001', 'Personería de Bogotá D.C.', '899.999.091-0', 'Carrera 7 No. 21-44', 'Bogotá D.C.', '(601) 382-0510', 'personeria@personeriabogota.gov.co', 'Personero de Bogotá', 'Personería / Defensoría del Pueblo', 'Recibe quejas disciplinarias contra servidores públicos del Distrito.'),
    ('defensoria-pueblo-001', 'Defensoría del Pueblo', '800.141.468-2', 'Calle 55 No. 10-32', 'Bogotá D.C.', '01 8000 914 814', 'defensoria@defensoria.gov.co', 'Defensor del Pueblo', 'Personería / Defensoría del Pueblo', 'Línea gratuita 01 8000 914 814. Protección de derechos humanos y fundamentales.'),
    ('arl-sura-001', 'ARL Sura', '800.088.702-2', 'Carrera 43A No. 5A-113', 'Medellín', '01 8000 051 052', 'arl@sura.com.co', 'Presidente ARL Sura', 'ARL / Riesgos Laborales', 'Administradora de Riesgos Laborales del Grupo Sura.'),
    ('compensar-001', 'Compensar — Caja de Compensación Familiar', '860.007.386-4', 'Avenida 68 No. 49A-51', 'Bogotá D.C.', '(601) 424-4040', 'servicioalcliente@compensar.com', 'Director General Compensar', 'Caja de Compensación Familiar', 'Caja de compensación familiar de Bogotá. Subsidio familiar, salud, vivienda y recreación.'),
    ('superfinanciera-001', 'Superintendencia Financiera de Colombia', '800.197.268-4', 'Carrera 7 No. 4-49', 'Bogotá D.C.', '01 8000 120 100', 'consumeratencion@superfinanciera.gov.co', 'Superintendente Financiero', 'Superintendencia', 'Ente de control del sistema financiero. Recibe quejas contra bancos, EPS y aseguradoras.'),
    ('supersalud-001', 'Superintendencia Nacional de Salud', '800.197.268-4', 'Carrera 13 No. 32-76 Piso 16', 'Bogotá D.C.', '01 8000 513 700', 'supersal@supersalud.gov.co', 'Superintendente Nacional de Salud', 'Superintendencia', 'Inspección, vigilancia y control del Sistema General de Seguridad Social en Salud.'),
    ('epm-001', 'EPM — Empresas Públicas de Medellín', '890.904.996-1', 'Carrera 58 No. 42-125', 'Medellín', '604 444-1414', 'servicioalcliente@epm.com.co', 'Gerente General EPM', 'Empresa de Servicios Públicos', 'Presta servicios de energía, agua, gas y telecomunicaciones.'),
    ('eaab-001', 'EAAB — Empresa de Acueducto de Bogotá', '899.999.094-5', 'Calle 24 No. 37-15', 'Bogotá D.C.', '601 344-4000', 'servicioalcliente@acueducto.com.co', 'Gerente General EAAB', 'Empresa de Servicios Públicos', 'Presta servicios de acueducto y alcantarillado en Bogotá.')
ON CONFLICT (entity_id) DO NOTHING;
