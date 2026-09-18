package entities;

import org.entermediadb.asset.MediaArchive
import org.entermediadb.users.PermissionManager
import org.openedit.Data
import org.openedit.data.Searcher
import java.util.ArrayList

public void init()
{
	MediaArchive mediaarchive = (MediaArchive) context.getPageValue("mediaarchive");

	String moduleid = context.getRequestParameter("moduleid");
	String groupid = context.getRequestParameter("settingsgroupid");
	
	PermissionManager permissionManager = mediaarchive.getBean("permissionManager");

	String[] fields = context.getRequestParameters("field");
	
	Searcher permissionsSearcher = mediaarchive.getSearcher("permissionentityassigned");

	Collection<Data> existing = permissionsSearcher.query().exact("moduleid", moduleid).exact("group", groupid).search();
	permissionsSearcher.deleteAll(existing, null);
	
	Collection<Data> tosave = new ArrayList<Data>();
	for (permissionid in fields) {
		String permissionidvalue = context.getRequestParameter(permissionid+".value");
		if(permissionidvalue == "true")
		{
			Data data = permissionsSearcher.createNewData();
			data.setValue("moduleid", moduleid);
			data.setValue("group", groupid);
			data.setValue("permissionsentity", permissionid);
			data.setValue("enabled", true);
			tosave.add(data);
		}
	}
	permissionsSearcher.saveAllData(tosave, null);

	mediaarchive.getSearcherManager().getCacheManager().clear("permissions" + mediaarchive.getCatalogId());
    
}

init();

