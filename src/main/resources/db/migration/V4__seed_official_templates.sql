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
   duration_min_minutes, duration_max_minutes, is_session_integrated,
   group_size_min, group_size_max, frequency_min_days, frequency_max_days,
   validity_days, max_active_metrics, score_aggregation, is_official,
   created_at, updated_at)
VALUES
  ('SPRINT_10M', 'Sprint corto 10m', 1, 'ACTIVE',
   'Prueba física de aceleración inicial. Aplica a todas las categorías.',
   'Medir aceleración inicial',
   'Cronometraje de 10 metros. Se registran 2 intentos y cuenta el mejor.',
   10, 10, false, 6, 8, 30, 30, 45, 1, 'WEIGHTED_AVERAGE', true, now(), now()),

  ('CIRCUITO_AGILIDAD', 'Circuito de agilidad', 1, 'ACTIVE',
   'Circuito con conos; penaliza conos derribados.',
   'Medir agilidad y coordinación',
   'Registrar tiempo total y número de conos derribados. El sistema calcula el tiempo ajustado.',
   15, 15, false, NULL, NULL, 30, 30, 45, 2, 'WEIGHTED_AVERAGE', true, now(), now()),

  ('CONDUCCION_CAMBIOS_DIRECCION', 'Conducción con cambios de dirección', 1, 'ACTIVE',
   'Circuito de conducción con cambios de dirección.',
   'Medir dominio del balón en movimiento y coordinación',
   'Registrar tiempo y número de toques fuera de control.',
   15, 15, false, NULL, NULL, 30, 30, 45, 2, 'WEIGHTED_AVERAGE', true, now(), now()),

  ('PASE_RECEPCION', 'Pase y recepción', 1, 'ACTIVE',
   'Precisión de pase y control orientado.',
   'Medir precisión de pase y control orientado',
   'Contar pases completados sobre oportunidades. Mínimo 8 oportunidades.',
   15, 15, false, NULL, NULL, 21, 30, 45, 1, 'WEIGHTED_AVERAGE', true, now(), now()),

  ('FINALIZACION', 'Finalización', 1, 'ACTIVE',
   'Técnica de golpeo y definición.',
   'Medir técnica de golpeo y definición',
   'Registrar aciertos sobre intentos y nivel de rúbrica de calidad de ejecución.',
   15, 15, false, NULL, NULL, 21, 30, 45, 2, 'WEIGHTED_AVERAGE', true, now(), now()),

  ('RONDO', 'Rondo (pase bajo presión)', 1, 'ACTIVE',
   'Rondo de referencia 4v2.',
   'Medir perfil corporal, escaneo previo y pase bajo presión',
   'Contar pases exitosos bajo presión (mínimo 6 oportunidades) y asignar nivel de rúbrica de perfil corporal/escaneo.',
   8, 10, false, NULL, NULL, 30, 30, 45, 2, 'WEIGHTED_AVERAGE', true, now(), now()),

  ('JUEGO_REDUCIDO', 'Juego reducido 3v3 / 4v4', 1, 'ACTIVE',
   'Contexto real de juego. Máximo 3 métricas activas.',
   'Medir decisión, desmarque, transición y comunicación en contexto real de juego',
   'Rúbrica estructurada por acción observada. Cada métrica se reporta por nivel, sin promedio libre.',
   15, 20, false, NULL, NULL, 30, 30, 45, 3, 'NONE', true, now(), now()),

  ('DUELO_1V1', 'Duelo 1v1', 1, 'ACTIVE',
   'Defensa individual, regate y reacción.',
   'Medir defensa individual, regate y capacidad de reacción',
   'Contar duelos ganados sobre total. Mínimo 6 oportunidades.',
   10, 10, false, NULL, NULL, 30, 30, 45, 1, 'WEIGHTED_AVERAGE', true, now(), now()),

  ('REACCION_TRAS_PERDIDA', 'Reacción tras pérdida', 1, 'ACTIVE',
   'Observación integrada en juego reducido o partido de entrenamiento.',
   'Medir repliegue, intensidad y resiliencia inmediata tras perder el balón',
   'Registrar nivel de rúbrica en cada acción observada. Mínimo 5 observaciones.',
   NULL, NULL, true, NULL, NULL, 30, 30, 45, 1, 'NONE', true, now(), now()),

  ('OBSERVACION_CONVIVENCIA', 'Observación de convivencia', 1, 'ACTIVE',
   'Observación durante la sesión completa. Comentario obligatorio.',
   'Medir respeto, comunicación y colaboración con compañeros',
   'Asignar nivel en la escala anclada 1-5 y escribir evidencia breve.',
   NULL, NULL, true, NULL, NULL, 30, 30, 45, 1, 'NONE', true, now(), now());

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
       m.scale_min, m.scale_max, m.comment_required, true, m.is_scored, m.laterality_mode,
       m.scoring_method,
       CASE WHEN m.scoring_method = 'CONVERSION_TABLE' THEN ct.conversion_table_id END,
       m.scoring_config::jsonb, now(), now()
