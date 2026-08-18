package com.cruvex.cubecraftplus.cubesocket.protocol;

import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class PacketBuffer {

    private final ByteBuf buffer;

    public PacketBuffer(ByteBuf buffer) {
        this.buffer = buffer;
    }

    public static int getVarIntSize(int input) {
        for (int i = 1; i < 5; ++i) {
            if ((input & -1 << i * 7) == 0) {
                return i;
            }
        }

        return 5;
    }

    public static void writeVarIntToBuffer(ByteBuf buf, int input) {
        while ((input & -128) != 0) {
            buf.writeByte(input & 127 | 128);
            input >>>= 7;
        }

        buf.writeByte(input);
    }

    public static int readVarIntFromBuffer(ByteBuf buffer) {
        int value = 0;
        int reads = 0;

        byte read;
        do {
            read = buffer.readByte();
            value |= (read & 127) << reads++ * 7;
            if (reads > 5) {
                throw new RuntimeException("VarInt too big");
            }
        } while ((read & 128) == 128);

        return value;
    }

    public int readVarIntFromBuffer() {
        return readVarIntFromBuffer(this.buffer);
    }

    public void writeVarIntToBuffer(int input) {
        writeVarIntToBuffer(this.buffer, input);
    }

    public byte[] readByteArray() {
        byte[] b = new byte[this.buffer.readInt()];

        for (int i = 0; i < b.length; ++i) {
            b[i] = this.buffer.readByte();
        }

        return b;
    }

    public void writeByteArray(byte[] data) {
        this.buffer.writeInt(data.length);
        this.buffer.writeBytes(data);
    }

    public void writeByte(int value) {
        this.buffer.writeByte(value);
    }

    public byte readByte() {
        return this.buffer.readByte();
    }

    public UUID readUUID() {
        return UUID.fromString(this.readString());
    }

    public void writeUUID(UUID uuid) {
        this.writeString(uuid.toString());
    }

    public void readBytes(byte[] data) {
        this.buffer.readBytes(data);
    }

    public void writeBytes(byte[] data) {
        this.buffer.writeBytes(data);
    }

    public short readShort() {
        return this.buffer.readShort();
    }

    public boolean readBoolean() {
        return this.buffer.readBoolean();
    }

    public int readInt() {
        return this.buffer.readInt();
    }

    public long readLong() {
        return this.buffer.readLong();
    }

    public float readFloat() {
        return this.buffer.readFloat();
    }

    public double readDouble() {
        return this.buffer.readDouble();
    }

    public void writeShort(short value) {
        this.buffer.writeShort(value);
    }

    public void writeBoolean(boolean value) {
        this.buffer.writeBoolean(value);
    }

    public void writeInt(int value) {
        this.buffer.writeInt(value);
    }

    public void writeLong(long value) {
        this.buffer.writeLong(value);
    }

    public void writeFloat(float value) {
        this.buffer.writeFloat(value);
    }

    public void writeDouble(double value) {
        this.buffer.writeDouble(value);
    }

    public String readString() {
        byte[] bytes = new byte[this.buffer.readInt()];

        for (int i = 0; i < bytes.length; ++i) {
            bytes[i] = this.buffer.readByte();
        }

        return new String(bytes, StandardCharsets.UTF_8);
    }

    public void writeString(String string) {
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
        this.buffer.writeInt(bytes.length);
        this.buffer.writeBytes(bytes);
    }

}
