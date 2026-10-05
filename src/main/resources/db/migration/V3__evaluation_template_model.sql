-- =====================================================================
-- CanteraGT — V3: Modelo de plantillas de evaluación (MVP)
-- ---------------------------------------------------------------------
-- Objetivo: que el esquema soporte TAL CUAL las 10 plantillas base
-- (plantillas-base-canteragt.md): tipos de métrica, intentos, mínimo de
-- oportunidades, tabla de conversión % -> score, umbrales por edad,
-- rúbricas, escala anclada, vigencia, frecuencia y lateralidad.
--
-- Principios aplicados:
--   * El DT registra EVIDENCIA CRUDA; el score es DERIVADO y recalculable.
--   * Plantillas oficiales bloqueadas en el MVP (trigger de protección).
--   * La plantilla no depende de categoría, posición ni duración: lo que
--     varía por edad / categoría / posición vive en metric_thresholds.
--   * Columnas de Fase 3 (owner_academy_id, parent_template_id) existen,
--     pero un CHECK impide usarlas hasta que se habilite el versionado.
--   * Una plantilla define un catálogo de métricas; el DT activa hasta 3
--     por evento y cada una produce su propio score.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Dimensiones del Four Corner Model
--    FA: Técnica/Táctica, Física, Psicológica, Social.
--    Se separa Técnica y Táctica porque las fichas las distinguen.
-- ---------------------------------------------------------------------
ALTER TABLE "evaluation_criteria" DROP CONSTRAINT evaluation_criteria_dimension_code_check;

UPDATE "evaluation_criteria" SET "dimension_code" = 'PSYCHOLOGICAL' WHERE "dimension_code" = 'MENTAL';

ALTER TABLE "evaluation_criteria"
ADD CONSTRAINT evaluation_criteria_dimension_code_check
CHECK ("dimension_code" IN ('TECHNICAL', 'TACTICAL', 'PHYSICAL', 'PSYCHOLOGICAL', 'SOCIAL'));

-- ---------------------------------------------------------------------
-- 2. Categorías con rango de edad (U8 ... U19 / U21 sin cambios de esquema)
-- ---------------------------------------------------------------------
ALTER TABLE "categories" RENAME COLUMN "age_limit" TO "max_age";
ALTER TABLE "categories" ADD COLUMN "code" varchar(10);
ALTER TABLE "categories" ADD COLUMN "min_age" int;

UPDATE "categories" SET "code" = upper(left(regexp_replace("name", '\s+', '', 'g'), 10)) WHERE "code" IS NULL;

ALTER TABLE "categories" ALTER COLUMN "code" SET NOT NULL;
ALTER TABLE "categories" ADD CONSTRAINT categories_code_key UNIQUE ("code");
ALTER TABLE "categories"
ADD CONSTRAINT categories_age_range_check
CHECK (("min_age" IS NULL OR "min_age" >= 4)
   AND ("max_age" IS NULL OR "max_age" <= 23)
   AND ("min_age" IS NULL OR "max_age" IS NULL OR "min_age" <= "max_age"));

-- ---------------------------------------------------------------------
-- 3. Plantillas: ficha completa
-- ---------------------------------------------------------------------
ALTER TABLE "evaluation_templates" ADD COLUMN "code" varchar(60);
UPDATE "evaluation_templates"
   SET "code" = upper(left(regexp_replace("name", '[^A-Za-z0-9]+', '_', 'g'), 60))
 WHERE "code" IS NULL;
ALTER TABLE "evaluation_templates" ALTER COLUMN "code" SET NOT NULL;

ALTER TABLE "evaluation_templates"
    ADD COLUMN "objective"              varchar(255),
    ADD COLUMN "instructions"           text,
    ADD COLUMN "is_session_integrated"  boolean  NOT NULL DEFAULT false,
    ADD COLUMN "group_size_min"         smallint,
    ADD COLUMN "group_size_max"         smallint,
    ADD COLUMN "frequency_min_days"     smallint NOT NULL DEFAULT 30,
    ADD COLUMN "frequency_max_days"     smallint NOT NULL DEFAULT 30,
    ADD COLUMN "validity_days"          smallint NOT NULL DEFAULT 45,
    ADD COLUMN "score_aggregation"      varchar(30) NOT NULL DEFAULT 'WEIGHTED_AVERAGE',
    ADD COLUMN "is_official"            boolean  NOT NULL DEFAULT true,
    -- Preparación Fase 3 (versionado por academia). Bloqueado en MVP.
    ADD COLUMN "owner_academy_id"       uuid,
    ADD COLUMN "parent_template_id"     uuid;

