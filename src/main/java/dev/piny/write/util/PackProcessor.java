package dev.piny.write.util;

import dev.piny.write.Write;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class PackProcessor {
    private PackProcessor() {}

    private static final List<PackProcessingStep> STEPS = new ArrayList<>();

    static {
        STEPS.add(PackProcessor::insertMarkerFiles);
    }

    @FunctionalInterface
    public interface PackProcessingStep {
        void process(Path extractedPackRoot) throws IOException;
    }

    public static void registerStep(PackProcessingStep step) {
        STEPS.add(step);
    }

    public static Path process(Path sourceZip, String identifier) {
        if (!Files.exists(sourceZip)) return null;

        Path compiledDir = Paths.get(Write.getInstance().getDataFolder().toString(), "compiled_packs");
        Path compiledZip = compiledDir.resolve(identifier + ".zip");
        Path hashFile = compiledDir.resolve(identifier + ".sha1");

        try {
            Files.createDirectories(compiledDir);

            String sourceHash = HostablePack.getSha1Hash(sourceZip.toFile());
            if (Files.exists(compiledZip) && Files.exists(hashFile) && Files.readString(hashFile).equals(sourceHash)) {
                return compiledZip;
            }

            Path processingDir = Paths.get(Write.getInstance().getDataFolder().toString(), "processing", identifier);
            deleteRecursively(processingDir);
            Files.createDirectories(processingDir);

            extractZip(sourceZip, processingDir);

            for (PackProcessingStep step : STEPS) {
                step.process(processingDir);
            }

            zipDirectory(processingDir, compiledZip);
            deleteRecursively(processingDir);

            Files.writeString(hashFile, sourceHash);

            return compiledZip;
        } catch (IOException | NoSuchAlgorithmException e) {
            Write.getInstance().getLogger().severe("Failed to compile resource pack '" + identifier + "': " + e.getMessage());
            return Files.exists(compiledZip) ? compiledZip : null;
        }
    }

    // this is just here so that I know the pack has been processed.
    private static void insertMarkerFiles(Path extractedPackRoot) throws IOException {
        try (Stream<Path> stream = Files.walk(extractedPackRoot)) {
            List<Path> mcmetaFiles = stream
                    .filter(path -> path.getFileName().toString().equals("pack.mcmeta"))
                    .toList();

            for (Path mcmeta : mcmetaFiles) {
                Files.writeString(mcmeta.resolveSibling("pack.wmarker"), "Processed by Write at " + System.currentTimeMillis());
            }
        }
    }

    private static void extractZip(Path zipFile, Path destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path target = destDir.resolve(entry.getName()).normalize();
                if (!target.startsWith(destDir)) {
                    throw new IOException("Zip entry escapes target directory: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    if (target.getParent() != null) Files.createDirectories(target.getParent());
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                }

                zis.closeEntry();
            }
        }
    }

    public static void zipDirectory(Path sourceDir, Path zipFile) throws IOException {
        Path tempZip = zipFile.resolveSibling(zipFile.getFileName().toString() + ".tmp");

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempZip))) {
            try (Stream<Path> stream = Files.walk(sourceDir)) {
                List<Path> files = stream.filter(Files::isRegularFile)
                        .sorted(Comparator.comparing(file -> sourceDir.relativize(file).toString().replace('\\', '/')))
                        .toList();

                for (Path file : files) {
                    String entryName = sourceDir.relativize(file).toString().replace('\\', '/');
                    ZipEntry zipEntry = new ZipEntry(entryName);
                    zipEntry.setTime(0L); // Help avoid changing hash when contents are stable
                    zos.putNextEntry(zipEntry);
                    Files.copy(file, zos);
                    zos.closeEntry();
                }
            }
        }

        Files.move(tempZip, zipFile, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;

        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {}
            });
        }
    }
}



