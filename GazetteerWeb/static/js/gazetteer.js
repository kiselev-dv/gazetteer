var app = angular.module('Gazetteer',[]);

app.directive('ngEnter', function () {
    return function (scope, element, attrs) {
        element.bind("keydown keypress", function (event) {
            if(event.which === 13) {
                scope.$apply(function (){
                    scope.$eval(attrs.ngEnter);
                });
 
                event.preventDefault();
            }
        });
    };
});

function MainController($scope, $http) {

	var controller = this;
	//$http.get(url, config)
	$scope.searchForm = {};
	$scope.searchForm.types = {};
	$scope.searchForm.type = [];

	$scope.searchForm.q = "";
	
	$scope.$watch('searchForm.types', function(v){
		$scope.searchForm.type = [];
		for(var k in v) {
			if (v[k]) {
				$scope.searchForm.type.push(k);
			}
		}
	}, true);
	
	$scope.searchForm.submitForm = function() {
		$http.get('/_search', {'params': controller.getParams($scope)})
			.success(function(data) {
				$scope.$broadcast('searchSuccess', {'data': data });
			});
		
		return false;
    }
	
}

MainController.prototype.getParams = function($scope) {
	var params = {};
	angular.extend(params, $scope.searchForm);
	params.types = undefined;
	return params;
}

app.controller('MainController', ['$scope', '$http', MainController]);

function ResultsListController($scope) {
	var controller = this;
	
	$scope.features = [{'name':'test'}];
	
	$scope.$on('searchSuccess', function(event, args){
		controller.searchSuccess(args);
	});
}

ResultsListController.prototype.searchSuccess = function(data) {
	alert(data);
};

app.controller('ResultsListController', ['$scope', ResultsListController]);