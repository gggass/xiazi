package com.app.dockeep.data.files;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Same-vault rename only. Callers share VaultFiles' monitor with import, trash and backup. */
public final class VaultMover {
    private final File root;
    private final File configuredRoot;
    private final Object lock;
    private final Renamer renamer;

    interface Renamer { boolean rename(File source, File destination); }

    public VaultMover(File root, Object lock) throws IOException {
        this(root, lock, File::renameTo);
    }

    VaultMover(File root, Object lock, Renamer renamer) throws IOException {
        this.configuredRoot = root.getAbsoluteFile();
        this.root = root.getCanonicalFile();
        this.lock = Objects.requireNonNull(lock);
        this.renamer = Objects.requireNonNull(renamer);
        if (!this.root.isDirectory()) throw new IllegalArgumentException("匣子目录不可用");
    }

    public static final class Move {
        public final File source;
        public final File destination;
        public final boolean renamed;
        private Move(File source, File destination) {
            this.source = source;
            this.destination = destination;
            this.renamed = !source.getName().equals(destination.getName());
        }
    }

    public static final class Failure {
        public final File source;
        public final String reason;
        private Failure(File source, String reason) { this.source = source; this.reason = reason; }
    }

    public static final class Result {
        public final List<Move> moved;
        public final List<File> skipped;
        public final List<Failure> failures;
        /** Duplicate selections and children already included in a selected parent. */
        public final int includedSelections;
        private Result(List<Move> moved, List<File> skipped, List<Failure> failures, int included) {
            this.moved = Collections.unmodifiableList(new ArrayList<>(moved));
            this.skipped = Collections.unmodifiableList(new ArrayList<>(skipped));
            this.failures = Collections.unmodifiableList(new ArrayList<>(failures));
            this.includedSelections = included;
        }
    }

    /** Invalid batches fail preflight before any mutation; OS failures are reported per item. */
    public Result move(List<File> selections, File destination) throws IOException {
        synchronized (lock) {
            File targetDirectory = checked(destination, true);
            if (!targetDirectory.isDirectory()) throw new IllegalArgumentException("目标文件夹不存在，请重新选择");
            Map<String, File> unique = new LinkedHashMap<>();
            for (File selection : selections) {
                File source = checked(selection, false);
                if (!source.exists()) throw new IllegalArgumentException("文件已不存在：" + source.getName());
                if (!source.isFile() && !source.isDirectory()) throw new IllegalArgumentException("不支持移动此类型文件");
                validName(source.getName());
                unique.put(source.getPath(), source);
            }
            List<File> selectedDirectories = new ArrayList<>();
            for (File source : unique.values()) if (source.isDirectory()) selectedDirectories.add(source);
            List<File> topLevel = new ArrayList<>();
            for (File source : unique.values()) {
                boolean included = false;
                for (File parent : selectedDirectories) {
                    if (!parent.equals(source) && inside(parent, source)) { included = true; break; }
                }
                if (!included) topLevel.add(source);
            }
            for (File source : topLevel) {
                if (source.equals(targetDirectory) || inside(source, targetDirectory))
                    throw new IllegalArgumentException("不能把文件夹移进自身或其子目录");
            }
            List<Move> moved = new ArrayList<>();
            List<File> skipped = new ArrayList<>();
            List<Failure> failures = new ArrayList<>();
            for (File source : topLevel) {
                if (source.getParentFile().equals(targetDirectory)) { skipped.add(source); continue; }
                try {
                    // Revalidate under the shared monitor, then reserve an unused name.
                    checked(source, false);
                    checked(targetDirectory, true);
                    if (!source.exists() || !targetDirectory.isDirectory()) throw new IOException("文件或目标目录已变化");
                    File target = unusedName(targetDirectory, source.getName(), source.isDirectory());
                    if (occupied(target) || !renamer.rename(source, target))
                        throw new IOException("移动未完成，原文件保留");
                    moved.add(new Move(source, target));
                } catch (IOException | SecurityException | IllegalArgumentException error) {
                    failures.add(new Failure(source, error.getMessage() == null ? "移动失败" : error.getMessage()));
                }
            }
            return new Result(moved, skipped, failures, selections.size() - topLevel.size());
        }
    }

    private File checked(File requested, boolean allowRoot) throws IOException {
        File canonical = requested.getCanonicalFile();
        if ((!allowRoot && canonical.equals(root)) || (!canonical.equals(root) && !inside(root, canonical)))
            throw new IllegalArgumentException("只能移动匣子内部文件，不能移动根目录");
        // The configured root may itself be an Android filesDir alias. Links below it are rejected.
        for (File part = requested.getAbsoluteFile(); part != null; part = part.getParentFile()) {
            if (part.equals(root) || part.equals(configuredRoot)) break;
            if (Files.isSymbolicLink(part.toPath())) throw new IllegalArgumentException("不支持移动符号链接路径");
        }
        return canonical;
    }

    private static boolean inside(File parent, File child) {
        return child.getPath().startsWith(parent.getPath() + File.separator);
    }

    private static boolean occupied(File file) {
        return Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS);
    }

    private static void validName(String name) {
        if (name.trim().isEmpty() || name.startsWith(".") || name.length() > 180 || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0)
            throw new IllegalArgumentException("文件名不受支持");
        for (int i = 0; i < name.length(); i++) if (name.charAt(i) < 32) throw new IllegalArgumentException("文件名不受支持");
    }

    private static File unusedName(File parent, String name, boolean directory) throws IOException {
        File target = new File(parent, name);
        if (!occupied(target)) return target;
        int dot = directory ? -1 : name.lastIndexOf('.');
        String extension = dot > 0 ? name.substring(dot) : "";
        if (extension.length() > 32 || extension.getBytes(StandardCharsets.UTF_8).length > 96) extension = "";
        String stem = extension.isEmpty() ? name : name.substring(0, dot);
        for (int number = 1; number <= 100000; number++) {
            String suffix = " (" + number + ")" + extension;
            String base = stem;
            while (!base.isEmpty() && (base.length() + suffix.length() > 180 ||
                    (base + suffix).getBytes(StandardCharsets.UTF_8).length > 255)) {
                base = base.substring(0, base.offsetByCodePoints(base.length(), -1));
            }
            if (base.isEmpty()) throw new IOException("无法生成不冲突的文件名");
            target = new File(parent, base + suffix);
            if (!occupied(target)) return target;
        }
        throw new IOException("同名文件过多，请先整理目标目录");
    }
}
