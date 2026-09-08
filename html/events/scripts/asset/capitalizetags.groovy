package asset;

import org.entermediadb.asset.MediaArchive
import org.entermediadb.asset.search.AssetSearcher
import org.openedit.Data
import org.openedit.MultiValued
import org.openedit.data.Searcher
import org.openedit.hittracker.HitTracker
import org.openedit.util.DateStorageUtil

public void init(){
	
	MediaArchive mediaarchive = (MediaArchive)context.getPageValue("mediaarchive");
	
	AssetSearcher searcher = mediaarchive.getAssetSearcher();
	
	HitTracker hits = searcher.getAllHits();
	
	if (hits != null)
	{
		hits.enableBulkOperations();
	}
	log.info("Starting to capitalize tags for ${hits.size()} assets.");
	List tosave = [];
	hits.each{
		
		Collection tags = it.getValues("keywords");
		if (tags != null) {
			//Upercase first letter of each tag
			tags = tags.collect { it.toString().capitalize() }
			it.setValues("keywords", tags);
			tosave.add(it);
		}

		if (tosave.size() >= 100)
		{
			mediaarchive.saveAssets(tosave);
			tosave.clear();
		}

	}
	
	
	mediaarchive.saveAssets(tosave);
}

init();