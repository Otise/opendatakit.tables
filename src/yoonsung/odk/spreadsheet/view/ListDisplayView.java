package yoonsung.odk.spreadsheet.view;

import yoonsung.odk.spreadsheet.DataStructure.DisplayPrefs;
import yoonsung.odk.spreadsheet.data.TableProperties;
import yoonsung.odk.spreadsheet.data.TableViewSettings;
import yoonsung.odk.spreadsheet.data.UserTable;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/**
 * A class for displaying data in list form.
 * 
 * @author hkworden
 */
public class ListDisplayView extends LinearLayout {
    
    private int BACKGROUND_COLOR = Color.WHITE;
    private int BORDER_COLOR = Color.BLACK;
    private int TEXT_COLOR = Color.BLACK;
    
    private Controller controller; // the table activity to call back to
    private UserTable table; // the table to display
    private TableProperties tp;
    private TableViewSettings tvs;
    private int[] lineHeights;
    private String[][] lineTextSpecs;
    private int[][] lineColSpecs;
    private Paint[] colPaints;
    
    public static ListDisplayView buildView(Context context,
            TableProperties tp, TableViewSettings tvs, Controller controller,
            UserTable table) {
        return new ListDisplayView(context, tp, tvs, controller, table);
    }
    
    private ListDisplayView(Context context, TableProperties tp,
            TableViewSettings tvs, Controller controller, UserTable table) {
        super(context);
        setOrientation(LinearLayout.VERTICAL);
        this.controller = controller;
        this.table = table;
        this.tp = tp;
        this.tvs = tvs;
        setFormatInfo();
        removeAllViews();
        setBackgroundColor(BACKGROUND_COLOR);
        buildList(context);
    }
    
    private void setFormatInfo() {
        String format = tp.getSummaryDisplayFormat();
        if (format == null || format.length() == 0) {
            format = getDefaultFormat();
        }
        Log.d("LDV", "format:" + format);
        String[] lines = (format.length() == 0) ?
                new String[] {} : format.split("\n");
        lineHeights = new int[lines.length];
        lineTextSpecs = new String[lines.length][];
        lineColSpecs = new int[lines.length][];
        colPaints = new Paint[lines.length];
        for (int i = 0; i < lines.length; i++) {
            String[] lineData = lines[i].split(":", 2);
            lineHeights[i] = 20;
            Paint paint = new Paint();
            paint.setAntiAlias(true);
            paint.setTextSize(16);
            paint.setColor(TEXT_COLOR);
            for (int j = 0; j < lineData[0].length(); j++) {
                char c = lineData[0].charAt(j);
                if (c == 'b') {
                    paint.setFakeBoldText(true);
                } else if (c == 'u') {
                    paint.setUnderlineText(true);
                } else if (c == 'l') {
                    paint.setTextSize(24);
                    lineHeights[i] = 28;
                }
            }
            colPaints[i] = paint;
            String[] lineSplit = (lineData.length < 2) ? new String[] {} :
                    lineData[1].split("%");
            lineTextSpecs[i] = new String[(lineSplit.length / 2) +
                    (lineSplit.length % 2 == 0 ? 0 : 1)];
            lineColSpecs[i] = new int[lineSplit.length / 2];
            for (int j = 0; j < lineSplit.length; j += 2) {
                lineTextSpecs[i][j / 2] = lineSplit[j];
            }
            for (int j = 1; j < lineSplit.length; j += 2) {
                int colIndex = tp.getColumnIndex(lineSplit[j]);
                if (colIndex < 0) {
                    String colDbName = tp.getColumnByDisplayName(lineSplit[j]);
                    if (colDbName == null) {
                        colDbName = tp.getColumnByAbbreviation(lineSplit[j]);
                    }
                    colIndex = tp.getColumnIndex(colDbName);
                }
                lineColSpecs[i][j / 2] = colIndex;
            }
        }
    }
    
    private void buildList(Context context) {
        View.OnClickListener clickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                controller.onListItemClick(v.getId());
            }
        };
        ScrollView scroll = new ScrollView(context);
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        int count = table.getHeight();
        for (int i = 0; i < count; i++) {
            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            View v = new ItemView(context, i);
            v.setId(i);
            v.setOnClickListener(clickListener);
            wrapper.addView(v, itemLp);
        }
        scroll.addView(wrapper);
        addView(scroll);
    }
    
    private String getDefaultFormat() {
        StringBuilder builder = new StringBuilder();
        if (table.getWidth() > 0) {
            builder.append("bl:%" + table.getHeader(0) + "%\n");
        }
        for (int i = 1; i < 4 && i < table.getWidth(); i++) {
            builder.append(":%" + table.getHeader(i) + "%\n");
        }
        return builder.toString();
    }
    
    private class ItemView extends View {
        
        private int rowNum;
        
        public ItemView(Context context, int rowNum) {
            super(context);
            this.rowNum = rowNum;
            int height = 10;
            for (int i = 0; i < lineHeights.length; i++) {
                height += lineHeights[i];
            }
            setMinimumHeight(height);
        }
        
        @Override
        public void onDraw(Canvas canvas) {
            int y = 0;
            for (int i = 0; i < lineHeights.length; i++) {
                y += lineHeights[i];
                canvas.drawText(getText(i), 0, y, colPaints[i]);
            }
        }
        
        private String getText(int lineNum) {
            StringBuilder builder = new StringBuilder();
            int i;
            for (i = 0; i < lineColSpecs[lineNum].length; i++) {
                builder.append(lineTextSpecs[lineNum][i]);
                String value = table.getData(rowNum, lineColSpecs[lineNum][i]);
                builder.append((value == null) ? "" : value);
            }
            if (lineTextSpecs[lineNum].length > i) {
                builder.append(lineTextSpecs[lineNum][i]);
            }
            return builder.toString();
        }
    }
    
    public interface Controller {
        
        public void onListItemClick(int rowNum);
    }
}
