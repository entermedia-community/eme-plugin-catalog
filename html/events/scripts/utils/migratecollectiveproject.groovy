// migratecollectiveproject.groovy
//
// One-off migration for the removal of the collectiveproject ("topic") table.
// Every project (librarycollection) only ever had one auto-created "General"/"Public"
// topic, so everything that pointed at a topic is re-pointed at the topic's project
// (its parentcollectionid):
//
//   1. channel rows with searchtype=collectiveproject  -> searchtype=librarycollection, dataid=project.
//      If the project already has an emeteamchat channel (e.g. created by the app), the topic
//      channel is merged into it: its messages and other channel rows move over, then it is deleted.
//   2. chatterbox messages whose channel (old OI chats) or entityid is a topic id -> the project's channel / id.
//   3. chattopiclastmodified / chattopiclastchecked rows keyed by a topic id -> keyed by the project id.
//   4. currencytransfer.collectiveproject -> currencytransfer.collectionid (when not set yet).
//
// The collectiveproject rows themselves are left in place. After checking the result, delete
// fields/collectiveproject.xml and the collectiveproject field in fields/currencytransfer.xml.
//
// Safe to run more than once. Run from Settings > Scripts ("Debug" button).

package utils

import org.entermediadb.asset.MediaArchive
import org.openedit.Data
import org.openedit.data.Searcher

public List loadAll(Searcher inSearcher, Collection inHits)
{
	List rows = new ArrayList();
	for (Object hit in inHits)
	{
		rows.add(inSearcher.loadData((Data) hit));
	}
	return rows;
}

/** The project's team chat channel, created when it has none yet. */
public Data findOrCreateProjectChannel(MediaArchive archive, String inCollectionId, Data inTemplate)
{
	Data channel = archive.query("channel").exact("searchtype", "librarycollection").exact("dataid", inCollectionId).exact("channeltype", "emeteamchat").sort("refreshdateDown").searchOne();
	if (channel != null)
	{
		return channel;
	}
	Searcher channels = archive.getSearcher("channel");
	channel = channels.createNewData();
	Data collection = archive.getCachedData("librarycollection", inCollectionId);
	channel.setName(collection != null ? collection.getName() : inCollectionId);
	channel.setValue("searchtype", "librarycollection");
	channel.setValue("dataid", inCollectionId);
	channel.setValue("channeltype", "emeteamchat");
	if (inTemplate != null)
	{
		channel.setValue("user", inTemplate.get("user"));
	}
	channel.setValue("refreshdate", new Date());
	channels.saveData(channel);
	return channel;
}

public int repointChannel(MediaArchive archive, String inField, String inOldValue, String inNewValue, List<String> inTables)
{
	int count = 0;
	for (String table in inTables)
	{
		Searcher searcher = archive.getSearcher(table);
		List rows = loadAll(searcher, archive.query(table).exact(inField, inOldValue).search());
		for (Data row in rows)
		{
			row.setValue(inField, inNewValue);
			searcher.saveData(row);
			count++;
		}
	}
	return count;
}

