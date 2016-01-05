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

class Atom {  // note: latest versions of spec simply call it 'box' instead of 'atom'.
    private int mSize;  // includes atom header (8 bytes)
    private int mType;
    private byte[] mData;  // an atom can either contain data or children, but not both.
    private Atom[] mChildren;
    private byte mVersion;  // if negative, then the atom does not contain version and flags data.
    private int mFlags;

    // create an empty atom of the given type.
    public Atom(String type) {
        mSize = 8;
        mType = getTypeInt(type);
        mData = null;
        mChildren = null;
        mVersion = -1;
        mFlags = 0;
    }

    // create an empty atom of type type, with a given version and flags.
    public Atom(String type, byte version, int flags) {
        mSize = 12;
        mType = getTypeInt(type);
        mData = null;
        mChildren = null;
        mVersion = version;
        mFlags = flags;
    }

    // set the size field of the atom based on its content.
    private void setSize() {
        int size = 8;  // type + size
        if (mVersion >= 0) {
            size += 4; // version + flags
        }
        if (mData != null) {
            size += mData.length;
        } else if (mChildren != null) {
            for (Atom child : mChildren) {
                size += child.getSize();
            }
        }
        mSize = size;
    }

    // get the size of the this atom.
    public int getSize() {
        return mSize;
    }

    private int getTypeInt(String type_str) {
        int type = 0;
        type |= (byte)(type_str.charAt(0)) << 24;
        type |= (byte)(type_str.charAt(1)) << 16;
        type |= (byte)(type_str.charAt(2)) << 8;
        type |= (byte)(type_str.charAt(3));
        return type;
    }

    public int getTypeInt() {
        return mType;
    }

    public String getTypeStr() {
        String type = "";
        type += (char)((byte)((mType >> 24) & 0xFF));
        type += (char)((byte)((mType >> 16) & 0xFF));
        type += (char)((byte)((mType >> 8) & 0xFF));
        type += (char)((byte)(mType & 0xFF));
        return type;
    }

    public boolean setData(byte[] data) {
        if (mChildren != null || data == null) {
            // TODO(nfaralli): log something here
            return false;
        }
        mData = data;
        setSize();
        return true;
    }

    public byte[] getData() {
        return mData;
    }

    public boolean addChild(Atom child) {
        if (mData != null || child == null) {
            // TODO(nfaralli): log something here
            return false;
        }
        int numChildren = 1;
        if (mChildren != null) {
            numChildren += mChildren.length;
        }
        Atom[] children = new Atom[numChildren];
        if (mChildren != null) {
            System.arraycopy(mChildren, 0, children, 0, mChildren.length);
        }
        children[numChildren - 1] = child;
        mChildren = children;
        setSize();
        return true;
    }

    // return the child atom of the corresponding type.
    // type can contain grand children: e.g. type = "trak.mdia.minf"
    // return null if the atom does not contain such a child.
    public Atom getChild(String type) {
        if (mChildren == null) {
            return null;
        }
        String[] types = type.split("\\.", 2);
        for (Atom child : mChildren) {
            if (child.getTypeStr().equals(types[0])) {
                if (types.length == 1) {
                    return child;
                } else {
                    return child.getChild(types[1]);
                }
            }
        }
        return null;
    }

    // return a byte array containing the full content of the atom (including header)
    public byte[] getBytes() {
        byte[] atom_bytes = new byte[mSize];
        int offset = 0;

        atom_bytes[offset++] = (byte)((mSize >> 24) & 0xFF);
        atom_bytes[offset++] = (byte)((mSize >> 16) & 0xFF);
        atom_bytes[offset++] = (byte)((mSize >> 8) & 0xFF);
        atom_bytes[offset++] = (byte)(mSize & 0xFF);
        atom_bytes[offset++] = (byte)((mType >> 24) & 0xFF);
        atom_bytes[offset++] = (byte)((mType >> 16) & 0xFF);
        atom_bytes[offset++] = (byte)((mType >> 8) & 0xFF);
        atom_bytes[offset++] = (byte)(mType & 0xFF);
        if (mVersion >= 0) {
            atom_bytes[offset++] = mVersion;
            atom_bytes[offset++] = (byte)((mFlags >> 16) & 0xFF);
            atom_bytes[offset++] = (byte)((mFlags >> 8) & 0xFF);
            atom_bytes[offset++] = (byte)(mFlags & 0xFF);
        }
        if (mData != null) {
            System.arraycopy(mData, 0, atom_bytes, offset, mData.length);
        } else if (mChildren != null) {
            byte[] child_bytes;
            for (Atom child : mChildren) {
                child_bytes = child.getBytes();
                System.arraycopy(child_bytes, 0, atom_bytes, offset, child_bytes.length);
                offset += child_bytes.length;
            }
        }
        return atom_bytes;
    }

