---
name: create-automation-scenario
description: Use when adding a new automationscenario (a node on the Automation map, e.g. a new chat mode or background workflow). Covers picking the automationlabel it hangs under (connectedtop), placing it on the semicircle below its label with the layout script, writing the scenario and its automationstep rows (including the <scenarioid>_welcome step chat uses), and reloading the data.
---

# Create an Automation Scenario

An automation scenario is a node on the big Automation map. Building one means writing rows in
four list tables under `plugins/catalog/html/data/lists/`:

| Table | What it holds | Files |
|---|---|---|
| `automationlabel` | Group headings on the map ("Server Chat", "Entity Chat", ...) | `automationlabel/scenariolabels.xml` |
| `automationscenario` | The scenario node. `connectedtop` = the label it sits under | `automationscenario/<connectedtop>.xml` (one file per label) |
| `automationposition` | X/Y of every node, labels **and** scenarios, keyed by the same id | `automationposition/scenariopositions.xml` |
| `automationstep` | The steps the scenario runs, each pointing at an `aiskill` | `automationstep/*.xml` |

Every scenario needs answers to two questions first: **which label is it under** and **where on
the map does it go**.

## Step 1: Choose the automationlabel

Read `automationlabel/scenariolabels.xml`. Current labels:

| Label id | Text on map | Scenario file | Used by |
|---|---|---|---|
| `eme_chat` | EME Chat | `automationscenario/eme_chat.xml` | Server-wide/customer chat (MCP, `chat_createjob_server`) |
| `entity_chat` | Entity Chat | `automationscenario/entity_chat.xml` | Chat attached to an entity/module record |
| `team_chat` | Team Chat | `automationscenario/team_chat.xml` | Team channels (`emeteamchat_responder`, goals) |
| `communication_tools` | Communication Tools | `automationscenario/communication_tools.xml` | Email, publishing |
| `content_creation_tools` | Content Creation Tools | `automationscenario/content_creation_tools.xml` | Image/Smart Creator |
| `file_management_tools` | File Management Tools | `automationscenario/file_management_tools.xml` | Hot folders, asset processing |

The scenario's `connectedtop` attribute is set to the label id; that draws the line from the
label down to the scenario. Pages filter on it too, e.g.
`$mediaarchive.query("automationscenario").exact("connectedtop","entity_chat")` builds the Entity
Chat menu. A new label needs its own row in `scenariolabels.xml` (`id`, `text`, `strokecolor`,
`bgcolor`) **and** its own `automationposition` row.

## Step 2: Choose the automationposition

`automationposition/scenariopositions.xml` holds one `<data id="..." posx="..." posy="...">` per
node. The id is the scenario id (or label id). Rules from
`plugins/community/html/default/components/javascript/emedia/agentautomation.js`:

- `posx`/`posy` are the node's top-left corner. Scenario nodes are drawn **200 x 200**; labels are
  padded text boxes about 250 x 60.
- y grows downward.
- A scenario with no position row is not drawn (the console logs `Positions not set!`).

### Layout: a semicircle below each label

Each label's children (scenarios whose `connectedtop` is that label) sit on a semicircle **below**
the label:

- The arc runs from 15° above horizontal on the left, under the label, to 15° on the right.
- The radius is at least 320px and grows with the number of children, so neighbouring centres are at
  least 290px apart (more than 200 × √2, so boxes never overlap, even on the diagonal).
- Every scenario is pushed down another 100px, so the top of its box is always below the label.
- The `welcome_menu_*` scenario comes first (leftmost); the rest keep their left-to-right order.
- The labels are in two rows, spread out so the arcs don't collide:

| Row | Labels (left to right) | Label posy |
|---|---|---|
| Top | `file_management_tools`, `communication_tools`, `content_creation_tools` | 1430 |
| Bottom | `eme_chat`, `entity_chat`, `team_chat` | 2133 |

Don't place new nodes by hand. Add the scenario (Step 3), then rerun the layout script. It
recomputes every label and scenario position, prints them with any overlaps, and with `--write`
updates `scenariopositions.xml`, adding rows for scenarios that don't have one yet:

```bash
cd plugins/catalog
python3 .agents/skills/create-automation-scenario/scripts/layout_map.py           # preview
python3 .agents/skills/create-automation-scenario/scripts/layout_map.py --write   # update the file
```

Run it after Step 3's scenario row is written, since it finds children from the `connectedtop`
attributes in `automationscenario/*.xml`. A new label must also be added to `ROWS` at the top of the
script. The constants there (`ALPHA`, `GAP`, `DROP`) control the arc angle, spacing and drop.

