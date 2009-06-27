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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * CheapAMR is a CheapSoundFile implementation for AMR (Adaptive Multi-Rate)
 * encoded sound files, which is one of the native formats supported by
 * Android's MediaRecorder library.  It supports files with a full 3GPP
 * header, and also files with only a basic AMR header.
 *
 * While there are 8 bitrates and several other frame types in AMR,
 * this implementation currently only supports frametype=1,
 * MR515, 10.3 kbits / sec, which is the format encoded on Android 1.0
 * phones.  In the future it may be necessary to support other bitrates.
 */
public class CheapAMR extends CheapSoundFile {
    public static Factory getFactory() {
        return new Factory() {
            public CheapSoundFile create() {
                return new CheapAMR();
            }
            public String[] getSupportedExtensions() {
                return new String[] { "3gpp", "3gp", "amr" };
            }
        };
    }

    // Member variables containing frame info
    private int mNumFrames;
    private int[] mFrameOffsets;
    private int[] mFrameLens;
    private int[] mFrameGains;
    private int mFileSize;

    // Member variables used only while initially parsing the file
    private int mOffset;
    private int mMaxFrames;
    private int mMinGain;
    private int mMaxGain;

    public CheapAMR() {
    }

    public int getNumFrames() {
        return mNumFrames;
    }

    public int getSamplesPerFrame() {
        return 40;
    }

    public int[] getFrameOffsets() {
        return mFrameOffsets;
    }

    public int[] getFrameLens() {
        return mFrameLens;
    }

    public int[] getFrameGains() {
        return mFrameGains;
    }

    public int getFileSizeBytes() {
        return mFileSize;        
    }

    public int getAvgBitrateKbps() {
        // TODO: support more bitrates
        return 10;
    }

    public int getSampleRate() {
        return 8000;
    }

    public int getChannels() {
        return 1;
    }

    public String getFiletype() {
        return "AMR";
    }

    public void ReadFile(File inputFile)
            throws java.io.FileNotFoundException,
            java.io.IOException {
        super.ReadFile(inputFile);
        mNumFrames = 0;
        mMaxFrames = 64;  // This will grow as needed
        mFrameOffsets = new int[mMaxFrames];
        mFrameLens = new int[mMaxFrames];
        mFrameGains = new int[mMaxFrames];
        mMinGain = 1000000000;
        mMaxGain = 0;
        mOffset = 0;

        // No need to handle filesizes larger than can fit in a 32-bit int
        mFileSize = (int)mInputFile.length();

        if (mFileSize < 128) {
            throw new java.io.IOException("File too small to parse");
        }

        FileInputStream stream = new FileInputStream(mInputFile);
        byte[] header = new byte[12];
        stream.read(header, 0, 6);
        mOffset += 6;
        if (header[0] == '#' &&
            header[1] == '!' &&
            header[2] == 'A' &&
            header[3] == 'M' &&
            header[4] == 'R' &&
            header[5] == '\n') {
            parseAMR(stream, mFileSize - 6);
        }

        stream.read(header, 6, 6);
        mOffset += 6;

        if (header[4] == 'f' &&
            header[5] == 't' &&
            header[6] == 'y' &&
            header[7] == 'p' &&
            header[8] == '3' &&
            header[9] == 'g' &&
            header[10] == 'p' &&
            header[11] == '4') {

            int boxLen =
                ((0xff & header[0]) << 24) |
                ((0xff & header[1]) << 16) |
                ((0xff & header[2]) << 8) |
                ((0xff & header[3]));

            if (boxLen >= 4 && boxLen <= mFileSize - 8) {
                stream.skip(boxLen - 12);
                mOffset += boxLen - 12;
            }

            parse3gpp(stream, mFileSize - boxLen);
        }
    }

