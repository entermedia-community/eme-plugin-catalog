// clearreloadagenttables.groovy
//
// Clears and resets data for the agent-job / automation tables, then clears
// the server caches. Server-side equivalent of
// bin/resources/tmp_clear_reload_agent_tables.sh:
//   per table : datamanager deleteall (wipe all rows) + restoredata (re-seed from XML)
//   finally   : clearcaches.html  (MediaArchive.clearAll())
//
// Per-table behaviour in this repo (checked against the code):
//   - agentjob, agentjobstep          : folderSearcher -> clear + re-seed from lists/<table> XML
//                                       (no list XML exists for these in this repo: clear only)
//   - automationscenario              : folderSearcher -> clear + re-seed from lists/automationscenario/*.xml
//   - agentjobrepeatperiod            : listSearcher   -> clear + re-seed from lists/agentjobrepeatperiod.xml
//   - agentjobrun, automationposition,
//     automationlabel                 : dataSearcher   -> resetData() is a no-op (clear only)
//
// Run from Settings > Scripts ("Debug" button), or as a path event.

import org.openedit.data.Searcher
import org.openedit.hittracker.HitTracker
import org.openedit.users.User

def tables = ["agentjob", "agentjobstep", "agentjobrun", "agentjobrepeatperiod",
		"automationscenario", "automationposition", "automationlabel", "view"]

def moduleManager = context.getSession().getAttribute("moduleManager")
if (moduleManager == null)
{
	log.error("moduleManager not found in session - cannot continue")
	return
}

def searcherManager = moduleManager.getBean("searcherManager")

def catalogId = context.getPageValue("catalogid")
if (catalogId == null || "$catalogid".equals(catalogId))
{
	catalogId = "site/catalog"
}

User user = context.getUser()

log.headline("Clearing + resetting agent/automation tables for catalog: " + catalogId)

for (String table : tables)
{
	try
	{
		Searcher searcher = searcherManager.getSearcher(catalogId, table)
		if (searcher == null)
		{
			log.info(table + " : no searcher found - skipping")
			continue
		}

		int before = countRows(searcher)
		searcher.deleteAll(user)
		searcher.resetData()
		int after = countRows(searcher)

		log.info(table + " : " + before + " rows -> cleared -> " + after + " rows after reset")
	}
	catch (Exception e)
	{
		log.error(table + " : FAILED - " + e.getMessage(), e)
	}
}

// Clear the server caches (same as clearcaches.html)
def mediaArchive = moduleManager.getBean(catalogId, "mediaArchive")
if (mediaArchive != null)
{
	mediaArchive.clearAll()
	log.info("Server caches cleared for catalog: " + catalogId)
}
else
{
	log.error("mediaArchive bean not found for catalog: " + catalogId)
}

def countRows(Searcher inSearcher)
{
	try
	{
		HitTracker hits = inSearcher.search(inSearcher.createSearchQuery())
		return hits.size()
	}
	catch (Exception e)
	{
		log.error("countRows failed for " + inSearcher.getSearchType() + ": " + e.getMessage())
		return -1
	}
}
