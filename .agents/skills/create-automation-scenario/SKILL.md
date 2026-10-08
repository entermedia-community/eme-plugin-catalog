---
name: create-automation-scenario
description: Use when adding a new automationscenario (a node on the Automation map, e.g. a new chat mode or background workflow). Covers picking the automationlabel it hangs under (connectedtop), picking a free X/Y automationposition on the map, writing the scenario and its automationstep rows (including the <scenarioid>_welcome step chat uses), and reloading the data.
---

# Create an Automation Scenario

An automation scenario is a node on the big Automation map. Building one means writing rows in
four list tables under `plugins/catalog/html/data/lists/`:

| Table | What it holds | Files |
|---|---|---|
| `automationlabel` | Group headings on the map ("Server Chat", "Entity Chat", ...) | `automationlabel/scenariolabels.xml` |
| `automationscenario` | The scenario node. `connectedtop` = the label it sits under | `automationscenario/*.xml` (grouped by label) |
| `automationposition` | X/Y of every node, labels **and** scenarios, keyed by the same id | `automationposition/scenariopositions.xml` |
| `automationstep` | The steps the scenario runs, each pointing at an `aiskill` | `automationstep/*.xml` |

Every scenario needs answers to two questions first: **which label is it under** and **where on
the map does it go**.

## Step 1: Choose the automationlabel

Read `automationlabel/scenariolabels.xml`. Current labels:

| Label id | Text on map | Scenario file | Used by |
|---|---|---|---|
| `customerservicelabel` | Server Chat | `automationscenario/server.xml` | Server-wide/customer chat (MCP, `chat_createjob_server`) |
| `chatlabel` | Entity Chat | `automationscenario/chatentity.xml` | Chat attached to an entity/module record |
| `teamchatlabel` | Team Chat | `automationscenario/chatteam.xml` | Team channels (`chat_detection`, goals) |
| `publishlabel` | Communication Tools | `automationscenario/publishing.xml` | Email, publishing |
| `contentcreationlabel` | Content Creation Tools | `automationscenario/imagecreation.xml`, `smartcreator_*.xml` | Image/Smart Creator |
| `importinglabel` | File Management Tools | `automationscenario/asset.xml` | Hot folders, asset processing |

The scenario's `connectedtop` attribute is set to the label id; that draws the line from the
label down to the scenario. Pages filter on it too, e.g.
`$mediaarchive.query("automationscenario").exact("connectedtop","chatlabel")` builds the Entity
Chat menu. A new label needs its own row in `scenariolabels.xml` (`id`, `text`, `strokecolor`,
`bgcolor`) **and** its own `automationposition` row.

## Step 2: Choose the automationposition

`automationposition/scenariopositions.xml` holds one `<data id="..." posx="..." posy="...">` per
node. The id is the scenario id (or label id). Rules from
`plugins/community/html/default/components/javascript/emedia/agentautomation.js`:

- `posx`/`posy` are the node's top-left corner. Scenario nodes are drawn **200 x 200**.
- y grows downward. To sit "under" a label, use a `posy` larger than the label's (about +100 to
  +300) and a `posx` near the label's.
- A scenario with no position row is not drawn (the console logs `Positions not set!`).
- Labels are about 50px tall, so the first row of scenarios usually starts ~120px below the label.

Current label positions:

| Label | posx | posy |
|---|---|---|
| `importinglabel` | 1141 | 1429 |
| `contentcreationlabel` | 2404 | 1407 |
| `publishlabel` | 1789 | 1448 |
| `chatlabel` | 1784 | 1983 |
| `teamchatlabel` | 2198 | 1983 |
| `customerservicelabel` | 1005 | 2113 |

Pick a spot that does not overlap another 200x200 box. This lists any overlap with a candidate:

```bash
python3 - <<'EOF'
import re
x, y = 1784.0, 2100.0   # candidate posx, posy
s = open('plugins/catalog/html/data/lists/automationposition/scenariopositions.xml').read()
for id_, px, py in re.findall(r'id="([^"]+)"\s+posx="([^"]+)"\s+posy="([^"]+)"', s):
    px, py = float(px), float(py)
    if abs(px - x) < 200 and abs(py - y) < 200:
        print("overlaps", id_, px, py)
EOF
```

Positions also change when someone drags nodes in the map editor (it saves through
`AutomationManager.savePositions`), so the database can differ from the file. Check before you
pick:

```bash
AUTH='Authorization: Bearer adminmd5421c0af185908a6c0c40d50fd5e3f16760d5580bc'
curl -s -H "$AUTH" -X POST -H 'Content-Type: application/json' \
  -d '{"page":"1","hitsperpage":"200","query":{"terms":[{"field":"id","operator":"matches","value":"*"}]}}' \
  http://localhost:8080/site/mediadb/services/lists/search/automationposition
```

## Step 3: Write the scenario row

Add it to the scenario file for the label you chose (Step 1):

```xml
<data id="my_scenario" ordering="50" scenarioicon="robot" enabled="true" isvisible="true"
      connectedtop="chatlabel" chatenabled="true">
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

Then add the position row from Step 2 with the **same id**:

```xml
<data id="my_scenario" posx="1784.0" posy="2100.0">
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

- Open the Automation map. The node is drawn at your X/Y, with a line up to its label.
- For chat scenarios, the scenario appears in that label's chat menu, and choosing it renders the
  `_welcome` step.

## Example: the welcome menus

`welcome_menu_customerservicelabel`, `welcome_menu_chatlabel` and `welcome_menu_teamchatlabel`
follow this recipe. Their ids are `welcome_menu_` + the label id in `connectedtop`. Each has one
step, `welcome_menu_<label>_welcome` in `automationstep/welcome_menu.xml`, which runs
`welcomeMenuSkill` (`WelcomeMenuSkill.java`). That skill lists the other chat scenarios under the
same label and renders them with `agentresponses/welcome_menu.html`.