    private void parse3gpp(InputStream stream, int maxLen)
            throws java.io.IOException {
        if (maxLen < 8)
            return;

        byte[] boxHeader = new byte[8];
        stream.read(boxHeader, 0, 8);
        mOffset += 8;

        int boxLen =
            ((0xff & boxHeader[0]) << 24) |
            ((0xff & boxHeader[1]) << 16) |
            ((0xff & boxHeader[2]) << 8) |
            ((0xff & boxHeader[3]));
        if (boxLen > maxLen)
            return;

        if (boxHeader[4] == 'm' &&
            boxHeader[5] == 'd' &&
            boxHeader[6] == 'a' &&
            boxHeader[7] == 't') {
            parseAMR(stream, boxLen);
            return;
        }

        parse3gpp(stream, boxLen);
    }

    void parseAMR(InputStream stream, int maxLen)
            throws java.io.IOException {

        int[] prevEner = new int[4];
        for (int i = 0; i < 4; i++) {
            prevEner[i] = 0;
        }

        int originalMaxLen = maxLen;
        int bytesTotal = 0;
        while (maxLen > 0) {
            int bytesConsumed = parseAMRFrame(stream, maxLen, prevEner);
            bytesTotal += bytesConsumed;
            maxLen -= bytesConsumed;

            if (mProgressListener != null) {
                boolean keepGoing = mProgressListener.reportProgress(
                    bytesTotal * 1.0 / originalMaxLen);
                if (!keepGoing) {
                    break;
                }
            }
        }
    }

    int parseAMRFrame(InputStream stream, int maxLen, int[] prevEner)
            throws java.io.IOException {
        int frameOffset = mOffset;
        byte[] frameTypeHeader = new byte[1];
        stream.read(frameTypeHeader, 0, 1);
        mOffset += 1;
        int frameType = ((0xff & frameTypeHeader[0]) >> 3) % 0x0F;
        int frameQuality = ((0xff & frameTypeHeader[0]) >> 2) & 0x01;
        int blockSize = BLOCK_SIZES[frameType];

        if (blockSize + 1 > maxLen) {
            // We can't read the full frame, so consume the remaining
            // bytes to end processing the AMR stream.
            return maxLen;
        }

        if (blockSize == 0) {
            return 1;
        }

        byte[] v = new byte[blockSize];
        stream.read(v, 0, blockSize);
        mOffset += blockSize;

        int[] bits = new int[blockSize * 8];
        int ii = 0;
        int value = 0xff & v[ii];
        for (int i = 0; i < blockSize * 8; i++) {
            bits[i] = ((value & 0x80) >> 7);
            value <<= 1;
            if ((i & 0x07) == 0x07 && i < blockSize * 8 - 1) {
                ii += 1;
                value = 0xff & v[ii];
            }
        }

        switch (frameType) {
        case 1:
            int[] gain = new int[4];
            gain[0] =
                0x01 * bits[24] +
                0x02 * bits[25] +
                0x04 * bits[26] +
                0x08 * bits[36] +
                0x10 * bits[45] +
                0x20 * bits[55];
            gain[1] =
                0x01 * bits[27] +
                0x02 * bits[28] +
                0x04 * bits[29] +
                0x08 * bits[37] +
                0x10 * bits[46] +
                0x20 * bits[56];
            gain[2] =
                0x01 * bits[30] +
                0x02 * bits[31] +
                0x04 * bits[32] +
                0x08 * bits[38] +
                0x10 * bits[47] +
                0x20 * bits[57];
            gain[3] =
                0x01 * bits[33] +
                0x02 * bits[34] +
                0x04 * bits[35] +
                0x08 * bits[39] +
                0x10 * bits[48] +
                0x20 * bits[58];

            for (int i = 0; i < 4; i++) {
                int gcode0 =
                    (385963008 +
                     prevEner[0] * 5571 +
                     prevEner[1] * 4751 +
                     prevEner[2] * 2785 +
                     prevEner[3] * 1556) >> 15;
                int quaEner = QUA_ENER_MR515[gain[i]];
                int gFac = GAIN_FAC_MR515[gain[i]];

                prevEner[3] = prevEner[2];
                prevEner[2] = prevEner[1];
                prevEner[1] = prevEner[0];
                prevEner[0] = quaEner;

                int frameGainEstimate = (gcode0 * gFac) >> 24;

                addFrame(frameOffset, blockSize + 1, frameGainEstimate);
            }

            break;
        default:
            System.out.println("Unsupported frame type: " + frameType);
            addFrame(frameOffset, blockSize + 1, 1);
            break;
        }

        // Return number of bytes consumed
        return blockSize + 1;
    }

