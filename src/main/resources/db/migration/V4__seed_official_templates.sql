-- =====================================================================
-- CanteraGT — V4: Semilla de la biblioteca oficial (10 plantillas base)
-- Fuente: plantillas-base-canteragt.md (versión 1 de cada plantilla).
--
-- Valores marcados con  [PENDIENTE D-xx]  no están definidos en la ficha;
-- quedan NULL o provisionales y se resuelven en la hoja de ruta
-- (sección "Decisiones de producto pendientes").
-- =====================================================================

-- Habilita la escritura sobre plantillas oficiales solo en esta transacción
SET LOCAL canteragt.allow_template_admin = 'on';

-- ---------------------------------------------------------------------
-- Tabla de conversión estándar (% de éxito -> score)
-- ---------------------------------------------------------------------
INSERT INTO score_conversion_tables (code, name, is_default)
VALUES ('STANDARD_V1', 'Tabla de conversión estándar', true);

INSERT INTO score_conversion_bands (conversion_table_id, min_pct, score)
SELECT t.conversion_table_id, b.min_pct, b.score
FROM score_conversion_tables t
CROSS JOIN (VALUES (0, 40), (40, 55), (60, 70), (75, 85), (90, 100)) AS b(min_pct, score)
WHERE t.code = 'STANDARD_V1';

-- ---------------------------------------------------------------------
-- Plantillas
-- ---------------------------------------------------------------------
INSERT INTO evaluation_templates
  (code, name, version, status, description, objective, instructions,
   is_session_integrated, group_size_min, group_size_max,
   frequency_min_days, frequency_max_days, validity_days,
   score_aggregation, is_official,
   created_at, updated_at)
VALUES
  ('SPRINT_10M', 'Sprint corto 10m', 1, 'ACTIVE',
   'Prueba física de aceleración inicial. Aplica a todas las categorías.',
   'Medir aceleración inicial',
   'Cronometraje de 10 metros. Se registran 2 intentos y cuenta el mejor.',
   false, 6, 8, 30, 30, 45, 'WEIGHTED_AVERAGE', true, now(), now()),

  ('CIRCUITO_AGILIDAD', 'Circuito de agilidad', 1, 'ACTIVE',
   'Circuito con conos; penaliza conos derribados.',
   'Medir agilidad y coordinación',
   'Registrar tiempo total y número de conos derribados. El sistema calcula el tiempo ajustado.',
   false, NULL, NULL, 30, 30, 45, 'WEIGHTED_AVERAGE', true, now(), now()),

  ('CONDUCCION_CAMBIOS_DIRECCION', 'Conducción con cambios de dirección', 1, 'ACTIVE',
   'Circuito de conducción con cambios de dirección.',
   'Medir dominio del balón en movimiento y coordinación',
   'Registrar tiempo y número de toques fuera de control, y asignar nivel de rúbrica de cambio de dirección.',
   false, NULL, NULL, 30, 30, 45, 'WEIGHTED_AVERAGE', true, now(), now()),

  ('PASE_RECEPCION', 'Pase y recepción', 1, 'ACTIVE',
   'Precisión de pase y control orientado.',
   'Medir precisión de pase y control orientado',
   'Contar pases completados sobre oportunidades. Mínimo 8 oportunidades.',
   false, NULL, NULL, 21, 30, 45, 'WEIGHTED_AVERAGE', true, now(), now()),

  ('FINALIZACION', 'Finalización', 1, 'ACTIVE',
   'Técnica de golpeo y definición.',
   'Medir técnica de golpeo y definición',
   'Registrar aciertos sobre intentos y nivel de rúbrica de calidad de ejecución.',
   false, NULL, NULL, 21, 30, 45, 'WEIGHTED_AVERAGE', true, now(), now()),

  ('RONDO', 'Rondo (pase bajo presión)', 1, 'ACTIVE',
   'Rondo de referencia 4v2.',
   'Medir perfil corporal, escaneo previo y pase bajo presión',
   'Contar pases exitosos bajo presión (mínimo 6 oportunidades) y asignar nivel de rúbrica de perfil corporal/escaneo.',
   false, NULL, NULL, 30, 30, 45, 'WEIGHTED_AVERAGE', true, now(), now()),

  ('JUEGO_REDUCIDO', 'Juego reducido 3v3 / 4v4', 1, 'ACTIVE',
   'Contexto real de juego. El DT elige hasta 3 de las 6 métricas por evento.',
   'Medir decisión, desmarque, transición, comunicación, ejecución técnica y competitividad en contexto real de juego',
   'Rúbrica estructurada por acción observada. Cada métrica se reporta por nivel, sin promedio libre.',
   false, NULL, NULL, 30, 30, 45, 'NONE', true, now(), now()),

  ('DUELO_1V1', 'Duelo 1v1', 1, 'ACTIVE',
   'Defensa individual, regate y reacción.',
   'Medir defensa individual, regate y capacidad de reacción',
   'Contar duelos ganados sobre total (mínimo 6 oportunidades) y asignar nivel de rúbrica de lectura del duelo en cada duelo.',
   false, NULL, NULL, 30, 30, 45, 'WEIGHTED_AVERAGE', true, now(), now()),

  ('REACCION_TRAS_PERDIDA', 'Reacción tras pérdida', 1, 'ACTIVE',
   'Observación integrada en juego reducido o partido de entrenamiento.',
   'Medir repliegue, intensidad y resiliencia inmediata tras perder el balón',
   'Registrar nivel de rúbrica en cada acción observada. Mínimo 5 observaciones.',
   true, NULL, NULL, 30, 30, 45, 'NONE', true, now(), now()),

  ('OBSERVACION_CONVIVENCIA', 'Observación de convivencia', 1, 'ACTIVE',
   'Observación durante la sesión completa. Comentario obligatorio. El DT elige hasta 3 de las 4 métricas por evento.',
   'Medir respeto, comunicación, colaboración y autocontrol en la convivencia con compañeros',
   'Asignar nivel en la escala anclada 1-5 y escribir evidencia breve.',
   true, NULL, NULL, 30, 30, 45, 'NONE', true, now(), now());

