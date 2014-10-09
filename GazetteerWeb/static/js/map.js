
String.prototype.hashCode = function(){
	var hash = 0;
	if (this.length == 0) return hash;
	for (i = 0; i < this.length; i++) {
		char = this.charCodeAt(i);
		hash = ((hash<<5)-hash)+char;
		hash = hash & hash; // Convert to 32bit integer
	}
	return hash;
}

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

(function( ng, app ) {
	
	MapController = function ($scope, $cookies, i18nService, 
			osmdocHierarchyService, searchAPI, featureAPI, $location) {
		
		$scope.name2FClass = {};
		i18nService.getTranslation($scope, 'ru');
		osmdocHierarchyService.loadHierarchy($scope, 'ru');
		
		addMap($scope);
		
		
		$scope.activeFeatureID = null;
		$scope.$watch(function () {return $location.search();}, 
				function() {
					$scope.activeFeatureID = $location.search()['fid'];
				}
		);
		
		$scope.$watch('activeFeatureID', function(term) {
			if(term) {
				$location.search('fid', term);
				featureAPI.showPopup($scope);
			}
			else {
				$location.search('fid', null);
			}
		});
		
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
		
		$scope.$watch('cathegories', function(types) {
			$scope.pagesMode = false;
			searchAPI.listPOI($scope, 1);
			$scope.filterMap($scope);
		}, true);
		
		$scope.id2Feature = {};
		$scope.id2Marker = {};
		
		$scope.map.on('viewreset', function(){
			if(!$scope.pagesMode) {
				searchAPI.listPOI($scope, 1);
			}
		});
		
		$scope.map.on('moveend', function(){
			if(!$scope.pagesMode) {
				searchAPI.listPOI($scope, 1);
			}
		});
		
		$scope.traverseHierarchy = function(h, traverser, groups) {
			angular.forEach(h.features, function(f){
				traverser.feature(f, groups);
			});
			angular.forEach(h.groups, function(g){
				groups.push(g.name);
				traverser.group(g);
				$scope.traverseHierarchy(g, traverser, groups);
				groups.pop(g.name);
			});
		};
		
		$scope.expandCathegories = function() {
			
			if(!$scope.hierarchy) {
				return [];
			}
			
			var rhash = {};
			
			angular.forEach($scope.cathegories.features, function(f){
				rhash[f] = 1;
			});
			
			$scope.traverseHierarchy($scope.hierarchy, {
				feature:function(f, gstack){
					angular.forEach($scope.cathegories.groups, function(g){
						if(gstack.indexOf(g) >= 0) {
							rhash[f.name] = 1;
						}
					});
				},
				group:function(g){
					
				}
			}, []);

			var res = [];
			
			angular.forEach(rhash, function(v, k){
				res.push(k);
			});
			
			return res;
		};
		
		//For now filter only by cathegory, not by q string
		$scope.filterMap = function() {
			var ftypes = $scope.expandCathegories();
				
			var remove = [];

			angular.forEach($scope.id2Feature, function(f, id){
				if(ftypes.indexOf(f.class_name) < 0) {
					remove.push(id);
				}
			});

			angular.forEach(remove, function(id){
				$scope.map.removeLayer($scope.id2Marker[id]);
				delete $scope.id2Marker[id];
				delete $scope.id2Feature[id];
			});
				
		};
		
		$scope.searchResultsPage = {};
		$scope.pagesCenter = $scope.map.getCenter();

		$scope.find = function() {
			$scope.pagesCenter = $scope.map.getCenter();
			searchAPI.search($scope, 1);
		};
		
		$scope.formatSearchResultTitle = function(f) {
			
			if(f.name || f.poi_class_names) {
				var title = (f.name || f.poi_class_names[0]);
				
				if(f.name && f.poi_class_names) {
					title += ' (' + f.poi_class_names[0] + ')';
				}
				
				return title;
			}
			
			return '';
		};

		$scope.formatSearchResultAddress = function(f) {
			return f.address;
		};
		
		$scope.getSRPages = function() {
			
			var r = {};
			
			if($scope.searchResultsPage) {
				
				var total = $scope.searchResultsPage.hits;
				var page = $scope.searchResultsPage.page;
				var pageSize = $scope.searchResultsPage.size;
				var maxPage = parseInt(total/pageSize);
				if(total % pageSize == 0) {
					maxPage += 1;
				}
				
				for(var i = 1; i <= maxPage && i <= 8; i++){
					r[i] = {
						'p':i,
						'active':page == i
					};
				}
				
				for(var i = page - 1; i <= page + 1; i++){
					if(i > 0 && i <= maxPage) {
						r[i] = {
							'p':i,
							'active':page == i
						};
					}
				}

				for(var i = maxPage - 2; i <= maxPage; i++){
					if(i > 0) {
						r[i] = {
							'p':i,
							'active':page == i
						};
					}
				}
			}
			var rarr = [];
			angular.forEach(r, function(v){
				rarr.push(v);
			});
			
			rarr.sort(function (a, b) { return a.p - b.p; });
			
			$scope.srPages = rarr;
		};
		
		$scope.goPage = function(p) {
			searchAPI.search($scope, p.p);
		};
		
		$scope.selectRow = function(f) {
			$scope.activeFeatureID = f.feature_id;
		}
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
	     'osmdocHierarchyService', 'SearchAPI', 'featureAPI', '$location', MapController]);
	
})(angular, app);

