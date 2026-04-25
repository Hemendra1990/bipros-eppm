package com.bipros.gis.application.service;

import com.bipros.common.exception.BusinessRuleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Iterator;

/**
 * Reads a GeoTIFF byte array, extracts its bounding envelope from GeoTIFF tags,
 * renders it to a PNG, and optionally scales the result so it doesn't exceed a
 * reasonable display size for the OpenLayers {@code ImageStatic} source and the
 * AI vision analyzer.
 * <p>
 * This implementation uses a lightweight built-in TIFF tag parser plus
 * {@code jai-imageio-core} for raster decoding. No external native libraries or
 * heavy GIS frameworks are required.
 */
@Component
@Slf4j
public class GeoTiffProcessor {

    private static final int MAX_DIMENSION = 2048;

    // TIFF tag numbers
    private static final int TAG_IMAGE_WIDTH = 256;
    private static final int TAG_IMAGE_LENGTH = 257;
    private static final int TAG_MODEL_PIXEL_SCALE = 33550;
    private static final int TAG_MODEL_TIEPOINT = 33922;
    private static final int TAG_GEO_KEY_DIRECTORY = 34735;

    // GeoKey IDs
    private static final int KEY_GT_MODEL_TYPE = 1024;
    private static final int KEY_GEOGRAPHIC_TYPE = 2048;
    private static final int KEY_PROJECTED_CS_TYPE = 3073;

    public ProcessedImage process(byte[] bytes) {
        GeoTiffInfo info = parseGeoTiffInfo(bytes);
        if (info == null) {
            throw new BusinessRuleException("INVALID_GEOTIFF", "File is not a valid GeoTIFF");
        }

        byte[] pngBytes = renderToPng(bytes);

        return new ProcessedImage(
            pngBytes,
            info.southBound,
            info.northBound,
            info.westBound,
            info.eastBound
        );
    }

    /**
     * Parses GeoTIFF tags to extract image dimensions and geographic bounds.
     * Returns null if the file is not a valid GeoTIFF with required tags.
     */
    private GeoTiffInfo parseGeoTiffInfo(byte[] bytes) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(bytes);

            // TIFF header
            byte b0 = buf.get();
            byte b1 = buf.get();
            ByteOrder order;
            if (b0 == 'I' && b1 == 'I') {
                order = ByteOrder.LITTLE_ENDIAN;
            } else if (b0 == 'M' && b1 == 'M') {
                order = ByteOrder.BIG_ENDIAN;
            } else {
                return null; // Not a TIFF
            }
            buf.order(order);

            short magic = buf.getShort();
            if (magic != 42) {
                return null;
            }

            int ifdOffset = buf.getInt();
            if (ifdOffset < 8 || ifdOffset >= bytes.length) {
                return null;
            }

            buf.position(ifdOffset);

            int width = 0;
            int height = 0;
            double[] pixelScale = null;
            double[] tiepoints = null;
            int epsgCode = -1;

            int numEntries = buf.getShort() & 0xFFFF;
            for (int i = 0; i < numEntries; i++) {
                int tag = buf.getShort() & 0xFFFF;
                int type = buf.getShort() & 0xFFFF;
                int count = buf.getInt();
                int valueOrOffset = buf.getInt();

                switch (tag) {
                    case TAG_IMAGE_WIDTH -> width = readIntValue(buf, type, count, valueOrOffset, bytes, order);
                    case TAG_IMAGE_LENGTH -> height = readIntValue(buf, type, count, valueOrOffset, bytes, order);
                    case TAG_MODEL_PIXEL_SCALE -> pixelScale = readDoubles(buf, type, count, valueOrOffset, bytes, order);
                    case TAG_MODEL_TIEPOINT -> tiepoints = readDoubles(buf, type, count, valueOrOffset, bytes, order);
                    case TAG_GEO_KEY_DIRECTORY -> epsgCode = extractEpsgFromGeoKeys(buf, type, count, valueOrOffset, bytes, order);
                }
            }

            if (width <= 0 || height <= 0 || pixelScale == null || pixelScale.length < 2
                || tiepoints == null || tiepoints.length < 6) {
                return null;
            }

            double tiepointPixelX = tiepoints[0];
            double tiepointPixelY = tiepoints[1];
            double tiepointGeoX = tiepoints[3];
            double tiepointGeoY = tiepoints[4];

            // Compute bounds from all four corners
            double[] xs = new double[4];
            double[] ys = new double[4];
            int idx = 0;
            for (double px : new double[]{0, width}) {
                for (double py : new double[]{0, height}) {
                    xs[idx] = tiepointGeoX + (px - tiepointPixelX) * pixelScale[0];
                    ys[idx] = tiepointGeoY + (py - tiepointPixelY) * pixelScale[1];
                    idx++;
                }
            }

            double west = xs[0];
            double east = xs[0];
            double south = ys[0];
            double north = ys[0];
            for (int i = 1; i < 4; i++) {
                west = Math.min(west, xs[i]);
                east = Math.max(east, xs[i]);
                south = Math.min(south, ys[i]);
                north = Math.max(north, ys[i]);
            }