-- ---------------------------------------------------------------------
-- Dimensiones por plantilla (Four Corner Model)
-- ---------------------------------------------------------------------
INSERT INTO template_dimensions (template_id, dimension_code, is_primary)
SELECT t.template_id, d.dimension_code, d.is_primary
FROM evaluation_templates t
JOIN (VALUES
  ('SPRINT_10M',                   'PHYSICAL',      true),
  ('CIRCUITO_AGILIDAD',            'PHYSICAL',      true),
  ('CONDUCCION_CAMBIOS_DIRECCION', 'TECHNICAL',     true),
  ('CONDUCCION_CAMBIOS_DIRECCION', 'PHYSICAL',      false),
  ('PASE_RECEPCION',               'TECHNICAL',     true),
  ('FINALIZACION',                 'TECHNICAL',     true),
  ('RONDO',                        'TECHNICAL',     true),
  ('RONDO',                        'TACTICAL',      false),
  ('JUEGO_REDUCIDO',               'TACTICAL',      true),
  ('JUEGO_REDUCIDO',               'TECHNICAL',     false),
  ('JUEGO_REDUCIDO',               'PSYCHOLOGICAL', false),
  ('JUEGO_REDUCIDO',               'SOCIAL',        false),
  ('DUELO_1V1',                    'TECHNICAL',     true),
  ('DUELO_1V1',                    'TACTICAL',      false),
  ('REACCION_TRAS_PERDIDA',        'TACTICAL',      true),
  ('REACCION_TRAS_PERDIDA',        'PSYCHOLOGICAL', false),
  ('OBSERVACION_CONVIVENCIA',      'SOCIAL',        true),
  ('OBSERVACION_CONVIVENCIA',      'PSYCHOLOGICAL', false)
) AS d(template_code, dimension_code, is_primary) ON d.template_code = t.code
WHERE t.version = 1 AND t.owner_academy_id IS NULL;