FROM (VALUES
  -- template_code, dimension, code, name, description, weight, order,
  -- metric_type, unit, better, attempts, aggregation, min_opp, scale_min, scale_max,
  -- comment_req, is_scored, laterality, scoring_method, scoring_config
  ('SPRINT_10M', 'PHYSICAL', 'TIEMPO_10M', 'Tiempo 10m',
   'Mejor tiempo de 2 intentos (segundos)', 1.000, 1,
   'TIME', 'SECONDS', 'LOWER', 2, 'BEST', NULL, NULL, NULL,
   false, true, 'NONE', 'AGE_THRESHOLD', '{}'),

  ('CIRCUITO_AGILIDAD', 'PHYSICAL', 'TIEMPO_AJUSTADO', 'Tiempo total',
   'Tiempo total del circuito; se suma la penalización por conos derribados', 1.000, 1,
   'TIME', 'SECONDS', 'LOWER', 1, 'SINGLE', NULL, NULL, NULL,
   false, true, 'NONE', 'TIME_WITH_PENALTY',
   '{"penalty_metric_code": "CONOS_DERRIBADOS", "penalty_seconds_per_error": null, "_pending": "D-03"}'),
  ('CIRCUITO_AGILIDAD', 'PHYSICAL', 'CONOS_DERRIBADOS', 'Conos derribados',
   'Número de errores (componente de penalización)', 0.000, 2,
   'ERROR_COUNT', 'COUNT', 'LOWER', 1, 'SINGLE', NULL, NULL, NULL,
   false, false, 'NONE', 'NONE', '{}'),

  ('CONDUCCION_CAMBIOS_DIRECCION', 'TECHNICAL', 'TIEMPO_CONDUCCION', 'Tiempo de conducción',
   'Tiempo bajo umbral con máximo de errores permitido', 1.000, 1,
   'TIME', 'SECONDS', 'LOWER', 1, 'SINGLE', NULL, NULL, NULL,
   false, true, 'NONE', 'TIME_WITH_MAX_ERRORS',
   '{"error_metric_code": "ERRORES_CONTROL", "max_errors": null, "_pending": "D-03"}'),
  ('CONDUCCION_CAMBIOS_DIRECCION', 'TECHNICAL', 'ERRORES_CONTROL', 'Toques fuera de control',
   'Número de toques fuera de control (componente)', 0.000, 2,
   'ERROR_COUNT', 'COUNT', 'LOWER', 1, 'SINGLE', NULL, NULL, NULL,
   false, false, 'NONE', 'NONE', '{}'),

  ('PASE_RECEPCION', 'TECHNICAL', 'PASES_COMPLETADOS', 'Pases completados',
   'Aciertos sobre oportunidades (mínimo 8)', 1.000, 1,
   'SUCCESS_RATIO', 'PERCENT', 'HIGHER', NULL, 'SINGLE', 8, NULL, NULL,
   false, true, 'OPTIONAL', 'CONVERSION_TABLE', '{}'),

  ('FINALIZACION', 'TECHNICAL', 'ACIERTOS_FINALIZACION', 'Aciertos de finalización',
   'Aciertos sobre intentos', 1.000, 1,
   'SUCCESS_RATIO', 'PERCENT', 'HIGHER', NULL, 'SINGLE', 6, NULL, NULL,
   false, true, 'OPTIONAL', 'CONVERSION_TABLE', '{"_pending": "D-05 mínimo de intentos"}'),
  ('FINALIZACION', 'TECHNICAL', 'CALIDAD_EJECUCION', 'Calidad de ejecución',
   'Rúbrica de técnica de golpeo', 1.000, 2,
   'RUBRIC', 'LEVEL', 'HIGHER', NULL, 'SINGLE', NULL, 1, 3,
   false, true, 'NONE', 'RUBRIC_LEVEL', '{}'),

  ('RONDO', 'TECHNICAL', 'PASE_BAJO_PRESION', 'Pase bajo presión',
   '% de éxito sobre oportunidades (mínimo 6)', 1.000, 1,
   'SUCCESS_RATIO', 'PERCENT', 'HIGHER', NULL, 'SINGLE', 6, NULL, NULL,
   false, true, 'NONE', 'CONVERSION_TABLE', '{"_pending": "D-06 ficha JSON rondo 4v2"}'),
  ('RONDO', 'TACTICAL', 'PERFIL_CORPORAL_ESCANEO', 'Perfil corporal / escaneo',
   'Rúbrica de 3 niveles', 1.000, 2,
   'RUBRIC', 'LEVEL', 'HIGHER', NULL, 'SINGLE', NULL, 1, 3,
   false, true, 'NONE', 'RUBRIC_LEVEL', '{}'),

  ('JUEGO_REDUCIDO', 'TACTICAL', 'DECISION_BAJO_PRESION', 'Decisión bajo presión',
   'Rúbrica por acción observada', 1.000, 1,
   'RUBRIC', 'LEVEL', 'HIGHER', NULL, 'MEDIAN', NULL, 1, 3,
   false, true, 'NONE', 'RUBRIC_LEVEL', '{}'),
  ('JUEGO_REDUCIDO', 'TACTICAL', 'DESMARQUE', 'Desmarque',
   'Rúbrica por acción observada', 1.000, 2,
   'RUBRIC', 'LEVEL', 'HIGHER', NULL, 'MEDIAN', NULL, 1, 3,
   false, true, 'NONE', 'RUBRIC_LEVEL', '{}'),
  ('JUEGO_REDUCIDO', 'SOCIAL', 'COMUNICACION', 'Comunicación',
   'Rúbrica por acción observada', 1.000, 3,
   'RUBRIC', 'LEVEL', 'HIGHER', NULL, 'MEDIAN', NULL, 1, 3,
   false, true, 'NONE', 'RUBRIC_LEVEL', '{}'),

  ('DUELO_1V1', 'TECHNICAL', 'DUELOS_GANADOS', 'Duelos ganados',
   'Acciones exitosas sobre oportunidades (mínimo 6)', 1.000, 1,
   'SUCCESS_RATIO', 'PERCENT', 'HIGHER', NULL, 'SINGLE', 6, NULL, NULL,
   false, true, 'NONE', 'CONVERSION_TABLE', '{}'),

  ('REACCION_TRAS_PERDIDA', 'TACTICAL', 'REACCION_PERDIDA', 'Reacción tras pérdida',
   'Rúbrica de 3 niveles por acción observada (mínimo 5 observaciones)', 1.000, 1,
   'RUBRIC', 'LEVEL', 'HIGHER', NULL, 'MEDIAN', 5, 1, 3,
   false, true, 'NONE', 'RUBRIC_LEVEL', '{}'),

  ('OBSERVACION_CONVIVENCIA', 'SOCIAL', 'CONVIVENCIA', 'Convivencia',
   'Escala anclada 1-5 con evidencia breve obligatoria', 1.000, 1,
   'ANCHORED_SCALE', 'LEVEL', 'HIGHER', NULL, 'SINGLE', NULL, 1, 5,
   true, true, 'NONE', 'ANCHORED_SCALE', '{}')
) AS m(template_code, dimension_code, code, name, description, weight, display_order,
       metric_type, unit, better_direction, attempts_count, attempt_aggregation, min_opportunities,
       scale_min, scale_max, comment_required, is_scored, laterality_mode, scoring_method, scoring_config)
JOIN evaluation_templates t
  ON t.code = m.template_code AND t.version = 1 AND t.owner_academy_id IS NULL
CROSS JOIN (SELECT conversion_table_id FROM score_conversion_tables WHERE code = 'STANDARD_V1') ct;

-- ---------------------------------------------------------------------
-- Niveles de rúbrica y escala anclada
-- Etiquetas PROVISIONALES; descriptores y score por nivel = [PENDIENTE D-04]
-- ---------------------------------------------------------------------
INSERT INTO metric_scale_levels (criterion_id, level, label)
SELECT c.criterion_id, l.level, l.label
FROM evaluation_criteria c
JOIN (VALUES (1, 'En desarrollo'), (2, 'Competente'), (3, 'Destacado')) AS l(level, label) ON true
WHERE c.metric_type = 'RUBRIC';

INSERT INTO metric_scale_levels (criterion_id, level, label)
SELECT c.criterion_id, l.level, l.label
FROM evaluation_criteria c
JOIN (VALUES (1, 'Nivel 1'), (2, 'Nivel 2'), (3, 'Nivel 3'), (4, 'Nivel 4'), (5, 'Nivel 5')) AS l(level, label) ON true
WHERE c.metric_type = 'ANCHORED_SCALE';