ALTER TABLE "evaluation_templates"
    ADD CONSTRAINT evaluation_templates_owner_fk
        FOREIGN KEY ("owner_academy_id") REFERENCES "academies" ("academy_id"),
    ADD CONSTRAINT evaluation_templates_parent_fk
        FOREIGN KEY ("parent_template_id") REFERENCES "evaluation_templates" ("template_id");

ALTER TABLE "evaluation_templates"
    ADD CONSTRAINT evaluation_templates_group_check
        CHECK ("group_size_min" IS NULL OR "group_size_max" IS NULL OR "group_size_min" <= "group_size_max"),
    -- Una plantilla integrada se observa durante la sesión: no forma grupos propios
    ADD CONSTRAINT evaluation_templates_integrated_group_check
        CHECK (NOT "is_session_integrated" OR ("group_size_min" IS NULL AND "group_size_max" IS NULL)),
    ADD CONSTRAINT evaluation_templates_frequency_check
        CHECK ("frequency_min_days" > 0 AND "frequency_min_days" <= "frequency_max_days"),
    ADD CONSTRAINT evaluation_templates_validity_check
        CHECK ("validity_days" > 0),
    ADD CONSTRAINT evaluation_templates_score_aggregation_check
        CHECK ("score_aggregation" IN ('WEIGHTED_AVERAGE', 'NONE')),
    -- Permanente: oficial <=> sin academia dueña
    ADD CONSTRAINT evaluation_templates_ownership_check
        CHECK ("is_official" = ("owner_academy_id" IS NULL)),
    -- Permanente: solo una plantilla de academia deriva de otra, y nunca de sí misma
    ADD CONSTRAINT evaluation_templates_parent_check
        CHECK ("parent_template_id" IS NULL
               OR ("owner_academy_id" IS NOT NULL AND "parent_template_id" <> "template_id")),
    -- MVP: solo plantillas oficiales, sin dueño ni herencia.
    -- Eliminar este CHECK en Fase 3 al habilitar el versionado por academia.
    ADD CONSTRAINT evaluation_templates_mvp_official_only_check
        CHECK ("is_official" AND "owner_academy_id" IS NULL AND "parent_template_id" IS NULL);

-- Unicidad por código + versión (antes era por nombre + versión)
DROP INDEX IF EXISTS "evaluation_templates_name_version_idx";
CREATE UNIQUE INDEX evaluation_templates_official_code_version_uq
    ON "evaluation_templates" ("code", "version")
    WHERE "owner_academy_id" IS NULL;
-- Preparación Fase 3: unicidad de versiones dentro de cada academia
CREATE UNIQUE INDEX evaluation_templates_academy_code_version_uq
    ON "evaluation_templates" ("owner_academy_id", "code", "version")
    WHERE "owner_academy_id" IS NOT NULL;

-- Una plantilla puede aportar evidencia a varias dimensiones
CREATE TABLE "template_dimensions" (
  "template_id"    uuid        NOT NULL REFERENCES "evaluation_templates" ("template_id"),
  "dimension_code" varchar(30) NOT NULL,
  "is_primary"     boolean     NOT NULL DEFAULT false,
  PRIMARY KEY ("template_id", "dimension_code"),
  CONSTRAINT template_dimensions_dimension_code_check
    CHECK ("dimension_code" IN ('TECHNICAL', 'TACTICAL', 'PHYSICAL', 'PSYCHOLOGICAL', 'SOCIAL'))
);

