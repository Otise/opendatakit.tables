/*
 * Copyright (C) 2012 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.opendatakit.tables.activities;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.opendatakit.common.android.utilities.ODKFileUtils;
import org.opendatakit.tables.R;
import org.opendatakit.tables.data.ColorRuleGroup;
import org.opendatakit.tables.data.ColumnProperties;
import org.opendatakit.tables.data.ColumnType;
import org.opendatakit.tables.data.KeyValueHelper;
import org.opendatakit.tables.data.KeyValueStoreHelper;
import org.opendatakit.tables.data.KeyValueStoreType;
import org.opendatakit.tables.data.TableProperties;
import org.opendatakit.tables.data.TableViewType;
import org.opendatakit.tables.fragments.TableMapFragment;
import org.opendatakit.tables.preferences.EditFormDialogPreference;
import org.opendatakit.tables.utils.TableFileUtils;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.widget.Toast;

/**
 * An activity for managing a table's properties.
 *
 * @author hkworden@gmail.com
 * @author sudar.sam@gmail.com
 */
public class TablePropertiesManager extends PreferenceActivity {

  private static final String TAG = "TablePropertiesManager";

  // these ints are used when selecting/changing the view files
  private static final int RC_DETAIL_VIEW_FILE = 0;
  private static final int RC_LIST_VIEW_FILE = 1;
  private static final int RC_MAP_LIST_VIEW_FILE = 2;

  private enum ViewPreferenceType {
    OVERVIEW_VIEW, COLLECTION_VIEW,
    // At the point where you could specify different files for an overview
    // and a collection list view, there were only the two constants
    // OVERVIEW_VIEW and COLLECTION_VIEW in this enum. I am changing this
    // to allow for only an unspecified view. The code seems to be checking
    // whether or not something is an overview or collection somehow and
    // storing the value in controller. I'm going to add a case for
    // AUTO_GENERATED to try and reflect that.
    AUTO_GENERATED
  }

