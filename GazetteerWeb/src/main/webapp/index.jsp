<%@page import="java.util.HashSet"%>
<%@page import="java.util.Arrays"%>
<%@page import="java.util.Collections"%>
<%@page import="java.util.Set"%>
<%@page import="org.apache.commons.lang3.StringUtils"%>
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
				<input name="q" id="search" type="text" 
					value="<%=StringUtils.stripToEmpty(request.getParameter("q")) %>"></input>
				
				<% 
				Set<String> types = new HashSet<String>();
				
				if (request.getParameterMap().get("type") != null) {
					types.addAll(Arrays.asList(request.getParameterValues("type")));
				}
				
				%>
				
				<div>
					<span><input type="checkbox" name="type" 
						value="adrpnt" <%=types.contains("adrpnt") ? "checked=\"yes\"" : "" %>>Address</span>
					
					<span><input type="checkbox" name="type" value="plcpnt" 
						 <%=types.contains("plcpnt") ? "checked=\"yes\"" : "" %>>Town</span>
						
					<span><input type="checkbox" name="type" value="hghway" 
						<%=types.contains("hghway") ? "checked=\"yes\"" : "" %>>Street</span>
					
					<span><input type="checkbox" name="type" value="admbnd" 
						<%=types.contains("admbnd") ? "checked=\"yes\"" : "" %>>Admin. boundary</span>
					
					<span><input type="checkbox" name="type" value="poipnt" 
						<%=types.contains("poipnt") ? "checked=\"yes\"" : "" %>>POI</span>
				</div>
				
				<div>
					<span><input type="checkbox" name="explain" value="true" 
						<%="true".equals(request.getParameter("explain")) ? "checked=\"yes\"" : "" %>>explain</span>
				</div>
				
			</form>
			
			<a href="hierarchy.jsp">Hierarchy</a>
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
					<a href="feature.jsp?id=<%=feature.getString("feature_id") %>"><%=feature.optString("address") %></a>
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