-- ---------------------------------------------------------------------
-- Métricas
-- ---------------------------------------------------------------------
INSERT INTO evaluation_criteria
  (template_id, dimension_code, code, name, description, weight, min_score, max_score, display_order,
   metric_type, unit, better_direction, attempts_count, attempt_aggregation, min_opportunities,
   scale_min, scale_max, comment_required, is_active_by_default, is_scored, laterality_mode,
   scoring_method, conversion_table_id, scoring_config, created_at, updated_at)
SELECT t.template_id, m.dimension_code, m.code, m.name, m.description, m.weight, 0, 100, m.display_order,
       m.metric_type, m.unit, m.better_direction, m.attempts_count, m.attempt_aggregation, m.min_opportunities,
       m.scale_min, m.scale_max, m.comment_required, m.is_active_by_default, m.is_scored, m.laterality_mode,
       m.scoring_method,
       CASE WHEN m.scoring_method = 'CONVERSION_TABLE' THEN ct.conversion_table_id END,
       m.scoring_config::jsonb, now(), now()
FROM (VALUES
  -- template_code, dimension, code, name, description, weight, order,
  -- metric_type, unit, better, attempts, aggregation, min_opp, scale_min, scale_max,
  -- comment_req, is_active_by_default, is_scored, laterality, scoring_method, scoring_config
  ('SPRINT_10M', 'PHYSICAL', 'TIEMPO_10M', 'Tiempo 10m',
   'Mejor tiempo de 2 intentos (segundos)', 1.000, 1,
   'TIME', 'SECONDS', 'LOWER', 2, 'BEST', NULL, NULL, NULL,
   false, true, true, 'NONE', 'AGE_THRESHOLD', '{}'),

  ('CIRCUITO_AGILIDAD', 'PHYSICAL', 'TIEMPO_AJUSTADO', 'Tiempo total',
   'Tiempo total del circuito; se suma la penalización por conos derribados', 1.000, 1,
   'TIME', 'SECONDS', 'LOWER', 1, 'SINGLE', NULL, NULL, NULL,
   false, true, true, 'NONE', 'TIME_WITH_PENALTY',
   '{"penalty_metric_code": "CONOS_DERRIBADOS", "penalty_seconds_per_error": null, "_pending": "D-03"}'),
  ('CIRCUITO_AGILIDAD', 'PHYSICAL', 'CONOS_DERRIBADOS', 'Conos derribados',
   'Número de errores (componente de penalización)', 0.000, 2,
   'ERROR_COUNT', 'COUNT', 'LOWER', 1, 'SINGLE', NULL, NULL, NULL,
   false, true, false, 'NONE', 'NONE', '{}'),

  ('CONDUCCION_CAMBIOS_DIRECCION', 'TECHNICAL', 'TIEMPO_CONDUCCION', 'Tiempo de conducción',
   'Tiempo bajo umbral con máximo de errores permitido', 1.000, 1,
   'TIME', 'SECONDS', 'LOWER', 1, 'SINGLE', NULL, NULL, NULL,
   false, true, true, 'NONE', 'TIME_WITH_MAX_ERRORS',
   '{"error_metric_code": "ERRORES_CONTROL", "max_errors": null, "_pending": "D-03"}'),
  ('CONDUCCION_CAMBIOS_DIRECCION', 'TECHNICAL', 'ERRORES_CONTROL', 'Toques fuera de control',
   'Número de toques fuera de control (componente)', 0.000, 2,
   'ERROR_COUNT', 'COUNT', 'LOWER', 1, 'SINGLE', NULL, NULL, NULL,
   false, true, false, 'NONE', 'NONE', '{}'),
  ('CONDUCCION_CAMBIOS_DIRECCION', 'PHYSICAL', 'CAMBIO_DIRECCION', 'Cambio de dirección',
   'Capacidad para frenar, apoyar y reacelerar con control corporal en cada cambio de dirección del circuito.', 1.000, 3,
   'RUBRIC', 'LEVEL', 'HIGHER', NULL, 'SINGLE', NULL, 1, 3,
   false, true, true, 'NONE', 'RUBRIC_LEVEL', '{}'),

  ('PASE_RECEPCION', 'TECHNICAL', 'PASES_COMPLETADOS', 'Pases completados',
   'Aciertos sobre oportunidades (mínimo 8)', 1.000, 1,
   'SUCCESS_RATIO', 'PERCENT', 'HIGHER', NULL, 'SINGLE', 8, NULL, NULL,
   false, true, true, 'OPTIONAL', 'CONVERSION_TABLE', '{}'),

  ('FINALIZACION', 'TECHNICAL', 'ACIERTOS_FINALIZACION', 'Aciertos de finalización',
   'Aciertos sobre intentos', 1.000, 1,
   'SUCCESS_RATIO', 'PERCENT', 'HIGHER', NULL, 'SINGLE', 6, NULL, NULL,
   false, true, true, 'OPTIONAL', 'CONVERSION_TABLE', '{"_pending": "D-05 mínimo de intentos"}'),
  ('FINALIZACION', 'TECHNICAL', 'CALIDAD_EJECUCION', 'Calidad de ejecución',
   'Rúbrica de técnica de golpeo', 1.000, 2,
   'RUBRIC', 'LEVEL', 'HIGHER', NULL, 'SINGLE', NULL, 1, 3,
   false, true, true, 'NONE', 'RUBRIC_LEVEL', '{}'),

  ('RONDO', 'TECHNICAL', 'PASE_BAJO_PRESION', 'Pase bajo presión',
   '% de éxito sobre oportunidades (mínimo 6)', 1.000, 1,
   'SUCCESS_RATIO', 'PERCENT', 'HIGHER', NULL, 'SINGLE', 6, NULL, NULL,
   false, true, true, 'NONE', 'CONVERSION_TABLE', '{"_pending": "D-06 ficha JSON rondo 4v2"}'),
  ('RONDO', 'TACTICAL', 'PERFIL_CORPORAL_ESCANEO', 'Perfil corporal / escaneo',
   'Rúbrica de 3 niveles', 1.000, 2,
   'RUBRIC', 'LEVEL', 'HIGHER', NULL, 'SINGLE', NULL, 1, 3,
   false, true, true, 'NONE', 'RUBRIC_LEVEL', '{}'),

  -- Catálogo de 6 métricas; el DT activa hasta 3 por evento (session_event_metrics)
  ('JUEGO_REDUCIDO', 'TACTICAL', 'DECISION_BAJO_PRESION', 'Decisión bajo presión',
   'Rúbrica por acción observada', 1.000, 1,
   'RUBRIC', 'LEVEL', 'HIGHER', NULL, 'MEDIAN', NULL, 1, 3,
   false, true, true, 'NONE', 'RUBRIC_LEVEL', '{}'),
  ('JUEGO_REDUCIDO', 'TACTICAL', 'DESMARQUE', 'Desmarque',
   'Rúbrica por acción observada', 1.000, 2,
   'RUBRIC', 'LEVEL', 'HIGHER', NULL, 'MEDIAN', NULL, 1, 3,
   false, true, true, 'NONE', 'RUBRIC_LEVEL', '{}'),
  ('JUEGO_REDUCIDO', 'TACTICAL', 'TRANSICION', 'Transición',
   'Rúbrica por acción observada', 1.000, 3,
   'RUBRIC', 'LEVEL', 'HIGHER', NULL, 'MEDIAN', NULL, 1, 3,
   false, false, true, 'NONE', 'RUBRIC_LEVEL', '{}'),
  ('JUEGO_REDUCIDO', 'SOCIAL', 'COMUNICACION', 'Comunicación',
   'Rúbrica por acción observada', 1.000, 4,
   'RUBRIC', 'LEVEL', 'HIGHER', NULL, 'MEDIAN', NULL, 1, 3,
   false, true, true, 'NONE', 'RUBRIC_LEVEL', '{}'),
  ('JUEGO_REDUCIDO', 'TECHNICAL', 'EJECUCION_TECNICA', 'Ejecución técnica',
   'Calidad del control, la conducción y el pase ejecutados en situación real de juego y bajo presión rival.', 1.000, 5,
   'RUBRIC', 'LEVEL', 'HIGHER', NULL, 'MEDIAN', NULL, 1, 3,
   false, false, true, 'NONE', 'RUBRIC_LEVEL', '{}'),
  ('JUEGO_REDUCIDO', 'PSYCHOLOGICAL', 'COMPETITIVIDAD', 'Competitividad',
   'Determinación para disputar cada balón y mantener el esfuerzo y la concentración durante todo el juego, independientemente del marcador.', 1.000, 6,
   'RUBRIC', 'LEVEL', 'HIGHER', NULL, 'MEDIAN', NULL, 1, 3,
   false, false, true, 'NONE', 'RUBRIC_LEVEL', '{}'),

  ('DUELO_1V1', 'TECHNICAL', 'DUELOS_GANADOS', 'Duelos ganados',
   'Acciones exitosas sobre oportunidades (mínimo 6)', 1.000, 1,
   'SUCCESS_RATIO', 'PERCENT', 'HIGHER', NULL, 'SINGLE', 6, NULL, NULL,
   false, true, true, 'NONE', 'CONVERSION_TABLE', '{}'),
  ('DUELO_1V1', 'TACTICAL', 'LECTURA_DUELO', 'Lectura del duelo',
   'Elección del perfil, la distancia y el momento de actuar: cuándo temporizar o entrar al defender, y por dónde y cuándo encarar al atacar.', 1.000, 2,
   'RUBRIC', 'LEVEL', 'HIGHER', NULL, 'MEDIAN', 6, 1, 3,
   false, true, true, 'NONE', 'RUBRIC_LEVEL', '{}'),

  -- Rúbricas 1-3 por acción observada (mínimo 5 observaciones)
  ('REACCION_TRAS_PERDIDA', 'TACTICAL', 'REPLIEGUE', 'Repliegue',
   'Capacidad para recuperar rápidamente una posición funcional y proteger espacios después de perder la posesión.', 1.000, 1,
   'RUBRIC', 'LEVEL', 'HIGHER', NULL, 'MEDIAN', 5, 1, 3,
   false, true, true, 'NONE', 'RUBRIC_LEVEL', '{}'),
  ('REACCION_TRAS_PERDIDA', 'TACTICAL', 'INTENSIDAD', 'Intensidad',
   'Calidad y determinación de la respuesta inmediata después de perder la posesión, mediante presión, recuperación o retraso del rival según la situación.', 1.000, 2,
   'RUBRIC', 'LEVEL', 'HIGHER', NULL, 'MEDIAN', 5, 1, 3,
   false, true, true, 'NONE', 'RUBRIC_LEVEL', '{}'),
  ('REACCION_TRAS_PERDIDA', 'PSYCHOLOGICAL', 'RESILIENCIA', 'Resiliencia',
   'Capacidad para recuperar rápidamente una conducta funcional después de una pérdida, error o situación adversa y volver a participar en la acción.', 1.000, 3,
   'RUBRIC', 'LEVEL', 'HIGHER', NULL, 'MEDIAN', 5, 1, 3,
   false, true, true, 'NONE', 'RUBRIC_LEVEL', '{}'),

  -- Escalas ancladas 1-5 con evidencia breve obligatoria (mínimo 5 observaciones).
  -- Catálogo de 4 métricas; el DT activa hasta 3 por evento.
  ('OBSERVACION_CONVIVENCIA', 'SOCIAL', 'RESPETO', 'Respeto',
   'Conducta respetuosa hacia compañeros, entrenadores, rivales, árbitros, normas y material durante la actividad.', 1.000, 1,
   'ANCHORED_SCALE', 'LEVEL', 'HIGHER', NULL, 'MEDIAN', 5, 1, 5,
   true, true, true, 'NONE', 'ANCHORED_SCALE', '{}'),
  ('OBSERVACION_CONVIVENCIA', 'SOCIAL', 'COMUNICACION', 'Comunicación',
   'Capacidad para comunicar información pertinente de forma clara, oportuna y constructiva durante la actividad.', 1.000, 2,
   'ANCHORED_SCALE', 'LEVEL', 'HIGHER', NULL, 'MEDIAN', 5, 1, 5,
   true, true, true, 'NONE', 'ANCHORED_SCALE', '{}'),
  ('OBSERVACION_CONVIVENCIA', 'SOCIAL', 'COLABORACION', 'Colaboración',
   'Disposición para cooperar con compañeros y entrenador, ayudar al grupo y contribuir al objetivo colectivo.', 1.000, 3,
   'ANCHORED_SCALE', 'LEVEL', 'HIGHER', NULL, 'MEDIAN', 5, 1, 5,
   true, true, true, 'NONE', 'ANCHORED_SCALE', '{}'),
  ('OBSERVACION_CONVIVENCIA', 'PSYCHOLOGICAL', 'AUTOCONTROL', 'Autocontrol',
   'Capacidad para regular sus emociones y su conducta ante errores, frustración, decisiones arbitrales o conflictos durante la actividad.', 1.000, 4,
   'ANCHORED_SCALE', 'LEVEL', 'HIGHER', NULL, 'MEDIAN', 5, 1, 5,
   true, false, true, 'NONE', 'ANCHORED_SCALE', '{}')
) AS m(template_code, dimension_code, code, name, description, weight, display_order,
       metric_type, unit, better_direction, attempts_count, attempt_aggregation, min_opportunities,
       scale_min, scale_max, comment_required, is_active_by_default, is_scored, laterality_mode, scoring_method, scoring_config)
