var app = angular.module('Gazetteer', [ 'ngRoute' ]);

app.config(['$locationProvider', function($locationProvider) {
	$locationProvider.hashPrefix('!');
}]);

app.config(function($routeProvider) {
    $routeProvider
    	.when('/feature=:fid&rowId=:rowId', {templateUrl: '/static/feature.html', controller:'FeatureController' });
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


function MainController($scope, $http, $location) {

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

	$scope.map = L.map('map').setView([47.398, 18.677], 4);
	L.tileLayer('http://{s}.tile.osm.org/{z}/{x}/{y}.png', {
	    attribution: '&copy; <a href="http://osm.org/copyright">OpenStreetMap</a> contributors'
	}).addTo($scope.map);
	
	$scope.markersMap = {};
	$scope.markers = [];

	$scope.$watch('searchResult', function(results) {
		
		angular.forEach($scope.markers, function(v, k) {
			$scope.map.removeLayer(v);
		});
		
		$scope.markersMap = {};
		$scope.markers = [];
		
		var lls = [];
		if (results) {
			angular.forEach(results.features, function(v, k) {
				if (!(v.center_point.lat == 0 && v.center_point.lon == 0)) {
					
					var m = L.marker([v.center_point.lat, v.center_point.lon]).addTo($scope.map)
				    	.bindPopup('<a href="#!/feature=' + v.feature_id + '&rowId=' + v.id + '">' + $scope.frmtSrchRes(v) + '</a>')
					
				    m.rid = hashCode(v.id);
					m.fid = hashCode(v.feature_id);
				    	
					$scope.markersMap[hashCode(v.id)] = m;
					$scope.markers.push(m);
					
					lls.push(L.latLng(v.center_point.lat, v.center_point.lon));
				}
			});

			$scope.bounds = L.latLngBounds(lls);
			$scope.map.fitBounds($scope.bounds);
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
	
	$scope.$on('showFeature', function(event, data) {
		$scope.selectedRowId = data.rid;
		if($scope.markersMap[$scope.selectedRowId]) {
			$scope.selectedMarker = $scope.markersMap[$scope.selectedRowId];
			$scope.map.setView($scope.selectedMarker.getLatLng(), 17);
			$scope.selectedMarker.openPopup();
		}
		
    });

}

MainController.prototype.getParams = function($scope) {
	var params = {};
	angular.extend(params, $scope.searchForm);
	params.types = undefined;
	params.submitForm = undefined;
	return params;
}

app.controller('MainController', ['$scope', '$http', '$location', MainController ]);

function hashCode(str){
	return '' + str;
}

function FeatureController($scope, $rootScope, $routeParams, $http) {
	$http.get('/feature', {
		'params':{
			'id': $routeParams.fid,
			'format': 'json'
		}
	}).success(function(data) {
		$scope.feature = data;
		$rootScope.$broadcast('showFeature', {'fid':data.feature_id, 'rid':$routeParams.rowId});
	});
}

