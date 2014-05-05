<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
	<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
	<title>OSM GAzetteer</title>
	<script type="text/javascript" src="static/js/jquery-1.11.1.min.js"></script>
	<script type="text/javascript" src="static/js/pure.min.js"></script>
	<link rel="stylesheet" href="static/css/view.css">
</head>
<body>
	<div id="wrapper">
		<div id="header">
			<form name="search">
				<input name="querry" id="search" type="text"></input>
			</form>
		</div>
		<div id="content">
		</div>
		<div id="templates" style="display: none;">
			<div class="features">
				<a class="feature" href=""></a>
			</div>
		</div>
	</div>
	<script type="text/javascript" src="static/js/viewer.js"></script>
</body>
</html>