package com.nfsmw.androidevolved;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.StatFs;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Installs the owner's game files embedded in this personal APK into Internal storage/nfsmw. */
final class BundledGameAssets {
    interface Progress {
        void update(long copiedBytes, long totalBytes);
    }

    private static final String ASSET_ROOT = "nfsmw/";
    private static final String MANIFEST = ASSET_ROOT + "__offline_install_manifest.txt";
    private static final String COMPLETE_MARKER = ".offline_bundle_complete";
    private static final int BUFFER_SIZE = 1024 * 1024;
    private static final long PROGRESS_STEP = 8L * 1024L * 1024L;

    private static final class Entry {
        final long size;
        final String path;
        Entry(long size, String path) { this.size = size; this.path = path; }
    }

    private BundledGameAssets() {}

    static boolean hasBundle(Context context) {
        try (InputStream ignored = context.getAssets().open(MANIFEST)) {
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    static long totalBytes(Context context) throws IOException {
        long total = 0;
        for (Entry entry : readManifest(context.getAssets())) {
            if (Long.MAX_VALUE - total < entry.size) throw new IOException("Game data size overflow");
            total += entry.size;
        }
        return total;
    }

    static boolean isInstalled(Context context, File destination) {
        File marker = new File(destination, COMPLETE_MARKER);
        if (!marker.isFile()) return false;
        try {
            for (Entry entry : readManifest(context.getAssets())) {
                File target = safeTarget(destination, entry.path);
                if (!target.isFile() || target.length() != entry.size) return false;
            }
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    static void install(Context context, File destination, Progress progress) throws IOException {
        AssetManager assets = context.getAssets();
        List<Entry> entries = readManifest(assets);
        long total = 0;
        for (Entry entry : entries) {
            if (Long.MAX_VALUE - total < entry.size) throw new IOException("Game data size overflow");
            total += entry.size;
        }
        if (entries.isEmpty() || total == 0) throw new IOException("The bundled game-data manifest is empty");

        File parent = destination.getParentFile();
        if (parent == null) throw new IOException("Cannot locate phone storage");
        StatFs storage = new StatFs(parent.getAbsolutePath());
        long available = storage.getAvailableBytes();
        if (available < total + 128L * 1024L * 1024L) {
            throw new IOException("Not enough free storage. Need about " +
                    String.format(java.util.Locale.ROOT, "%.2f GB", total / 1073741824.0) +
                    " plus temporary space.");
        }
        if (!destination.isDirectory() && !destination.mkdirs())
            throw new IOException("Cannot create Internal storage/nfsmw");

        File marker = new File(destination, COMPLETE_MARKER);
        if (marker.exists() && !marker.delete()) throw new IOException("Cannot refresh game-data marker");

        long completed = 0;
        if (progress != null) progress.update(0, total);
        for (Entry entry : entries) {
            File target = safeTarget(destination, entry.path);
            if (!target.getParentFile().isDirectory() && !target.getParentFile().mkdirs())
                throw new IOException("Cannot create folder for " + entry.path);

            if (target.isFile() && target.length() == entry.size) {
                completed += entry.size;
                if (progress != null) progress.update(completed, total);
                continue;
            }

            File temporary = new File(target.getPath() + ".installing");
            long written = 0;
            long nextProgress = PROGRESS_STEP;
            try (InputStream input = new BufferedInputStream(
                         assets.open(ASSET_ROOT + entry.path, AssetManager.ACCESS_STREAMING), BUFFER_SIZE);
                 BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(temporary), BUFFER_SIZE)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    written += count;
                    if (written > entry.size) throw new IOException("Unexpected size for " + entry.path);
                    if (progress != null && written >= nextProgress) {
                        progress.update(completed + written, total);
                        nextProgress = written + PROGRESS_STEP;
                    }
                }
            } catch (IOException exception) {
                temporary.delete();
                throw new IOException("Could not extract " + entry.path + ": " + exception.getMessage(), exception);
            }
            if (written != entry.size) {
                temporary.delete();
                throw new IOException("Incomplete extraction: " + entry.path);
            }
            if (target.exists() && !target.delete()) {
                temporary.delete();
                throw new IOException("Cannot replace " + entry.path);
            }
            if (!temporary.renameTo(target)) {
                temporary.delete();
                throw new IOException("Cannot finish extracting " + entry.path);
            }
            completed += entry.size;
            if (progress != null) progress.update(completed, total);
        }

        try (FileOutputStream output = new FileOutputStream(marker)) {
            output.write(("installed=" + total + "\n").getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
    }

    private static List<Entry> readManifest(AssetManager assets) throws IOException {
        ArrayList<Entry> entries = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(assets.open(MANIFEST), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                int tab = line.indexOf('\t');
                if (tab <= 0 || tab == line.length() - 1) throw new IOException("Invalid bundled-data manifest");
                long size;
                try { size = Long.parseLong(line.substring(0, tab)); }
                catch (NumberFormatException exception) { throw new IOException("Invalid file size in manifest", exception); }
                String relative = line.substring(tab + 1).replace('\\', '/');
                if (size < 0 || relative.startsWith("/") || relative.contains("../") ||
                        relative.equals("..") || relative.contains("/..")) {
                    throw new IOException("Unsafe path in bundled-data manifest");
                }
                entries.add(new Entry(size, relative));
            }
        }
        return entries;
    }

    private static File safeTarget(File root, String relative) throws IOException {
        File base = root.getCanonicalFile();
        File target = new File(base, relative).getCanonicalFile();
        if (!target.getPath().startsWith(base.getPath() + File.separator))
            throw new IOException("Unsafe game-data path: " + relative);
        return target;
    }
}
