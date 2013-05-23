package org.opendatakit.tables.fragments;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opendatakit.tables.R;
import org.opendatakit.tables.activities.TableActivity;
import org.opendatakit.tables.data.ColorRuleGroup;
import org.opendatakit.tables.data.ColorRuleGroup.ColorGuide;
import org.opendatakit.tables.data.ColumnProperties;
import org.opendatakit.tables.data.KeyValueStoreHelper;
import org.opendatakit.tables.data.TableProperties;
import org.opendatakit.tables.data.UserTable;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockMapFragment;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap.OnCameraChangeListener;
import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import com.google.android.gms.maps.GoogleMap.OnMapLongClickListener;
import com.google.android.gms.maps.GoogleMap.OnMarkerClickListener;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

/**
 * The InnerMapFragment has the capability of showing a map. It displays markers
 * based off of location column set in the TableProperitiesManager Activity.
 * 
 * @author Chris Gelon (cgelon)
 */
public class TableMapInnerFragment extends SherlockMapFragment {
  /** Tag for debug statements. */
  private static String TAG = "InnerMapFragment";

  /** The default hue for markers if no color rules are applied. */
  private static float DEFAULT_MARKER_HUE = BitmapDescriptorFactory.HUE_AZURE;
  
  /**
   * The index of the currently selected marker.  Used when saving the instance.
   */
  public static final String SAVE_KEY_INDEX = "saveKeyIndex";

  /**
   * Interface for listening to different events that may be triggered by this
   * inner fragment.
   */
  public interface TableMapInnerFragmentListener {
    void onHideList();

    void onSetIndex(int i);
  }

  /** The object that is listening in on events. */
  public TableMapInnerFragmentListener listener;

  /** A mapping of all markers to index to determine which marker is selected. */
  private Map<Marker, Integer> mMarkerIds;

  /** A set of all the visible markers. */
  private Set<Marker> mVisibleMarkers;

  /** The currently selected marker. */
  private Marker mCurrentMarker;
  
  /** 
   * This value is only set after the activity was saved and then reinstated.
   * It is used to figure out which marker was selected before the activity was 
   * previously destroyed. It will be set to -1 if no index was selected.
   */
  private int mCurrentIndex;