    void addFrame(int offset, int frameSize, int gain) {
        mFrameOffsets[mNumFrames] = offset;
        mFrameLens[mNumFrames] = frameSize;
        mFrameGains[mNumFrames] = gain;
        if (gain < mMinGain)
            mMinGain = gain;
        if (gain > mMaxGain)
            mMaxGain = gain;

        mNumFrames++;
        if (mNumFrames == mMaxFrames) {
            int newMaxFrames = mMaxFrames * 2;

            int[] newOffsets = new int[newMaxFrames];
            int[] newLens = new int[newMaxFrames];
            int[] newGains = new int[newMaxFrames];
            for (int i = 0; i < mNumFrames; i++) {
                newOffsets[i] = mFrameOffsets[i];
                newLens[i] = mFrameLens[i];
                newGains[i] = mFrameGains[i];
            }
            mFrameOffsets = newOffsets;
            mFrameLens = newLens;
            mFrameGains = newGains;
            mMaxFrames = newMaxFrames;
        }
    }

    public void WriteFile(File outputFile, int startFrame, int numFrames)
            throws java.io.IOException {
        outputFile.createNewFile();
        FileInputStream in = new FileInputStream(mInputFile);
        FileOutputStream out = new FileOutputStream(outputFile);

        byte[] header = new byte[6];
        header[0] = '#';
        header[1] = '!';
        header[2] = 'A';
        header[3] = 'M';
        header[4] = 'R';
        header[5] = '\n';
        out.write(header, 0, 6);

        int maxFrameLen = 0;
        for (int i = 0; i < numFrames; i++) {
            if (mFrameLens[startFrame + i] > maxFrameLen)
                maxFrameLen = mFrameLens[startFrame + i];
        }
        byte[] buffer = new byte[maxFrameLen];
        int pos = 0;
        for (int i = 0; i < numFrames; i++) {
            int skip = mFrameOffsets[startFrame + i] - pos;
            int len = mFrameLens[startFrame + i];
            if (skip < 0) {
                continue;
            }
            if (skip > 0) {
                in.skip(skip);
                pos += skip;
            }
            in.read(buffer, 0, len);
            out.write(buffer, 0, len);
            pos += len;
        }

        in.close();
        out.close();
    }

    // Block size in bytes for each of the 16 frame types, not
    // counting the initial byte that indicates the frame type.
    // Can be used to skip over unsupported frame types.
    static private int BLOCK_SIZES[] = {
        12, 13, 15, 17, 19, 20, 26, 31,
        5, 0, 0, 0, 0, 0, 0, 0 };

    static private int GAIN_FAC_MR515[] = {
        28753, 2785, 6594, 7413, 10444, 1269, 4423, 1556,
        12820, 2498, 4833, 2498, 7864, 1884, 3153, 1802,
        20193, 3031, 5857, 4014, 8970, 1392, 4096, 655,
        13926, 3112, 4669, 2703, 6553, 901, 2662, 655,
        23511, 2457, 5079, 4096, 8560, 737, 4259, 2088,
        12288, 1474, 4628, 1433, 7004, 737, 2252, 1228,
        17326, 2334, 5816, 3686, 8601, 778, 3809, 614,
        9256, 1761, 3522, 1966, 5529, 737, 3194, 778
    };

    static private int QUA_ENER_MR515[] = {
        17333, -3431, 4235, 5276, 8325, -10422, 683, -8609,
        10148, -4398, 1472, -4398, 5802, -6907, -2327, -7303,
        14189, -2678, 3181, -180, 6972, -9599, 0, -16305,
        10884, -2444, 1165, -3697, 4180, -13468, -3833, -16305,
        15543, -4546, 1913, 0, 6556, -15255, 347, -5993,
        9771, -9090, 1086, -9341, 4772, -15255, -5321, -10714,
        12827, -5002, 3118, -938, 6598, -14774, -646, -16879,
        7251, -7508, -1343, -6529, 2668, -15255, -2212, -2454, -14774
    };
};
