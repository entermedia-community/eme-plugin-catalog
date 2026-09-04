# AGENTS.md

## Catalog Plugin Guide

### Purpose

`plugins/catalog` holds the shared data model for the whole EME stack: the field/table
definitions, list-of-values, search views, AI configuration, and server-side event scripts that
every other plugin (finder, community, profile, mediadb, ...) reads or extends. It has no Java
`code/` folder — almost everything here is XML/XConf data and Groovy/Velocity event scripts.

### Folder Map

- `html/data/fields/` Table (entity) and property definitions — the schema itself
- `html/data/lists/` List-type field values (dropdown options, enabled flags, endpoint docs, etc.)
- `html/data/views/` Saved search/report views over the tables above
- `html/data/_site.xconf` Default page settings applied to everything under `html/data`
- `html/configuration/` Base templates and system config: `baseentitytemplate.xml` (base for
  `entity*` tables), `baseaiprompt.xml`, `beans.xml`, `embedding.xml`, `elementaljob.xml`
- `html/events/<module>/` Server-side event scripts grouped by domain (asset, billing, blog,
  categories, entities, goaltask, llm, notifications, publishing, users, ...) — these run on
  create/update/delete and other lifecycle hooks for the tables in `html/data/fields`
- `html/images/` Static assets used by catalog-owned pages (e.g. watermark)

### What This Plugin Owns

- The full data dictionary: every table name, its fields, and their types
- List-of-values used by dropdowns and enabled/ordering config across the app (including
  `aiskill`/`automationstep` which drive the AI automation pipeline defined in `plugins/finder`)
- REST endpoint documentation in `html/data/lists/endpoint/*.xml` (sample requests/responses for
  the JSON services implemented in `plugins/mediadb/html/services`)
- Cross-cutting event scripts that fire on data changes, independent of which plugin's UI
  triggered the change

### Editing Rules

- Never hand-write XML under `html/data/{fields,lists,views}` or `html/configuration` — use the
  `catalog-table-creator` skill (`.agents/skills/catalog-table-creator/SKILL.md`) so folder layout
  and cross-file linking conventions are followed correctly.
- Table names starting with `entity` follow the entity template pattern in
  `html/configuration/baseentitytemplate.xml`; anything else is a plain table.
- New list-type fields require a matching file under `html/data/lists/<fieldname>/`.
- Event scripts belong under `html/events/<module>/` grouped by the table/domain they react to;
  keep new scripts in the folder matching their table, not a catch-all location.
- If you add or change a REST-facing table, update the matching endpoint doc in
  `html/data/lists/endpoint/*.xml` so API consumers and AI agents can discover it.

### Validation Checklist

1. Confirm the new/changed field or list XML validates (well-formed XML, correct `id`s).
2. Clear the page cache (or restart) — changes to `html/data` and `html/configuration` are read
   through the xconf cache, not picked up on simple reload like a plain HTML file.
3. Reindex if a field's searchability/type changed, since Elasticsearch mapping is derived from
   these field definitions (see `plugins/system` for the index bootstrap).
4. Exercise the table from the admin UI (finder) to confirm the field/list renders and saves.

### Notes For Agents

- This plugin is data, not code — most "customization" requests that mention a new table, field,
  dropdown, or automation step land here, even if the feature is surfaced by another plugin's UI.
- `plugins/catalog/html/data/lists/automationstep/*.xml` controls which Java `Skill` classes (see
  `plugins/finder/.agents/skills/create-java-ai-skill/SKILL.md`) run and in what order.
