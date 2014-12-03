package me.osm.gazetter.join.out_handlers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.osm.gazetter.out.AddrRowValueExctractorImpl;
import me.osm.gazetter.striper.FeatureTypes;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Originally objects like building, poi or highways
 * may have more than one address,
 * */
public abstract class AddressPerRowJOHBase extends SingleWriterJOHBase {

	protected volatile boolean dropEmptyAddresses = true;
	
	@Override
	public void handle(JSONObject object, String stripe) {
		List<JSONObject> addresses = listAddresses(object, stripe);
		if(addresses != null && !addresses.isEmpty()) {
			for(JSONObject address : addresses) {
				if(address.has("boundariesHash") && address.getInt("boundariesHash") == 0) {
					if (!dropEmptyAddresses) {
						handle(object, null, stripe);
					}
				}
				else {
					handle(object, address, stripe);
				}
			}
		}
		else if (!dropEmptyAddresses) {
			handle(object, null, stripe);
		}
	}
	
	public static String getUID(JSONObject oject, JSONObject addressRow) {
		return AddrRowValueExctractorImpl.getUID(oject, addressRow, oject.getString("ftype"));
	}
	
	protected void handle(JSONObject object, JSONObject address, String stripe) {
		String ftype = object.optString("ftype");
		
		// Process only those of boundaries which wasn't splitted. 
		// They are stored in binx.gjson
		if(ftype.equals(FeatureTypes.ADMIN_BOUNDARY_FTYPE) && stripe.startsWith("binx")) {
			handleAdminBoundaryAddrRow(object, address, stripe);
		}

		if(FeatureTypes.PLACE_POINT_FTYPE.equals(ftype)) {
			handlePlacePointAddrRow(object, address, stripe);
		}

		if(FeatureTypes.PLACE_BOUNDARY_FTYPE.equals(ftype)) {
			handlePlaceBoundaryAddrRow(object, address, stripe);
		}
		
		if(FeatureTypes.HIGHWAY_FEATURE_TYPE.equals(ftype)) {
			handleHighwayAddrRow(object, address, stripe);
		}
		
		if(FeatureTypes.JUNCTION_FTYPE.equals(ftype)) {
			handleHighwaysJunction(object, address, stripe);
		}

		if(FeatureTypes.ADDR_POINT_FTYPE.equals(ftype)) {
			handleAddrNodeAddrRow(object, address, stripe);
		}
		
		if(FeatureTypes.POI_FTYPE.equals(ftype)) {
			handlePoiPointAddrRow(object, address, stripe);
		}
		
	}
	
	/**
	 * Override to process AdminBoundaries
	 * */
	protected void handleAdminBoundaryAddrRow(JSONObject object,
			JSONObject address, String stripe) {
		
	}

	/**
	 * Override to process Place points
	 * */
	protected void handlePlacePointAddrRow(JSONObject object,
			JSONObject address, String stripe) {
		
	}

	/**
	 * Override to process Highways
	 * */
	protected void handleHighwayAddrRow(JSONObject object,
			JSONObject address, String stripe) {
		
	}

	/**
	 * Override to process Highways
	 * */
	protected void handlePlaceBoundaryAddrRow(JSONObject object,
			JSONObject address, String stripe) {
		
	}
	
	/**
	 * Override to process Highways Junctions
	 * */
	protected void handleHighwaysJunction(JSONObject object,
			JSONObject address, String stripe) {
		
	}

	/**
	 * Override to process Address nodes
	 * */
	protected void handleAddrNodeAddrRow(JSONObject object,
			JSONObject address, String stripe) {
		
	}

	/**
	 * Override to process Poi nodes
	 * */
	protected void handlePoiPointAddrRow(JSONObject object,
			JSONObject address, String stripe) {
		
	}