public void init()
{
	MediaArchive archive = (MediaArchive) context.getPageValue("mediaarchive");
	// Tables whose "channel" field holds a channel id and must follow a merged channel
	List<String> channeltables = ["chatterbox", "emeservernotification", "ragcontext", "tutoranswer", "tutordailychallenge"];

	// Topic id -> project id
	Map<String, String> projectoftopic = new HashMap();
	for (Object hit in archive.query("collectiveproject").all().search())
	{
		Data topic = (Data) hit;
		String collectionid = topic.get("parentcollectionid");
		if (collectionid != null)
		{
			projectoftopic.put(topic.getId(), collectionid);
		}
		else
		{
			log.error("Topic without a project, skipped: " + topic.getId() + " " + topic.getName());
		}
	}
	log.info("Topics found: " + projectoftopic.size());
	if (projectoftopic.isEmpty())
	{
		return;
	}
	Collection topicids = projectoftopic.keySet();

	// 1. Channels that point at a topic
	Searcher channels = archive.getSearcher("channel");
	Searcher agentcontexts = archive.getSearcher("agentcontext");
	int repointed = 0;
	int merged = 0;
	int movedrows = 0;
	List topicchannels = loadAll(channels, archive.query("channel").exact("searchtype", "collectiveproject").search());
	for (Data oldchannel in topicchannels)
	{
		String collectionid = projectoftopic.get(oldchannel.get("dataid"));
		if (collectionid == null)
		{
			log.error("Channel " + oldchannel.getId() + " points at unknown topic " + oldchannel.get("dataid") + ", skipped");
			continue;
		}
		Data target = archive.query("channel").exact("searchtype", "librarycollection").exact("dataid", collectionid).exact("channeltype", "emeteamchat").sort("refreshdateDown").searchOne();
		if (target == null)
		{
			oldchannel.setValue("searchtype", "librarycollection");
			oldchannel.setValue("dataid", collectionid);
			channels.saveData(oldchannel);
			repointed++;
			continue;
		}
		movedrows += repointChannel(archive, "channel", oldchannel.getId(), target.getId(), channeltables);
		for (Data oldcontext in loadAll(agentcontexts, archive.query("agentcontext").exact("channel", oldchannel.getId()).search()))
		{
			agentcontexts.delete(oldcontext, null);
		}
		channels.delete(oldchannel, null);
		merged++;
	}
	log.info("Channels re-pointed: " + repointed + ", merged into an existing project channel: " + merged + " (rows moved: " + movedrows + ")");

	// 2. Messages saved with the topic id as their channel or entity
	Searcher chats = archive.getSearcher("chatterbox");
	int movedmessages = 0;
	for (Data chat in loadAll(chats, archive.query("chatterbox").orgroup("channel", topicids).search()))
	{
		String collectionid = projectoftopic.get(chat.get("channel"));
		Data projectchannel = findOrCreateProjectChannel(archive, collectionid, null);
		chat.setValue("channel", projectchannel.getId());
		if (projectoftopic.containsKey(chat.get("entityid")))
		{
			chat.setValue("entityid", collectionid);
		}
		chats.saveData(chat);
		movedmessages++;
	}
	int fixedentities = 0;
	for (Data chat in loadAll(chats, archive.query("chatterbox").orgroup("entityid", topicids).search()))
	{
		chat.setValue("entityid", projectoftopic.get(chat.get("entityid")));
		chats.saveData(chat);
		fixedentities++;
	}
	log.info("Messages moved off topic ids: " + movedmessages + ", message entity ids fixed: " + fixedentities);

	// 3. Chat last-modified / last-checked trackers
	Searcher modified = archive.getSearcher("chattopiclastmodified");
	int trackers = 0;
	for (Data row in loadAll(modified, archive.query("chattopiclastmodified").orgroup("chattopicid", topicids).search()))
	{
		String collectionid = projectoftopic.get(row.get("chattopicid"));
		Data existing = archive.query("chattopiclastmodified").exact("chattopicid", collectionid).searchOne();
		if (existing != null)
		{
			modified.delete(row, null);
		}
		else
		{
			row.setValue("chattopicid", collectionid);
			row.setValue("collectionid", collectionid);
			modified.saveData(row);
		}
		trackers++;
	}
	Searcher checked = archive.getSearcher("chattopiclastchecked");
	for (Data row in loadAll(checked, archive.query("chattopiclastchecked").orgroup("chattopicid", topicids).search()))
	{
		String collectionid = projectoftopic.get(row.get("chattopicid"));
		Data existing = archive.query("chattopiclastchecked").exact("chattopicid", collectionid).exact("userid", row.get("userid")).searchOne();
		if (existing != null)
		{
			checked.delete(row, null);
		}
		else
		{
			row.setValue("chattopicid", collectionid);
			row.setValue("collectionid", collectionid);
			checked.saveData(row);
		}
		trackers++;
	}
	log.info("Chat trackers re-keyed: " + trackers);

	// 4. Currency transfers link straight to their project
	Searcher transfers = archive.getSearcher("currencytransfer");
	int linked = 0;
	for (Data transfer in loadAll(transfers, archive.query("currencytransfer").orgroup("collectiveproject", topicids).search()))
	{
		if (transfer.get("collectionid") == null)
		{
			transfer.setValue("collectionid", projectoftopic.get(transfer.get("collectiveproject")));
			transfers.saveData(transfer);
			linked++;
		}
	}
	log.info("Currency transfers linked to their project: " + linked);
	log.info("collectiveproject migration finished");
}

init();