function addMap($scope) {
	$scope.map = L.map('map').setView([42.4564, 18.5347], 15);
	
	var attrString = tr($scope, 'map.js.copy.data') + 
		' &copy; <a href="http://osm.org">' + 
		tr($scope, 'map.js.copy.contributors') + '</a>, ' + 
		tr($scope, 'map.js.copy.rendering') + 
		' <a href=\"http://giscience.uni-hd.de/\" target=\"_blank\">University of Heidelberg</a>';
	
	L.tileLayer('http://openmapsurfer.uni-hd.de/tiles/roads/x={x}&y={y}&z={z}', {
	    attribution: attrString,
	    maxZoom: 18
	}).addTo($scope.map);

	var overlays = {};
	overlays[tr($scope, 'map.js.layer.relief')] = 
		L.tileLayer('http://openmapsurfer.uni-hd.de/tiles/asterh/x={x}&y={y}&z={z}', {		
		maxZoom: 18
	});

	overlays[tr($scope, 'map.js.layer.contours')] = 
		L.tileLayer('http://openmapsurfer.uni-hd.de/tiles/asterc/x={x}&y={y}&z={z}', {
		maxZoom: 18
	});

	this.layersControl = L.control.layers({}, overlays);
	this.layersControl.addTo($scope.map);
	$scope.map.addControl(new L.Control.Scale());
	
	$scope.map.on('popupopen', function(e) {
	    var px = $scope.map.project(e.popup._latlng);
	    px.y -= e.popup._container.clientHeight/2
	    $scope.map.panTo($scope.map.unproject(px),{animate: false});
	    
	    $scope.activeFeatureID = e.popup._source.feature_id;
	});

	$scope.map.on('popupclose', function(e) {
		$scope.activeFeatureID = '';
	});
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
	    		$scope.traverseHierarchy($scope.hierarchy, {
	    			feature:function(f){
	    				 $scope.name2FClass[f.name] = f;
	    			},
	    			group:function(g){
	    				
	    			}
	    		}, []);    
	        });
        }
    }
}]);

