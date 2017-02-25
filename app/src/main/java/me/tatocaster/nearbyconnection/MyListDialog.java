package me.tatocaster.nearbyconnection;

/**
 * Created by tatocaster on 2/26/17.
 */

import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.widget.ArrayAdapter;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Simple wrapper for an AlertDialog with a list of items, each of which has a display string and
 * and associated value (similar to a "select" tag in HTML).
 */
public class MyListDialog {

    private AlertDialog mDialog;
    private ArrayAdapter<String> mAdapter;
    private HashMap<String, String> mItemMap;

    public MyListDialog(Context context, AlertDialog.Builder builder,
                        DialogInterface.OnClickListener listener) {

        mItemMap = new HashMap<>();
        mAdapter = new ArrayAdapter<>(context, android.R.layout.select_dialog_singlechoice);

        // Create dialog from builder
        builder.setAdapter(mAdapter, listener);
        mDialog = builder.create();
    }


    /**
     * Add an item to the Dialog's list.
     *
     * @param title the human-readable string that should be used to display the item.
     * @param value a value associated with the item that should not be displayed.
     */
    public void addItem(String title, String value) {
        mItemMap.put(title, value);
        mAdapter.add(title);
    }

    /**
     * Remove an item from the list by its title.
     *
     * @param title the title of the item to remove.
     */
    public void removeItemByTitle(String title) {
        mItemMap.remove(title);
        mAdapter.remove(title);
    }

    /**
     * Remove an item from the list by its associated value.
     * Note: this is an O(n) operation.
     *
     * @param value the value of the item to remove.
     */
    public void removeItemByValue(String value) {
        Iterator<Map.Entry<String, String>> iterator = mItemMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            if (entry.getValue().equals(value)) {
                iterator.remove();
                mAdapter.remove(entry.getKey());
                mAdapter.notifyDataSetChanged();
            }
        }
    }

    /**
     * Get the title of the item at an index,
     *
     * @param index the index of the item in the list.
     * @return the item's title.
     */
    public String getItemKey(int index) {
        return mAdapter.getItem(index);
    }

    /**
     * Get the value of an item at an index.
     *
     * @param index the index of the item in the list.
     * @return the item's value.
     */
    public String getItemValue(int index) {
        return mItemMap.get(getItemKey(index));
    }

    /**
     * Show the dialog (calls AlertDialog#show).
     */
    public void show() {
        mDialog.show();
    }

    /**
     * Dismiss the dialog if it is showing (calls AlertDialog#dismiss).
     */
    public void dismiss() {
        if (mDialog.isShowing()) {
            mDialog.dismiss();
        }
    }

}