    // Used for debugging purpose only.
    public String toString() {
        String str = "";
        byte[] atom_bytes = getBytes();

        for (int i = 0; i < atom_bytes.length; i++) {
            if(i % 8 == 0 && i > 0) {
                str += '\n';
            }
            str += String.format("0x%02X", atom_bytes[i]);
            if (i < atom_bytes.length - 1) {
                str += ',';
                if (i % 8 < 7) {
                    str += ' ';
                }
            }
        }
        str += '\n';
        return str;
    }
}

public class MP4Header {
    private int[] mFrameSize;    // size of each AAC frames, in bytes. First one should be 2.
    private int mMaxFrameSize;   // size of the biggest frame.
    private int mTotSize;        // size of the AAC stream.
    private int mBitrate;        // bitrate used to encode the AAC stream.
    private byte[] mTime;        // time used for 'creation time' and 'modification time' fields.
    private byte[] mDurationMS;  // duration of stream in milliseconds.
    private byte[] mNumSamples;  // number of samples in the stream.
    private byte[] mHeader;      // the complete header.
    private int mSampleRate;     // sampling frequency in Hz (e.g. 44100).
    private int mChannels;       // number of channels.

    // Creates a new MP4Header object that should be used to generate an .m4a file header.
    public MP4Header(int sampleRate, int numChannels, int[] frame_size, int bitrate) {
        if (frame_size == null || frame_size.length < 2 || frame_size[0] != 2) {
            //TODO(nfaralli): log something here
            return;
        }
        mSampleRate = sampleRate;
        mChannels = numChannels;
        mFrameSize = frame_size;
        mBitrate = bitrate;
        mMaxFrameSize = mFrameSize[0];
        mTotSize = mFrameSize[0];
        for (int i=1; i<mFrameSize.length; i++) {
            if (mMaxFrameSize < mFrameSize[i]) {
                mMaxFrameSize = mFrameSize[i];
            }
            mTotSize += mFrameSize[i];
        }
        long time = System.currentTimeMillis() / 1000;
        time += (66 * 365 + 16) * 24 * 60 * 60;  // number of seconds between 1904 and 1970
        mTime = new byte[4];
        mTime[0] = (byte)((time >> 24) & 0xFF);
        mTime[1] = (byte)((time >> 16) & 0xFF);
        mTime[2] = (byte)((time >> 8) & 0xFF);
        mTime[3] = (byte)(time & 0xFF);
        int numSamples = 1024 * (frame_size.length - 1);  // 1st frame does not contain samples.
        int durationMS = (numSamples * 1000) / mSampleRate;
        if ((numSamples * 1000) % mSampleRate > 0) {  // round the duration up.
            durationMS++;
        }
        mNumSamples= new byte[] {
                (byte)((numSamples >> 26) & 0XFF),
                (byte)((numSamples >> 16) & 0XFF),
                (byte)((numSamples >> 8) & 0XFF),
                (byte)(numSamples & 0XFF)
        };
        mDurationMS = new byte[] {
                (byte)((durationMS >> 26) & 0XFF),
                (byte)((durationMS >> 16) & 0XFF),
                (byte)((durationMS >> 8) & 0XFF),
                (byte)(durationMS & 0XFF)
        };
        setHeader();
    }

    public byte[] getMP4Header() {
        return mHeader;
    }

