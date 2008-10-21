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

package com.ringdroid.soundfile;

import java.io.File;

/**
 * CheapSoundFile is the parent class of several subclasses that each
 * do a "cheap" scan of various sound file formats, parsing as little
 * as possible in order to understand the high-level frame structure
 * and get a rough estimate of the volume level of each frame.  Each
 * subclass is able to:
 *  - open a sound file
 *  - return the sample rate and number of frames
 *  - return an approximation of the volume level of each frame
 *  - write a new sound file with a subset of the frames
 *
 * A frame should represent no less than 1 ms and no more than 100 ms of
 * audio.  This is compatible with the native frame sizes of most audio
 * file formats already, but if not, this class should expose virtual
 * frames in that size range.
 */
public class CheapSoundFile {
	/**
	 * Static method to create the appropriate CheapSoundFile subclass
	 * given a filename.
	 *
	 * TODO: make this more modular rather than hardcoding the logic
	 */
    public static CheapSoundFile create(String fileName)
        throws java.io.FileNotFoundException,
               java.io.IOException {
        File f = new File(fileName);
        if (!f.exists()) {
            throw new java.io.FileNotFoundException(fileName);
        }
        String name = f.getName().toLowerCase();
        if (name.endsWith(".3gpp") ||
            name.endsWith(".3gp") ||
            name.endsWith(".amr")) {
            return new CheapAMR(f);
        } else if (name.endsWith(".mp3")) {
            return new CheapMP3(f);
        } else if (name.endsWith(".wav")) {
            return new CheapWAV(f);
        }
        else {
            return null;
        }
    }

	/**
	 * Return the filename extensions that are recognized by one of
	 * our subclasses.
	 *
	 * TODO: share this logic with create().
	 */
    public static String[] getSupportedExtensions() {
        return new String[] { "3gpp", "3gp", "amr", "mp3", "wav" };
    }

    protected CheapSoundFile() {
    }

    public int getNumFrames() {
        return 0;
    }

    public int getSamplesPerFrame() {
        return 0;
    }

    public int[] getFrameOffsets() {
        return null;
    }

    public int[] getFrameLens() {
        return null;
    }

    public int[] getFrameGains() {
        return null;
    }

    public int getFileSizeBytes() {
        return 0;
    }

    public int getAvgBitrateKbps() {
        return 0;
    }

    public int getSampleRate() {
        return 0;
    }

    public int getChannels() {
        return 0;
    }

    public void WriteFile(File outputFile, int startFrame, int numFrames)
            throws java.io.IOException {
    }
};
