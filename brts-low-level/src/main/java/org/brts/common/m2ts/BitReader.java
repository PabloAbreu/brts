package org.brts.common.m2ts;

/**
 * MSB-first bit reader for H.264 RBSP bytes (emulation-prevention bytes
 * already removed by the caller).
 */
public final class BitReader {
    private final byte[] buf;
    private int pos; // absolute bit position (MSB = 0 within each byte)

    BitReader(byte[] rbsp) { this.buf = rbsp; }

    boolean canRead() { return (pos >> 3) < buf.length; }

    int readBit() {
        int byteIdx = pos >> 3;
        int shift   = 7 - (pos & 7);
        pos++;
        return byteIdx < buf.length ? (buf[byteIdx] >> shift) & 1 : 0;
    }

    int readBits(int n) {
        int v = 0;
        for (int i = 0; i < n; i++) v = (v << 1) | readBit();
        return v;
    }

    /** Unsigned Exp-Golomb code (H.264 spec §9.1). */
    int readUE() {
        int zeros = 0;
        while (canRead() && readBit() == 0) zeros++;
        return zeros == 0 ? 0 : (1 << zeros) - 1 + readBits(zeros);
    }

    /** Signed Exp-Golomb code mapped from unsigned. */
    int readSE() {
        int ue = readUE();
        return (ue & 1) != 0 ? (ue + 1) >> 1 : -(ue >> 1);
    }
}