-- ---------------------------------------------------------------------
-- 4. Tablas de conversión % de éxito -> score
--    Cada banda se define por su límite inferior (inclusivo): el score es
--    el de la banda con mayor min_pct <= % obtenido. Sin huecos posibles.
-- ---------------------------------------------------------------------
CREATE TABLE "score_conversion_tables" (
  "conversion_table_id" uuid DEFAULT uuid_generate_v7() PRIMARY KEY,
  "code"        varchar(60)  UNIQUE NOT NULL,
  "name"        varchar(150) NOT NULL,
  "is_default"  boolean      NOT NULL DEFAULT false,
  "status"      varchar(20)  NOT NULL DEFAULT 'ACTIVE',
  "created_at"  TIMESTAMP    NOT NULL DEFAULT now(),
  "updated_at"  TIMESTAMP    NOT NULL DEFAULT now(),
  CONSTRAINT score_conversion_tables_status_check CHECK ("status" IN ('ACTIVE', 'RETIRED'))
);
CREATE UNIQUE INDEX score_conversion_tables_single_default_uq
    ON "score_conversion_tables" ("is_default") WHERE "is_default";

CREATE TABLE "score_conversion_bands" (
  "band_id"             uuid DEFAULT uuid_generate_v7() PRIMARY KEY,
  "conversion_table_id" uuid NOT NULL REFERENCES "score_conversion_tables" ("conversion_table_id"),
  "min_pct"             decimal(5,2) NOT NULL,
  "score"               decimal(5,2) NOT NULL,
  CONSTRAINT score_conversion_bands_pct_check   CHECK ("min_pct" >= 0 AND "min_pct" <= 100),
  CONSTRAINT score_conversion_bands_score_check CHECK ("score" >= 0 AND "score" <= 100),
  CONSTRAINT score_conversion_bands_uq UNIQUE ("conversion_table_id", "min_pct")
);

-- ---------------------------------------------------------------------
-- 5. Métricas (evaluation_criteria) con tipo, evidencia y regla de scoring
-- ---------------------------------------------------------------------
ALTER TABLE "evaluation_criteria"
    ADD COLUMN "metric_type"           varchar(30) NOT NULL DEFAULT 'RUBRIC',
    ADD COLUMN "unit"                  varchar(20) NOT NULL DEFAULT 'LEVEL',
    ADD COLUMN "better_direction"      varchar(10) NOT NULL DEFAULT 'HIGHER',
    ADD COLUMN "attempts_count"        smallint,
    ADD COLUMN "attempt_aggregation"   varchar(20) NOT NULL DEFAULT 'SINGLE',
    ADD COLUMN "min_opportunities"     smallint,
    ADD COLUMN "scale_min"             smallint,
    ADD COLUMN "scale_max"             smallint,
    ADD COLUMN "comment_required"      boolean     NOT NULL DEFAULT false,
    ADD COLUMN "is_active_by_default"  boolean     NOT NULL DEFAULT true,
    ADD COLUMN "is_scored"             boolean     NOT NULL DEFAULT true,
    ADD COLUMN "laterality_mode"       varchar(10) NOT NULL DEFAULT 'NONE',
    ADD COLUMN "scoring_method"        varchar(40) NOT NULL DEFAULT 'NONE',
    ADD COLUMN "conversion_table_id"   uuid REFERENCES "score_conversion_tables" ("conversion_table_id"),
    ADD COLUMN "scoring_config"        jsonb       NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE "evaluation_criteria"
    ADD CONSTRAINT evaluation_criteria_metric_type_check
        CHECK ("metric_type" IN ('TIME', 'SUCCESS_RATIO', 'ERROR_COUNT', 'RUBRIC', 'ANCHORED_SCALE')),
    ADD CONSTRAINT evaluation_criteria_unit_check
        CHECK ("unit" IN ('SECONDS', 'COUNT', 'PERCENT', 'LEVEL')),
    ADD CONSTRAINT evaluation_criteria_better_direction_check
        CHECK ("better_direction" IN ('HIGHER', 'LOWER')),
    ADD CONSTRAINT evaluation_criteria_attempt_aggregation_check
        CHECK ("attempt_aggregation" IN ('SINGLE', 'BEST', 'SUM', 'MEDIAN')),
    ADD CONSTRAINT evaluation_criteria_attempts_check
        CHECK ("attempts_count" IS NULL OR "attempts_count" BETWEEN 1 AND 10),
    ADD CONSTRAINT evaluation_criteria_laterality_mode_check
        CHECK ("laterality_mode" IN ('NONE', 'OPTIONAL', 'REQUIRED')),
    ADD CONSTRAINT evaluation_criteria_scoring_method_check
        CHECK ("scoring_method" IN ('NONE', 'CONVERSION_TABLE', 'AGE_THRESHOLD',
                                    'TIME_WITH_PENALTY', 'TIME_WITH_MAX_ERRORS',
                                    'RUBRIC_LEVEL', 'ANCHORED_SCALE')),
    -- Regla general #4: aciertos/oportunidades exige mínimo de oportunidades (>= 5)
    ADD CONSTRAINT evaluation_criteria_ratio_min_opp_check
        CHECK ("metric_type" <> 'SUCCESS_RATIO' OR "min_opportunities" >= 5),
    ADD CONSTRAINT evaluation_criteria_scale_check
        CHECK ("metric_type" NOT IN ('RUBRIC', 'ANCHORED_SCALE')
               OR ("scale_min" IS NOT NULL AND "scale_max" IS NOT NULL AND "scale_min" < "scale_max")),
    ADD CONSTRAINT evaluation_criteria_conversion_check
        CHECK ("scoring_method" <> 'CONVERSION_TABLE' OR "conversion_table_id" IS NOT NULL),
    ADD CONSTRAINT evaluation_criteria_scored_check
        CHECK ("is_scored" = ("scoring_method" <> 'NONE')),
    ADD CONSTRAINT evaluation_criteria_weight_check
        CHECK ("weight" >= 0),
    -- Destino de FKs compuestas que garantizan "métrica de ESTA plantilla"
    ADD CONSTRAINT evaluation_criteria_criterion_template_uq
        UNIQUE ("criterion_id", "template_id");

