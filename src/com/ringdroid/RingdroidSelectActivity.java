/*
 * Copyright (C) 2008 Google Inc.
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
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.MergeCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
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
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import com.ringdroid.soundfile.CheapSoundFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Main screen that shows up when you launch Ringdroid.  Handles selecting
 * an audio file or using an intent to record a new one, and then
 * launches RingdroidEditActivity from here.
 */
public class RingdroidSelectActivity
    extends ListActivity
    implements TextWatcher
{
    private TextView mFilter;
    private SimpleCursorAdapter mAdapter;
    private boolean mWasGetContentIntent;

    // Result codes
    private static final int REQUEST_CODE_EDIT = 1;

    // Menu commands
    private static final int CMD_ABOUT = 1;

    // Context menu
    private static final int CMD_EDIT = 2;
    private static final int CMD_DELETE = 3;

    public RingdroidSelectActivity() {
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        String status = Environment.getExternalStorageState();
        if (status.equals(Environment.MEDIA_MOUNTED_READ_ONLY)) {
            showFinalAlert(getResources().getText(R.string.sdcard_readonly));
            return;
        }
        if (status.equals(Environment.MEDIA_SHARED)) {
            showFinalAlert(getResources().getText(R.string.sdcard_shared));
            return;
        }
        if (!status.equals(Environment.MEDIA_MOUNTED)) {
            showFinalAlert(getResources().getText(R.string.no_sdcard));
            return;
        }

        Intent intent = getIntent();
        mWasGetContentIntent = intent.getAction().equals(
            Intent.ACTION_GET_CONTENT);

        // Inflate our UI from its XML layout description.
        setContentView(R.layout.media_select);

        Button recordButton = (Button) findViewById(R.id.record);
        recordButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View clickedButton) {
                    onRecord();
                }
            });

        try {
            mAdapter = new SimpleCursorAdapter(
                this,
                // Use a template that displays a text view
                R.layout.media_select_row,
                // Give the cursor to the list adatper
                createCursor(""),
                // Map from database columns...
                new String[] {
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media._ID },
                // To widget ids in the row layout...
                new int[] {
                    R.id.row_artist,
                    R.id.row_album,
                    R.id.row_title,
                    R.id.row_icon });
            setListAdapter(mAdapter);

            // Normal click - open the editor
            getListView().setOnItemClickListener(new OnItemClickListener() {
                    public void onItemClick(AdapterView parent,
                                            View view,
                                            int position,
                                            long id) {
                        startRingdroidEditor();
                    }
                });

        } catch (SecurityException e) {
            // No permission to retrieve audio?
            Log.e("Ringdroid", e.toString());
        }

        mAdapter.setViewBinder(new SimpleCursorAdapter.ViewBinder() {
                public boolean setViewValue(View view,
                                            Cursor cursor,
                                            int columnIndex) {
                    if (view.getId() == R.id.row_icon) {
                        setSoundIconFromCursor((ImageView) view, cursor);
                        return true;
                    }
                    return false;
                }
            });

        // Long-press opens a context menu
        registerForContextMenu(getListView());

        mFilter = (TextView) findViewById(R.id.search_filter);
        if (mFilter != null) {
            mFilter.addTextChangedListener(this);
        }
    }

    private void setSoundIconFromCursor(ImageView view, Cursor cursor) {
        if (0 != cursor.getInt(cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.IS_RINGTONE))) {
            view.setImageResource(R.drawable.type_ringtone);
            ((View) view.getParent()).setBackgroundColor(
                getResources().getColor(R.drawable.type_bkgnd_ringtone));
        } else if (0 != cursor.getInt(cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.IS_ALARM))) {
            view.setImageResource(R.drawable.type_alarm);
            ((View) view.getParent()).setBackgroundColor(
                getResources().getColor(R.drawable.type_bkgnd_alarm));
        } else if (0 != cursor.getInt(cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.IS_NOTIFICATION))) {
            view.setImageResource(R.drawable.type_notification);
            ((View) view.getParent()).setBackgroundColor(
                getResources().getColor(R.drawable.type_bkgnd_notification));
        } else if (0 != cursor.getInt(cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.IS_MUSIC))) {
            view.setImageResource(R.drawable.type_music);
            ((View) view.getParent()).setBackgroundColor(
                getResources().getColor(R.drawable.type_bkgnd_music));
        }
    }

    /** Called with an Activity we started with an Intent returns. */
    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent dataIntent) {
        if (requestCode != REQUEST_CODE_EDIT) {
            return;
        }

        if (resultCode != RESULT_OK) {
            return;
        }

        setResult(RESULT_OK, dataIntent);
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuItem item;

        item = menu.add(0, CMD_ABOUT, 0, R.string.menu_about);
        item.setIcon(R.drawable.menu_about);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(CMD_ABOUT).setVisible(true);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case CMD_ABOUT:
            RingdroidEditActivity.onAbout(this);
            return true;
        default:
            return false;
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu,
                                    View v,
                                    ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        Cursor c = mAdapter.getCursor();
        String title = c.getString(c.getColumnIndexOrThrow(
            MediaStore.Audio.Media.TITLE));
        menu.setHeaderTitle(title);

        menu.add(0, CMD_EDIT, 0, "Edit");
        menu.add(0, CMD_DELETE, 0,  "Delete");
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterContextMenuInfo info =
            (AdapterContextMenuInfo) item.getMenuInfo();
        switch (item.getItemId()) {
        case CMD_EDIT:
            startRingdroidEditor();
            return true;
        case CMD_DELETE:
            confirmDelete();
            return true;
        default:
            return super.onContextItemSelected(item);
        }
    }

    private void confirmDelete() {
        // See if the selected list item was created by Ringdroid to
        // determine which alert message to show
        Cursor c = mAdapter.getCursor();
        int artistIndex = c.getColumnIndexOrThrow(
            MediaStore.Audio.Media.ARTIST);
        String artist = c.getString(c.getColumnIndexOrThrow(
            MediaStore.Audio.Media.ARTIST));
        CharSequence ringdroidArtist =
            getResources().getText(R.string.artist_name);

        CharSequence message;
        if (artist.equals(ringdroidArtist)) {
            message = getResources().getText(
                R.string.confirm_delete_ringdroid);
        } else {
            message = getResources().getText(
                R.string.confirm_delete_non_ringdroid);
        }

        CharSequence title;
        if (0 != c.getInt(c.getColumnIndexOrThrow(
            MediaStore.Audio.Media.IS_RINGTONE))) {
            title = getResources().getText(R.string.delete_ringtone);
        } else if (0 != c.getInt(c.getColumnIndexOrThrow(
            MediaStore.Audio.Media.IS_ALARM))) {
            title = getResources().getText(R.string.delete_alarm);
        } else if (0 != c.getInt(c.getColumnIndexOrThrow(
            MediaStore.Audio.Media.IS_NOTIFICATION))) {
            title = getResources().getText(R.string.delete_notification);
        } else if (0 != c.getInt(c.getColumnIndexOrThrow(
            MediaStore.Audio.Media.IS_MUSIC))) {
            title = getResources().getText(R.string.delete_music);
        } else {
            title = getResources().getText(R.string.delete_audio);
        }

        new AlertDialog.Builder(RingdroidSelectActivity.this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(
                R.string.delete_ok_button,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                                        int whichButton) {
                        onDelete();
                    }
                })
            .setNegativeButton(
                R.string.delete_cancel_button,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                                        int whichButton) {
                    }
                })
            .setCancelable(true)
            .show();
    }

    private void onDelete() {
        Cursor c = mAdapter.getCursor();
        int dataIndex = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
        String filename = c.getString(dataIndex);

        int uriIndex = c.getColumnIndex(
            "\"" + MediaStore.Audio.Media.INTERNAL_CONTENT_URI + "\"");
        if (uriIndex == -1) {
            uriIndex = c.getColumnIndex(
                "\"" + MediaStore.Audio.Media.EXTERNAL_CONTENT_URI + "\"");
        }
        if (uriIndex == -1) {
            showFinalAlert(getResources().getText(R.string.delete_failed));
            return;
        }

        if (!new File(filename).delete()) {
            showFinalAlert(getResources().getText(R.string.delete_failed));
        }

        String itemUri = c.getString(uriIndex) + "/" +
            c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));
        getContentResolver().delete(Uri.parse(itemUri), null, null);
    }

    private void showFinalAlert(CharSequence message) {
        new AlertDialog.Builder(RingdroidSelectActivity.this)
            .setTitle(getResources().getText(R.string.alert_title_failure))
            .setMessage(message)
            .setPositiveButton(
                R.string.alert_ok_button,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,
                                        int whichButton) {
                        finish();
                    }
                })
            .setCancelable(false)
            .show();
    }

    private void onRecord() {
        try {
            Intent intent = new Intent(Intent.ACTION_EDIT,
                                       Uri.parse("record"));
            intent.putExtra("was_get_content_intent",
                            mWasGetContentIntent);
            intent.setClassName(
                "com.ringdroid",
                "com.ringdroid.RingdroidEditActivity");
            startActivityForResult(intent, REQUEST_CODE_EDIT);
        } catch (Exception e) {
            Log.e("Ringdroid", "Couldn't start editor");
        }
    }

    private void startRingdroidEditor() {
        Cursor c = mAdapter.getCursor();
        int dataIndex = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
        String filename = c.getString(dataIndex);
        try {
            Intent intent = new Intent(Intent.ACTION_EDIT,
                                       Uri.parse(filename));
            intent.putExtra("was_get_content_intent",
                            mWasGetContentIntent);
            intent.setClassName(
                "com.ringdroid",
                "com.ringdroid.RingdroidEditActivity");
            startActivityForResult(intent, REQUEST_CODE_EDIT);
        } catch (Exception e) {
            Log.e("Ringdroid", "Couldn't start editor");
        }
    }

    private Cursor getInternalAudioCursor(String selection,
                                          String[] selectionArgs) {
        return managedQuery(
            MediaStore.Audio.Media.INTERNAL_CONTENT_URI,
            INTERNAL_COLUMNS,
            selection,
            selectionArgs,
            MediaStore.Audio.Media.DEFAULT_SORT_ORDER);
    }

    private Cursor getExternalAudioCursor(String selection,
                                          String[] selectionArgs) {
        return managedQuery(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            EXTERNAL_COLUMNS,
            selection,
            selectionArgs,
            MediaStore.Audio.Media.DEFAULT_SORT_ORDER);
    }

    Cursor createCursor(String filter) {
        ArrayList<String> args = new ArrayList<String>();
        String selection = "(";
        for (String extension : CheapSoundFile.getSupportedExtensions()) {
            args.add("%." + extension);
            if (selection.length() > 1) {
                selection += " OR ";
            }
            selection += "(_DATA LIKE ?)";
        }
        selection += ")";

        selection = "(" + selection + ") AND (_DATA NOT LIKE ?)";
        args.add("%espeak-data/scratch%");

        if (filter != null && filter.length() > 0) {
            filter = "%" + filter + "%";
            selection =
                "(" + selection + " AND " +
                "((TITLE LIKE ?) OR (ARTIST LIKE ?) OR (ALBUM LIKE ?)))";
            args.add(filter);
            args.add(filter);
            args.add(filter);
        }

        String[] argsArray = args.toArray(new String[args.size()]);

        Cursor external = getExternalAudioCursor(selection, argsArray);
        Cursor internal = getInternalAudioCursor(selection, argsArray);

        Cursor c = new MergeCursor(new Cursor[] {
            getExternalAudioCursor(selection, argsArray),
            getInternalAudioCursor(selection, argsArray)});
        startManagingCursor(c);
        return c;
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

    private static final String[] INTERNAL_COLUMNS = new String[] {
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.IS_RINGTONE,
        MediaStore.Audio.Media.IS_ALARM,
        MediaStore.Audio.Media.IS_NOTIFICATION,
        MediaStore.Audio.Media.IS_MUSIC,
        "\"" + MediaStore.Audio.Media.INTERNAL_CONTENT_URI + "\""
    };

    private static final String[] EXTERNAL_COLUMNS = new String[] {
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.IS_RINGTONE,
        MediaStore.Audio.Media.IS_ALARM,
        MediaStore.Audio.Media.IS_NOTIFICATION,
        MediaStore.Audio.Media.IS_MUSIC,
        "\"" + MediaStore.Audio.Media.EXTERNAL_CONTENT_URI + "\""
    };
}
