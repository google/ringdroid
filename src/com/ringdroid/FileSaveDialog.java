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

import java.util.ArrayList;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Resources;
import android.os.Message;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

public class FileSaveDialog extends Dialog {

    // File kinds - these should correspond to the order in which
    // they're presented in the spinner control
    public static final int FILE_KIND_MUSIC = 0;
    public static final int FILE_KIND_ALARM = 1;
    public static final int FILE_KIND_NOTIFICATION = 2;
    public static final int FILE_KIND_RINGTONE = 3;

    private Spinner mTypeSpinner;
    private EditText mFilename;
    private Message mResponse;
    private String mOriginalName;
    private ArrayList<String> mTypeArray;
    private int mPreviousSelection;

    /**
     * Return a human-readable name for a kind (music, alarm, ringtone, ...).
     * These won't be displayed on-screen (just in logs) so they shouldn't
     * be translated.
     */
    public static String KindToName(int kind) {
        switch(kind) {
        default:
            return "Unknown";
        case FILE_KIND_MUSIC:
            return "Music";
        case FILE_KIND_ALARM:
            return "Alarm";
        case FILE_KIND_NOTIFICATION:
            return "Notification";
        case FILE_KIND_RINGTONE:
            return "Ringtone";
        }
    }

    public FileSaveDialog(Context context,
                          Resources resources,
                          String originalName,
                          Message response) {
        super(context);

        // Inflate our UI from its XML layout description.
        setContentView(R.layout.file_save);

        setTitle(resources.getString(R.string.file_save_title));

        mTypeArray = new ArrayList<String>();
        mTypeArray.add(resources.getString(R.string.type_music));
        mTypeArray.add(resources.getString(R.string.type_alarm));
        mTypeArray.add(resources.getString(R.string.type_notification));
        mTypeArray.add(resources.getString(R.string.type_ringtone));

        mFilename = (EditText)findViewById(R.id.filename);
        mOriginalName = originalName;

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
            context, android.R.layout.simple_spinner_item, mTypeArray);
        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item);
        mTypeSpinner = (Spinner) findViewById(R.id.ringtone_type);
        mTypeSpinner.setAdapter(adapter);
        mTypeSpinner.setSelection(FILE_KIND_RINGTONE);
        mPreviousSelection = FILE_KIND_RINGTONE;

        setFilenameEditBoxFromName(false);

        mTypeSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> parent,
                                           View v,
                                           int position,
                                           long id) {
                    setFilenameEditBoxFromName(true);
                }
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });

        Button save = (Button)findViewById(R.id.save);
        save.setOnClickListener(saveListener);
        Button cancel = (Button)findViewById(R.id.cancel);
        cancel.setOnClickListener(cancelListener);
        mResponse = response;
    }

    private void setFilenameEditBoxFromName(boolean onlyIfNotEdited) {
        if (onlyIfNotEdited) {
            CharSequence currentText = mFilename.getText();
            String expectedText = mOriginalName + " " +
                mTypeArray.get(mPreviousSelection);

            if (!expectedText.contentEquals(currentText)) {
                return;
            }
        }

        int newSelection = mTypeSpinner.getSelectedItemPosition();
        String newSuffix = mTypeArray.get(newSelection);
        mFilename.setText(mOriginalName + " " + newSuffix);
        mPreviousSelection = mTypeSpinner.getSelectedItemPosition();
    }

    private View.OnClickListener saveListener = new View.OnClickListener() {
            public void onClick(View view) {
                mResponse.obj = mFilename.getText();
                mResponse.arg1 = mTypeSpinner.getSelectedItemPosition();
                mResponse.sendToTarget();
                dismiss();
            }
        };

    private View.OnClickListener cancelListener = new View.OnClickListener() {
            public void onClick(View view) {
                dismiss();
            }
        };
}
