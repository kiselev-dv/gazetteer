package me.osm.gazetteer.web.stats;

import org.json.JSONArray;
import org.json.JSONObject;

public class APIRequest {
	
	public static class APIRequestBuilder {
		private APIRequest subj;
		
		public static APIRequestBuilder builder() {
			return new APIRequestBuilder();
		}

		private APIRequestBuilder() {
			this.subj = new APIRequest();
		}
		
		public APIRequestBuilder userIp(String userIp) {
			this.subj.userIp = userIp;
			return this;
		}

		public APIRequestBuilder userId(String userId) {
			this.subj.userId = userId;
			return this;
		}

		public APIRequestBuilder sessionId(String sessionId) {
			this.subj.sessionId = sessionId;
			return this;
		}

		public APIRequestBuilder lang(String lang) {
			this.subj.lang = lang;
			return this;
		}

		public APIRequestBuilder api(String api) {
			this.subj.api = api;
			return this;
		}

		public APIRequestBuilder resultLon(Double resultLon) {
			this.subj.resultLon = resultLon;
			return this;
		}

		public APIRequestBuilder resultLat(Double resultLat) {
			this.subj.resultLat = resultLat;
			return this;
		}
		
		public APIRequestBuilder resultId(String resultId) {
			this.subj.resultId = resultId;
			return this;
		}

		public APIRequestBuilder resultString(String resultString) {
			this.subj.resultString = resultString;
			return this;
		}

		public APIRequestBuilder query(String query) {
			this.subj.query = query;
			return this;
		}
		
		public APIRequestBuilder status(int status) {
			this.subj.status = status;
			return this;
		}

		public APIRequestBuilder resultLatLon(JSONObject feature) {
			JSONObject center = feature.getJSONObject("center_point");
			if(center != null) {
				this.resultLat(center.getDouble("lat"));
				this.resultLon(center.getDouble("lon"));
			}
			return this;
		}

		public APIRequestBuilder fillSearchResult(JSONObject result) {
			JSONArray features = result.optJSONArray("features");
			if (features.length() > 0) {
				JSONObject f = features.getJSONObject(0);
				fillByFeature(f);
			}
			else {
				resultString("empty");
				resultId("empty");
			}
			return this;
		}

		public APIRequestBuilder fillByFeature(JSONObject f) {
			resultLatLon(f);
			resultId(f.optString("id"));
			resultString(f.optString("address"));
			
			return this;
		}

		public APIRequest build() {
			return subj;
		}

	}
	
	private String userIp;
	private String userId;
	private String sessionId;
	private String lang;
	
	private String api;

	private String query;

	private Double resultLon;
	private Double resultLat;
	
	private String resultId;
	private String resultString;
	private int status;
	
	public APIRequest() {
		
	}

	public String getUserIp() {
		return userIp;
	}

	public String getUserId() {
		return userId;
	}

	public String getSessionId() {
		return sessionId;
	}

	public String getLang() {
		return lang;
	}

	public String getApi() {
		return api;
	}

	public String getQuery() {
		return query;
	}

	public Double getResultLon() {
		return resultLon;
	}

	public Double getResultLat() {
		return resultLat;
	}

	public String getResultId() {
		return resultId;
	}

	public int getStatus() {
		return status;
	}

	public String getResultString() {
		return resultString;
	}
	
}