    public static byte[] getMP4Header(
            int sampleRate, int numChannels, int[] frame_size, int bitrate) {
        return new MP4Header(sampleRate, numChannels, frame_size, bitrate).mHeader;
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
        // create the atoms needed to build the header.
        Atom a_ftyp = getFTYPAtom();
        Atom a_moov = getMOOVAtom();
        Atom a_mdat = new Atom("mdat");  // create an empty atom. The AAC stream data should follow
                                         // immediately after. The correct size will be set later.

        // set the correct chunk offset in the stco atom.
        Atom a_stco = a_moov.getChild("trak.mdia.minf.stbl.stco");
        if (a_stco == null) {
            mHeader = null;
            return;
        }
        byte[] data = a_stco.getData();
        int chunk_offset = a_ftyp.getSize() + a_moov.getSize() + a_mdat.getSize();
        int offset = data.length - 4;  // here stco should contain only one chunk offset.
        data[offset++] = (byte)((chunk_offset >> 24) & 0xFF);
        data[offset++] = (byte)((chunk_offset >> 16) & 0xFF);
        data[offset++] = (byte)((chunk_offset >> 8) & 0xFF);
        data[offset++] = (byte)(chunk_offset & 0xFF);

        // create the header byte array based on the previous atoms.
        byte[] header = new byte[chunk_offset];  // here chunk_offset is also the size of the header
        offset = 0;
        for (Atom atom : new Atom[] {a_ftyp, a_moov, a_mdat}) {
            byte[] atom_bytes = atom.getBytes();
            System.arraycopy(atom_bytes, 0, header, offset, atom_bytes.length);
            offset += atom_bytes.length;
        }

        //set the correct size of the mdat atom
        int size = 8 + mTotSize;
        offset -= 8;
        header[offset++] = (byte)((size >> 24) & 0xFF);
        header[offset++] = (byte)((size >> 16) & 0xFF);
        header[offset++] = (byte)((size >> 8) & 0xFF);
        header[offset++] = (byte)(size & 0xFF);

        mHeader = header;
    }

    private Atom getFTYPAtom() {
        Atom atom = new Atom("ftyp");
        atom.setData(new byte[] {
                'M', '4', 'A', ' ',  // Major brand
                0, 0, 0, 0,          // Minor version
                'M', '4', 'A', ' ',  // compatible brands
                'm', 'p', '4', '2',
                'i', 's', 'o', 'm'
        });
        return atom;
    }

    private Atom getMOOVAtom() {
        Atom atom = new Atom("moov");
        atom.addChild(getMVHDAtom());
        atom.addChild(getTRAKAtom());
        return atom;
    }

    private Atom getMVHDAtom() {
        Atom atom = new Atom("mvhd", (byte)0, 0);
        atom.setData(new byte[] {
                mTime[0], mTime[1], mTime[2], mTime[3],  // creation time.
                mTime[0], mTime[1], mTime[2], mTime[3],  // modification time.
                0, 0, 0x03, (byte)0xE8,  // timescale = 1000 => duration expressed in ms.
                mDurationMS[0], mDurationMS[1], mDurationMS[2], mDurationMS[3],  // duration in ms.
                0, 1, 0, 0,  // rate = 1.0
                1, 0,        // volume = 1.0
                0, 0,        // reserved
                0, 0, 0, 0,  // reserved
                0, 0, 0, 0,  // reserved
                0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,  // unity matrix
                0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0x40, 0, 0, 0,
                0, 0, 0, 0,  // pre-defined
                0, 0, 0, 0,  // pre-defined
                0, 0, 0, 0,  // pre-defined
                0, 0, 0, 0,  // pre-defined
                0, 0, 0, 0,  // pre-defined
                0, 0, 0, 0,  // pre-defined
                0, 0, 0, 2   // next track ID
        });
        return atom;
    }

    private Atom getTRAKAtom() {
        Atom atom = new Atom("trak");
        atom.addChild(getTKHDAtom());
        atom.addChild(getMDIAAtom());
        return atom;
    }