app.factory('SearchAPI', ['$http', function($http) {  
	var searchAPIFactory = {
		
		search:function($scope, page) {
			
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
					'lat':$scope.pagesCenter.lat,
					'lon':$scope.pagesCenter.lng,
					'mark':('' + $scope.cathegories + $scope.searchQuerry).hashCode(),
					'page':page
				}
			}).success(function(data) {
				if(data.result == 'success') {
					var curentHash = ('' + $scope.cathegories + $scope.searchQuerry).hashCode();
					if(data.mark == curentHash) {
						
						angular.forEach($scope.searchResultsPage.features, function(f){
							if($scope.id2Feature[f.feature_id] !== undefined){
								$scope.map.removeLayer($scope.id2Marker[f.feature_id]);
								delete $scope.id2Feature[f.feature_id];
								delete $scope.id2Marker[f.feature_id];
								$scope.activeFeatureID = '';
							}
						});

						$scope.searchResultsPage = data;
						$scope.getSRPages();
						
						var pointsArray = [];
						angular.forEach(data.features, function(f){
							if($scope.id2Feature[f.feature_id] == undefined){
								$scope.id2Feature[f.feature_id] = f;
								
								var m = L.marker(f.center_point);
								$scope.id2Marker[f.feature_id] = m;
								m.feature_id = f.feature_id;
								m.addTo($scope.map).bindPopup(createPopUP(f));
								
								pointsArray.push(f.center_point);
							}
						});
						
						$scope.map.fitBounds(L.latLngBounds(pointsArray));
						$scope.pagesMode = true;
					}
				}
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
					'page':page,
					'mark':('' + $scope.cathegories + $scope.searchQuerry).hashCode()
				}
			}).success(function(data) {
				if(data.result == 'success') {
					var curentHash = ('' + $scope.cathegories + $scope.searchQuerry).hashCode();
					if(data.mark == curentHash) {
						angular.forEach(data.features, function(f){
							if($scope.id2Feature[f.feature_id] == undefined){
								$scope.id2Feature[f.feature_id] = f;
								
								var m = L.marker(f.center_point);
								$scope.id2Marker[f.feature_id] = m;
								m.feature_id = f.feature_id;
								m.addTo($scope.map).bindPopup(createPopUP(f));
							}
						});
						
						//load data paged but no more than 1000 items
						if(data.page * data.size < data.hits && data.page < 20) {
							searchAPIFactory.listPOI($scope, data.page + 1);
						}
					}
				}
			});
		}
		
	};
	
	return searchAPIFactory;
}]);

app.factory('featureAPI', ['$http', function($http) {  
	return {
		showPopup:function($scope) {

			if($scope.id2Marker && $scope.activeFeatureID && $scope.id2Marker[$scope.activeFeatureID]) {
				$scope.id2Marker[$scope.activeFeatureID].openPopup();
			}
			else if($scope.activeFeatureID) {
				$http.get(API_ROOT + '/feature', {
					'params' : {
						'id':$scope.activeFeatureID,
						'related':false
					}
				}).success(function(data) {
					if(!$scope.id2Feature[data.feature_id]) {
						$scope.id2Feature[data.feature_id] = data;
						
						var m = L.marker(data.center_point);
						$scope.id2Marker[data.feature_id] = m;
						m.addTo($scope.map).bindPopup(createPopUP(data));
						m.feature_id = data.feature_id;
						
						$scope.id2Marker[data.feature_id].openPopup();
					}
				});
			}
		}
	}
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

function createPopUP(f) {
	var title = '';
	
	if(f.name || f.poi_class_names) {
		title = (f.name || f.poi_class_names[0]);
		
		if(f.name && f.poi_class_names) {
			title += ' (' + f.poi_class_names[0] + ')';
		}
	}

	var address = getAddress(f);
	
	if(title) {
		return '<div class="fpopup"><h2>' + title + '</h2>' +
		'<div>' + address + '</div></div>';
	}
	
	return '<div>' + address + '</div>';
}

function getAddress(f) {
	
	var addrArray = [];
	
	a = {};
	
	if(f.address) {
		a = f;
	}
	else if(f.addresses) {
		a = f.addresses[0];
	}
	
	if(a.housenumber) {
		addrArray.push(a.housenumber);
	}
	if(a.street_name) {
		addrArray.push(a.street_name);
	}
	if(a.neighborhood_name) {
		addrArray.push(a.neighborhood_name);
	}
	else if(a.nearest_neighbour) {
		addrArray.push(a.nearest_neighbour.name);
	}
	if(a.locality_name) {
		addrArray.push(a.locality_name);
	}
	else if(a.nearest_place) {
		addrArray.push(a.nearest_place.name);
	}
	if(a.local_admin_name) {
		addrArray.push(a.local_admin_name);
	}
	if(a.admin2_name) {
		addrArray.push(a.admin2_name);
	}
	if(a.admin1_name) {
		addrArray.push(a.admin1_name);
	}
	if(a.admin0_name) {
		addrArray.push(a.admin0_name);
	}

	return addrArray.join(', ');
}