-- Regla general #1 (selección por defecto): máximo 3 métricas con score
-- marcadas is_active_by_default por plantilla. Una plantilla puede tener un
-- catálogo mayor (ej. juego reducido: 4); el DT elige por evento en
-- session_event_metrics. Los componentes de penalización (is_scored = false:
-- conos derribados, toques fuera de control...) no cuentan: solo alimentan
-- el score de la métrica principal. Trigger diferido para cargas en bloque.
CREATE OR REPLACE FUNCTION canteragt_check_max_active_metrics()
RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  v_count int;
BEGIN
  SELECT count(*) INTO v_count
    FROM evaluation_criteria
   WHERE template_id = NEW.template_id
     AND is_active_by_default
     AND is_scored;

  IF v_count > 3 THEN
    RAISE EXCEPTION 'Una plantilla admite como máximo 3 métricas activas con score (template_id=%, activas=%)',
      NEW.template_id, v_count USING ERRCODE = 'check_violation';
  END IF;
  RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER evaluation_criteria_max_active_metrics_trg
AFTER INSERT OR UPDATE OF "template_id", "is_active_by_default", "is_scored" ON "evaluation_criteria"
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION canteragt_check_max_active_metrics();

-- Coherencia plantilla <-> dimensiones (Four Corner Model):
--   * cada dimensión declarada en template_dimensions tiene al menos una
--     métrica con score que la alimenta (si no, el perfil queda sin datos);
--   * cada métrica pertenece a una dimensión declarada por su plantilla.
-- Trigger diferido: se valida al final de la transacción, con plantilla,
-- dimensiones y métricas ya cargadas.
CREATE OR REPLACE FUNCTION canteragt_check_template_dimensions()
RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  v_template_id uuid;
  v_missing     text;
BEGIN
  v_template_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.template_id ELSE NEW.template_id END;

  SELECT string_agg(d.dimension_code, ', ') INTO v_missing
    FROM template_dimensions d
   WHERE d.template_id = v_template_id
     AND NOT EXISTS (SELECT 1 FROM evaluation_criteria c
                      WHERE c.template_id = d.template_id
                        AND c.dimension_code = d.dimension_code
                        AND c.is_scored);

  IF v_missing IS NOT NULL THEN
    RAISE EXCEPTION 'Dimensiones declaradas sin métrica con score (template_id=%): %',
      v_template_id, v_missing USING ERRCODE = 'check_violation';
  END IF;

  SELECT string_agg(DISTINCT c.dimension_code, ', ') INTO v_missing
    FROM evaluation_criteria c
   WHERE c.template_id = v_template_id
     AND NOT EXISTS (SELECT 1 FROM template_dimensions d
                      WHERE d.template_id = c.template_id
                        AND d.dimension_code = c.dimension_code);

  IF v_missing IS NOT NULL THEN
    RAISE EXCEPTION 'Métricas en dimensiones no declaradas por la plantilla (template_id=%): %',
      v_template_id, v_missing USING ERRCODE = 'check_violation';
  END IF;
  RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER template_dimensions_coherence_trg