    private Atom getTKHDAtom() {
        Atom atom = new Atom("tkhd", (byte)0, 0x07);  // track enabled, in movie, and in preview.
        atom.setData(new byte[] {
                mTime[0], mTime[1], mTime[2], mTime[3],  // creation time.
                mTime[0], mTime[1], mTime[2], mTime[3],  // modification time.
                0, 0, 0, 1,  // track ID
                0, 0, 0, 0,  // reserved
                mDurationMS[0], mDurationMS[1], mDurationMS[2], mDurationMS[3],  // duration in ms.
                0, 0, 0, 0,  // reserved
                0, 0, 0, 0,  // reserved
                0, 0,        // layer
                0, 0,        // alternate group
                1, 0,        // volume = 1.0
                0, 0,        // reserved
                0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,  // unity matrix
                0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0x40, 0, 0, 0,
                0, 0, 0, 0,  // width
                0, 0, 0, 0   // height
        });
        return atom;
    }

    private Atom getMDIAAtom() {
        Atom atom = new Atom("mdia");
        atom.addChild(getMDHDAtom());
        atom.addChild(getHDLRAtom());
        atom.addChild(getMINFAtom());
        return atom;
    }

    private Atom getMDHDAtom() {
        Atom atom = new Atom("mdhd", (byte)0, 0);
        atom.setData(new byte[] {
                mTime[0], mTime[1], mTime[2], mTime[3],  // creation time.
                mTime[0], mTime[1], mTime[2], mTime[3],  // modification time.
                (byte)(mSampleRate >> 24), (byte)(mSampleRate >> 16),  // timescale = Fs =>
                (byte)(mSampleRate >> 8), (byte)(mSampleRate),  // duration expressed in samples.
                mNumSamples[0], mNumSamples[1], mNumSamples[2], mNumSamples[3],  // duration
                0, 0,     // languages
                0, 0      // pre-defined
        });
        return atom;
    }

    private Atom getHDLRAtom() {
        Atom atom = new Atom("hdlr", (byte)0, 0);
        atom.setData(new byte[] {
                0, 0, 0, 0,  // pre-defined
                's', 'o', 'u', 'n',  // handler type
                0, 0, 0, 0,  // reserved
                0, 0, 0, 0,  // reserved
                0, 0, 0, 0,  // reserved
                'S', 'o', 'u', 'n',  // name (used only for debugging and inspection purposes).
                'd', 'H', 'a', 'n',
                'd', 'l', 'e', '\0'
        });
        return atom;
    }

    private Atom getMINFAtom() {
        Atom atom = new Atom("minf");
        atom.addChild(getSMHDAtom());
        atom.addChild(getDINFAtom());
        atom.addChild(getSTBLAtom());
        return atom;
    }

    private Atom getSMHDAtom() {
        Atom atom = new Atom("smhd", (byte)0, 0);
        atom.setData(new byte[] {
                0, 0,     // balance (center)
                0, 0      // reserved
        });
        return atom;
    }

    private Atom getDINFAtom() {
        Atom atom = new Atom("dinf");
        atom.addChild(getDREFAtom());
        return atom;
    }

    private Atom getDREFAtom() {
        Atom atom = new Atom("dref", (byte)0, 0);
        byte[] url = getURLAtom().getBytes();
        byte[] data = new byte[4 + url.length];
        data[3] = 0x01;  // entry count = 1
        System.arraycopy(url, 0, data, 4, url.length);
        atom.setData(data);
        return atom;
    }

    private Atom getURLAtom() {
        Atom atom = new Atom("url ", (byte)0, 0x01);  // flags = 0x01: data is self contained.
        return atom;
    }

    private Atom getSTBLAtom() {
        Atom atom = new Atom("stbl");
        atom.addChild(getSTSDAtom());
        atom.addChild(getSTTSAtom());
        atom.addChild(getSTSCAtom());
        atom.addChild(getSTSZAtom());
        atom.addChild(getSTCOAtom());
        return atom;
    }

    private Atom getSTSDAtom() {
        Atom atom = new Atom("stsd", (byte)0, 0);
        byte[] mp4a = getMP4AAtom().getBytes();
        byte[] data = new byte[4 + mp4a.length];
        data[3] = 0x01;  // entry count = 1
        System.arraycopy(mp4a, 0, data, 4, mp4a.length);
        atom.setData(data);
        return atom;
    }

