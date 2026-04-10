package org.brts.common.m2ts;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum BitrateCode {
    // all these values are partly given by IA, partly by reverse engineering real
    // Blu-ray PMT data.
    // They seem to be consistent across multiple discs but I have no official
    // source for that mapping, so they may be wrong.
    AC3_192kbps(0x29, 192, 2),
    AC3_320kbps(0x34, 320, 2),
    AC3_320kbps_alias(0x35, 320, 2),// not sure about this one
    AC3_448kbps(0x3C, 448, 6),
    AC3_384kbps(0x18, 384, 2),
    EAC3_896kbps(0x40, 896, 8),
    AC3_640kbps(0x48, 640, 6),
    AC3_640kbps_alias(0x46, 640, 6),// not sure about this one
    EAC3_1024kbps(0x50, 1024, 8),
    TRUE_HD(0x48, -1, 8);// sometimes it's just 6 channels. bitrate is not constant

    private @Getter final int code;
    private @Getter final int bitrateKbps;
    private @Getter final int channels;

    public static BitrateCode fromCode(int code) {
        for (BitrateCode bc : values()) {
            if (bc.code == code) {
                return bc;
            }
        }
        return null; // unknown bitrate code
    }
    public static BitrateCode fromBitrate(int bitrateKbps) {
        for (BitrateCode bc : values()) {
            if (bc.bitrateKbps == bitrateKbps) {
                return bc;
            }
        }
        return null; // unknown bitrate
    }
}
