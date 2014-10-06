var HTML_ROOT = '/static';
var API_ROOT = '';

var app = angular.module('Map', [ 'ngRoute', 'ngCookies', 'ngResource' ]);

app.config(['$locationProvider', function($locationProvider) {
	$locationProvider.hashPrefix('!');
}]);

app.config(function($routeProvider) {
    $routeProvider
    	.when('/map', {
    		templateUrl: HTML_ROOT + '/templates/map.html', 
    		controller:'MapController', 
    		reloadOnSearch: false });

    $routeProvider
    	.when('/', {redirectTo: '/map'});
    
    $routeProvider.otherwise({
    	redirectTo: '/map'
    });
});

(function( ng, app ) {
	
	MapController = function ($scope, $cookies, i18nService, 
			osmdocHierarchyService, searchAPI) {
		
		i18nService.getTranslation($scope, 'ru');
		osmdocHierarchyService.loadHierarchy($scope, 'ru');
		
		addMap($scope);
		
		$scope.cathegories = {
			features:[],
			groups:[]
		};
		
		$scope.expand = function(obj) {
			obj.expanded = !!!obj.expanded;
		};
		
		$scope.select = function(obj, type) {
			if(obj.selected) {
				MapController.addSelection(obj, $scope.cathegories[type]);
			}
			else {
				MapController.removeSelection(obj, $scope.cathegories[type]);
			}
		};
		
		$scope.$watch('cathegories', function(types){
			searchAPI.listPOI($scope, 1);
		}, true);
		
		$scope.id2Feature = {};
		$scope.id2Marker = {};
		
		$scope.map.on('viewreset', function(){
			searchAPI.listPOI($scope, 1);
		});
		
		$scope.map.on('moveend', function(){
			searchAPI.listPOI($scope, 1);
		});
	}; 
	
	MapController.addSelection = function(obj, arr) {
		if(obj && arr) {
			arr.push(obj.name);
		}
	};

	MapController.removeSelection = function(obj, arr) {
		if(obj && arr) {
			for(var i=0; i<arr.length; i++) {
				if(arr[i] == obj.name) {
					arr.splice(i, 1);
					break;
				}
			}
		}
	};
	
	app.controller('MapController',['$scope', '$cookies', 'i18nService', 
	     'osmdocHierarchyService', 'SearchAPI', MapController]);
	
})(angular, app);

function addMap(scope) {
	scope.map = L.map('map').setView([42.4564, 18.5347], 15);
	
	var attrString = tr(scope, 'map.js.copy.data') + 
		' &copy; <a href="http://osm.org">' + 
		tr(scope, 'map.js.copy.contributors') + '</a>, ' + 
		tr(scope, 'map.js.copy.rendering') + 
		' <a href=\"http://giscience.uni-hd.de/\" target=\"_blank\">University of Heidelberg</a>';
	
	L.tileLayer('http://openmapsurfer.uni-hd.de/tiles/roads/x={x}&y={y}&z={z}', {
	    attribution: attrString,
	    maxZoom: 18
	}).addTo(scope.map);

	var overlays = {};
	overlays[tr(scope, 'map.js.layer.relief')] = 
		L.tileLayer('http://openmapsurfer.uni-hd.de/tiles/asterh/x={x}&y={y}&z={z}', {		
		maxZoom: 18
	});

	overlays[tr(scope, 'map.js.layer.contours')] = 
		L.tileLayer('http://openmapsurfer.uni-hd.de/tiles/asterc/x={x}&y={y}&z={z}', {
		maxZoom: 18
	});

	this.layersControl = L.control.layers({}, overlays);
	this.layersControl.addTo(scope.map);
	scope.map.addControl(new L.Control.Scale());
}

app.factory('i18nService', ['$resource', function($resource) {  
    return {
    	getTranslation:function($scope, language) {
            var path = HTML_ROOT + '/i18n/map_' + language + '.json';
            var ssid = 'map.js_' + language;
            
            if (sessionStorage) {
                if (sessionStorage.getItem(ssid)) {
                    $scope.translation = JSON.parse(sessionStorage.getItem(ssid));
                } else {
                    $resource(path).get(function(data) {
                        $scope.translation = data;
                        sessionStorage.setItem(ssid, JSON.stringify($scope.translation));
                    });
                };
            } else {
                $resource(path).get(function (data) {
                    $scope.translation = data;
                });
            }
        }
    }
}]);

function tr(scope, key) {
	if(scope.translation !== undefined && scope.translation[key] !== undefined) {
		return scope.translation[key];
	}
	
	return key;
}

app.factory('osmdocHierarchyService', ['$http', function($http) {  
    return {
    	loadHierarchy:function($scope, language) {
    		$http.get(API_ROOT + '/osmdoc/hierachy', {
    			'params' : {
    				'lang': language
    			}
	    	}).success(function(data) {
	                $scope.hierarchy = data;
	        });
        }
    }
}]);

app.factory('SearchAPI', ['$http', function($http) {  
	var searchAPIFactory = {
		
		search:function($scope) {
			$http.get(API_ROOT + '/feature/_search', {
				'params' : {
					'q':$scope.searchQuerry
				}
			}).success(function(data) {
				$scope.hierarchy = data;
			});
		},
		
		listPOI:function($scope, page) {
			
			if($scope.cathegories.features.length == 0 
					&& $scope.cathegories.groups == 0
					&& !$scope.searchQuerry) {
				
				return;
			}
			
			$http.get(API_ROOT + '/feature/_search', {
				'params' : {
					'q':$scope.searchQuerry,
					'poiclass':$scope.cathegories.features,
					'poigroup':$scope.cathegories.groups,
					'bbox':$scope.map.getBounds().toBBoxString(),
					'size':50,
					'page':page
				}
			}).success(function(data) {
				if(data.result == 'success') {
					angular.forEach(data.features, function(f){
						if($scope.id2Feature[f.feature_id] == undefined){
							$scope.id2Feature[f.feature_id] = f;
							
							var m = L.marker(f.center_point);
							$scope.id2Marker[f.feature_id] = m;
							m.addTo($scope.map).bindPopup(searchAPIFactory.createPopUP(f));
						}
					});
					
					//load data paged but no more than 1000 items
					if(data.page * data.size < data.hits && data.page < 20) {
						searchAPIFactory.listPOI($scope, data.page + 1);
					}
				}
				
			});
		},
		
		createPopUP: function(f) {
			var title = (f.name || f.poi_class_names[0]);
			
			if(f.name) {
				title += ' (' + f.poi_class_names[0] + ')';
			}
			
			var r = '<div class="fpopup"><h2>' + title + '</h2>' +
				'<div>' + f.address + '</div></div>';
			
			return r;
		}
		
	};
	
	return searchAPIFactory;
}]);

app.factory('SuggestAPI', ['$http', function($http) {  
	return {
		search:function($scope) {
			$http.get(API_ROOT + '/feature/_suggest', {
				'params' : {
					'q':$scope.searchQuerry
				}
			}).success(function(data) {
				$scope.hierarchy = data;
			});
		}
	}
}]);