            GeoTiffInfo info = new GeoTiffInfo();
            info.width = width;
            info.height = height;
            info.westBound = west;
            info.eastBound = east;
            info.southBound = south;
            info.northBound = north;
            info.epsgCode = epsgCode;
            return info;

        } catch (Exception e) {
            log.warn("GeoTIFF tag parsing failed: {}", e.getMessage());
            return null;
        }
    }

    private int readIntValue(ByteBuffer buf, int type, int count, int valueOrOffset, byte[] bytes, ByteOrder order) {
        int sizePerValue = tiffTypeSize(type);
        int totalSize = sizePerValue * count;
        if (totalSize <= 4 && count == 1) {
            // Value stored inline; sign-extend appropriately
            return switch (type) {
                case 3 -> valueOrOffset & 0xFFFF; // SHORT
                case 4 -> valueOrOffset;          // LONG
                case 1 -> valueOrOffset & 0xFF;   // BYTE
                default -> valueOrOffset;
            };
        } else {
            // Value stored at offset
            ByteBuffer vb = ByteBuffer.wrap(bytes, valueOrOffset, totalSize).order(order);
            return switch (type) {
                case 3 -> vb.getShort() & 0xFFFF;
                case 4 -> vb.getInt();
                case 1 -> vb.get() & 0xFF;
                default -> vb.getInt();
            };
        }
    }

    private double[] readDoubles(ByteBuffer buf, int type, int count, int valueOrOffset, byte[] bytes, ByteOrder order) {
        int sizePerValue = tiffTypeSize(type);
        int totalSize = sizePerValue * count;
        ByteBuffer vb;
        if (totalSize <= 4) {
            vb = ByteBuffer.allocate(4).order(order);
            vb.putInt(valueOrOffset);
            vb.flip();
        } else {
            vb = ByteBuffer.wrap(bytes, valueOrOffset, totalSize).order(order);
        }

        double[] result = new double[count];
        for (int i = 0; i < count; i++) {
            result[i] = switch (type) {
                case 12 -> vb.getDouble();          // DOUBLE
                case 5 -> readRational(vb, order);  // RATIONAL
                case 11 -> vb.getFloat();           // FLOAT
                case 4 -> vb.getInt();              // LONG
                case 3 -> vb.getShort() & 0xFFFF;   // SHORT
                default -> vb.getDouble();
            };
        }
        return result;
    }

    private double readRational(ByteBuffer buf, ByteOrder order) {
        int num = buf.getInt();
        int den = buf.getInt();
        return den == 0 ? 0.0 : (double) num / den;
    }

    private int extractEpsgFromGeoKeys(ByteBuffer buf, int type, int count, int valueOrOffset, byte[] bytes, ByteOrder order) {
        int sizePerValue = tiffTypeSize(type);
        int totalSize = sizePerValue * count;
        ByteBuffer vb;
        if (totalSize <= 4) {
            vb = ByteBuffer.allocate(4).order(order);
            vb.putInt(valueOrOffset);
            vb.flip();
        } else {
            vb = ByteBuffer.wrap(bytes, valueOrOffset, totalSize).order(order);
        }

        // Skip header: KeyDirectoryVersion, KeyRevision, Minor, NumberOfKeys (4 shorts)
        for (int i = 0; i < 4; i++) {
            vb.getShort();
        }

        int numKeys = (count - 4) / 4;
        for (int i = 0; i < numKeys; i++) {
            int keyId = vb.getShort() & 0xFFFF;
            int tagLoc = vb.getShort() & 0xFFFF;
            int keyCount = vb.getShort() & 0xFFFF;
            int keyValue = vb.getShort() & 0xFFFF;

            if (tagLoc == 0 && (keyId == KEY_GEOGRAPHIC_TYPE || keyId == KEY_PROJECTED_CS_TYPE)) {
                return keyValue; // EPSG code
            }
        }
        return -1;
    }

    private int tiffTypeSize(int type) {
        return switch (type) {
            case 1, 2 -> 1;   // BYTE, ASCII
            case 3 -> 2;      // SHORT
            case 4, 11 -> 4;  // LONG, FLOAT
            case 5, 12 -> 8;  // RATIONAL, DOUBLE
            default -> 1;
        };
    }

    private byte[] renderToPng(byte[] bytes) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            ImageInputStream iis = ImageIO.createImageInputStream(bais);
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new BusinessRuleException("UNSUPPORTED_IMAGE", "No ImageIO reader available for this file");
            }
            ImageReader reader = readers.next();
            reader.setInput(iis, false);
            BufferedImage image = reader.read(0);
            reader.dispose();
            iis.close();

            int width = image.getWidth();
            int height = image.getHeight();

            BufferedImage rgbImage;
            if (image.getType() == BufferedImage.TYPE_INT_RGB || image.getType() == BufferedImage.TYPE_INT_ARGB) {
                rgbImage = image;
            } else {
                rgbImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = rgbImage.createGraphics();
                g.drawImage(image, 0, 0, null);
                g.dispose();
            }

            if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
                double scale = Math.min((double) MAX_DIMENSION / width, (double) MAX_DIMENSION / height);
                int newWidth = (int) (width * scale);
                int newHeight = (int) (height * scale);
                Image scaled = rgbImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
                BufferedImage scaledImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
                Graphics2D g2 = scaledImage.createGraphics();
                g2.drawImage(scaled, 0, 0, null);
                g2.dispose();
                rgbImage = scaledImage;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(rgbImage, "png", baos);
            return baos.toByteArray();

        } catch (IOException e) {
            throw new BusinessRuleException("IMAGE_RENDER_FAILED",
                "Failed to render image to PNG: " + e.getMessage());
        }
    }

    private static class GeoTiffInfo {
        int width;
        int height;
        double westBound;
        double eastBound;
        double southBound;
        double northBound;
        int epsgCode;
    }

    public record ProcessedImage(byte[] pngBytes, double southBound, double northBound,
                                  double westBound, double eastBound) {}
}
