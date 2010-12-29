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

        if (boxLen > maxLen || boxLen <= 0)
            return;

        if (boxHeader[4] == 'm' &&
            boxHeader[5] == 'd' &&
            boxHeader[6] == 'a' &&
            boxHeader[7] == 't') {
            parseAMR(stream, boxLen);
            return;
        }

	stream.skip(boxLen - 8);
	mOffset += (boxLen - 8);

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

	int[] gain;
        switch (frameType) {
	case 0:
            mBitRate = 5;
            gain = new int[4];
            gain[0] =
                0x01 * bits[28] +
                0x02 * bits[29] +
                0x04 * bits[30] +
                0x08 * bits[31] +
                0x10 * bits[46] +
                0x20 * bits[47] +
		0x40 * bits[48] +
		0x80 * bits[49];
            gain[1] = gain[0];
            gain[2] =
                0x01 * bits[32] +
                0x02 * bits[33] +
                0x04 * bits[34] +
                0x08 * bits[35] +
                0x10 * bits[40] +
                0x20 * bits[41] +
                0x40 * bits[42] +
                0x80 * bits[43];
            gain[3] = gain[2];

            for (int i = 0; i < 4; i++) {
		int index = gain[i] * 4 + (i & 1) * 2 + 1;
		int gFac = GAIN_FAC_MR475[index];

                double log2 = Math.log(gFac) / Math.log(2);
                int exp = (int)log2;
                int frac = (int)((log2 - exp) * 32768);

		exp -= 12;
		int tmp = exp * 49320;
		tmp += ((frac * 24660) >> 15) * 2;
		int quaEner = ((tmp * 8192) + 0x8000) >> 16;

                int gcode0 =
                    (385963008 +
                     prevEner[0] * 5571 +
                     prevEner[1] * 4751 +
                     prevEner[2] * 2785 +
                     prevEner[3] * 1556) >> 15;

                prevEner[3] = prevEner[2];
                prevEner[2] = prevEner[1];
                prevEner[1] = prevEner[0];
                prevEner[0] = quaEner;

                int frameGainEstimate = (gcode0 * gFac) >> 24;

                addFrame(frameOffset, blockSize + 1, frameGainEstimate);
            }

	    break;

        case 1:
            mBitRate = 5;
            gain = new int[4];
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

    static private int GAIN_FAC_MR475[] = {
	812, 128, 542, 140, 2873, 1135, 2266, 3402,
	2067, 563, 12677, 647, 4132, 1798, 5601, 5285,
	7689, 374, 3735, 441, 10912, 2638, 11807, 2494,
	20490, 797, 5218, 675, 6724, 8354, 5282, 1696,
	1488, 428, 5882, 452, 5332, 4072, 3583, 1268,
	2469, 901, 15894, 1005, 14982, 3271, 10331, 4858,
	3635, 2021, 2596, 835, 12360, 4892, 12206, 1704,
	13432, 1604, 9118, 2341, 3968, 1538, 5479, 9936,
	3795, 417, 1359, 414, 3640, 1569, 7995, 3541,
	11405, 645, 8552, 635, 4056, 1377, 16608, 6124,
	11420, 700, 2007, 607, 12415, 1578, 11119, 4654,
	13680, 1708, 11990, 1229, 7996, 7297, 13231, 5715,
	2428, 1159, 2073, 1941, 6218, 6121, 3546, 1804,
	8925, 1802, 8679, 1580, 13935, 3576, 13313, 6237,
	6142, 1130, 5994, 1734, 14141, 4662, 11271, 3321,
	12226, 1551, 13931, 3015, 5081, 10464, 9444, 6706,
	1689, 683, 1436, 1306, 7212, 3933, 4082, 2713,
	7793, 704, 15070, 802, 6299, 5212, 4337, 5357,
	6676, 541, 6062, 626, 13651, 3700, 11498, 2408,
	16156, 716, 12177, 751, 8065, 11489, 6314, 2256,
	4466, 496, 7293, 523, 10213, 3833, 8394, 3037,
	8403, 966, 14228, 1880, 8703, 5409, 16395, 4863,
	7420, 1979, 6089, 1230, 9371, 4398, 14558, 3363,
	13559, 2873, 13163, 1465, 5534, 1678, 13138, 14771,
	7338, 600, 1318, 548, 4252, 3539, 10044, 2364,
	10587, 622, 13088, 669, 14126, 3526, 5039, 9784,
	15338, 619, 3115, 590, 16442, 3013, 15542, 4168,
	15537, 1611, 15405, 1228, 16023, 9299, 7534, 4976,
	1990, 1213, 11447, 1157, 12512, 5519, 9475, 2644,
	7716, 2034, 13280, 2239, 16011, 5093, 8066, 6761,
	10083, 1413, 5002, 2347, 12523, 5975, 15126, 2899,
	18264, 2289, 15827, 2527, 16265, 10254, 14651, 11319,
	1797, 337, 3115, 397, 3510, 2928, 4592, 2670,
	7519, 628, 11415, 656, 5946, 2435, 6544, 7367,
	8238, 829, 4000, 863, 10032, 2492, 16057, 3551,
	18204, 1054, 6103, 1454, 5884, 7900, 18752, 3468,
	1864, 544, 9198, 683, 11623, 4160, 4594, 1644,
	3158, 1157, 15953, 2560, 12349, 3733, 17420, 5260,
	6106, 2004, 2917, 1742, 16467, 5257, 16787, 1680,
	17205, 1759, 4773, 3231, 7386, 6035, 14342, 10012,
	4035, 442, 4194, 458, 9214, 2242, 7427, 4217,
	12860, 801, 11186, 825, 12648, 2084, 12956, 6554,
	9505, 996, 6629, 985, 10537, 2502, 15289, 5006,
	12602, 2055, 15484, 1653, 16194, 6921, 14231, 5790,
	2626, 828, 5615, 1686, 13663, 5778, 3668, 1554,
	11313, 2633, 9770, 1459, 14003, 4733, 15897, 6291,
	6278, 1870, 7910, 2285, 16978, 4571, 16576, 3849,
	15248, 2311, 16023, 3244, 14459, 17808, 11847, 2763,
	1981, 1407, 1400, 876, 4335, 3547, 4391, 4210,
	5405, 680, 17461, 781, 6501, 5118, 8091, 7677,
	7355, 794, 8333, 1182, 15041, 3160, 14928, 3039,
	20421, 880, 14545, 852, 12337, 14708, 6904, 1920,
	4225, 933, 8218, 1087, 10659, 4084, 10082, 4533,
	2735, 840, 20657, 1081, 16711, 5966, 15873, 4578,
	10871, 2574, 3773, 1166, 14519, 4044, 20699, 2627,
	15219, 2734, 15274, 2186, 6257, 3226, 13125, 19480,
	7196, 930, 2462, 1618, 4515, 3092, 13852, 4277,
	10460, 833, 17339, 810, 16891, 2289, 15546, 8217,
	13603, 1684, 3197, 1834, 15948, 2820, 15812, 5327,
	17006, 2438, 16788, 1326, 15671, 8156, 11726, 8556,
	3762, 2053, 9563, 1317, 13561, 6790, 12227, 1936,
	8180, 3550, 13287, 1778, 16299, 6599, 16291, 7758,
	8521, 2551, 7225, 2645, 18269, 7489, 16885, 2248,
	17882, 2884, 17265, 3328, 9417, 20162, 11042, 8320,
	1286, 620, 1431, 583, 5993, 2289, 3978, 3626,
	5144, 752, 13409, 830, 5553, 2860, 11764, 5908,
	10737, 560, 5446, 564, 13321, 3008, 11946, 3683,
	19887, 798, 9825, 728, 13663, 8748, 7391, 3053,
	2515, 778, 6050, 833, 6469, 5074, 8305, 2463,
	6141, 1865, 15308, 1262, 14408, 4547, 13663, 4515,
	3137, 2983, 2479, 1259, 15088, 4647, 15382, 2607,
	14492, 2392, 12462, 2537, 7539, 2949, 12909, 12060,
	5468, 684, 3141, 722, 5081, 1274, 12732, 4200,
	15302, 681, 7819, 592, 6534, 2021, 16478, 8737,
	13364, 882, 5397, 899, 14656, 2178, 14741, 4227,
	14270, 1298, 13929, 2029, 15477, 7482, 15815, 4572,
	2521, 2013, 5062, 1804, 5159, 6582, 7130, 3597,
	10920, 1611, 11729, 1708, 16903, 3455, 16268, 6640,
	9306, 1007, 9369, 2106, 19182, 5037, 12441, 4269,
	15919, 1332, 15357, 3512, 11898, 14141, 16101, 6854,
	2010, 737, 3779, 861, 11454, 2880, 3564, 3540,
	9057, 1241, 12391, 896, 8546, 4629, 11561, 5776,
	8129, 589, 8218, 588, 18728, 3755, 12973, 3149,
	15729, 758, 16634, 754, 15222, 11138, 15871, 2208,
	4673, 610, 10218, 678, 15257, 4146, 5729, 3327,
	8377, 1670, 19862, 2321, 15450, 5511, 14054, 5481,
	5728, 2888, 7580, 1346, 14384, 5325, 16236, 3950,
	15118, 3744, 15306, 1435, 14597, 4070, 12301, 15696,
	7617, 1699, 2170, 884, 4459, 4567, 18094, 3306,
	12742, 815, 14926, 907, 15016, 4281, 15518, 8368,
	17994, 1087, 2358, 865, 16281, 3787, 15679, 4596,
	16356, 1534, 16584, 2210, 16833, 9697, 15929, 4513,
	3277, 1085, 9643, 2187, 11973, 6068, 9199, 4462,
	8955, 1629, 10289, 3062, 16481, 5155, 15466, 7066,
	13678, 2543, 5273, 2277, 16746, 6213, 16655, 3408,
	20304, 3363, 18688, 1985, 14172, 12867, 15154, 15703,
	4473, 1020, 1681, 886, 4311, 4301, 8952, 3657,
	5893, 1147, 11647, 1452, 15886, 2227, 4582, 6644,
	6929, 1205, 6220, 799, 12415, 3409, 15968, 3877,
	19859, 2109, 9689, 2141, 14742, 8830, 14480, 2599,
	1817, 1238, 7771, 813, 19079, 4410, 5554, 2064,
	3687, 2844, 17435, 2256, 16697, 4486, 16199, 5388,
	8028, 2763, 3405, 2119, 17426, 5477, 13698, 2786,
	19879, 2720, 9098, 3880, 18172, 4833, 17336, 12207,
	5116, 996, 4935, 988, 9888, 3081, 6014, 5371,
	15881, 1667, 8405, 1183, 15087, 2366, 19777, 7002,
	11963, 1562, 7279, 1128, 16859, 1532, 15762, 5381,
	14708, 2065, 20105, 2155, 17158, 8245, 17911, 6318,
	5467, 1504, 4100, 2574, 17421, 6810, 5673, 2888,
	16636, 3382, 8975, 1831, 20159, 4737, 19550, 7294,
	6658, 2781, 11472, 3321, 19397, 5054, 18878, 4722,
	16439, 2373, 20430, 4386, 11353, 26526, 11593, 3068,
	2866, 1566, 5108, 1070, 9614, 4915, 4939, 3536,
	7541, 878, 20717, 851, 6938, 4395, 16799, 7733,
	10137, 1019, 9845, 964, 15494, 3955, 15459, 3430,
	18863, 982, 20120, 963, 16876, 12887, 14334, 4200,
	6599, 1220, 9222, 814, 16942, 5134, 5661, 4898,
	5488, 1798, 20258, 3962, 17005, 6178, 17929, 5929,
	9365, 3420, 7474, 1971, 19537, 5177, 19003, 3006,
	16454, 3788, 16070, 2367, 8664, 2743, 9445, 26358,
	10856, 1287, 3555, 1009, 5606, 3622, 19453, 5512,
	12453, 797, 20634, 911, 15427, 3066, 17037, 10275,
	18883, 2633, 3913, 1268, 19519, 3371, 18052, 5230,
	19291, 1678, 19508, 3172, 18072, 10754, 16625, 6845,
	3134, 2298, 10869, 2437, 15580, 6913, 12597, 3381,
	11116, 3297, 16762, 2424, 18853, 6715, 17171, 9887,
	12743, 2605, 8937, 3140, 19033, 7764, 18347, 3880,
	20475, 3682, 19602, 3380, 13044, 19373, 10526, 23124};

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
