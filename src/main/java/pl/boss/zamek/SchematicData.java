package pl.boss.zamek;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Schemat w formacie Sponge (.schem, wersja 2 i 3 - tak zapisuje WorldEdit).
 * Klasa nie zalezy od Bukkita, wiec da sie ja testowac osobno.
 */
final class SchematicData {
    final int width, height, length;
    private final String[] palette;
    private final int[] cells;      // indeks w palecie; kolejnosc: (y * length + z) * width + x
    private final boolean[] paste;  // czy dana komorka ma byc wklejona

    private SchematicData(int width, int height, int length, String[] palette, int[] cells) {
        this.width = width;
        this.height = height;
        this.length = length;
        this.palette = palette;
        this.cells = cells;
        this.paste = computeMask();
    }

    static SchematicData load(InputStream in) throws IOException {
        Map<String, Object> root = Nbt.read(in);
        Map<String, Object> s = root.containsKey("Schematic") ? map(root.get("Schematic")) : root;

        int w = num(s.get("Width")), h = num(s.get("Height")), l = num(s.get("Length"));
        if (w <= 0 || h <= 0 || l <= 0) throw new IOException("Zly rozmiar schematu");

        Map<String, Object> paletteTag;
        byte[] data;
        if (s.get("Blocks") instanceof Map) {            // Sponge v3
            Map<String, Object> blocks = map(s.get("Blocks"));
            paletteTag = map(blocks.get("Palette"));
            data = (byte[]) blocks.get("Data");
        } else {                                         // Sponge v2
            paletteTag = map(s.get("Palette"));
            data = (byte[]) s.get("BlockData");
        }
        if (paletteTag == null || data == null) throw new IOException("Schemat nie ma palety/bloków");

        int max = -1;
        for (Object v : paletteTag.values()) max = Math.max(max, num(v));
        String[] palette = new String[max + 1];
        for (Map.Entry<String, Object> e : paletteTag.entrySet()) palette[num(e.getValue())] = e.getKey();

        int total = w * h * l;
        int[] cells = new int[total];
        int pos = 0;
        for (int i = 0; i < total; i++) {
            int value = 0, shift = 0;
            byte b;
            do {
                if (pos >= data.length) throw new IOException("Za malo danych blokow w schemacie");
                b = data[pos++];
                value |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0);
            if (value < 0 || value >= palette.length || palette[value] == null)
                throw new IOException("Zly indeks palety: " + value);
            cells[i] = value;
        }
        return new SchematicData(w, h, l, palette, cells);
    }

    int index(int x, int y, int z) { return (y * length + z) * width + x; }

    /** Indeks bloku w palecie dla danej komorki. */
    int paletteIndexAt(int x, int y, int z) { return cells[index(x, y, z)]; }

    /** Czy komorka ma byc wklejona (blok budowli albo powietrze zamkniete w srodku). */
    boolean shouldPaste(int x, int y, int z) { return paste[index(x, y, z)]; }

    String[] palette() { return palette; }

    int pasteCount() {
        int n = 0;
        for (boolean b : paste) if (b) n++;
        return n;
    }

    private static boolean isAir(String state) {
        return state.startsWith("minecraft:air") || state.startsWith("minecraft:cave_air") || state.startsWith("minecraft:void_air");
    }

    /**
     * Wkleja wszystkie bloki oraz powietrze zamkniete wewnatrz budowli (zeby wnetrze bylo puste).
     * Powietrze "na zewnatrz" (laczace sie z krawedzia schematu) jest pomijane, dzieki czemu
     * budowla nie wycina terenu wokol siebie.
     */
    private boolean[] computeMask() {
        int n = cells.length;
        boolean[] air = new boolean[n];
        for (int i = 0; i < n; i++) air[i] = isAir(palette[cells[i]]);

        boolean[] outside = new boolean[n];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int y = 0; y < height; y++) for (int z = 0; z < length; z++) for (int x = 0; x < width; x++) {
            boolean edge = x == 0 || y == 0 || z == 0 || x == width - 1 || y == height - 1 || z == length - 1;
            int i = index(x, y, z);
            if (edge && air[i] && !outside[i]) {
                outside[i] = true;
                queue.add(new int[]{x, y, z});
            }
        }
        int[][] dirs = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int[] d : dirs) {
                int nx = c[0] + d[0], ny = c[1] + d[1], nz = c[2] + d[2];
                if (nx < 0 || ny < 0 || nz < 0 || nx >= width || ny >= height || nz >= length) continue;
                int j = index(nx, ny, nz);
                if (air[j] && !outside[j]) {
                    outside[j] = true;
                    queue.add(new int[]{nx, ny, nz});
                }
            }
        }
        boolean[] result = new boolean[n];
        for (int i = 0; i < n; i++) result[i] = !air[i] || !outside[i];
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) { return (Map<String, Object>) o; }

    private static int num(Object o) {
        if (o instanceof Number n) return n.intValue();
        throw new IllegalArgumentException("Oczekiwano liczby, jest: " + o);
    }
}
