# Evaluation API contract (CAN-003 MVP)

All JSON property names use `snake_case`. Authentication uses the Keycloak bearer token.

## Official templates

| Method | Path | Behavior |
| --- | --- | --- |
| GET | `/api/v1/evaluation-templates` | Lists `ACTIVE`, official templates and their dimensions and criteria. |
| GET | `/api/v1/evaluation-templates/{templateId}` | Returns one active official template and its dimensions, metric definitions, scale bounds, and scale levels. |

Legacy GET paths (`/get-all-templates` and `/get-template-by-id/{templateId}`) remain aliases for compatibility. Template administration is not exposed by the application. The database migration also blocks updates/deletes to official templates and criteria.

## Record and retrieve an evaluation

| Method | Path | Behavior |
| --- | --- | --- |
| POST | `/api/v1/players/{playerId}/evaluations` | Records a completed evaluation. COACH role required, plus current coach and player assignments in the same academy category. |
| GET | `/api/v1/players/{playerId}/evaluations/{evaluationId}` | Retrieves an evaluation created by the authenticated coach. |

Staff profile routes under `/api/v1/players/**` and `/api/v1/coaches/**` that address a resource by ID require
`X-Academy-Id`. The backend resolves the active academy and category assignments for each request. A coach must
currently share the player's academy category; an academy administrator must administer the selected academy and
the target must have a current assignment there. Out-of-scope profiles return 404.

Example request:

```json
{
  "template_id": "00000000-0000-0000-0000-000000000001",
  "template_version": 1,
  "results": [
    {
      "criterion_id": "00000000-0000-0000-0000-000000000002",
      "raw_value": 1.82,
      "laterality": "NONE",
      "observations": [
        { "sequence": 1, "value": 1.91, "valid": true, "note": "Primer intento" },
        { "sequence": 2, "value": 1.82, "valid": true }
      ]
    }
  ],
  "strengths": "Buena aceleración inicial",
  "improvement_areas": "Mantener la técnica al final",
  "general_comment": ""
}
```

The evaluator is resolved from the authenticated JWT and cannot be supplied by the client. The template ID and version must identify the current active official version; criteria must belong to that template. Default active criteria are required. Metric-specific raw evidence, scale bounds, required comments, laterality, and observation payloads are validated. Evaluation, results, and observations are saved in one transaction. The template reference preserves the version used; official template and criterion rows are immutable in the MVP.

## Current scope and verification

The focused command is documented in [testing.md](testing.md). PostgreSQL coverage verifies all ten seeded templates and HTTP persistence on a disposable migrated database. Overall and Growth scoring remain outside the MVP until their functional definitions are approved. The scope of changes to a coach's global profile or account status when that coach has assignments in multiple academies also remains a product decision; ID based access is still checked against the selected academy.