JOIN evaluation_templates t
  ON t.code = m.template_code AND t.version = 1 AND t.owner_academy_id IS NULL
CROSS JOIN (SELECT conversion_table_id FROM score_conversion_tables WHERE code = 'STANDARD_V1') ct;

-- ---------------------------------------------------------------------
-- Niveles de rúbrica y escala anclada
-- Etiquetas PROVISIONALES y score por nivel = [PENDIENTE D-04].
-- Descriptores definidos para REACCION_TRAS_PERDIDA, OBSERVACION_CONVIVENCIA
-- y las métricas que completan las dimensiones declaradas (CAMBIO_DIRECCION,
-- EJECUCION_TECNICA, COMPETITIVIDAD, LECTURA_DUELO); el resto queda NULL =
-- [PENDIENTE D-04].
-- ---------------------------------------------------------------------
INSERT INTO metric_scale_levels (criterion_id, level, label, descriptor)
SELECT c.criterion_id, l.level, l.label, d.descriptor
FROM evaluation_criteria c
JOIN evaluation_templates t ON t.template_id = c.template_id
JOIN (VALUES
  ('RUBRIC', 1, 'En desarrollo'), ('RUBRIC', 2, 'Competente'), ('RUBRIC', 3, 'Destacado'),
  ('ANCHORED_SCALE', 1, 'Nivel 1'), ('ANCHORED_SCALE', 2, 'Nivel 2'), ('ANCHORED_SCALE', 3, 'Nivel 3'),
  ('ANCHORED_SCALE', 4, 'Nivel 4'), ('ANCHORED_SCALE', 5, 'Nivel 5')
) AS l(metric_type, level, label) ON l.metric_type = c.metric_type
LEFT JOIN (VALUES
  ('CONDUCCION_CAMBIOS_DIRECCION', 'CAMBIO_DIRECCION', 1,
   'Frena tarde o sin control, pierde el equilibrio o necesita pasos extra en los cambios de dirección y tarda en reacelerar.'),
  ('CONDUCCION_CAMBIOS_DIRECCION', 'CAMBIO_DIRECCION', 2,
   'Realiza los cambios de dirección con control, aunque desacelera de más o reacelera con poca potencia en algunos de ellos.'),
  ('CONDUCCION_CAMBIOS_DIRECCION', 'CAMBIO_DIRECCION', 3,
   'Frena con apoyos cortos y equilibrados, cambia de dirección con el cuerpo orientado y reacelera de forma explosiva en todos los cambios.'),

  ('JUEGO_REDUCIDO', 'EJECUCION_TECNICA', 1,
   'Sus controles y pases bajo presión suelen ser imprecisos: pierde el balón, lo deja lejos o necesita varios toques para jugar.'),
  ('JUEGO_REDUCIDO', 'EJECUCION_TECNICA', 2,
   'Ejecuta controles y pases correctos en situaciones sencillas, pero pierde precisión cuando aumenta la presión o la velocidad del juego.'),
  ('JUEGO_REDUCIDO', 'EJECUCION_TECNICA', 3,
   'Controla, conduce y pasa con precisión y buena orientación incluso bajo presión, dando continuidad al juego.'),

  ('JUEGO_REDUCIDO', 'COMPETITIVIDAD', 1,
   'Participa de forma intermitente: evita disputas, reduce el esfuerzo cuando el juego no le favorece o se desconecta de la actividad.'),
  ('JUEGO_REDUCIDO', 'COMPETITIVIDAD', 2,
   'Compite con esfuerzo en la mayoría de las acciones, aunque baja su intensidad o concentración en algunos momentos del juego.'),
  ('JUEGO_REDUCIDO', 'COMPETITIVIDAD', 3,
   'Disputa cada balón con determinación y mantiene esfuerzo y concentración constantes durante todo el juego, incluso cuando va perdiendo.'),

  ('DUELO_1V1', 'LECTURA_DUELO', 1,
   'Actúa sin leer la situación: al defender entra precipitadamente o se queda lejos, y al atacar encara sin elegir lado ni momento.'),
  ('DUELO_1V1', 'LECTURA_DUELO', 2,
   'Elige perfil y distancia adecuados en situaciones sencillas, aunque a veces se anticipa o duda en el momento de entrar o de encarar.'),
  ('DUELO_1V1', 'LECTURA_DUELO', 3,
   'Lee al rival y actúa en el momento oportuno: temporiza y entra cuando el atacante se expone, o encara por el lado débil con cambio de ritmo.'),

  ('REACCION_TRAS_PERDIDA', 'REPLIEGUE', 1,
   'No realiza una recuperación funcional después de perder el balón. Se queda desconectado de la jugada, recupera tarde o deja desprotegida su zona de responsabilidad.'),
  ('REACCION_TRAS_PERDIDA', 'REPLIEGUE', 2,
   'Reacciona y recupera posición cuando la situación lo requiere, aunque puede presentar retrasos o inconsistencias.'),
  ('REACCION_TRAS_PERDIDA', 'REPLIEGUE', 3,
   'Identifica rápidamente la necesidad de recuperar, realiza un repliegue eficaz y contribuye a cerrar espacios o proteger zonas peligrosas.'),

  ('REACCION_TRAS_PERDIDA', 'INTENSIDAD', 1,
   'La reacción es lenta, pasiva o sin intención clara de intervenir en la transición.'),
  ('REACCION_TRAS_PERDIDA', 'INTENSIDAD', 2,
   'Reacciona con esfuerzo suficiente para presionar, retrasar al rival o recuperar posición, aunque no mantiene la intensidad de manera consistente.'),
  ('REACCION_TRAS_PERDIDA', 'INTENSIDAD', 3,
   'Reacciona inmediatamente y con determinación, aplicando presión, realizando recuperación o retrasando la progresión rival según la situación.'),

  ('REACCION_TRAS_PERDIDA', 'RESILIENCIA', 1,
   'Después de perder el balón, cometer un error o sufrir una acción adversa, permanece desconectado o disminuye notablemente su participación en las acciones posteriores.'),
  ('REACCION_TRAS_PERDIDA', 'RESILIENCIA', 2,
   'Recupera progresivamente su participación y vuelve a intervenir de manera funcional después de una situación adversa.'),
  ('REACCION_TRAS_PERDIDA', 'RESILIENCIA', 3,
   'Se recupera rápidamente después del error o pérdida, vuelve a participar activamente y mantiene una conducta funcional en la siguiente acción.'),

  ('OBSERVACION_CONVIVENCIA', 'RESPETO', 1,
   'Presenta conductas repetidas de falta de respeto hacia compañeros, entrenadores, árbitros, rivales o material.'),
  ('OBSERVACION_CONVIVENCIA', 'RESPETO', 2,
   'Generalmente respeta las normas, pero presenta episodios de conducta inapropiada ante frustración, correcciones o desacuerdos.'),
  ('OBSERVACION_CONVIVENCIA', 'RESPETO', 3,
   'Mantiene una conducta respetuosa y cumple las normas básicas de convivencia.'),
  ('OBSERVACION_CONVIVENCIA', 'RESPETO', 4,
   'Mantiene consistentemente una conducta respetuosa, incluso ante errores, correcciones o situaciones de frustración.'),
  ('OBSERVACION_CONVIVENCIA', 'RESPETO', 5,
   'Demuestra respeto constante y contribuye activamente a un ambiente positivo, inclusivo y deportivo.'),

  ('OBSERVACION_CONVIVENCIA', 'COMUNICACION', 1,
   'No comunica información necesaria o utiliza formas de comunicación que perjudican la actividad o al grupo.'),
  ('OBSERVACION_CONVIVENCIA', 'COMUNICACION', 2,
   'Comunica ocasionalmente, pero de manera tardía, poco clara o poco útil.'),
  ('OBSERVACION_CONVIVENCIA', 'COMUNICACION', 3,
   'Comunica información pertinente de forma comprensible cuando la situación lo requiere.'),
  ('OBSERVACION_CONVIVENCIA', 'COMUNICACION', 4,
   'Comunica de forma frecuente, clara y funcional para ayudar a compañeros y organizar situaciones de juego o entrenamiento.'),
  ('OBSERVACION_CONVIVENCIA', 'COMUNICACION', 5,
   'Utiliza comunicación clara, oportuna y constructiva que mejora la coordinación y ayuda al grupo.'),

  ('OBSERVACION_CONVIVENCIA', 'COLABORACION', 1,
   'Prioriza sistemáticamente su actuación individual y muestra poca disposición a ayudar al grupo.'),
  ('OBSERVACION_CONVIVENCIA', 'COLABORACION', 2,
   'Colabora en algunas situaciones, pero su participación colectiva es irregular.'),
  ('OBSERVACION_CONVIVENCIA', 'COLABORACION', 3,
   'Coopera con compañeros y entrenador y cumple las responsabilidades necesarias dentro de la actividad.'),
  ('OBSERVACION_CONVIVENCIA', 'COLABORACION', 4,
   'Busca activamente ayudar a compañeros, facilitar el trabajo colectivo y cumplir las necesidades del equipo.'),
  ('OBSERVACION_CONVIVENCIA', 'COLABORACION', 5,
   'Favorece constantemente el trabajo colectivo, ayuda a compañeros y contribuye positivamente al funcionamiento del grupo.'),

  ('OBSERVACION_CONVIVENCIA', 'AUTOCONTROL', 1,
   'Pierde el control con frecuencia: reacciona con enfado, protestas o abandono ante errores, decisiones o conflictos.'),
  ('OBSERVACION_CONVIVENCIA', 'AUTOCONTROL', 2,
   'Muestra reacciones emocionales intensas ante la frustración, aunque logra calmarse con ayuda del entrenador o de compañeros.'),
  ('OBSERVACION_CONVIVENCIA', 'AUTOCONTROL', 3,
   'Generalmente controla sus emociones; ocasionalmente muestra frustración, pero se recupera por sí mismo sin afectar la actividad.'),
  ('OBSERVACION_CONVIVENCIA', 'AUTOCONTROL', 4,
   'Mantiene la calma de forma consistente ante errores, decisiones adversas o conflictos y responde de manera adecuada.'),
  ('OBSERVACION_CONVIVENCIA', 'AUTOCONTROL', 5,
   'Regula sus emociones incluso en situaciones de alta presión y ayuda a calmar a compañeros en momentos de tensión.')
) AS d(template_code, criterion_code, level, descriptor)
  ON d.template_code = t.code AND d.criterion_code = c.code AND d.level = l.level;
