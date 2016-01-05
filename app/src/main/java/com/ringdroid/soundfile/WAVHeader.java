/*
 * Copyright (C) 2015 Google Inc.
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

public class WAVHeader {
    private byte[] mHeader;          // the complete header.
    private int mSampleRate;         // sampling frequency in Hz (e.g. 44100).
    private int mChannels;           // number of channels.
    private int mNumSamples;         // total number of samples per channel.
    private int mNumBytesPerSample;  // number of bytes per sample, all channels included.

    public WAVHeader(int sampleRate, int numChannels, int numSamples) {
        mSampleRate = sampleRate;
        mChannels = numChannels;
        mNumSamples = numSamples;
        mNumBytesPerSample = 2 * mChannels;  // assuming 2 bytes per sample (for 1 channel)
        mHeader = null;
        setHeader();
    }

    public byte[] getWAVHeader() {
        return mHeader;
    }

    public static byte[] getWAVHeader(int sampleRate, int numChannels, int numSamples) {
        return new WAVHeader(sampleRate, numChannels, numSamples).mHeader;
    }

    public String toString() {
        String str = "";
        if (mHeader == null) {
            return str;
        }
        int num_32bits_per_lines = 8;
        int count = 0;
        for (byte b : mHeader) {
            boolean break_line = count > 0 && count % (num_32bits_per_lines * 4) == 0;
            boolean insert_space = count > 0 && count % 4 == 0 && !break_line;
            if (break_line) {
                str += '\n';
            }
            if (insert_space) {
                str += ' ';
            }
            str += String.format("%02X", b);
            count++;
        }

        return str;
    }

    private void setHeader() {
        byte[] header = new byte[46];
        int offset = 0;
        int size;

        // set the RIFF chunk
        System.arraycopy(new byte[] {'R', 'I', 'F', 'F'}, 0, header, offset, 4);
        offset += 4;
        size = 36 + mNumSamples * mNumBytesPerSample;
        header[offset++] = (byte)(size & 0xFF);
        header[offset++] = (byte)((size >> 8) & 0xFF);
        header[offset++] = (byte)((size >> 16) & 0xFF);
        header[offset++] = (byte)((size >> 24) & 0xFF);
        System.arraycopy(new byte[] {'W', 'A', 'V', 'E'}, 0, header, offset, 4);
        offset += 4;

        // set the fmt chunk
        System.arraycopy(new byte[] {'f', 'm', 't', ' '}, 0, header, offset, 4);
        offset += 4;
        System.arraycopy(new byte[] {0x10, 0, 0, 0}, 0, header, offset, 4);  // chunk size = 16
        offset += 4;
        System.arraycopy(new byte[] {1, 0}, 0, header, offset, 2);  // format = 1 for PCM
        offset += 2;
        header[offset++] = (byte)(mChannels & 0xFF);
        header[offset++] = (byte)((mChannels >> 8) & 0xFF);
        header[offset++] = (byte)(mSampleRate & 0xFF);
        header[offset++] = (byte)((mSampleRate >> 8) & 0xFF);
        header[offset++] = (byte)((mSampleRate >> 16) & 0xFF);
        header[offset++] = (byte)((mSampleRate >> 24) & 0xFF);
        int byteRate = mSampleRate * mNumBytesPerSample;
        header[offset++] = (byte)(byteRate & 0xFF);
        header[offset++] = (byte)((byteRate >> 8) & 0xFF);
        header[offset++] = (byte)((byteRate >> 16) & 0xFF);
        header[offset++] = (byte)((byteRate >> 24) & 0xFF);
        header[offset++] = (byte)(mNumBytesPerSample & 0xFF);
        header[offset++] = (byte)((mNumBytesPerSample >> 8) & 0xFF);
        System.arraycopy(new byte[] {0x10, 0}, 0, header, offset, 2);
        offset += 2;

        // set the beginning of the data chunk
        System.arraycopy(new byte[] {'d', 'a', 't', 'a'}, 0, header, offset, 4);
        offset += 4;
        size = mNumSamples * mNumBytesPerSample;
        header[offset++] = (byte)(size & 0xFF);
        header[offset++] = (byte)((size >> 8) & 0xFF);
        header[offset++] = (byte)((size >> 16) & 0xFF);
        header[offset++] = (byte)((size >> 24) & 0xFF);

        mHeader = header;
    }
}
