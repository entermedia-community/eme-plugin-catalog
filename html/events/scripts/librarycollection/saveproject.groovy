import org.entermediadb.asset.MediaArchive
import org.entermediadb.location.Position
import org.entermediadb.projects.*
import org.openedit.Data

import org.openedit.data.BaseSearcher
import org.openedit.data.Searcher
import org.openedit.util.URLUtilities

public void init()
{
	MediaArchive mediaArchive = context.getPageValue("mediaarchive");
	BaseSearcher collectionsearcher = mediaArchive.getSearcher("librarycollection");
	LibraryCollection data = (LibraryCollection)collectionsearcher.loadData(data);

	if( data == null)
	{
		log.error("Could not find collection " + id);
		return;
	}
	
	boolean saved = false;

	if( data.get("owner") == null && data.get("owner").equals(user.getId()) )
	{
		data.setValue("owner",user.getId());
		saved = true;
	}
	
	if( data.get("creationdate") == null )
	{
		data.setValue("creationdate", new Date());
		saved = true;
	}
	
	if( data.get("urlname") == null )
	{
		
		String name = URLUtilities.dash(data.getName()); 
		name = URLUtilities.urlEscape(name);
		data.setValue("urlname",name);
		saved = true;
	}

	//Search Google and put point on map
	if (data.get("geo_point") == null) 
	{
		String location = "";
		if (data.get("street")) {
			location = data.get("street");
		}
		if (data.get("city")) {
			location += " " + data.get("city");
		}
		Data country = mediaArchive.getData("country", data.get("country"));
		if (country) {
			location += " " + country;
		} 
		
		if (location != "")
		{
			//location = location.replaceAll("null","");
			Position p = (Position)collectionsearcher.getGeoCoder().findFirstPosition(location);
			if( p != null)
			{
				data.setValue("geo_point",p);
				data.setValue("geo_point_formatedaddress",p.getFormatedAddress());
			}	
			saved = true;
		}
	}

	if (saved)
	{
		collectionsearcher.saveData(data);
		log.info("librarycollection saved: " + data.getName() + " by: " + user.getId() );
	}
	
}

init();