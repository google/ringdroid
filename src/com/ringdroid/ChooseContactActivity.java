/*
 * Copyright (C) 2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ringdroid;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.MergeCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Contacts;
import android.provider.Contacts.People;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * After a ringtone has been saved, this activity lets you pick a contact
 * and assign the ringtone to that contact.
 */
public class ChooseContactActivity
    extends ListActivity
    implements TextWatcher
{
    private TextView mFilter;
    private SimpleCursorAdapter mAdapter;
    private Uri mRingtoneUri;

    public ChooseContactActivity() {
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setTitle(R.string.choose_contact_title);

        Intent intent = getIntent();
        mRingtoneUri = intent.getData();

        // Inflate our UI from its XML layout description.
        setContentView(R.layout.choose_contact);

        try {
            mAdapter = new SimpleCursorAdapter(
                this,
                // Use a template that displays a text view
                R.layout.contact_row,
                // Give the cursor to the list adatper
                createCursor(""),
                // Map from database columns...
                new String[] {
                    People.CUSTOM_RINGTONE,
                    People.STARRED,
                    People.DISPLAY_NAME },
                // To widget ids in the row layout...
                new int[] {
                    R.id.row_ringtone,
                    R.id.row_starred,
                    R.id.row_display_name });

            mAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
                    public boolean setViewValue(View view,
                                                Cursor cursor,
                                                int columnIndex) {
                        String name = cursor.getColumnName(columnIndex);
                        String value = cursor.getString(columnIndex);
                        if (name.equals(People.CUSTOM_RINGTONE)) {
                            if (value != null && value.length() > 0) {
                                view.setVisibility(View.VISIBLE);
                            } else  {
                                view.setVisibility(View.INVISIBLE);
                            }
                            return true;
                        }
                        if (name.equals(People.STARRED)) {
                            if (value != null && value.equals("1")) {
                                view.setVisibility(View.VISIBLE);
                            } else  {
                                view.setVisibility(View.INVISIBLE);
                            }
                            return true;
                        }

                        return false;
                    }
                });

            setListAdapter(mAdapter);

            // On click, assign ringtone to contact
            getListView().setOnItemClickListener(new OnItemClickListener() {
                    public void onItemClick(AdapterView parent,
                                            View view,
                                            int position,
                                            long id) {
                        assignRingtoneToContact();
                    }
                });

        } catch (SecurityException e) {
            // No permission to retrieve contacts?
            Log.e("Ringdroid", e.toString());
        }

        mFilter = (TextView) findViewById(R.id.search_filter);
        if (mFilter != null) {
            mFilter.addTextChangedListener(this);
        }
    }

    private void assignRingtoneToContact() {
        Cursor c = mAdapter.getCursor();
        int dataIndex = c.getColumnIndexOrThrow(People._ID);
        String contactId = c.getString(dataIndex);

        dataIndex = c.getColumnIndexOrThrow(People.DISPLAY_NAME);
        String displayName = c.getString(dataIndex);

        Uri uri = Uri.withAppendedPath(People.CONTENT_URI, contactId);

        ContentValues values = new ContentValues();
        values.put(People.CUSTOM_RINGTONE, mRingtoneUri.toString());
        getContentResolver().update(uri, values, null, null);

        String message =
            getResources().getText(R.string.success_contact_ringtone) +
            " " +
            displayName;

        Toast.makeText(this, message, Toast.LENGTH_SHORT)
            .show();
        finish();
        return;
    }

    private Cursor createCursor(String filter) {
        String selection;
        if (filter != null && filter.length() > 0) {
            selection = "(DISPLAY_NAME LIKE \"%" + filter + "%\")";
        } else {
            selection = null;
        }
        Cursor cursor = managedQuery(
            People.CONTENT_URI,
            new String[] {
                People._ID,
                People.CUSTOM_RINGTONE,
                People.DISPLAY_NAME,
                People.LAST_TIME_CONTACTED,
                People.NAME,
                People.STARRED,
                People.TIMES_CONTACTED },
            selection,
            null,
            "STARRED DESC, TIMES_CONTACTED DESC, LAST_TIME_CONTACTED DESC");

        Log.i("Ringdroid", cursor.getCount() + " contacts");

        return cursor;
    }

    public void beforeTextChanged(CharSequence s, int start,
                                  int count, int after) {
    }

    public void onTextChanged(CharSequence s,
                              int start, int before, int count) {
    }

    public void afterTextChanged(Editable s) {
        String filterStr = mFilter.getText().toString();
        mAdapter.changeCursor(createCursor(filterStr));
    }
}