  private Map<String, Integer> mColumnIndexMap;
  private Map<String, ColumnProperties> mColumnPropertiesMap;

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    mCurrentIndex = (savedInstanceState != null) ? savedInstanceState.getInt(SAVE_KEY_INDEX) : -1;
    init();
    getMap().setOnMapLongClickListener(getOnMapLongClickListener());
    getMap().setOnMapClickListener(getOnMapClickListener());
    // getMap().setOnCameraChangeListener(getCameraChangeListener());
  }
  
  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);

    outState.putInt(SAVE_KEY_INDEX, (mCurrentMarker != null) ? mMarkerIds.get(mCurrentMarker) : -1);
  }

  /** Re-initializes the map, including the markers. */
  public void init() {
    getMap().clear();
    resetColorProperties();
    Log.d(TAG, "Start creating markers.");
    setMarkers();
    Log.d(TAG, "End creating markers.");
  }

  @Override
  public void onDestroy() {
    super.onDestroy();

    // Clear up any memory references.
    mMarkerIds.clear();
    mVisibleMarkers.clear();
    mCurrentMarker = null;
  }

  /**
   * Sets up the color properties used for color rules.
   */
  public void resetColorProperties() {
    TableProperties tp = ((TableActivity) getActivity()).getTableProperties();
    // Now let's set up the color rule things.
    Map<String, Integer> indexMap = new HashMap<String, Integer>();
    Map<String, ColumnProperties> propertiesMap = new HashMap<String, ColumnProperties>();
    List<String> columnOrder = tp.getColumnOrder();
    for (int i = 0; i < columnOrder.size(); i++) {
      propertiesMap.put(columnOrder.get(i), tp.getColumnByIndex(i));
      indexMap.put(columnOrder.get(i), i);
    }
    mColumnIndexMap = indexMap;
    mColumnPropertiesMap = propertiesMap;
  }

  /**
   * Sets the location markers based off of the columns set in the table
   * properties.
   */
  private void setMarkers() {
    if (mMarkerIds != null) {
      mMarkerIds.clear();
    }
    if (mVisibleMarkers != null) {
      mVisibleMarkers.clear();
    }

    mMarkerIds = new HashMap<Marker, Integer>();
    mVisibleMarkers = new HashSet<Marker>();

    String latitudeElementKey = getLatitudeElementKey();
    String longitudeElementKey = getLongitudeElementKey();
    if (latitudeElementKey == null || longitudeElementKey == null) {
      Toast.makeText(getActivity(), getActivity().getString(R.string.lat_long_not_set),
          Toast.LENGTH_LONG).show();
      return;
    }

    TableProperties tp = ((TableActivity) getActivity()).getTableProperties();
    UserTable table = ((TableActivity) getActivity()).getTable();

    // Try to find the map columns in the store.
    ColumnProperties latitudeColumn = tp.getColumnByElementKey(latitudeElementKey);
    ColumnProperties longitudeColumn = tp.getColumnByElementKey(longitudeElementKey);

    // Find the locations from entries in the table.
    int latitudeColumnIndex = tp.getColumnIndex(latitudeColumn.getElementKey());
    int longitudeColumnIndex = tp.getColumnIndex(longitudeColumn.getElementKey());
    LatLng firstLocation = null;

    // Go through each row and create a marker at the specified location.
    for (int i = 0; i < table.getHeight(); i++) {
      String latitudeString = table.getData(i, latitudeColumnIndex);
      String longitudeString = table.getData(i, longitudeColumnIndex);
      if (latitudeString == null || longitudeString == null || latitudeString.length() == 0
          || longitudeString.length() == 0)
        continue;

      // Create a LatLng from the latitude and longitude strings.
      LatLng location = parseLocationFromString(latitudeString, longitudeString);
      if (location == null)
        continue;
      if (firstLocation == null)
        firstLocation = location;

      Marker marker = getMap().addMarker(
          new MarkerOptions().position(location).draggable(false)
              .icon(BitmapDescriptorFactory.defaultMarker(getHueForRow(i))));
      mMarkerIds.put(marker, i);
      if (mCurrentIndex == i) {
        selectMarker(marker);
      }
    }

    if (firstLocation != null) {
      getMap().moveCamera(CameraUpdateFactory.newLatLngZoom(firstLocation, 12f));
      getMap().setOnMarkerClickListener(getOnMarkerClickListener());
    }
  }

  /**
   * Retrieves the hue of the specified row depending on the current color
   * rules.
   * 
   * @param index
   *          The index of the row to search for.
   * @return The hue depending on the color rules for this row, or the default
   *         marker color if no rules apply to the row.
   */
  private float getHueForRow(int index) {
    TableProperties tp = ((TableActivity) getActivity()).getTableProperties();
    UserTable table = ((TableActivity) getActivity()).getTable();

    // Grab the key value store helper from the map fragment.
    final KeyValueStoreHelper kvsHelper = tp.getKeyValueStoreHelper(TableMapFragment.KVS_PARTITION);
    String colorType = kvsHelper.getString(TableMapFragment.KEY_COLOR_RULE_TYPE);
    // Create a guide depending on what type of color rule is selected.
    ColorGuide guide = null;
    if (colorType.equals(TableMapFragment.COLOR_TYPE_TABLE)) {
      guide = ColorRuleGroup.getTableColorRuleGroup(tp).getColorGuide(table.getRowData(index),
          mColumnIndexMap, this.mColumnPropertiesMap);
    }
    if (colorType.equals(TableMapFragment.COLOR_TYPE_TABLE)) {
      guide = ColorRuleGroup.getStatusColumnRuleGroup(tp).getColorGuide(table.getRowData(index),
          mColumnIndexMap, this.mColumnPropertiesMap);
    }
    if (colorType.equals(TableMapFragment.COLOR_TYPE_COLUMN)) {
      String colorColumnKey = kvsHelper.getString(TableMapFragment.KEY_COLOR_RULE_COLUMN);
      if (colorColumnKey != null) {
        guide = ColorRuleGroup.getColumnColorRuleGroup(tp, colorColumnKey).getColorGuide(
            table.getRowData(index), mColumnIndexMap, this.mColumnPropertiesMap);
      }
    }

    // Based on if the guide matched or not, grab the hue.
    if (guide != null && guide.didMatch()) {
      float[] hsv = new float[3];
      Color.colorToHSV(guide.getBackground(), hsv);
      return hsv[0];
    }
    return DEFAULT_MARKER_HUE;
  }

  private String getLatitudeElementKey() {
    TableProperties tp = ((TableActivity) getActivity()).getTableProperties();
    // Grab the key value store helper from the table activity.
    final KeyValueStoreHelper kvsHelper = tp.getKeyValueStoreHelper(TableMapFragment.KVS_PARTITION);
    String latitudeElementKey = kvsHelper.getString(TableMapFragment.KEY_MAP_LAT_COL);
    if (latitudeElementKey == null) {
      // Go through each of the columns and check to see if there are
      // any columns labeled
      // latitude or longitude.
      ColumnProperties[] cps = tp.getColumns();
      if (latitudeElementKey == null) {
        for (int i = 0; i < cps.length; i++) {
          if (cps[i].getDisplayName().equalsIgnoreCase("latitude")) {
            latitudeElementKey = cps[i].getElementKey();
            kvsHelper.setString(TableMapFragment.KEY_MAP_LAT_COL, latitudeElementKey);
            break;
          }
        }
      }
    }

    return latitudeElementKey;
  }

  private String getLongitudeElementKey() {
    TableProperties tp = ((TableActivity) getActivity()).getTableProperties();
    // Grab the key value store helper from the table activity.
    final KeyValueStoreHelper kvsHelper = tp.getKeyValueStoreHelper(TableMapFragment.KVS_PARTITION);
    String longitudeElementKey = kvsHelper.getString(TableMapFragment.KEY_MAP_LONG_COL);
    if (longitudeElementKey == null) {
      // Go through each of the columns and check to see if there are
      // any columns labled longitude
      ColumnProperties[] cps = tp.getColumns();
      if (longitudeElementKey == null) {
        for (int i = 0; i < cps.length; i++) {
          if (cps[i].getDisplayName().equalsIgnoreCase("longitude")) {
            longitudeElementKey = cps[i].getElementKey();
            kvsHelper.setString(TableMapFragment.KEY_MAP_LONG_COL, longitudeElementKey);
            break;
          }
        }
      }
    }

    return longitudeElementKey;
  }

  /**
   * Parses the location string and creates a LatLng. The format of the string
   * should be: lat,lng
   */
  private LatLng parseLocationFromString(String location) {
    String[] split = location.split(",");
    try {
      return new LatLng(Double.parseDouble(split[0]), Double.parseDouble(split[1]));
    } catch (Exception e) {
      Log.e(TAG, "The following location is not in the proper lat,lng form: " + location);
    }
    return null;
  }

  /**
   * Parses the latitude and longitude strings and creates a LatLng.
   */
  private LatLng parseLocationFromString(String latitude, String longitude) {
    try {
      return new LatLng(Double.parseDouble(latitude), Double.parseDouble(longitude));
    } catch (Exception e) {
      Log.e(TAG, "The following location is not in the proper lat,lng form: " + latitude + ","
          + longitude);
    }
    return null;
  }

  /**
   * If a marker is selected, deselect it.
   */
  private OnMapClickListener getOnMapClickListener() {
    return new OnMapClickListener() {
      @Override
      public void onMapClick(LatLng point) {
        deselectCurrentMarker();
        listener.onHideList();
      }
    };
  }

  private OnMapLongClickListener getOnMapLongClickListener() {
    return new OnMapLongClickListener() {
      @Override
      public void onMapLongClick(LatLng location) {
        // Create a mapping from the lat and long columns to the
        // values in the location.
        TableProperties tp = ((TableActivity) getActivity()).getTableProperties();
        Map<String, String> elementNameToValue = new HashMap<String, String>();
        for (ColumnProperties cp : tp.getColumns()) {
          elementNameToValue.put(cp.getElementName(), "");
        }
        final KeyValueStoreHelper kvsHelper = tp
            .getKeyValueStoreHelper(TableMapFragment.KVS_PARTITION);
        String latitudeElementKey = kvsHelper.getString(TableMapFragment.KEY_MAP_LAT_COL);
        String longitudeElementKey = kvsHelper.getString(TableMapFragment.KEY_MAP_LONG_COL);
        elementNameToValue.put(latitudeElementKey, Double.toString(location.latitude));
        elementNameToValue.put(longitudeElementKey, Double.toString(location.longitude));
        // To store the mapping in a bundle, we need to put it in string list.
        ArrayList<String> bundleStrings = new ArrayList<String>();
        for (String key : elementNameToValue.keySet()) {
          bundleStrings.add(key);
          bundleStrings.add(elementNameToValue.get(key));
        }
        // Bundle it all up for the fragment.
        Bundle b = new Bundle();
        b.putStringArrayList(LocationDialogFragment.ELEMENT_NAME_TO_VALUE_KEY, bundleStrings);
        b.putString(LocationDialogFragment.LOCATION_KEY, location.toString());
        LocationDialogFragment dialog = new LocationDialogFragment();
        dialog.setArguments(b);
        dialog.show(getChildFragmentManager(), "LocationDialogFragment");
      }
    };
  }

  public OnCameraChangeListener getCameraChangeListener() {
    return new OnCameraChangeListener() {
      @Override
      public void onCameraChange(CameraPosition position) {
        checkMarkersOnMap();
      }
    };
  }

  private void checkMarkersOnMap() {
    if (getMap() != null) {
      LatLngBounds bounds = getMap().getProjection().getVisibleRegion().latLngBounds;
      for (Marker marker : mMarkerIds.keySet()) {
        if (bounds.contains(marker.getPosition())) {
          // If the marker is on the screen.
          if (!mVisibleMarkers.contains(marker)) {
            mVisibleMarkers.add(marker);
          }
        } else {
          // If the marker is off screen.
          if (mVisibleMarkers.contains(marker)) {
            mVisibleMarkers.remove(marker);
          }
        }
      }
    }
  }

  private List<Marker> organizeMarkersByDistance(Set<Marker> markers) {
    List<Marker> orderedMarkers = new ArrayList<Marker>();
    Map<Double, Marker> distanceToMarker = new HashMap<Double, Marker>();
    // Find the distances from the center to each marker.
    LatLng center = getMap().getCameraPosition().target;
    for (Marker marker : markers) {
      double distance = distance(center, marker.getPosition());
      // If there are multiple markers with the same distance,
      // slightly
      // distance
      // them so there are no overlapping keys.
      while (distanceToMarker.containsKey(distance)) {
        distance += 0.00000001;
      }
      distanceToMarker.put(distance, marker);
    }
    // After getting all the distances, sort them, and then add them to
    // the orderedMarkers.
    Double[] distances = (Double[]) distanceToMarker.keySet().toArray();
    Arrays.sort(distances);
    for (Double distance : distances) {
      orderedMarkers.add(distanceToMarker.get(distance));
    }
    return orderedMarkers;
  }

  private List<Integer> getListOfIndexes(List<Marker> markers) {
    List<Integer> indexes = new ArrayList<Integer>();
    for (Marker marker : markers) {
      indexes.add(mMarkerIds.get(marker));
    }
    return indexes;
  }

  private double distance(LatLng StartP, LatLng EndP) {
    double lat1 = StartP.latitude;
    double lat2 = EndP.latitude;
    double lon1 = StartP.longitude;
    double lon2 = EndP.longitude;
    double dLat = Math.toRadians(lat2 - lat1);
    double dLon = Math.toRadians(lon2 - lon1);
    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1))
        * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
    double c = 2 * Math.asin(Math.sqrt(a));
    return 6366000 * c;
  }

  /**
   * When a marker is clicked, set the index of the list fragment, and then show
   * it. If that index is already selected, then hide it.
   */
  private OnMarkerClickListener getOnMarkerClickListener() {
    return new OnMarkerClickListener() {
      @Override
      public boolean onMarkerClick(Marker arg0) {
        int index = (mCurrentMarker != null) ? mMarkerIds.get(mCurrentMarker) : -1;

        // Make the marker visible if it is either invisible or a
        // new marker.
        // Make the marker invisible if clicking on the already
        // selected marker.
        if (index != mMarkerIds.get(arg0)) {
          deselectCurrentMarker();
          int newIndex = mMarkerIds.get(arg0);
          listener.onSetIndex(newIndex);
          selectMarker(arg0);
        } else {
          deselectCurrentMarker();
          listener.onHideList();
        }

        return true;
      }
    };
  }

  /**
   * Selects a marker, updating the marker list, and changing the marker's color
   * to green. Makes the marker the currently selected marker.
   * 
   * @param marker
   *          The marker to be selected.
   */
  private void selectMarker(Marker marker) {
    int index = mMarkerIds.get(marker);
    Marker newMarker = getMap().addMarker(
        new MarkerOptions().position(marker.getPosition()).draggable(false)
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
    marker.remove();
    mMarkerIds.remove(marker);
    mMarkerIds.put(newMarker, index);
    mCurrentMarker = newMarker;
  }

  /**
   * Deselects the currently selected marker, updating the marker list, and
   * changing the marker back to a default color.
   */
  private void deselectCurrentMarker() {
    if (mCurrentMarker == null) {
      return;
    }

    int index = mMarkerIds.get(mCurrentMarker);
    Marker newMarker = getMap().addMarker(
        new MarkerOptions().position(mCurrentMarker.getPosition()).draggable(false)
            .icon(BitmapDescriptorFactory.defaultMarker(getHueForRow(index))));
    mCurrentMarker.remove();
    mMarkerIds.remove(mCurrentMarker);
    mMarkerIds.put(newMarker, index);
    mCurrentMarker = null;
    listener.onSetIndex(-1);
  }
}
