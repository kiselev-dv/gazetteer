<%@page import="java.util.Arrays"%>
<%@page import="org.json.JSONArray"%>
<%@page import="org.json.JSONObject"%>
<%@page import="me.osm.gazetteer.web.api.FeatureAPI"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
   
<%! 
private static final String parseLVL(JSONObject addr, String key) {
	
	String text = addr.optString(key + "_name", null);
	
	if(text != null) {
		JSONObject refs = addr.optJSONObject("refs");
		if(refs != null) {
			String lnk = refs.optString(key, null);
			if(lnk != null) {
				return "<span><a href=\"feature.jsp?id=" + lnk + "\">" + text + "</a></span>";
			}
		}
		else {
			return "<span>" + text + "</span>";
		}
	}
	
	return null;	
}

public static final String formatAddrString(JSONObject addr) {
	
	StringBuilder result = new StringBuilder();
	
	for(String k : Arrays.asList("admin0", "admin1", "admin2", "local_admin", "locality", "neighborhood", "street")){
		String lvl = parseLVL(addr, k);
		if(lvl != null) {
			result.append(", ").append(lvl);
		}
	}
		
	String hn = addr.optString("housenumber", null);
	
	if(hn != null) {
		result.append(", ").append(hn);
	}
	
	if(result.length() > 2) {
		return result.substring(2);
	}
	
	return "";
}

private static String getLinkToOSM(JSONObject f) {
	JSONObject cp = f.getJSONObject("center_point");
	return "http://www.openstreetmap.org/"
			+ "?mlat=" + cp.getDouble("lat") + "&mlon=" + cp.getDouble("lon")
			+ "#map=19/" + cp.getDouble("lat") + "/" + cp.getDouble("lon"); 
}

private static String getHrefToOSM(JSONObject f) {
	JSONObject cp = f.getJSONObject("center_point");
	return "<a href=\"" + getLinkToOSM(f) + "\">" + cp.getDouble("lat") + ", " + cp.getDouble("lon") + "</a>"; 
}
%>    
    
<% 
	JSONObject feature = new FeatureAPI().request(request);
	if(feature == null) {
		response.sendError(404);
	}
	
	String title = feature.getString("name");
	if(feature.has("poi_class")) {
		title += " (" + feature.getString("poi_class") + ")";
	}
%>    
    
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>Feature <%=feature.getString("feature_id") %></title>
</head>
<body>
	<h3><%=title %></h3>
	<%
	JSONArray addresses = feature.getJSONArray("addresses");
	for(int i = 0; i < addresses.length(); i++){
		JSONObject addr = addresses.getJSONObject(i);
	%>
		<div><%=formatAddrString(addr) %></div>
	<%} %>
	<div><%=getHrefToOSM(feature) %></div>
</body>
</html>