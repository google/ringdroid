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

import java.util.HashMap;

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

public class SongMetadataReader {
    public Uri GENRES_URI = MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI;
    public Activity mActivity = null;
    public String mFilename = "";
    public String mTitle = "";
    public String mArtist = "";
    public String mAlbum = "";
    public String mGenre = "";
    public int mYear = -1;

    SongMetadataReader(Activity activity, String filename) {
        mActivity = activity;
        mFilename = filename;
        mTitle = getBasename(filename);
        try {
            ReadMetadata();
        } catch (Exception e) {
        }
    }

    private void ReadMetadata() {
        // Get a map from genre ids to names
        HashMap<String, String> genreIdMap = new HashMap<String, String>();
        Cursor c = mActivity.getContentResolver().query(
            GENRES_URI,
            new String[] {
                    MediaStore.Audio.Genres._ID,
                    MediaStore.Audio.Genres.NAME },
            null, null, null);
        for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext()) {
            genreIdMap.put(c.getString(0), c.getString(1));
        }
        c.close();
        mGenre = "";
        for (String genreId : genreIdMap.keySet()) {
            c = mActivity.getContentResolver().query(
                makeGenreUri(genreId),
                new String[] { MediaStore.Audio.Media.DATA },
                MediaStore.Audio.Media.DATA + " LIKE \"" + mFilename + "\"",
                null, null);
            if (c.getCount() != 0) {
                mGenre = genreIdMap.get(genreId);
                break;
            }
            c.close();
            c = null;
        }

        Uri uri = MediaStore.Audio.Media.getContentUriForPath(mFilename);
        c = mActivity.getContentResolver().query(
            uri,
            new String[] {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.YEAR,
                MediaStore.Audio.Media.DATA },
            MediaStore.Audio.Media.DATA + " LIKE \"" + mFilename + "\"",
            null, null);
        if (c.getCount() == 0) {
            mTitle = getBasename(mFilename);
            mArtist = "";
            mAlbum = "";
            mYear = -1;
            return;
        }
        c.moveToFirst();
        mTitle = getStringFromColumn(c, MediaStore.Audio.Media.TITLE);
        if (mTitle == null || mTitle.length() == 0) {
            mTitle = getBasename(mFilename);
        }
        mArtist = getStringFromColumn(c, MediaStore.Audio.Media.ARTIST);
        mAlbum = getStringFromColumn(c, MediaStore.Audio.Media.ALBUM);
        mYear = getIntegerFromColumn(c, MediaStore.Audio.Media.YEAR);
        c.close();
    }

    private Uri makeGenreUri(String genreId) {
        String CONTENTDIR = MediaStore.Audio.Genres.Members.CONTENT_DIRECTORY;
        return Uri.parse(
            new StringBuilder()
            .append(GENRES_URI.toString())
            .append("/")
            .append(genreId)
            .append("/")
            .append(CONTENTDIR)
            .toString());
    }

    private String getStringFromColumn(Cursor c, String columnName) {
        int index = c.getColumnIndexOrThrow(columnName);
        String value = c.getString(index);
        if (value != null && value.length() > 0) {
            return value;
        } else {
            return null;
        }
    }

    private int getIntegerFromColumn(Cursor c, String columnName) {
        int index = c.getColumnIndexOrThrow(columnName);
        Integer value = c.getInt(index);
        if (value != null) {
            return value;
        } else {
            return -1;
        }
    }

    private String getBasename(String filename) {
        return filename.substring(filename.lastIndexOf('/') + 1,
                                  filename.lastIndexOf('.'));
    }
}
