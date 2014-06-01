var app = angular.module('Gazetteer',[]);

app.controller("MainController", function($scope){
	
	//$http.get(url, config)
	$scope.searchForm = {};
	$scope.searchForm.q = "";
	
	$scope.searchForm.submitForm = function() {
		console.log('asd');
        return false;
    }
	
});