AFTER INSERT OR UPDATE OR DELETE ON "template_dimensions"
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION canteragt_check_template_dimensions();

CREATE CONSTRAINT TRIGGER evaluation_criteria_dimensions_coherence_trg
AFTER INSERT OR UPDATE OF "template_id", "dimension_code", "is_scored" OR DELETE ON "evaluation_criteria"
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION canteragt_check_template_dimensions();

-- Niveles de rúbrica y anclas de escala (descriptor visible para el DT)
CREATE TABLE "metric_scale_levels" (
  "scale_level_id" uuid DEFAULT uuid_generate_v7() PRIMARY KEY,
  "criterion_id"   uuid         NOT NULL REFERENCES "evaluation_criteria" ("criterion_id"),
  "level"          smallint     NOT NULL,
  "label"          varchar(80)  NOT NULL,
  "descriptor"     varchar(500),
  "score"          decimal(5,2),          -- NULL = mapeo nivel->score pendiente
  "created_at"     TIMESTAMP    NOT NULL DEFAULT now(),
  "updated_at"     TIMESTAMP    NOT NULL DEFAULT now(),
  CONSTRAINT metric_scale_levels_uq UNIQUE ("criterion_id", "level"),
  CONSTRAINT metric_scale_levels_score_check CHECK ("score" IS NULL OR ("score" >= 0 AND "score" <= 100))
);

-- Umbrales por edad / categoría / sexo / posición (pruebas físicas)
-- Banda por límite: para LOWER-is-better se toma la banda con menor
-- bound_value >= valor medido; para HIGHER, la de mayor bound_value <= valor.
CREATE TABLE "metric_thresholds" (
  "threshold_id"   uuid DEFAULT uuid_generate_v7() PRIMARY KEY,
  "criterion_id"   uuid          NOT NULL REFERENCES "evaluation_criteria" ("criterion_id"),
  "category_id"    uuid          REFERENCES "categories" ("category_id"),
  "age_min"        smallint,
  "age_max"        smallint,
  "sex"            varchar(20),
  "position_code"  varchar(30)   REFERENCES "positions" ("code"),
  "bound_value"    decimal(8,3)  NOT NULL,
  "score"          decimal(5,2)  NOT NULL,
  "threshold_set"  varchar(40)   NOT NULL DEFAULT 'BASE_V1',
  "status"         varchar(20)   NOT NULL DEFAULT 'ACTIVE',
  "created_at"     TIMESTAMP     NOT NULL DEFAULT now(),
  "updated_at"     TIMESTAMP     NOT NULL DEFAULT now(),
  CONSTRAINT metric_thresholds_age_check
    CHECK ("age_min" IS NULL OR "age_max" IS NULL OR "age_min" <= "age_max"),
  CONSTRAINT metric_thresholds_scope_check
    CHECK ("category_id" IS NOT NULL OR "age_min" IS NOT NULL),
  CONSTRAINT metric_thresholds_score_check CHECK ("score" >= 0 AND "score" <= 100),
  CONSTRAINT metric_thresholds_status_check CHECK ("status" IN ('ACTIVE', 'RETIRED')),
  CONSTRAINT metric_thresholds_sex_check CHECK ("sex" IS NULL OR "sex" IN ('MALE', 'FEMALE', 'OTHER'))
);
CREATE INDEX metric_thresholds_lookup_idx
    ON "metric_thresholds" ("criterion_id", "status", "age_min", "age_max");