    // See also Part 14 section 5.6.1 of ISO/IEC 14496 for this atom.
    private Atom getMP4AAtom() {
        Atom atom = new Atom("mp4a");
        byte[] ase = new byte[] {  // Audio Sample Entry data
                0, 0, 0, 0, 0, 0,  // reserved
                0, 1,  // data reference index
                0, 0, 0, 0,  // reserved
                0, 0, 0, 0,  // reserved
                (byte)(mChannels >> 8), (byte)mChannels,  // channel count
                0, 0x10, // sample size
                0, 0,  // pre-defined
                0, 0,  // reserved
                (byte)(mSampleRate >> 8), (byte)(mSampleRate), 0, 0,  // sample rate
        };
        byte[] esds = getESDSAtom().getBytes();
        byte[] data = new byte[ase.length + esds.length];
        System.arraycopy(ase, 0, data, 0, ase.length);
        System.arraycopy(esds, 0, data, ase.length, esds.length);
        atom.setData(data);
        return atom;
    }

    private Atom getESDSAtom() {
        Atom atom = new Atom("esds", (byte)0, 0);
        atom.setData(getESDescriptor());
        return atom;
    }

    // Returns an ES Descriptor for an ISO/IEC 14496-3 audio stream, AAC LC, 44100Hz, 2 channels,
    // 1024 samples per frame per channel. The decoder buffer size is set so that it can contain at
    // least 2 frames. (See section 7.2.6.5 of ISO/IEC 14496-1 for more details).
    private byte[] getESDescriptor() {
        int[] samplingFrequencies = new int[] {96000, 88200, 64000, 48000, 44100, 32000, 24000,
                22050, 16000, 12000, 11025, 8000, 7350};
        // First 5 bytes of the ES Descriptor.
        byte[] ESDescriptor_top = new byte[] {0x03, 0x19, 0x00, 0x00, 0x00};
        // First 4 bytes of Decoder Configuration Descriptor. Audio ISO/IEC 14496-3, AudioStream.
        byte[] decConfigDescr_top = new byte[] {0x04, 0x11, 0x40, 0x15};
        // Audio Specific Configuration: AAC LC, 1024 samples/frame/channel.
        // Sampling frequency and channels configuration are not set yet.
        byte[] audioSpecificConfig = new byte[] {0x05, 0x02, 0x10, 0x00};
        byte[] slConfigDescr = new byte[] {0x06, 0x01, 0x02};  // specific for MP4 file.
        int offset;
        int bufferSize = 0x300;
        while (bufferSize < 2 * mMaxFrameSize) {
            // TODO(nfaralli): what should be the minimum size of the decoder buffer?
            // Should it be a multiple of 256?
            bufferSize += 0x100;
        }

        // create the Decoder Configuration Descriptor
        byte[] decConfigDescr = new byte[2 + decConfigDescr_top[1]];
        System.arraycopy(decConfigDescr_top, 0, decConfigDescr, 0, decConfigDescr_top.length);
        offset = decConfigDescr_top.length;
        decConfigDescr[offset++] = (byte)((bufferSize >> 16) & 0xFF);
        decConfigDescr[offset++] = (byte)((bufferSize >> 8) & 0xFF);
        decConfigDescr[offset++] = (byte)(bufferSize & 0xFF);
        decConfigDescr[offset++] = (byte)((mBitrate >> 24) & 0xFF);
        decConfigDescr[offset++] = (byte)((mBitrate >> 16) & 0xFF);
        decConfigDescr[offset++] = (byte)((mBitrate >> 8) & 0xFF);
        decConfigDescr[offset++] = (byte)(mBitrate & 0xFF);
        decConfigDescr[offset++] = (byte)((mBitrate >> 24) & 0xFF);
        decConfigDescr[offset++] = (byte)((mBitrate >> 16) & 0xFF);
        decConfigDescr[offset++] = (byte)((mBitrate >> 8) & 0xFF);
        decConfigDescr[offset++] = (byte)(mBitrate & 0xFF);
        int index;
        for (index=0; index<samplingFrequencies.length; index++) {
            if (samplingFrequencies[index] == mSampleRate) {
                break;
            }
        }
        if (index == samplingFrequencies.length) {
            // TODO(nfaralli): log something here.
            // Invalid sampling frequency. Default to 44100Hz...
            index = 4;
        }
        audioSpecificConfig[2] |= (byte)((index >> 1) & 0x07);
        audioSpecificConfig[3] |= (byte)(((index & 1) << 7) | ((mChannels & 0x0F) << 3));
        System.arraycopy(
                audioSpecificConfig, 0, decConfigDescr, offset, audioSpecificConfig.length);

        // create the ES Descriptor
        byte[] ESDescriptor = new byte[2 + ESDescriptor_top[1]];
        System.arraycopy(ESDescriptor_top, 0, ESDescriptor, 0, ESDescriptor_top.length);
        offset = ESDescriptor_top.length;
        System.arraycopy(decConfigDescr, 0, ESDescriptor, offset, decConfigDescr.length);
        offset += decConfigDescr.length;
        System.arraycopy(slConfigDescr, 0, ESDescriptor, offset, slConfigDescr.length);
        return ESDescriptor;
    }

