var app = angular.module('Gazetteer', [ 'ngRoute', 'leaflet-directive' ]);

app.config(['$locationProvider', function($locationProvider) {
	$locationProvider.hashPrefix('!');
}]);

app.config(function($routeProvider) {
    $routeProvider
    	.when('/feature=:fid', {templateUrl: '/static/feature.html', controller:'FeatureController' });
});


app.directive('ngEnter', function() {
	return function(scope, element, attrs) {
		element.bind("keydown keypress", function(event) {
			if (event.which === 13) {
				scope.$apply(function() {
					scope.$eval(attrs.ngEnter);
				});

				event.preventDefault();
			}
		});
	};
});

function MainController($scope, $http, $location, leafletBoundsHelpers) {

	var controller = this;
	$scope.searchForm = {};
	$scope.searchForm.types = {};
	$scope.searchForm.type = [];

	$scope.searchForm.q = "";

	$scope.$watch('searchForm.types', function(v) {
		$scope.searchForm.type = [];
		for ( var k in v) {
			if (v[k]) {
				$scope.searchForm.type.push(k);
			}
		}
	}, true);

	$scope.searchForm.submitForm = function() {
		$http.get('/_search', {
			'params' : controller.getParams($scope)
		}).success(function(data) {
			$scope.searchResult = data;
		});

		return false;
	};

	$scope.center = {lat : 47.398, lng : 18.677, zoom : 4};
	$scope.defaults = {
		scrollWheelZoom : false
	};
	$scope.markersMap = {};
	$scope.markers = [];

	$scope.bounds = leafletBoundsHelpers.createBoundsFromArray([
			[ 51.508742458803326, -0.087890625 ],
			[ 51.508742458803326, -0.087890625 ] ]);

	$scope.$watch('searchResult', function(results) {
		$scope.markersMap = {};
		$scope.markers = [];
		
		if (results) {
			angular.forEach(results.features, function(v, k) {
				if (!(v.center_point.lat == 0 && v.center_point.lon == 0)) {
					var m = {
						'lat' : v.center_point.lat,
						'lng' : v.center_point.lon,
						'message' : $scope.frmtSrchRes(v)
					};

					$scope.markersMap[hashCode(v.id)] = m;
					$scope.markers.push(m);

					$scope.bounds = L.latLngBounds($scope.markers);
				}
			});
		}
	});

	$scope.frmtSrchRes = function(f) {
		if (f.type == 'adrpnt') {
			return f.address;
		}
		if (f.type == 'poipnt') {
			return f.poi_class_names[0] + ' ' + (f.name || '') + ' (' + f.address + ')';
		}
		return f.name;
	};

}

MainController.prototype.getParams = function($scope) {
	var params = {};
	angular.extend(params, $scope.searchForm);
	params.types = undefined;
	params.submitForm = undefined;
	return params;
}

app.controller('MainController', ['$scope', '$http', '$location',
                              		'leafletBoundsHelpers', MainController ]);

function hashCode(str){
    var hash = 0,
        len = str.length;

    for (var i = 0; i < len; i++) {
        hash = hash * 31 + str.charCodeAt(i);
    }
    return hash;
}

function FeatureController($scope, $routeParams, $http) {
	$http.get('/feature', {
		'params':{
			'id': $routeParams.fid,
			'format': 'json'
		}
	}).success(function(data) {
		$scope.feature = data;
	});
}
