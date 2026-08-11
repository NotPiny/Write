package dev.piny.write.util;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.UUID;

public final class McPacksUploader {

    private static final String ENDPOINT = "https://mcpacks.dev/api/v1/packs";
    private static final Gson GSON = new Gson();

    private McPacksUploader() {}

    public static UploadResponse upload(File file) throws IOException, InterruptedException {
        String boundary = "----JavaBoundary" + UUID.randomUUID();
        byte[] body = buildMultipartBody(boundary, "file", file);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        UploadResponse parsed = GSON.fromJson(response.body(), UploadResponse.class);

        if (parsed == null) {
            throw new IOException("Empty or malformed response from mcpacks.dev (status " + response.statusCode() + ")");
        }
        if (!parsed.success) {
            throw new IOException("Upload failed (status " + response.statusCode() + "): " + response.body());
        }

        return parsed;
    }

    private static byte[] buildMultipartBody(String boundary, String fieldName, File file) throws IOException {
        byte[] fileBytes = Files.readAllBytes(file.toPath());

        String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + file.getName() + "\"\r\n"
                + "Content-Type: application/zip\r\n\r\n";

        String footer = "\r\n--" + boundary + "--\r\n";

        byte[] headerBytes = header.getBytes();
        byte[] footerBytes = footer.getBytes();

        byte[] combined = new byte[headerBytes.length + fileBytes.length + footerBytes.length];
        int offset = 0;
        System.arraycopy(headerBytes, 0, combined, offset, headerBytes.length);
        offset += headerBytes.length;
        System.arraycopy(fileBytes, 0, combined, offset, fileBytes.length);
        offset += fileBytes.length;
        System.arraycopy(footerBytes, 0, combined, offset, footerBytes.length);

        return combined;
    }

    public static final class UploadResponse {
        public boolean success;
        public PackData data;
    }

    public static final class PackData {
        public String uuid;
        public String filename;
        public String sha1;

        @SerializedName("download_url")
        public String downloadUrl;

        @SerializedName("file_size")
        public long fileSize;

        @SerializedName("server_properties")
        public ServerProperties serverProperties;
    }

    public static final class ServerProperties {
        @SerializedName("resource-pack")
        public String resourcePack;

        @SerializedName("resource-pack-sha1")
        public String resourcePackSha1;
    }
}