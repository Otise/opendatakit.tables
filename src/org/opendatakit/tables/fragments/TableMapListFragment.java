package org.opendatakit.tables.fragments;

import java.util.ArrayList;

import org.opendatakit.tables.R;
import org.opendatakit.tables.activities.TableActivity;
import org.opendatakit.tables.data.KeyValueStoreHelper;
import org.opendatakit.tables.data.TableProperties;
import org.opendatakit.tables.data.UserTable;
import org.opendatakit.tables.views.webkits.CustomTableView;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragment;

/**
 * A TableMapListFragment displays data in a table in a list format. The format
 * is specified by a html file in the TablePropertiesManager Activity. This
 * TableMapListFragment is special: it only displays one row, which is the data
 * from the selected map marker.
 * 
 * @author Chris Gelon (cgelon)
 */
public class TableMapListFragment extends SherlockFragment {
  private static final String SAVED_KEY_INDEXES = "savedKeyIndexes";

  /** The container that holds this fragment's views. */
  private ViewGroup mContainer;

  /** The indexes in the UserTable for the rows to display. */
  private ArrayList<Integer> mIndexes;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    
    if (savedInstanceState != null) {
      mIndexes = savedInstanceState.getIntegerArrayList(SAVED_KEY_INDEXES);
    }
  }
  
  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    mContainer = container;
    setVisibility(View.GONE);
    return null;
  }

  @Override
  public void onResume() {
    super.onResume();
    resetView();
  }
  
  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    
    outState.putIntegerArrayList(SAVED_KEY_INDEXES, mIndexes);
  }

  private void resetView() {
    if (mIndexes != null && mIndexes.size() > 0) {
      TableProperties tp = ((TableActivity) getActivity()).getTableProperties();
      UserTable table = ((TableActivity) getActivity()).getTable();
      // Grab the key value store helper from the map fragment.
      final KeyValueStoreHelper kvsHelper = tp
          .getKeyValueStoreHelper(TableMapFragment.KVS_PARTITION);
      // Find which file stores the html information for displaying
      // the list.
      String filename = kvsHelper.getString(TableMapFragment.KEY_FILENAME);
      if (filename == null) {
        Toast.makeText(getActivity(), getActivity().getString(R.string.list_view_file_not_set),
            Toast.LENGTH_LONG).show();
        return;
      }
      // Create the custom view and set it.
      CustomTableView view = CustomTableView.get(getActivity(), tp, table, filename,
          mIndexes);
      view.display();
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
      mContainer.removeAllViews();
      mContainer.addView(view, params);

      WebViewClient client = new WebViewClient() {
        public void onPageFinished(WebView view, String url) {
          LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.WRAP_CONTENT, view.getMeasuredHeight());
          if (containerParams.height > 0) {
            // mContainer.setLayoutParams(containerParams);
          }
          mContainer.setVisibility(View.VISIBLE);
        }
      };

      view.setOnFinishedLoaded(client);
    }
  }

  /**
   * Sets the index of the list view, which will be the row of the data wanting
   * to be displayed.
   */
  public void setIndex(int index) {
    if (mIndexes != null) {
      mIndexes.clear();
    }
    mIndexes = new ArrayList<Integer>();
    if (index >= 0) {
      mIndexes.add(index);
    }
    resetView();
  }

  /**
   * Sets the visibility of this fragment (will set the container's visibility).
   * 
   * @param visibility
   *          The new visibility of the fragment (constants found in View).
   */
  public void setVisibility(int visibility) {
    mContainer.setVisibility(visibility);
  }
}