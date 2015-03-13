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

import android.app.ListActivity;
import android.app.LoaderManager;
import android.content.ContentValues;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

/**
 * After a ringtone has been saved, this activity lets you pick a contact
 * and assign the ringtone to that contact.
 */
public class ChooseContactActivity
    extends ListActivity
    implements TextWatcher, LoaderManager.LoaderCallbacks<Cursor>
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
                // Set an empty cursor right now. Will be set in onLoadFinished()
                null,
                // Map from database columns...
                new String[] {
                    Contacts.CUSTOM_RINGTONE,
                    Contacts.STARRED,
                    Contacts.DISPLAY_NAME },
                // To widget ids in the row layout...
                new int[] {
                    R.id.row_ringtone,
                    R.id.row_starred,
                    R.id.row_display_name },
                0);

            mAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
                    public boolean setViewValue(View view,
                                                Cursor cursor,
                                                int columnIndex) {
                        String name = cursor.getColumnName(columnIndex);
                        String value = cursor.getString(columnIndex);
                        if (name.equals(Contacts.CUSTOM_RINGTONE)) {
                            if (value != null && value.length() > 0) {
                                view.setVisibility(View.VISIBLE);
                            } else  {
                                view.setVisibility(View.INVISIBLE);
                            }
                            return true;
                        }
                        if (name.equals(Contacts.STARRED)) {
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
            getListView().setOnItemClickListener(
                new OnItemClickListener() {
                    public void onItemClick(AdapterView<?> parent,
                                            View view,
                                            int position,
                                            long id) {
                        assignRingtoneToContact();
                    }
                }
            );

            getLoaderManager().initLoader(0,  null, this);

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
        int dataIndex = c.getColumnIndexOrThrow(Contacts._ID);
        String contactId = c.getString(dataIndex);

        dataIndex = c.getColumnIndexOrThrow(Contacts.DISPLAY_NAME);
        String displayName = c.getString(dataIndex);

        Uri uri = Uri.withAppendedPath(Contacts.CONTENT_URI, contactId);

        ContentValues values = new ContentValues();
        values.put(Contacts.CUSTOM_RINGTONE, mRingtoneUri.toString());
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

    /* Implementation of TextWatcher.beforeTextChanged */
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    /* Implementation of TextWatcher.onTextChanged */
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    /* Implementation of TextWatcher.afterTextChanged */
    public void afterTextChanged(Editable s) {
        //String filterStr = mFilter.getText().toString();
        //mAdapter.changeCursor(createCursor(filterStr));
        Bundle args = new Bundle();
        args.putString("filter", mFilter.getText().toString());
        getLoaderManager().restartLoader(0,  args, this);
    }

    /* Implementation of LoaderCallbacks.onCreateLoader */
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        String selection = null;
        String filter = args != null ? args.getString("filter") : null;
        if (filter != null && filter.length() > 0) {
            selection = "(DISPLAY_NAME LIKE \"%" + filter + "%\")";
        }
        return new CursorLoader(
                this,
                Contacts.CONTENT_URI,
                new String[] {
                        Contacts._ID,
                        Contacts.CUSTOM_RINGTONE,
                        Contacts.DISPLAY_NAME,
                        Contacts.LAST_TIME_CONTACTED,
                        Contacts.STARRED,
                        Contacts.TIMES_CONTACTED },
                selection,
                null,
                "STARRED DESC, " +
                "TIMES_CONTACTED DESC, " +
                "LAST_TIME_CONTACTED DESC, " +
                "DISPLAY_NAME ASC"
                );
    }

    /* Implementation of LoaderCallbacks.onLoadFinished */
    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        Log.v("Ringdroid", data.getCount() + " contacts");
        mAdapter.swapCursor(data);
    }

    /* Implementation of LoaderCallbacks.onLoaderReset */
    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // This is called when the last Cursor provided to onLoadFinished()
        // above is about to be closed.  We need to make sure we are no
        // longer using it.
        mAdapter.swapCursor(null);
    }
}
