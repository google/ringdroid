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

import android.app.Dialog;
import android.content.Context;
import android.content.res.Resources;
import android.os.Message;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import java.util.ArrayList;
import java.util.HashMap;

public class FileSaveDialog extends Dialog {

    private Spinner mTypeSpinner;
    private EditText mFilename;
    private Message mResponse;

    public FileSaveDialog(Context context, Resources resources,
                          String defaultName, Message response) {
        super(context);

        // Inflate our UI from its XML layout description.
        setContentView(R.layout.file_save);

        setTitle(resources.getString(R.string.file_save_title));

        ArrayList<String> typeArray = new ArrayList<String>(0);
        typeArray.add(resources.getString(R.string.type_music));
        typeArray.add(resources.getString(R.string.type_alarm));
        typeArray.add(resources.getString(R.string.type_notification));
        typeArray.add(resources.getString(R.string.type_ringtone));

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
            context, android.R.layout.simple_spinner_item, typeArray);
        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item);
        mTypeSpinner = (Spinner) findViewById(R.id.ringtone_type);
        mTypeSpinner.setAdapter(adapter);
        mTypeSpinner.setSelection(3);

        Button save = (Button)findViewById(R.id.save);
        save.setOnClickListener(saveListener);
        Button cancel = (Button)findViewById(R.id.cancel);
        cancel.setOnClickListener(cancelListener);
        mFilename = (EditText)findViewById(R.id.filename);
        mFilename.setText(defaultName);
        mResponse = response;
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
