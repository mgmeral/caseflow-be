# AI service contract fixtures

Used by `CaseflowAiClientContractTest`.

- `*-response.json`: response bodies exactly as `caseflow-ai-service` serializes them (its `api/dto`
  classes). The test proves `caseflow-be` reads every field it depends on.
- `requests/*.json`: the exact request bodies `caseflow-be` sends. The test asserts that BE serializes
  to exactly this JSON. `caseflow-ai-service`'s `BeContractTest` asserts that it accepts an identical
  copy in `caseflow-ai-service/src/test/resources/be-contract/`.

When either side changes a DTO, update both fixture sets in the same change. See
`caseflow-central-brain/tasks/active/AI-001-*.md` (`AI-001-CONTRACT`).