    private Atom getSTTSAtom() {
        Atom atom = new Atom("stts", (byte)0, 0);
        int numAudioFrames = mFrameSize.length - 1;
        atom.setData(new byte[] {
                0, 0, 0, 0x02,  // entry count
                0, 0, 0, 0x01,  // first frame contains no audio
                0, 0, 0, 0,
                (byte)((numAudioFrames >> 24) & 0xFF), (byte)((numAudioFrames >> 16) & 0xFF),
                (byte)((numAudioFrames >> 8) & 0xFF), (byte)(numAudioFrames & 0xFF),
                0, 0, 0x04, 0,  // delay between frames = 1024 samples (cf. timescale = Fs)
        });
        return atom;
    }

    private Atom getSTSCAtom() {
        Atom atom = new Atom("stsc", (byte)0, 0);
        int numFrames = mFrameSize.length;
        atom.setData(new byte[] {
                0, 0, 0, 0x01,  // entry count
                0, 0, 0, 0x01,  // first chunk
                (byte)((numFrames >> 24) & 0xFF), (byte)((numFrames >> 16) & 0xFF),  // samples per
                (byte)((numFrames >> 8) & 0xFF), (byte)(numFrames & 0xFF),           // chunk
                0, 0, 0, 0x01,  // sample description index
        });
        return atom;
    }

    private Atom getSTSZAtom() {
        Atom atom = new Atom("stsz", (byte)0, 0);
        int numFrames = mFrameSize.length;
        byte[] data = new byte[8 + 4 * numFrames];
        int offset = 0;
        data[offset++] = 0;  // sample size (=0 => each frame can have a different size)
        data[offset++] = 0;
        data[offset++] = 0;
        data[offset++] = 0;
        data[offset++] = (byte)((numFrames >> 24) & 0xFF);  // sample count
        data[offset++] = (byte)((numFrames >> 16) & 0xFF);
        data[offset++] = (byte)((numFrames >> 8) & 0xFF);
        data[offset++] = (byte)(numFrames & 0xFF);
        for (int size : mFrameSize) {
            data[offset++] = (byte)((size >> 24) & 0xFF);
            data[offset++] = (byte)((size >> 16) & 0xFF);
            data[offset++] = (byte)((size >> 8) & 0xFF);
            data[offset++] = (byte)(size & 0xFF);
        }
        atom.setData(data);
        return atom;
    }

    private Atom getSTCOAtom() {
        Atom atom = new Atom("stco", (byte)0, 0);
        atom.setData(new byte[] {
                0, 0, 0, 0x01,   // entry count
                0, 0, 0, 0  // chunk offset. Set to 0 here. Must be set later. Here it should be
                            // the size of the complete header, as the AAC stream will follow
                            // immediately.
        });
        return atom;
    }
}
