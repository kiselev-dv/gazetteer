package me.osm.gazetteer.join.out_handlers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.osm.gazetteer.out.AddrRowValueExctractorImpl;
import me.osm.gazetteer.striper.FeatureTypes;
import me.osm.gazetteer.striper.JSONFeature;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Writes objects address as individual features.
 *
 * Originally objects like building, poi or highways
 * may have more than one address.
 *
 * For such objects, takes all address, and writes them
 * with data for object as individual objects.
 *
 * But preserve information that these objects linked to one
 * real world object via feature_id property.
 * */
public abstract class AddressPerRowJOHBase extends SingleWriterJOHBase {

	protected volatile boolean dropEmptyAddresses = true;

	@Override
	public void handle(JSONObject object, String stripe) {
		List<JSONObject> addresses = listAddresses(object, stripe);
		if(addresses != null && !addresses.isEmpty()) {
			for(JSONObject address : addresses) {
				handle(object, address, stripe);
			}
		}
		else if (!dropEmptyAddresses) {
			handle(object, null, stripe);
		}
	}

	/**
	 * Get unique id for address row
	 *
	 * @param oject subject
	 * @param addressRow subjects address
	 *
	 * @return unique id
	 * */
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

		if(FeatureTypes.HIGHWAY_NET_FEATURE_TYPE.equals(ftype)) {
			handleHighwayNetAddrRow(object, address, stripe);
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
	 * Override to process Highways networks
	 * */
	protected void handleHighwayNetAddrRow(JSONObject object, JSONObject address,
			String stripe) {

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

		else if(FeatureTypes.HIGHWAY_NET_FEATURE_TYPE.equals(ftype)) {
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

				if(jsonObject.optJSONObject("boundaries") == null) {
					return null;
				}

				poiAddrMatch = "boundaries";
				addresses =  new ArrayList<JSONObject>(Collections.singletonList(jsonObject.optJSONObject("boundaries")));
			}

			for(JSONObject addrO : addresses) {
				addrO.put("poiAddrMatch", poiAddrMatch);
			}

			return addresses;
		}

		JSONObject b =jsonObject.optJSONObject("boundaries");
		if(b != null) {
			return Collections.singletonList(jsonObject.optJSONObject("boundaries"));
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

		JSONArray candidates = joinedAddresses.optJSONArray(key);
		if(candidates != null && candidates.length() > 0) {

			for(int ci = 0; ci < candidates.length(); ci++) {
				JSONObject co = candidates.getJSONObject(ci);

				JSONArray addresses = co.optJSONArray("addresses");
				if(addresses != null) {
					for(int i = 0; i < addresses.length(); i++) {
						JSONObject rowJsonObject = JSONFeature.copy(addresses.getJSONObject(i));
						String id = co.optString("id");

						if (id != null) {
							rowJsonObject.put("linked-addr-id", id);
						}

						result.add(rowJsonObject);
						founded = true;
					}
				}
			}

		}

		return founded;
	}

}