  private String appName;
  // private DbHelper dbh;
  private TableProperties tp;



  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    appName = getIntent().getStringExtra(Controller.INTENT_KEY_APP_NAME);
    if ( appName == null ) {
      appName = TableFileUtils.getDefaultAppName();
    }
    String tableId = getIntent().getStringExtra(Controller.INTENT_KEY_TABLE_ID);
    if (tableId == null) {
      throw new RuntimeException("Table ID (" + tableId + ") is invalid.");
    }
    tp = TableProperties.getTablePropertiesForTable(this, appName, tableId, KeyValueStoreType.ACTIVE);
    setTitle(getString(R.string.table_manager_title, tp.getDisplayName()));
    init();
  }

  private void init() {

    PreferenceScreen root = getPreferenceManager().createPreferenceScreen(this);

    // general category

    PreferenceCategory genCat = new PreferenceCategory(this);
    root.addPreference(genCat);
    genCat.setTitle(getString(R.string.general_settings));

    EditTextPreference dnPref = new EditTextPreference(this);
    dnPref.setTitle(getString(R.string.table_display_name));
    dnPref.setDialogTitle(getString(R.string.change_table_display_name));
    dnPref.setText(tp.getDisplayName());
    dnPref.setSummary(tp.getDisplayName());
    dnPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
      @Override
      public boolean onPreferenceChange(Preference preference, Object newValue) {
        tp.setDisplayName((String) newValue);
        setTitle(getString(R.string.table_manager_title, tp.getDisplayName()));
        init();
        return false;
      }
    });
    genCat.addPreference(dnPref);

    // display category

    PreferenceCategory displayCat = new PreferenceCategory(this);
    root.addPreference(displayCat);
    displayCat.setTitle(getString(R.string.display_settings));
    addViewPreferences(displayCat);

    FileSelectorPreference detailViewPref = new FileSelectorPreference(this, RC_DETAIL_VIEW_FILE);
    detailViewPref.setTitle(getString(R.string.detail_view_file));
    detailViewPref.setDialogTitle(getString(R.string.change_detail_view_file));
    final KeyValueStoreHelper kvsh = tp.getKeyValueStoreHelper(DetailDisplayActivity.KVS_PARTITION);
    String detailViewFilename = kvsh.getString(DetailDisplayActivity.KEY_FILENAME);
    detailViewPref.setText(detailViewFilename);
    detailViewPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
      @Override
      public boolean onPreferenceChange(Preference preference, Object newValue) {
        // tp.setDetailViewFilename((String) newValue);
        kvsh.setString(DetailDisplayActivity.KEY_FILENAME, (String) newValue);
        init();
        return false;
      }
    });
    displayCat.addPreference(detailViewPref);

    // Now let's add the pref for the Form.
    EditFormDialogPreference formPref = new EditFormDialogPreference(this, tp);
    displayCat.addPreference(formPref);
    formPref.setTitle(getString(R.string.edit_default_form));
    formPref.setDialogTitle(getString(R.string.edit_default_form));

    Preference rowColorRulePrefs = new Preference(this);
    rowColorRulePrefs.setTitle(getString(R.string.edit_table_color_rules));
    rowColorRulePrefs.setOnPreferenceClickListener(new OnPreferenceClickListener() {

      @Override
      public boolean onPreferenceClick(Preference preference) {
        Intent rowColorRuleManagerIntent = new Intent(TablePropertiesManager.this,
            ColorRuleManagerActivity.class);
        rowColorRuleManagerIntent.putExtra(
            Controller.INTENT_KEY_APP_NAME, tp.getAppName());
        rowColorRuleManagerIntent.putExtra(ColorRuleManagerActivity.INTENT_KEY_TABLE_ID,
            tp.getTableId());
        rowColorRuleManagerIntent.putExtra(ColorRuleManagerActivity.INTENT_KEY_RULE_GROUP_TYPE,
            ColorRuleGroup.Type.TABLE.name());
        startActivity(rowColorRuleManagerIntent);
        return true;
      }

    });
    displayCat.addPreference(rowColorRulePrefs);

    Preference statusColumnColorRulePref = new Preference(this);
    statusColumnColorRulePref.setTitle(getString(R.string.edit_status_column_color_rules));
    statusColumnColorRulePref.setOnPreferenceClickListener(new OnPreferenceClickListener() {

      @Override
      public boolean onPreferenceClick(Preference preference) {
        Intent rowColorRuleManagerIntent = new Intent(TablePropertiesManager.this,
            ColorRuleManagerActivity.class);
        rowColorRuleManagerIntent.putExtra(Controller.INTENT_KEY_APP_NAME,
            tp.getAppName());
        rowColorRuleManagerIntent.putExtra(ColorRuleManagerActivity.INTENT_KEY_TABLE_ID,
            tp.getTableId());
        rowColorRuleManagerIntent.putExtra(ColorRuleManagerActivity.INTENT_KEY_RULE_GROUP_TYPE,
            ColorRuleGroup.Type.STATUS_COLUMN.name());
        startActivity(rowColorRuleManagerIntent);
        return true;
      }

    });
    displayCat.addPreference(statusColumnColorRulePref);

    setPreferenceScreen(root);
  }

  private void addViewPreferences(PreferenceCategory prefCat) {

    final List<ColumnProperties> numberCols = new ArrayList<ColumnProperties>();
    final List<ColumnProperties> locationCols = new ArrayList<ColumnProperties>();
    final List<ColumnProperties> dateCols = new ArrayList<ColumnProperties>();
    final List<ColumnProperties> geoPointCols = tp.getGeopointColumns();
    for (ColumnProperties cp : tp.getDatabaseColumns().values()) {
      if (cp.getColumnType() == ColumnType.NUMBER || cp.getColumnType() == ColumnType.INTEGER) {
        numberCols.add(cp);
        if (tp.isLatitudeColumn(geoPointCols, cp) || tp.isLongitudeColumn(geoPointCols, cp)) {
          locationCols.add(cp);
        }
      } else if (cp.getColumnType() == ColumnType.GEOPOINT) {
        locationCols.add(cp);
      } else if (cp.getColumnType() == ColumnType.DATE || cp.getColumnType() == ColumnType.DATETIME
          || cp.getColumnType() == ColumnType.TIME) {
        dateCols.add(cp);
      } else if (tp.isLatitudeColumn(geoPointCols, cp) || tp.isLongitudeColumn(geoPointCols, cp)) {
        locationCols.add(cp);
      }
    }

    // int[] viewTypes = settings.getPossibleViewTypes();
    TableViewType[] viewTypes = tp.getPossibleViewTypes();
    String[] viewTypeIds = new String[viewTypes.length];
    String[] viewTypeNames = new String[viewTypes.length];
    // for (int i = 0; i < viewTypes.length; i++) {
    // int viewType = viewTypes[i];
    // viewTypeIds[i] = String.valueOf(viewType);
    // viewTypeNames[i] = LanguageUtil.getViewTypeLabel(viewType);
    // }
    // so now we need to populate the actual menu with the thing to save
    // and to the human-readable labels.
    for (int i = 0; i < viewTypes.length; i++) {
      viewTypeIds[i] = viewTypes[i].name();
      viewTypeNames[i] = viewTypes[i].name();
    }
    ListPreference viewTypePref = new ListPreference(this);
    viewTypePref.setTitle(getString(R.string.view_type));
    viewTypePref.setDialogTitle(getString(R.string.change_view_type));
    viewTypePref.setEntryValues(viewTypeIds);
    viewTypePref.setEntries(viewTypeNames);
    // viewTypePref.setValue(String.valueOf(settings.getViewType()));
    viewTypePref.setValue(tp.getCurrentViewType().name());
    // TODO: currently throwing an error i think
    // viewTypePref.setSummary(LanguageUtil.getViewTypeLabel(
    // settings.getViewType()));
    viewTypePref.setSummary(tp.getCurrentViewType().name());
    viewTypePref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
      @Override
      public boolean onPreferenceChange(Preference preference, Object newValue) {
        // int viewType = Integer.valueOf((String) newValue);
        // settings.setViewType(viewType);
        tp.setCurrentViewType(TableViewType.valueOf((String) newValue));
        init();
        return false;
      }
    });
    prefCat.addPreference(viewTypePref);

    switch (tp.getCurrentViewType()) {
    case List: {
      Preference listViewPrefs = new Preference(this);
      listViewPrefs.setTitle(getString(R.string.list_view_manager));
      listViewPrefs.setOnPreferenceClickListener(new OnPreferenceClickListener() {

        @Override
        public boolean onPreferenceClick(Preference preference) {
          Intent selectListViewIntent = new Intent(TablePropertiesManager.this,
              ListViewManager.class);
          selectListViewIntent.putExtra(Controller.INTENT_KEY_APP_NAME, tp.getAppName());
          selectListViewIntent.putExtra(Controller.INTENT_KEY_TABLE_ID, tp.getTableId());
          startActivity(selectListViewIntent);
          return true;
        }

      });
      prefCat.addPreference(listViewPrefs);
    }
      break;
    case Graph:
      Log.d(TAG, "Graph view type was selected");
      break;

    case Map:
      // Grab the key value store helper from the table activity.
      final KeyValueStoreHelper kvsHelper = tp
          .getKeyValueStoreHelper(TableMapFragment.KVS_PARTITION);

      // Try to find the latitude column in the store.
      ColumnProperties latCol = tp.getColumnByElementKey(kvsHelper
          .getString(TableMapFragment.KEY_MAP_LAT_COL));
      // If there is none, take the first of the location columns and set it.
      if (latCol == null) {
        for (ColumnProperties column : locationCols) {
          if (tp.isLatitudeColumn(geoPointCols, column)) {
            latCol = column;
            break;
          }
        }
        if (latCol == null) {
          latCol = locationCols.get(0);
        }
        kvsHelper.setString(TableMapFragment.KEY_MAP_LAT_COL, latCol.getElementKey());
      }

      // Try to find the longitude column in the store.
      ColumnProperties longCol = tp.getColumnByElementKey(kvsHelper
          .getString(TableMapFragment.KEY_MAP_LONG_COL));
      // If there is none, take the first of the location columns and set it.
      if (longCol == null) {
        for (ColumnProperties column : locationCols) {
          if (tp.isLongitudeColumn(geoPointCols, column)) {
            longCol = column;
            break;
          }
        }
        if (longCol == null) {
          longCol = locationCols.get(0);
        }
        kvsHelper.setString(TableMapFragment.KEY_MAP_LONG_COL, longCol.getElementKey());
      }

      // Add every location column to the list.
      String[] locColDisplayNames = new String[locationCols.size()];
      String[] locColElementKeys = new String[locationCols.size()];
      for (int i = 0; i < locationCols.size(); i++) {
        locColDisplayNames[i] = locationCols.get(i).getDisplayName();
        locColElementKeys[i] = locationCols.get(i).getElementKey();
      }

      // Lat Preference!
      ListPreference mapLatPref = new ListPreference(this);
      mapLatPref.setTitle(getString(R.string.map_view_latitude_column));
      mapLatPref.setDialogTitle(getString(R.string.change_map_view_latitude_column));
      mapLatPref.setEntryValues(locColElementKeys);
      mapLatPref.setEntries(locColDisplayNames);
      mapLatPref.setValue(latCol.getElementKey());
      mapLatPref.setSummary(latCol.getDisplayName());
      mapLatPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
          kvsHelper.setString(TableMapFragment.KEY_MAP_LAT_COL, (String) newValue);
          init();
          return false;
        }
      });
      prefCat.addPreference(mapLatPref);

      // Long Preference!
      ListPreference mapLongPref = new ListPreference(this);
      mapLongPref.setTitle(getString(R.string.map_view_longitude_column));
      mapLongPref.setDialogTitle(getString(R.string.change_map_view_longitude_column));
      mapLongPref.setEntryValues(locColElementKeys);
      mapLongPref.setEntries(locColDisplayNames);
      mapLongPref.setValue(longCol.getElementKey());
      mapLongPref.setSummary(longCol.getDisplayName());
      mapLongPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
          kvsHelper.setString(TableMapFragment.KEY_MAP_LONG_COL, (String) newValue);
          init();
          return false;
        }
      });
      prefCat.addPreference(mapLongPref);

      // ListView Preference!
      FileSelectorPreference listFilePref = new FileSelectorPreference(this, RC_MAP_LIST_VIEW_FILE);
      listFilePref.setTitle(getString(R.string.list_view_file));
      listFilePref.setDialogTitle(getString(R.string.change_map_view_list_view_file));
      String currentFilename = kvsHelper.getString(TableMapFragment.KEY_FILENAME);
      listFilePref.setText(currentFilename);
      prefCat.addPreference(listFilePref);

      // Color Options Preference!
      String colorType = kvsHelper.getString(TableMapFragment.KEY_COLOR_RULE_TYPE);
      if (colorType == null) {
        kvsHelper.setString(TableMapFragment.KEY_COLOR_RULE_TYPE, TableMapFragment.COLOR_TYPE_NONE);
        colorType = TableMapFragment.COLOR_TYPE_NONE;
      }
      ListPreference colorRulePref = new ListPreference(this);
      colorRulePref.setTitle("Map View Color Rule");
      colorRulePref.setDialogTitle("Change which color rule markers adhere to.");
      String[] colorRuleTypes = { TableMapFragment.COLOR_TYPE_NONE,
          TableMapFragment.COLOR_TYPE_TABLE, TableMapFragment.COLOR_TYPE_STATUS,
          TableMapFragment.COLOR_TYPE_COLUMN };
      colorRulePref.setEntryValues(colorRuleTypes);
      colorRulePref.setEntries(colorRuleTypes);
      colorRulePref.setValue(colorType);
      colorRulePref.setSummary(colorType);
      colorRulePref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
          kvsHelper.setString(TableMapFragment.KEY_COLOR_RULE_TYPE, (String) newValue);
          init();
          return false;
        }
      });
      prefCat.addPreference(colorRulePref);

      // If the color rule type is columns, add the preference to select the
      // column.
      if (colorType.equals(TableMapFragment.COLOR_TYPE_COLUMN)) {
        int numberOfDisplayColumns = tp.getNumberOfDisplayColumns();
        String[] colorColDisplayNames = new String[numberOfDisplayColumns];
        String[] colorColElementKeys = new String[numberOfDisplayColumns];
        for (int i = 0; i < numberOfDisplayColumns; i++) {
          ColumnProperties cp = tp.getColumnByIndex(i);
          colorColDisplayNames[i] = cp.getDisplayName();
          colorColElementKeys[i] = cp.getElementKey();
        }

        ColumnProperties colorColumn = tp.getColumnByElementKey(kvsHelper
            .getString(TableMapFragment.KEY_COLOR_RULE_COLUMN));
        if (colorColumn == null) {
          kvsHelper.setString(TableMapFragment.KEY_COLOR_RULE_COLUMN, tp.getColumnByIndex(0)
              .getElementKey());
          colorColumn = tp.getColumnByIndex(0);
        }

        ListPreference colorColumnPref = new ListPreference(this);
        colorColumnPref.setTitle("Color Rule Column");
        colorColumnPref.setDialogTitle("Change the column that applies the color rule.");
        colorColumnPref.setEntryValues(colorColElementKeys);
        colorColumnPref.setEntries(colorColDisplayNames);
        colorColumnPref.setValue(colorColumn.getElementKey());
        colorColumnPref.setSummary(colorColumn.getDisplayName());
        colorColumnPref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
          @Override
          public boolean onPreferenceChange(Preference preference, Object newValue) {
            kvsHelper.setString(TableMapFragment.KEY_COLOR_RULE_COLUMN, (String) newValue);
            init();
            return false;
          }
        });
        prefCat.addPreference(colorColumnPref);
      }
      break;
    default:
      Log.e(TAG, "unrecognized view type: " + tp.getCurrentViewType()
          + ", resetting to spreadsheet");
      tp.setCurrentViewType(TableViewType.Spreadsheet);

    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (resultCode == RESULT_CANCELED) {
      return;
    }
    KeyValueStoreHelper kvsh;
    Uri uri;
    String filename;
    String relativePath;
    switch (requestCode) {
    case RC_DETAIL_VIEW_FILE:
      uri = data.getData();
      filename = uri.getPath();
      relativePath = getRelativePathOfFile(filename);
      kvsh = tp.getKeyValueStoreHelper(DetailDisplayActivity.KVS_PARTITION);
      kvsh.setString(DetailDisplayActivity.KEY_FILENAME, relativePath);
      // tp.setDetailViewFilename(filename);
      init();
      break;
    case RC_LIST_VIEW_FILE:
      uri = data.getData();
      filename = uri.getPath();
      // We need to get the relative path under the app name.
      relativePath = getRelativePathOfFile(filename);
      // Trying to get the new name to the _VIEWS partition.
      kvsh = tp.getKeyValueStoreHelper(ListDisplayActivity.KVS_PARTITION_VIEWS);
      // Set the name here statically, just to test. Later will want to
      // allow custom naming, checking for redundancy, etc.
      KeyValueHelper aspectHelper = kvsh.getAspectHelper("List View 1");
      aspectHelper.setString(ListDisplayActivity.KEY_FILENAME, relativePath);
      init();
      break;
    case RC_MAP_LIST_VIEW_FILE:
      tp.getKeyValueStoreHelper(TableMapFragment.KVS_PARTITION).setString(
          TableMapFragment.KEY_FILENAME, getRelativePathOfFile(data.getData().getPath()));
      init();
    default:
      super.onActivityResult(requestCode, resultCode, data);
    }
  }

  /**
   * Get the relative filepath under the app directory for the full path as
   * returned from OI file picker.
   *
   * @param fullPath
   * @return
   */
  private String getRelativePathOfFile(String fullPath) {
    String relativePath = TableFileUtils.getRelativePath(fullPath);
    return relativePath;
  }

  @Override
  public void onBackPressed() {
    setResult(RESULT_OK);
    finish();
  }

  /**
   * This preference allows the user to select a file from their SD card. If the
   * user does not have a file picker installed on their phone, then a toast
   * will indicate so.
   *
   * @author Chris Gelon (cgelon)
   */
  private class FileSelectorPreference extends EditTextPreference {
    /** Indicates which preference we are using the selector for. */
    private int mRequestCode;

    public FileSelectorPreference(Context context, int requestCode) {
      super(context);
      mRequestCode = requestCode;
    }

    @Override
    protected void onClick() {
      if (hasFilePicker()) {
        Intent intent = new Intent("org.openintents.action.PICK_FILE");
        if (getText() != null) {
          File fullFile = ODKFileUtils.asAppFile(appName, getText());
          try {
            intent.setData(Uri.parse("file://" + fullFile.getCanonicalPath()));
          } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            Toast.makeText(TablePropertiesManager.this, getString(R.string.file_not_found, fullFile.getAbsolutePath()),
                Toast.LENGTH_LONG).show();
          }
        }
        try {
          startActivityForResult(intent, mRequestCode);
        } catch (ActivityNotFoundException e) {
          e.printStackTrace();
          Toast.makeText(TablePropertiesManager.this, getString(R.string.file_picker_not_found),
              Toast.LENGTH_LONG).show();
        }
      } else {
        super.onClick();
        Toast.makeText(TablePropertiesManager.this, getString(R.string.file_picker_not_found),
            Toast.LENGTH_LONG).show();
      }
    }

    /**
     * @return True if the phone has a file picker installed, false otherwise.
     */
    private boolean hasFilePicker() {
      PackageManager packageManager = getPackageManager();
      Intent intent = new Intent("org.openintents.action.PICK_FILE");
      List<ResolveInfo> list = packageManager.queryIntentActivities(intent,
          PackageManager.MATCH_DEFAULT_ONLY);
      return (list.size() > 0);
    }
  }

  // private class ConditionalRulerDialogPreference extends Preference {
  //
  // private final Dialog dialog;
  //
  // public ConditionalRulerDialogPreference(int[] values, String[] labels,
  // Map<ColumnProperties, ConditionalRuler> rulerMap) {
  // super(TablePropertiesManager.this);
  // dialog = new ConditionalRulerDialog(values, labels, rulerMap);
  // }
  //
  // @Override
  // protected void onClick() {
  // dialog.show();
  // }
  // }
  // SS: this was commented out as TableViewSettings was removed. So whatever
  // this is used for needs to be redone to use TableProperties rather than
  // tvs.
  // public class ConditionalRulerDialog extends Dialog {
  //
  // private final int[] values;
  // private final String[] labels;
  // private final Map<ColumnProperties, ConditionalRuler> rulerMap;
  // private final ColumnProperties[] columns;
  // private final String[] comparatorLabels;
  // private final String[] columnDisplays;
  // private LinearLayout ruleList;
  //
  // public ConditionalRulerDialog(int[] values, String[] labels,
  // Map<ColumnProperties, ConditionalRuler> rulerMap) {
  // super(TablePropertiesManager.this);
  // this.values = values;
  // this.labels = labels;
  // this.rulerMap = rulerMap;
  // columns = new ColumnProperties[tp.getColumns().length];
  // comparatorLabels = new String[ConditionalRuler.Comparator.COUNT];
  // for (int i = 0; i < comparatorLabels.length; i++) {
  // comparatorLabels[i] =
  // LanguageUtil.getTvsConditionalComparator(i);
  // }
  // columnDisplays = new String[tp.getColumns().length];
  // for (int i = 0; i < tp.getColumns().length; i++) {
  // ColumnProperties cp = tp.getColumns()[i];
  // columns[i] = cp;
  // columnDisplays[i] = cp.getDisplayName();
  // }
  // setContentView(buildView());
  // }
  //
  // private View buildView() {
  // ruleList = new LinearLayout(TablePropertiesManager.this);
  // ruleList.setOrientation(LinearLayout.VERTICAL);
  // String[] comparatorValues =
  // new String[ConditionalRuler.Comparator.COUNT];
  // String[] comparatorDisplays =
  // new String[ConditionalRuler.Comparator.COUNT];
  // for (int i = 0; i < ConditionalRuler.Comparator.COUNT; i++) {
  // comparatorValues[i] = String.valueOf(i);
  // comparatorDisplays[i] =
  // LanguageUtil.getTvsConditionalComparator(i);
  // }
  // LinearLayout.LayoutParams rwLp = new LinearLayout.LayoutParams(
  // LinearLayout.LayoutParams.FILL_PARENT,
  // LinearLayout.LayoutParams.FILL_PARENT);
  // Context context = TablePropertiesManager.this;
  // for (ColumnProperties cp : tp.getColumns()) {
  // ConditionalRuler cr = rulerMap.get(cp);
  // for (int i = 0; i < cr.getRuleCount(); i++) {
  // ruleList.addView(buildRuleView(cp, cr, i), rwLp);
  // }
  // }
  // LinearLayout controlWrapper = new LinearLayout(context);
  // Button addButton = new Button(context);
  // addButton.setText("Add");
  // addButton.setOnClickListener(new View.OnClickListener() {
  // @Override
  // public void onClick(View v) {
  // ColumnProperties cp = columns[0];
  // ConditionalRuler cr = rulerMap.get(cp);
  // cr.addRule(ConditionalRuler.Comparator.EQUALS, "",
  // values[0]);
  // ruleList.addView(buildRuleView(cp, cr,
  // cr.getRuleCount() - 1));
  // }
  // });
  // controlWrapper.addView(addButton);
  // Button closeButton = new Button(context);
  // closeButton.setText("Close");
  // closeButton.setOnClickListener(new View.OnClickListener() {
  // @Override
  // public void onClick(View v) {
  // dismiss();
  // }
  // });
  // controlWrapper.addView(closeButton);
  // LinearLayout wrapper = new LinearLayout(context);
  // wrapper.setOrientation(LinearLayout.VERTICAL);
  // wrapper.addView(ruleList);
  // wrapper.addView(controlWrapper);
  // return wrapper;
  // }
  //
  // private View buildRuleView(final ColumnProperties cp,
  // final ConditionalRuler cr, final int ruleIndex) {
  // Context context = TablePropertiesManager.this;
  //
  // final Spinner colSpinner = getSpinner(context, columnDisplays);
  // int columnIndex = -1;
  // for (int i = 0; i < columns.length; i++) {
  // if (cp == columns[i]) {
  // columnIndex = i;
  // break;
  // }
  // }
  // if (columnIndex == -1) {
  // throw new RuntimeException();
  // }
  // colSpinner.setSelection(columnIndex);
  //
  // final Spinner settingSpinner = getSpinner(context, labels);
  // int setting = cr.getRuleSetting(ruleIndex);
  // int settingIndex = -1;
  // for (int i = 0; i < values.length; i++) {
  // if (setting == values[i]) {
  // settingIndex = i;
  // break;
  // }
  // }
  // if (settingIndex == -1) {
  // throw new RuntimeException();
  // }
  // settingSpinner.setSelection(settingIndex);
  //
  // final Spinner compSpinner = getSpinner(context, comparatorLabels);
  // compSpinner.setSelection(cr.getRuleComparator(ruleIndex));
  //
  // final EditText valueEt = new EditText(context);
  // valueEt.setText(cr.getRuleValue(ruleIndex));
  //
  // Button deleteButton = new Button(context);
  // deleteButton.setText("Delete");
  //
  // colSpinner.setOnItemSelectedListener(
  // new AdapterView.OnItemSelectedListener() {
  // @Override
  // public void onItemSelected(AdapterView<?> parent, View view,
  // int position, long id) {
  // ColumnProperties nextCp = columns[position];
  // if (cp == nextCp) {
  // return;
  // }
  // cr.deleteRule(ruleIndex);
  // rulerMap.get(nextCp).addRule(
  // compSpinner.getSelectedItemPosition(),
  // valueEt.getText().toString(),
  // values[settingSpinner.getSelectedItemPosition()]);
  // setContentView(buildView());
  // }
  // @Override
  // public void onNothingSelected(AdapterView<?> parent) {}
  // });
  // settingSpinner.setOnItemSelectedListener(
  // new AdapterView.OnItemSelectedListener() {
  // @Override
  // public void onItemSelected(AdapterView<?> parent, View view,
  // int position, long id) {
  // if (cr.getRuleSetting(ruleIndex) == values[position]) {
  // return;
  // }
  // cr.setRuleSetting(ruleIndex, values[position]);
  // }
  // @Override
  // public void onNothingSelected(AdapterView<?> parent) {}
  // });
  // compSpinner.setOnItemSelectedListener(
  // new AdapterView.OnItemSelectedListener() {
  // @Override
  // public void onItemSelected(AdapterView<?> parent, View view,
  // int position, long id) {
  // if (cr.getRuleComparator(ruleIndex) == position) {
  // return;
  // }
  // cr.setRuleComparator(ruleIndex, position);
  // }
  // @Override
  // public void onNothingSelected(AdapterView<?> parent) {}
  // });
  // valueEt.setOnFocusChangeListener(new View.OnFocusChangeListener() {
  // @Override
  // public void onFocusChange(View v, boolean hasFocus) {
  // if (hasFocus) {
  // return;
  // }
  // cr.setRuleValue(ruleIndex, valueEt.getText().toString());
  // }
  // });
  // deleteButton.setOnClickListener(new View.OnClickListener() {
  // @Override
  // public void onClick(View v) {
  // cr.deleteRule(ruleIndex);
  // setContentView(buildView());
  // }
  // });
  //
  // LinearLayout topRow = new LinearLayout(context);
  // LinearLayout bottomRow = new LinearLayout(context);
  // topRow.addView(colSpinner);
  // topRow.addView(settingSpinner);
  // bottomRow.addView(compSpinner);
  // bottomRow.addView(valueEt);
  // bottomRow.addView(deleteButton);
  // LinearLayout rw = new LinearLayout(context);
  // rw.setOrientation(LinearLayout.VERTICAL);
  // rw.addView(topRow);
  // rw.addView(bottomRow);
  // return rw;
  // }
  //
  // private Spinner getSpinner(Context context, String[] values) {
  // Spinner spinner = new Spinner(context);
  // ArrayAdapter<String> adapter = new ArrayAdapter<String>(context,
  // android.R.layout.simple_spinner_item, values);
  // adapter.setDropDownViewResource(
  // android.R.layout.simple_spinner_dropdown_item);
  // spinner.setAdapter(adapter);
  // return spinner;
  // }
  // }
}
