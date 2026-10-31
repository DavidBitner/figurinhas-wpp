package com.davidbitner.stickers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class AnimatedWebp {
    private AnimatedWebp() {
    }

    static byte[] assemble(List<byte[]> frames, int width, int height, int frameDurationMs) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(ascii("WEBP"));

        ByteArrayOutputStream vp8x = new ByteArrayOutputStream();
        vp8x.write(0x12);
        vp8x.write(0);
        vp8x.write(0);
        vp8x.write(0);
        writeLe24(vp8x, width - 1);
        writeLe24(vp8x, height - 1);
        writeChunk(body, "VP8X", vp8x.toByteArray());

        ByteArrayOutputStream anim = new ByteArrayOutputStream();
        writeLe32(anim, 0);
        writeLe16(anim, 0);
        writeChunk(body, "ANIM", anim.toByteArray());

        for (byte[] frame : frames) {
            ByteArrayOutputStream anmf = new ByteArrayOutputStream();
            writeLe24(anmf, 0);
            writeLe24(anmf, 0);
            writeLe24(anmf, width - 1);
            writeLe24(anmf, height - 1);
            writeLe24(anmf, frameDurationMs);
            anmf.write(0x02);
            anmf.write(extractImageChunks(frame));
            writeChunk(body, "ANMF", anmf.toByteArray());
        }

        byte[] payload = body.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream(payload.length + 8);
        out.write(ascii("RIFF"));
        writeLe32(out, payload.length);
        out.write(payload);
        return out.toByteArray();
    }

    private static byte[] extractImageChunks(byte[] webp) throws IOException {
        if (webp.length < 20 || !tagAt(webp, 0).equals("RIFF") || !tagAt(webp, 8).equals("WEBP")) {
            throw new IOException("Frame WebP inválido");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        boolean foundImage = false;
        int offset = 12;
        while (offset + 8 <= webp.length) {
            String tag = tagAt(webp, offset);
            int size = readLe32(webp, offset + 4);
            int padded = size + (size & 1);
            int end = offset + 8 + padded;
            if (size < 0 || end > webp.length) {
                break;
            }
            if (tag.equals("ALPH") || tag.equals("VP8 ") || tag.equals("VP8L")) {
                out.write(webp, offset, 8 + padded);
                if (!tag.equals("ALPH")) {
                    foundImage = true;
                }
            }
            offset = end;
        }
        if (!foundImage) {
            throw new IOException("Frame WebP sem imagem");
        }
        return out.toByteArray();
    }

    private static void writeChunk(ByteArrayOutputStream out, String tag, byte[] data) throws IOException {
        out.write(ascii(tag));
        writeLe32(out, data.length);
        out.write(data);
        if ((data.length & 1) == 1) {
            out.write(0);
        }
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    private static String tagAt(byte[] b, int offset) {
        return new String(b, offset, 4, StandardCharsets.US_ASCII);
    }

    private static int readLe32(byte[] b, int offset) {
        return (b[offset] & 0xFF) | ((b[offset + 1] & 0xFF) << 8) | ((b[offset + 2] & 0xFF) << 16) | ((b[offset + 3] & 0xFF) << 24);
    }

    private static void writeLe16(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
    }

    private static void writeLe24(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
        out.write((v >> 16) & 0xFF);
    }

    private static void writeLe32(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
        out.write((v >> 16) & 0xFF);
        out.write((v >> 24) & 0xFF);
    }
}
