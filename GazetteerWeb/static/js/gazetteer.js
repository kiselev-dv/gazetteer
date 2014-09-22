var app = angular.module('Gazetteer', [ 'ngRoute' ]);

app.config(['$locationProvider', function($locationProvider) {
	$locationProvider.hashPrefix('!');
}]);

app.config(function($routeProvider) {
    $routeProvider
    	.when('/search', {
    		templateUrl: '/static/search.html', 
    		controller:'SearchController', 
    		reloadOnSearch: false });
    
    $routeProvider
	.when('/feature/:id', {
		templateUrl: '/static/feature.html', 
		controller:'FeatureController'});

    $routeProvider
    	.when('/', {redirectTo: '/search'});
    
    $routeProvider.otherwise({
            redirectTo: '/search'
        });
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


function SearchController($scope, $http, $location, $routeParams, $rootScope) {

	var controller = this;
	
	$scope.searchForm = {};
	$scope.searchForm.types = {};
	$scope.searchForm.type = [];

	$scope.$watch(function () {return $location.search();}, 
		function() {
			$scope.searchForm.q = $location.search()['q'] || '';
			$scope.searchForm.type = $location.search()['type'] || [];
			$scope.selectedRowId = $location.search()['id'];
			$scope.searchForm.submitForm();
		}
	);
	     
    $scope.$watch('searchForm.q', function(term) {
       $location.search('q', term);
    });

    $scope.$watch('searchForm.type', function(term) {
    	$location.search('type', term);
    	var h = {};
    	if(Array.isArray(term)) {
    		for(i in term) {
    			h[term[i]] = true;
    		}
    	}
    	else if (term) {
    		h[term] = true;
    	}
    	$scope.searchForm.types = h;
    });
	
	$scope.$watch('searchForm.types', function(v) {
		$scope.searchForm.type = [];
		for ( var k in v) {
			if (v[k]) {
				$scope.searchForm.type.push(k);
			}
		}
	}, true);

	$scope.searchForm.submitForm = function() {

		if($scope.searchForm.q) {
			$http.get('/_search', {
				'params' : controller.getParams($scope)
			}).success(function(data) {
				$scope.searchResult = data;
			});
		}

		return false;
	};
	
	$scope.map = L.map('map').setView([47.398, 18.677], 4);
	L.tileLayer('http://{s}.tile.osm.org/{z}/{x}/{y}.png', {
	    attribution: '&copy; <a href="http://osm.org/copyright">OpenStreetMap</a> contributors'
	}).addTo($scope.map);
	
	$scope.markersMap = {};
	$scope.markers = [];
	
	$scope.$watch('selectedRowId', function(rid) {
		$location.search('id', rid);
		if(rid) {
			$http.get('/feature', {
				'params':{
					'row': rid,
					'format': 'json'
				}
			}).success(function(data) {
				$scope.feature = data;
				$scope.$broadcast('showFeature', {'fid':data.feature_id, 'rid':$routeParams.rowId});
			});
		}
	}, true);

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
				    	.bindPopup($scope.frmtSrchRes(v))
				    	.on('click', function(event){
				    		$scope.selectF(v, event);
				    		$scope.$digest();
				    	});
					
				    m.rid = '' + v.id;
					m.fid = '' + v.feature_id;
				    	
					$scope.markersMap['' + v.id] = m;
					$scope.markers.push(m);
					
					if(v.id == $scope.selectedRowId) {
						m.openPopup();
						$scope.map.setView(m.getLatLng(), 17);
					}
					
					lls.push(L.latLng(v.center_point.lat, v.center_point.lon));
				}
			});

			$scope.bounds = L.latLngBounds(lls);
			$scope.map.fitBounds($scope.bounds);
		}
	});
	
	$scope.selectF = function(f, $event) {
		$scope.selectedRowId = f.id;
		if($scope.markersMap[$scope.selectedRowId]) {
			$scope.selectedMarker = $scope.markersMap[$scope.selectedRowId];
			$scope.map.setView($scope.selectedMarker.getLatLng(), 17);
			$scope.selectedMarker.openPopup();
		}
	};

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

SearchController.prototype.getParams = function($scope) {
	var params = {};
	angular.extend(params, $scope.searchForm);
	params.types = undefined;
	params.submitForm = undefined;
	return params;
}

function FeatureController($scope, $http, $location, $routeParams) {
	$http.get('/feature', {
		'params' : {
			'id': $routeParams.id,
			'related': true
		}
	}).success(function(data) {
		$scope.feature = data;
		if($scope.feature._related) {
			
			$scope.related = {};
			
			for(var k in data._related) {
				for(var i in data._related[k]) {
					var f = data._related[k][i];
					var key = k;
					if(f._hitFields) {
						for(var hfi in f._hitFields) {
							var hf = f._hitFields[hfi];
							if(hf.indexOf('refs') >= 0) {
								key += 'ref';
							}
						}
					}
					if(!$scope.related[key]){
						$scope.related[key] = [];
					}
					$scope.related[key].push(f);
				}
			}
			
			$scope.feature._related = undefined;
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

function unique(arr) {
	var sorted = arr.sort(function (a, b) { return a*1 - b*1; });
    var ret = [sorted[0]];
    for (var i = 1; i < sorted.length; i++) { 
        if (sorted[i-1] !== sorted[i]) {
            ret.push(sorted[i]);
        }
    }
    return ret;
}