Positions also change when someone drags nodes in the map editor (it saves through
`AutomationManager.savePositions`), so the database can differ from the file. The script overwrites
dragged positions after the reload in Step 5. Check what is in the database first:

```bash
AUTH='Authorization: Bearer adminmd5421c0af185908a6c0c40d50fd5e3f16760d5580bc'
curl -s -H "$AUTH" -X POST -H 'Content-Type: application/json' \
  -d '{"page":"1","hitsperpage":"200","query":{"terms":[{"field":"id","operator":"matches","value":"*"}]}}' \
  http://localhost:8080/site/mediadb/services/lists/search/automationposition
```

## Step 3: Write the scenario row

Add it to `automationscenario/<connectedtop>.xml`, the file named after the label you chose in Step 1. Every row in a file has that file's label as its `connectedtop`; a new label gets a new file. If you change a scenario's `connectedtop`, move its row to the new label's file. If the label has a `welcome_menu_<label id>` scenario, keep that row **first** in the file and add new rows after it.

```xml
<data id="my_scenario" ordering="50" scenarioicon="robot" enabled="true" isvisible="true"
      connectedtop="entity_chat" chatenabled="true">
  <name>
    <language id="en"><![CDATA[My Scenario]]></language>
  </name>
  <markdowncontent><![CDATA[One-sentence description. It is shown in menus and used for search.]]></markdowncontent>
</data>
```

- `connectedtop`: the label id from Step 1.
- `chatenabled="true"`: the scenario can be picked in chat menus. `isvisible`: shown in lists.
- `ordering`: sort order inside menus (lower comes first).
- `scenarioicon`: a Bootstrap Icons name without the `bi-` prefix (`robot`, `search`, `broadcast`, `list`).

Then run the layout script from Step 2 with `--write`. It adds the position row, using the
**same id**:

```xml
<data id="my_scenario" posx="1700.0" posy="2833.0">
  <name/>
</data>
```

## Step 4: Write the steps

Steps go in `automationstep/<file>.xml` (an existing one or a new file). Each step has
`automationscenario="my_scenario"` and an `aiskill` bean id. Steps without `runafter` run first;
the others are chained with `runafter="<previous step id>"`.

For a chat scenario, the first step's id must be **`<scenarioid>_welcome`**. When the user switches
chat scenarios, `AgentModule` runs the function `<scenarioid>_welcome` by default:

```xml
<data id="my_scenario_welcome" aiskill="renderLocalTemplateSkill" automationscenario="my_scenario"
      enabled="true" agenttype="eventagent" processingmessage="Welcome">
  <name><![CDATA[Welcome]]></name>
</data>
```

`renderLocalTemplateSkill` renders `plugins/mediadb/html/views/agentresponses/<stepid>.html`, so
also create `my_scenario_welcome.html` there. To write a new Java skill for a step, use the
`create-java-ai-skill` skill.

## Step 5: Reload

These are list tables, so edits do nothing until you reset each changed table and clear caches. The
label/position maps are also cached (`automationscenariopositionmap`, `automationlabelsmap`):

```bash
AUTH='Authorization: Bearer adminmd5421c0af185908a6c0c40d50fd5e3f16760d5580bc'
for t in automationscenario automationposition automationstep automationlabel; do
  curl -s -o /dev/null -H "$AUTH" "http://localhost:8080/site/find/views/settings/lists/datamanager/list/restoredata.html?searchtype=$t&oemaxlevel=1"
done
curl -s -o /dev/null -H "$AUTH" http://localhost:8080/site/find/views/settings/status/tools/clearcaches.html
```

Add `aiskill` to the loop if you added an aiskill row. If you added a Java class or edited
`plugin.xml`, run `bin/restart.sh` first. If you renamed or removed a row, delete the old id
explicitly (see `reload-list-data`), because `restoredata` only adds and updates.

## Step 6: Verify

- Open the Automation map. The node is on its label's semicircle, below the label, with a line up to it.
- For chat scenarios, the scenario appears in that label's chat menu, and choosing it renders the
  `_welcome` step.

## Example: the welcome menus

`welcome_menu_eme_chat` and `welcome_menu_entity_chat`
follow this recipe. Their ids are `welcome_menu_` + the label id in `connectedtop`. Each one is the first row of its label's scenario file. Each has one
step, `welcome_menu_<label>_welcome` in `automationstep/welcome_menu.xml`, which runs
`welcomeMenuSkill` (`WelcomeMenuSkill.java`). That skill lists the other chat scenarios under the
same label and renders them with `agentresponses/welcome_menu.html`.