-- ---------------------------------------------------------------------
-- 6. Eventos de evaluación dentro de la sesión (bloques / rotación)
-- ---------------------------------------------------------------------
CREATE TABLE "session_evaluation_events" (
  "session_event_id" uuid DEFAULT uuid_generate_v7() PRIMARY KEY,
  "session_id"       uuid        NOT NULL REFERENCES "training_sessions" ("session_id"),
  "template_id"      uuid        NOT NULL REFERENCES "evaluation_templates" ("template_id"),
  "block_order"      smallint    NOT NULL,
  "status"           varchar(20) NOT NULL DEFAULT 'PLANNED',
  "started_at"       TIMESTAMP,
  "ended_at"         TIMESTAMP,
  "created_at"       TIMESTAMP   NOT NULL DEFAULT now(),
  "updated_at"       TIMESTAMP   NOT NULL DEFAULT now(),
  CONSTRAINT session_evaluation_events_order_uq UNIQUE ("session_id", "block_order"),
  CONSTRAINT session_evaluation_events_event_template_uq UNIQUE ("session_event_id", "template_id"),
  CONSTRAINT session_evaluation_events_status_check
    CHECK ("status" IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'))
);
CREATE INDEX session_evaluation_events_template_idx ON "session_evaluation_events" ("template_id");

-- Métricas activas elegidas por el DT para un evento: un mismo ejercicio
-- produce un score independiente por cada métrica activa (máximo 3 con score).
-- Se precargan con las is_active_by_default de la plantilla. Los componentes
-- de penalización no se eligen: acompañan a su métrica principal.
-- Las FKs compuestas impiden elegir una métrica de otra plantilla.
CREATE TABLE "session_event_metrics" (
  "session_event_id" uuid      NOT NULL,
  "template_id"      uuid      NOT NULL,
  "criterion_id"     uuid      NOT NULL,
  "created_at"       TIMESTAMP NOT NULL DEFAULT now(),
  PRIMARY KEY ("session_event_id", "criterion_id"),
  CONSTRAINT session_event_metrics_event_fk
    FOREIGN KEY ("session_event_id", "template_id")
    REFERENCES "session_evaluation_events" ("session_event_id", "template_id"),
  CONSTRAINT session_event_metrics_criterion_fk
    FOREIGN KEY ("criterion_id", "template_id")
    REFERENCES "evaluation_criteria" ("criterion_id", "template_id")
);
CREATE INDEX session_event_metrics_criterion_idx ON "session_event_metrics" ("criterion_id");

CREATE OR REPLACE FUNCTION canteragt_check_event_metrics()
RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  v_is_scored boolean;
  v_count     int;
BEGIN
  SELECT c.is_scored INTO v_is_scored
    FROM evaluation_criteria c
   WHERE c.criterion_id = NEW.criterion_id;

  IF NOT v_is_scored THEN
    RAISE EXCEPTION 'La métrica % es un componente sin score y no se puede activar por separado',
      NEW.criterion_id USING ERRCODE = 'check_violation';
  END IF;

  SELECT count(*) INTO v_count
    FROM session_event_metrics
   WHERE session_event_id = NEW.session_event_id;

  IF v_count > 3 THEN
    RAISE EXCEPTION 'Un evento admite como máximo 3 métricas activas (session_event_id=%, activas=%)',
      NEW.session_event_id, v_count USING ERRCODE = 'check_violation';
  END IF;
  RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER session_event_metrics_check_trg
AFTER INSERT OR UPDATE ON "session_event_metrics"
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION canteragt_check_event_metrics();

-- ---------------------------------------------------------------------
-- 7. Evaluaciones: una ejecución de plantilla por jugador
-- ---------------------------------------------------------------------
ALTER TABLE "evaluations" RENAME COLUMN "overall_score"  TO "template_score";
ALTER TABLE "evaluations" RENAME COLUMN "mental_comment" TO "psychological_comment";
-- growth_score es derivado (se calcula, no se guarda)
ALTER TABLE "evaluations" DROP COLUMN "growth_score";

ALTER TABLE "evaluations"
    ADD COLUMN "session_event_id"   uuid REFERENCES "session_evaluation_events" ("session_event_id"),
    ADD COLUMN "social_comment"     text,
    ADD COLUMN "general_comment"    text,
    ADD COLUMN "valid_until"        date,
    ADD COLUMN "scoring_status"     varchar(30) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN "scoring_version"    varchar(20),
    ADD COLUMN "scored_at"          TIMESTAMP,
    ADD COLUMN "completed_at"       TIMESTAMP,
    ADD COLUMN "voided_at"          TIMESTAMP,
    ADD COLUMN "voided_by_coach_id" uuid REFERENCES "coaches" ("coach_id"),
    ADD COLUMN "void_reason"        varchar(500);

ALTER TABLE "evaluations" DROP CONSTRAINT evaluations_status_check;
ALTER TABLE "evaluations"
    ADD CONSTRAINT evaluations_status_check
        CHECK ("status" IN ('DRAFT', 'COMPLETED', 'VOIDED', 'ARCHIVED')),
    ADD CONSTRAINT evaluations_scoring_status_check
        CHECK ("scoring_status" IN ('PENDING', 'SCORED', 'PARTIAL', 'INSUFFICIENT_EVIDENCE', 'NOT_APPLICABLE')),
    -- Anular en lugar de borrar: el historial se conserva
    ADD CONSTRAINT evaluations_void_check
        CHECK (("status" = 'VOIDED') = ("voided_at" IS NOT NULL AND "void_reason" IS NOT NULL)),
    -- La evaluación usa la misma plantilla que su evento (sin evento: no aplica)
    ADD CONSTRAINT evaluations_event_template_fk
        FOREIGN KEY ("session_event_id", "template_id")
        REFERENCES "session_evaluation_events" ("session_event_id", "template_id");

-- Un jugador se evalúa una sola vez por evento (salvo evaluaciones anuladas)
CREATE UNIQUE INDEX evaluations_event_player_uq
    ON "evaluations" ("session_event_id", "player_id")
    WHERE "status" <> 'VOIDED' AND "session_event_id" IS NOT NULL;
-- Consulta "última evaluación vigente por jugador y plantilla"
CREATE INDEX evaluations_player_template_date_idx
    ON "evaluations" ("player_id", "template_id", "evaluation_date" DESC);

-- ---------------------------------------------------------------------
-- 8. Resultados: evidencia cruda + score derivado
-- ---------------------------------------------------------------------
ALTER TABLE "evaluation_results" ALTER COLUMN "score" DROP NOT NULL;

ALTER TABLE "evaluation_results"
    ADD COLUMN "raw_value"      decimal(8,3),   -- tiempo final (ej. mejor intento)
    ADD COLUMN "successes"      smallint,
    ADD COLUMN "opportunities"  smallint,
    ADD COLUMN "error_count"    smallint,
    ADD COLUMN "level"          smallint,       -- rúbrica / escala anclada
    ADD COLUMN "laterality"     varchar(10) NOT NULL DEFAULT 'NONE',
    ADD COLUMN "scoring_status" varchar(30) NOT NULL DEFAULT 'PENDING';

ALTER TABLE "evaluation_results"
    ADD CONSTRAINT evaluation_results_laterality_check
        CHECK ("laterality" IN ('NONE', 'LEFT', 'RIGHT')),
    ADD CONSTRAINT evaluation_results_ratio_check
        CHECK ("successes" IS NULL OR ("opportunities" IS NOT NULL AND "successes" BETWEEN 0 AND "opportunities")),
    ADD CONSTRAINT evaluation_results_non_negative_check
        CHECK (("raw_value" IS NULL OR "raw_value" >= 0) AND ("error_count" IS NULL OR "error_count" >= 0)),
    ADD CONSTRAINT evaluation_results_score_check
        CHECK ("score" IS NULL OR ("score" >= 0 AND "score" <= 100)),
    ADD CONSTRAINT evaluation_results_scoring_status_check
        CHECK ("scoring_status" IN ('PENDING', 'PENDING_THRESHOLD', 'SCORED', 'INSUFFICIENT_EVIDENCE', 'NOT_SCORED'));

-- La lateralidad se registra por separado: una fila por pierna
DROP INDEX IF EXISTS "evaluation_results_evaluation_id_criterion_id_idx";
CREATE UNIQUE INDEX evaluation_results_eval_criterion_laterality_uq
    ON "evaluation_results" ("evaluation_id", "criterion_id", "laterality");

-- Cada resultado es de una métrica de la plantilla evaluada y, si la
-- evaluación pertenece a un evento, de una métrica activa en ese evento
-- (los componentes sin score acompañan a su métrica principal).
-- Con score_aggregation = 'NONE' cada resultado conserva su score propio y
-- evaluations.template_score queda NULL.
CREATE OR REPLACE FUNCTION canteragt_check_result_criterion()
RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  v_eval_template uuid;
  v_event_id      uuid;
  v_crit_template uuid;
  v_is_scored     boolean;
BEGIN
  SELECT e.template_id, e.session_event_id INTO v_eval_template, v_event_id
    FROM evaluations e
   WHERE e.evaluation_id = NEW.evaluation_id;

  SELECT c.template_id, c.is_scored INTO v_crit_template, v_is_scored
    FROM evaluation_criteria c
   WHERE c.criterion_id = NEW.criterion_id;

  IF v_crit_template IS DISTINCT FROM v_eval_template THEN
    RAISE EXCEPTION 'La métrica % no pertenece a la plantilla de la evaluación %',
      NEW.criterion_id, NEW.evaluation_id USING ERRCODE = 'check_violation';
  END IF;

  IF v_event_id IS NOT NULL AND v_is_scored AND NOT EXISTS (
       SELECT 1 FROM session_event_metrics m
        WHERE m.session_event_id = v_event_id AND m.criterion_id = NEW.criterion_id) THEN
    RAISE EXCEPTION 'La métrica % no está activa en el evento %',
      NEW.criterion_id, v_event_id USING ERRCODE = 'check_violation';
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER evaluation_results_criterion_trg
BEFORE INSERT OR UPDATE OF "evaluation_id", "criterion_id" ON "evaluation_results"
FOR EACH ROW EXECUTE FUNCTION canteragt_check_result_criterion();

-- Intentos (sprint: mejor de 2) y observaciones (rúbrica por acción observada)
CREATE TABLE "evaluation_observations" (
  "observation_id"       uuid DEFAULT uuid_generate_v7() PRIMARY KEY,
  "evaluation_result_id" uuid         NOT NULL REFERENCES "evaluation_results" ("evaluation_result_id"),
  "sequence"             smallint     NOT NULL,
  "value"                decimal(8,3),       -- tiempo del intento
  "level"                smallint,           -- nivel observado en la acción
  "is_success"           boolean,            -- acierto / fallo de la oportunidad
  "is_valid"             boolean      NOT NULL DEFAULT true,
  "note"                 varchar(255),
  "created_at"           TIMESTAMP    NOT NULL DEFAULT now(),
  CONSTRAINT evaluation_observations_uq UNIQUE ("evaluation_result_id", "sequence"),
  CONSTRAINT evaluation_observations_payload_check
    CHECK ("value" IS NOT NULL OR "level" IS NOT NULL OR "is_success" IS NOT NULL)
);

-- ---------------------------------------------------------------------
-- 9. Bloqueo de plantillas oficiales (MVP)
--    Solo se pueden modificar desde una migración/proceso admin que ejecute:
--      SET LOCAL canteragt.allow_template_admin = 'on';
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION canteragt_protect_official_templates()
RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  v_template_id uuid;
  v_official    boolean;
BEGIN
  IF coalesce(current_setting('canteragt.allow_template_admin', true), 'off') = 'on' THEN
    RETURN COALESCE(NEW, OLD);
  END IF;

  IF TG_TABLE_NAME = 'evaluation_templates' THEN
    v_official := COALESCE(OLD.is_official, false);
  ELSE
    v_template_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.template_id ELSE NEW.template_id END;
    SELECT t.is_official INTO v_official FROM evaluation_templates t WHERE t.template_id = v_template_id;
  END IF;

  IF COALESCE(v_official, false) THEN
    RAISE EXCEPTION 'Las plantillas oficiales de CanteraGT no pueden modificarse durante el MVP (%.%)',
      TG_TABLE_NAME, TG_OP USING ERRCODE = 'check_violation';
  END IF;
  RETURN COALESCE(NEW, OLD);
END;
$$;

CREATE TRIGGER evaluation_templates_protect_trg
BEFORE UPDATE OR DELETE ON "evaluation_templates"
FOR EACH ROW EXECUTE FUNCTION canteragt_protect_official_templates();

CREATE TRIGGER evaluation_criteria_protect_trg
BEFORE INSERT OR UPDATE OR DELETE ON "evaluation_criteria"
FOR EACH ROW EXECUTE FUNCTION canteragt_protect_official_templates();
