package pl.boss.zamek;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Minimalny czytnik NBT (wystarczy do plikow .schem). */
final class Nbt {
    private Nbt() {}

    /** Wczytuje glowny compound. Obsluguje pliki spakowane gzip oraz surowe. */
    static Map<String, Object> read(InputStream in) throws IOException {
        byte[] raw = in.readAllBytes();
        InputStream src = new ByteArrayInputStream(raw);
        if (raw.length > 2 && (raw[0] & 0xFF) == 0x1F && (raw[1] & 0xFF) == 0x8B) {
            src = new GZIPInputStream(src);
        }
        try (DataInputStream in2 = new DataInputStream(new BufferedInputStream(src))) {
            byte type = in2.readByte();
            if (type != 10) throw new IOException("Plik NBT musi zaczynac sie od compounda (typ " + type + ")");
            in2.readUTF(); // nazwa roota
            return readCompound(in2);
        }
    }

    private static Map<String, Object> readCompound(DataInputStream in) throws IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        while (true) {
            byte type = in.readByte();
            if (type == 0) return map;
            String name = in.readUTF();
            map.put(name, readPayload(in, type));
        }
    }

    private static Object readPayload(DataInputStream in, byte type) throws IOException {
        switch (type) {
            case 1: return in.readByte();
            case 2: return in.readShort();
            case 3: return in.readInt();
            case 4: return in.readLong();
            case 5: return in.readFloat();
            case 6: return in.readDouble();
            case 7: {
                byte[] arr = new byte[in.readInt()];
                in.readFully(arr);
                return arr;
            }
            case 8: return in.readUTF();
            case 9: {
                byte itemType = in.readByte();
                int len = in.readInt();
                List<Object> list = new ArrayList<>(Math.max(0, len));
                for (int i = 0; i < len; i++) list.add(readPayload(in, itemType));
                return list;
            }
            case 10: return readCompound(in);
            case 11: {
                int[] arr = new int[in.readInt()];
                for (int i = 0; i < arr.length; i++) arr[i] = in.readInt();
                return arr;
            }
            case 12: {
                long[] arr = new long[in.readInt()];
                for (int i = 0; i < arr.length; i++) arr[i] = in.readLong();
                return arr;
            }
            default: throw new IOException("Nieznany typ tagu NBT: " + type);
        }
    }
}
