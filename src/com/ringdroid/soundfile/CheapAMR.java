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
import java.lang.Math;

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
    private int mBitRate;

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
        return mBitRate;
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
        mBitRate = 10;
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

        parse3gpp(stream, maxLen - boxLen);
    }

    void parseAMR(InputStream stream, int maxLen)
            throws java.io.IOException {
        int[] prevEner = new int[4];
        for (int i = 0; i < 4; i++) {
            prevEner[i] = 0;
        }

        int[] prevEnerMR122 = new int[4];
        for (int i = 0; i < 4; i++) {
            prevEnerMR122[i] = -2381;
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
            mBitRate = 5;
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
        case 7:
            mBitRate = 12;
            int[] adaptiveIndex = new int[4];
            int[] adaptiveGain = new int[4];
            int[] fixedGain = new int[4];
            int[][] pulse = new int[4][];
            for (int i = 0; i < 4; i++) {
                pulse[i] = new int[10];
            }
            getMR122Params(bits, adaptiveIndex, adaptiveGain, fixedGain, pulse);

            int T0 = 0;
            for (int subframe = 0; subframe < 4; subframe++) {
                int[] code = new int[40];
                for (int i = 0; i < 40; i++) {
                    code[i] = 0;
                }

                int sign;
                for (int j = 0; j < 5; j++) {
                    if (((pulse[subframe][j] >> 3) & 1) == 0) {
                        sign = 4096;
                    } else {
                        sign = -4096;
                    }

                    int pos1 = j + GRAY[pulse[subframe][j] & 7] * 5;
                    int pos2 = j + GRAY[pulse[subframe][j + 5] & 7] * 5;
                    code[pos1] = sign;
                    if (pos2 < pos1) {
                        sign = -sign;
                    }
                    code[pos2] = code[pos2] + sign;
                }

                int index = adaptiveIndex[subframe];

                if (subframe == 0 || subframe == 2) {
                    if (index < 463) {
                        T0 = (index + 5) / 6 + 17;
                    } else {
                        T0 = index - 368;
                    }
                } else {
                    int pitMin = 18;
                    int pitMax = 143;
                    int T0Min = T0 - 5;
                    if (T0Min < pitMin) {
                        T0Min = pitMin;
                    }
                    int T0Max = T0Min + 9;
                    if (T0Max > pitMax) {
                        T0Max = pitMax;
                        T0Min = T0Max - 9;
                    }
                    T0 = T0Min + (index + 5) / 6 - 1;
                }

                int pitSharp =
                    (QUA_GAIN_PITCH[adaptiveGain[subframe]] >> 2) << 2;
                if (pitSharp > 16383) {
                    pitSharp = 32767;
                } else {
                    pitSharp *= 2;
                }

                for (int j = T0; j < 40; j++) {
                    code[j] += (code[j - T0] * pitSharp) >> 15;
                }
            
                int enerCode = 0;
                for (int j = 0; j < 40; j++) {
                    enerCode += code[j] * code[j];
                }

                if ((0x3fffffff <= enerCode) || (enerCode < 0)) {
                    enerCode = 0x7fffffff;
                } else {
                    enerCode *= 2;
                }
                enerCode = ((enerCode + 0x8000) >> 16) * 52428;

                double log2 = Math.log(enerCode) / Math.log(2);
                int exp = (int)log2;
                int frac = (int)((log2 - exp) * 32768);
                enerCode = ((exp - 30) << 16) + (frac * 2);

                int ener =
                    prevEner[0] * 44 +
                    prevEner[1] * 37 +
                    prevEner[2] * 22 +
                    prevEner[3] * 12;

                ener = 2 * ener + 783741;
                ener = (ener - enerCode) / 2;

                int expGCode = ener >> 16;
                int fracGCode = (ener >> 1) - (expGCode << 15);

                int gCode0 = (int)
                    (Math.pow(2.0, expGCode + (fracGCode / 32768.0)) + 0.5);

                if (gCode0 <= 2047) {
                    gCode0 = gCode0 << 4;
                } else {
                    gCode0 = 32767;
                }

                index = fixedGain[subframe];

                int gainCode =
                    ((gCode0 * QUA_GAIN_CODE[3 * index]) >> 15) << 1;

                if ((gainCode & 0xFFFF8000) != 0) {
                    gainCode = 32767;
                }

                int frameGainEstimate = gainCode;

                addFrame(frameOffset, blockSize + 1, frameGainEstimate);

                int quaEnerMR122 = QUA_GAIN_CODE[3 * index + 1];
                prevEner[3] = prevEner[2];
                prevEner[2] = prevEner[1];
                prevEner[1] = prevEner[0];
                prevEner[0] = quaEnerMR122;
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

    void getMR122Params(int[] bits,
                        int[] adaptiveIndex,
                        int[] adaptiveGain,
                        int[] fixedGain,
                        int[][] pulse) {
        adaptiveIndex[0] =
            0x01 * bits[45] +
            0x02 * bits[43] +
            0x04 * bits[41] +
            0x08 * bits[39] +
            0x10 * bits[37] +
            0x20 * bits[35] +
            0x40 * bits[33] +
            0x80 * bits[31] +
            0x100 * bits[29];
        adaptiveIndex[1] =
            0x01 * bits[242] +
            0x02 * bits[79] +
            0x04 * bits[77] +
            0x08 * bits[75] +
            0x10 * bits[73] +
            0x20 * bits[71];
        adaptiveIndex[2] =
            0x01 * bits[46] +
            0x02 * bits[44] +
            0x04 * bits[42] +
            0x08 * bits[40] +
            0x10 * bits[38] +
            0x20 * bits[36] +
            0x40 * bits[34] +
            0x80 * bits[32] +
            0x100 * bits[30];
        adaptiveIndex[3] =
            0x01 * bits[243] +
            0x02 * bits[80] +
            0x04 * bits[78] +
            0x08 * bits[76] +
            0x10 * bits[74] +
            0x20 * bits[72];

        adaptiveGain[0] =
            0x01 * bits[88] +
            0x02 * bits[55] +
            0x04 * bits[51] +
            0x08 * bits[47];
        adaptiveGain[1] =
            0x01 * bits[89] +
            0x02 * bits[56] +
            0x04 * bits[52] +
            0x08 * bits[48];
        adaptiveGain[2] =
            0x01 * bits[90] +
            0x02 * bits[57] +
            0x04 * bits[53] +
            0x08 * bits[49];
        adaptiveGain[3] =
            0x01 * bits[91] +
            0x02 * bits[58] +
            0x04 * bits[54] +
            0x08 * bits[50];

        fixedGain[0] =
            0x01 * bits[104] +
            0x02 * bits[92] +
            0x04 * bits[67] +
            0x08 * bits[63] +
            0x10 * bits[59];
        fixedGain[1] =
            0x01 * bits[105] +
            0x02 * bits[93] +
            0x04 * bits[68] +
            0x08 * bits[64] +
            0x10 * bits[60];
        fixedGain[2] =
            0x01 * bits[106] +
            0x02 * bits[94] +
            0x04 * bits[69] +
            0x08 * bits[65] +
            0x10 * bits[61];
        fixedGain[3] =
            0x01 * bits[107] +
            0x02 * bits[95] +
            0x04 * bits[70] +
            0x08 * bits[66] +
            0x10 * bits[62];

        pulse[0][0] =
            0x01 * bits[122] +
            0x02 * bits[123] +
            0x04 * bits[124] +
            0x08 * bits[96];
        pulse[0][1] =
            0x01 * bits[125] +
            0x02 * bits[126] +
            0x04 * bits[127] +
            0x08 * bits[100];
        pulse[0][2] =
            0x01 * bits[128] +
            0x02 * bits[129] +
            0x04 * bits[130] +
            0x08 * bits[108];
        pulse[0][3] =
            0x01 * bits[131] +
            0x02 * bits[132] +
            0x04 * bits[133] +
            0x08 * bits[112];
        pulse[0][4] =
            0x01 * bits[134] +
            0x02 * bits[135] +
            0x04 * bits[136] +
            0x08 * bits[116];
        pulse[0][5] =
            0x01 * bits[182] +
            0x02 * bits[183] +
            0x04 * bits[184];
        pulse[0][6] =
            0x01 * bits[185] +
            0x02 * bits[186] +
            0x04 * bits[187];
        pulse[0][7] =
            0x01 * bits[188] +
            0x02 * bits[189] +
            0x04 * bits[190];
        pulse[0][8] =
            0x01 * bits[191] +
            0x02 * bits[192] +
            0x04 * bits[193];
        pulse[0][9] =
            0x01 * bits[194] +
            0x02 * bits[195] +
            0x04 * bits[196];
        pulse[1][0] =
            0x01 * bits[137] +
            0x02 * bits[138] +
            0x04 * bits[139] +
            0x08 * bits[97];
        pulse[1][1] =
            0x01 * bits[140] +
            0x02 * bits[141] +
            0x04 * bits[142] +
            0x08 * bits[101];
        pulse[1][2] =
            0x01 * bits[143] +
            0x02 * bits[144] +
            0x04 * bits[145] +
            0x08 * bits[109];
        pulse[1][3] =
            0x01 * bits[146] +
            0x02 * bits[147] +
            0x04 * bits[148] +
            0x08 * bits[113];
        pulse[1][4] =
            0x01 * bits[149] +
            0x02 * bits[150] +
            0x04 * bits[151] +
            0x08 * bits[117];
        pulse[1][5] =
            0x01 * bits[197] +
            0x02 * bits[198] +
            0x04 * bits[199];
        pulse[1][6] =
            0x01 * bits[200] +
            0x02 * bits[201] +
            0x04 * bits[202];
        pulse[1][7] =
            0x01 * bits[203] +
            0x02 * bits[204] +
            0x04 * bits[205];
        pulse[1][8] =
            0x01 * bits[206] +
            0x02 * bits[207] +
            0x04 * bits[208];
        pulse[1][9] =
            0x01 * bits[209] +
            0x02 * bits[210] +
            0x04 * bits[211];
        pulse[2][0] =
            0x01 * bits[152] +
            0x02 * bits[153] +
            0x04 * bits[154] +
            0x08 * bits[98];
        pulse[2][1] =
            0x01 * bits[155] +
            0x02 * bits[156] +
            0x04 * bits[157] +
            0x08 * bits[102];
        pulse[2][2] =
            0x01 * bits[158] +
            0x02 * bits[159] +
            0x04 * bits[160] +
            0x08 * bits[110];
        pulse[2][3] =
            0x01 * bits[161] +
            0x02 * bits[162] +
            0x04 * bits[163] +
            0x08 * bits[114];
        pulse[2][4] =
            0x01 * bits[164] +
            0x02 * bits[165] +
            0x04 * bits[166] +
            0x08 * bits[118];
        pulse[2][5] =
            0x01 * bits[212] +
            0x02 * bits[213] +
            0x04 * bits[214];
        pulse[2][6] =
            0x01 * bits[215] +
            0x02 * bits[216] +
            0x04 * bits[217];
        pulse[2][7] =
            0x01 * bits[218] +
            0x02 * bits[219] +
            0x04 * bits[220];
        pulse[2][8] =
            0x01 * bits[221] +
            0x02 * bits[222] +
            0x04 * bits[223];
        pulse[2][9] =
            0x01 * bits[224] +
            0x02 * bits[225] +
            0x04 * bits[226];
        pulse[3][0] =
            0x01 * bits[167] +
            0x02 * bits[168] +
            0x04 * bits[169] +
            0x08 * bits[99];
        pulse[3][1] =
            0x01 * bits[170] +
            0x02 * bits[171] +
            0x04 * bits[172] +
            0x08 * bits[103];
        pulse[3][2] =
            0x01 * bits[173] +
            0x02 * bits[174] +
            0x04 * bits[175] +
            0x08 * bits[111];
        pulse[3][3] =
            0x01 * bits[176] +
            0x02 * bits[177] +
            0x04 * bits[178] +
            0x08 * bits[115];
        pulse[3][4] =
            0x01 * bits[179] +
            0x02 * bits[180] +
            0x04 * bits[181] +
            0x08 * bits[119];
        pulse[3][5] =
            0x01 * bits[227] +
            0x02 * bits[228] +
            0x04 * bits[229];
        pulse[3][6] =
            0x01 * bits[230] +
            0x02 * bits[231] +
            0x04 * bits[232];
        pulse[3][7] =
            0x01 * bits[233] +
            0x02 * bits[234] +
            0x04 * bits[235];
        pulse[3][8] =
            0x01 * bits[236] +
            0x02 * bits[237] +
            0x04 * bits[238];
        pulse[3][9] =
            0x01 * bits[239] +
            0x02 * bits[240] +
            0x04 * bits[241];
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

    static private int QUA_GAIN_CODE[] = {
        159, -3776, -22731, 206, -3394, -20428,
        268, -3005, -18088, 349, -2615, -15739,
        419, -2345, -14113, 482, -2138, -12867,
        554, -1932, -11629, 637, -1726, -10387,
        733, -1518, -9139, 842, -1314, -7906,
        969, -1106, -6656, 1114, -900, -5416,
        1281, -694, -4173, 1473, -487, -2931,
        1694, -281, -1688, 1948, -75, -445,
        2241, 133, 801, 2577, 339, 2044,
        2963, 545, 3285, 3408, 752, 4530,
        3919, 958, 5772, 4507, 1165, 7016,
        5183, 1371, 8259, 5960, 1577, 9501,
        6855, 1784, 10745, 7883, 1991, 11988,
        9065, 2197, 13231, 10425, 2404, 14474,
        12510, 2673, 16096, 16263, 3060, 18429,
        21142, 3448, 20763, 27485, 3836, 23097};

    static private int GRAY[] = {0, 1, 3, 2, 5, 6, 4, 7};

    static private int QUA_GAIN_PITCH[] = {
        0, 3277, 6556, 8192, 9830, 11469, 12288, 13107, 13926,
        14746, 15565, 16384, 17203, 18022, 18842, 19661};

    /** For debugging
    public static void main(String[] argv) throws Exception {
        File f = new File("");
        CheapAMR c = new CheapAMR();
        c.ReadFile(f);
        c.WriteFile(new File(""),
                    0, c.getNumFrames());
    } **/
};
