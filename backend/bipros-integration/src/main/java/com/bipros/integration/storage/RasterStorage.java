package com.bipros.integration.storage;

import java.net.URI;

/**
 * Minimal put/get/delete abstraction over an S3-compatible object store.
 * Implementations: {@link MinioRasterStorage} (default, points at MinIO or AWS
 * S3) and {@link LocalFilesystemRasterStorage} (tests, or dev without docker).
 * Keeping the interface narrow avoids leaking vendor SDK types (S3Client etc.)
 * into service code.
 */
public interface RasterStorage {

    /**
     * @param key         path-like identifier, e.g. {@code projectId/2024/08/sceneId.tif}
     * @param bytes       raw raster bytes
     * @param contentType {@code image/tiff}, {@code image/png}, …
     * @return URI that can be passed back to {@link #get(URI)} later. Always
     *         absolute ({@code s3://bucket/key} for S3 impls, {@code file:///…} for local).
     */
    URI put(String key, byte[] bytes, String contentType);

    /** @return raw bytes at the URI, or throws if not found. */
    byte[] get(URI uri);

    /** Idempotent — silently succeeds if the object doesn't exist. */
    void delete(URI uri);
}