	protected List<JSONObject> listAddresses(JSONObject jsonObject, String stripe) {
		
		String ftype = jsonObject.optString("ftype");

		if(FeatureTypes.ADDR_POINT_FTYPE.equals(ftype)) {
			JSONArray addresses = jsonObject.optJSONArray("addresses");
			if(addresses != null) {
				List<JSONObject> result = new ArrayList<JSONObject>();
				for(int ri = 0; ri < addresses.length(); ri++ ) {
					result.add(addresses.getJSONObject(ri));
				}
				return result;
			}
		}
		
		else if(FeatureTypes.HIGHWAY_FEATURE_TYPE.equals(ftype)) {
			JSONArray boundaries = jsonObject.optJSONArray("boundaries");
			if(boundaries != null) {
				List<JSONObject> result = new ArrayList<JSONObject>();
				for(int i = 0; i < boundaries.length(); i++) {
					result.add(boundaries.getJSONObject(i));
				}
				return result;
			}
		}
		
		else if(FeatureTypes.PLACE_POINT_FTYPE.equals(ftype)) {
			JSONObject boundaries = jsonObject.optJSONObject("boundaries");
			if(boundaries != null) {
				List<JSONObject> result = new ArrayList<JSONObject>();
				result.add(boundaries);
				return result;
			}
		}
		
		else if(FeatureTypes.POI_FTYPE.equals(ftype)) {
			List<JSONObject> addresses = new ArrayList<JSONObject>();
			String poiAddrMatch = fillPoiAddresses(jsonObject, addresses);
			if(addresses.isEmpty() || "nearest".equals(poiAddrMatch)) {
				poiAddrMatch = "boundaries";
				addresses =  new ArrayList<JSONObject>(Collections.singletonList(jsonObject.optJSONObject("boundaries")));
			}
			
			for(JSONObject addrO : addresses) {
				addrO.put("poiAddrMatch", poiAddrMatch);
			}
			
			return addresses;
		}
		
		return null;
	}
	
	private String fillPoiAddresses(JSONObject poi, List<JSONObject> result) {
		
		JSONObject joinedAddresses = poi.optJSONObject("joinedAddresses");
		if(joinedAddresses != null) {
			
			//"sameSource"
			if(getAddressesFromObj(result, joinedAddresses, "sameSource")) {
				return "sameSource";
			}
			
			//"contains"
			if(getAddressesFromCollection(result, joinedAddresses, "contains")) {
				return "contains";
			}

			//"shareBuildingWay"
			if(getAddressesFromCollection(result, joinedAddresses, "shareBuildingWay")) {
				return "shareBuildingWay";
			}

			//"nearestShareBuildingWay"
			if(getAddressesFromCollection(result, joinedAddresses, "nearestShareBuildingWay")) {
				return "nearestShareBuildingWay";
			}

			//"nearest"
			if(getAddressesFromObj(result, joinedAddresses, "nearest")) {
				return "nearest";
			}
			
		}
		
		return null;
	}

	private boolean getAddressesFromObj(List<JSONObject> result,
			JSONObject joinedAddresses, String key) {
		
		boolean founded = false;
		
		JSONObject ss = joinedAddresses.optJSONObject(key);
		if(ss != null) {
			JSONArray addresses = ss.optJSONArray("addresses");
			if(addresses != null) {
				for(int i = 0; i < addresses.length(); i++) {
					result.add(addresses.getJSONObject(i));
				}
				founded = true;
			}
		}
		
		return founded;
	}

	private boolean getAddressesFromCollection(List<JSONObject> result,
			JSONObject joinedAddresses, String key) {
		
		boolean founded = false;
		
		JSONArray contains = joinedAddresses.optJSONArray("contains");
		if(contains != null && contains.length() > 0) {
			
			for(int ci = 0; ci < contains.length(); ci++) {
				JSONObject co = contains.getJSONObject(ci);
				JSONArray addresses = co.optJSONArray("addresses");
				if(addresses != null) {
					for(int i = 0; i < addresses.length(); i++) {
						result.add(addresses.getJSONObject(i));
						founded = true;
					}
				}
			}
			
		}
		
		return founded;
	}

}
