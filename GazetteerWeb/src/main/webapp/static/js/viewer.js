
Viewer = function() {
	this.form = $('form')[0];
	this.input = $('#search')[0];
	
	this.featuresDirective = {
		'.feature' : {
	        'feature<-features':{
	          '@href':function(i){ return "api/feature=" + i.item.feature_id; },
	          '.':'feature.name'
	        }
	    }
	};
	
	this.featuresRender = $p('#templates .features').compile(this.featuresDirective);
	
	var viewer = this;
	this.form.onsubmit = function(){
		return viewer.formSubmitHandler.apply(viewer, arguments);
	};
};

Viewer.prototype.formSubmitHandler = function() {
	var q = this.input.value;
	jQuery.ajax( 'api', {
		
		'data' : {
			'search' : q,
		},
		context: this,
		success: this.handleSearchDone
	} );
	return false;
};

Viewer.prototype.handleSearchDone = function(data) {
	$('#content')[0].innerHTML = this.featuresRender(data);
};

viewer = new Viewer();
