<%@page import="org.json.JSONArray"%>
<%@page import="org.json.JSONObject"%>
<%@page import="me.osm.gazetteer.web.api.SearchAPI"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
	<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
	<title>OSM GAzetteer</title>
	<%-- <script type="text/javascript" src="static/js/jquery-1.11.1.min.js"></script>--%>
	<%-- <script type="text/javascript" src="static/js/pure.min.js"></script> --%>
	<link rel="stylesheet" href="static/css/view.css">
</head>
<body>
	<div id="wrapper">
		<div id="header">
			<form name="search">
				<input name="q" id="search" type="text"></input>
				<div>
					<span><input type="checkbox" name="type" value="adrpnt">Address</span>
					<span><input type="checkbox" name="type" value="plcpnt">Town</span>
					<span><input type="checkbox" name="type" value="hghway">Street</span>
					<span><input type="checkbox" name="type" value="admbnd">Admin. boundary</span>
					<span><input type="checkbox" name="type" value="poipnt">POI</span>
				</div>
				<div>
					<span><input type="checkbox" name="explain" value="true">explain</span>
				</div>
			</form>
		</div>
		<div id="content">
			<ul>
		<%
		JSONObject res = new SearchAPI().request(request);	 
		if(res!= null && res.has("features")) {
			JSONArray features = res.getJSONArray("features");
			JSONArray exp = res.optJSONArray("explanations");
			for(int i = 0; i < features.length(); i++) {
				JSONObject feature = features.getJSONObject(i);
				String expl = null;
				if(exp != null) {
					expl = exp.getString(i);
				}
				%>
				<li class="addr-row">
					<a href="feature?id=<%=feature.getString("feature_id") %>"><%=feature.optString("name") %></a>
					<%if (expl != null) { %>
						<div style="white-space: nowrap; font-size: 4;"><%=expl %></div>
					<%} %>	
				</li>
				<%
			}
		}	
		%></ul>	
		</div>
		<div id="templates" style="display: none;">
			<div class="features">
				<a class="feature" href=""></a>
			</div>
		</div>
	</div>
	<%-- <script type="text/javascript" src="static/js/viewer.js"></script> --%>
</body>
</html>