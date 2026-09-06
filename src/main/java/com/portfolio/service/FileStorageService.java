package com.portfolio.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/svg+xml",
            "application/pdf"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg",
            ".pdf"
    );

    /** 썸네일을 생성하는 형식 (ImageIO로 안전하게 읽을 수 있는 것만) */
    private static final Set<String> THUMBNAIL_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png");

    public static final String THUMB_SUFFIX = "_thumb";
    private static final int THUMB_MAX_WIDTH = 900;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    private final Path fileStorageLocation;

    public FileStorageService(@Value("${file.upload-dir}") String uploadDir) {
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("파일 저장 디렉토리를 생성할 수 없습니다.", ex);
        }
    }

    public String storeFile(MultipartFile file) {
        String originalFileName = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "" : file.getOriginalFilename());

        try {
            if (originalFileName.contains("..")) {
                throw new RuntimeException("유효하지 않은 파일명입니다: " + originalFileName);
            }

            if (file.isEmpty()) {
                throw new RuntimeException("빈 파일은 저장할 수 없습니다: " + originalFileName);
            }

            if (file.getSize() > MAX_FILE_SIZE) {
                throw new RuntimeException("파일 크기가 10MB를 초과합니다 (" + humanSize(file.getSize()) + "): " + originalFileName);
            }

            String fileExtension = "";
            if (originalFileName.contains(".")) {
                fileExtension = originalFileName.substring(originalFileName.lastIndexOf(".")).toLowerCase();
            }
            if (!ALLOWED_EXTENSIONS.contains(fileExtension)) {
                throw new RuntimeException("허용되지 않는 파일 형식입니다: " + (fileExtension.isEmpty() ? "확장자 없음" : fileExtension)
                        + " (허용: jpg, jpeg, png, gif, webp, svg, pdf)");
            }

            String mimeType = file.getContentType();
            if (mimeType == null || !ALLOWED_MIME_TYPES.contains(mimeType)) {
                throw new RuntimeException("허용되지 않는 파일 타입입니다: " + mimeType);
            }

            String fileName = UUID.randomUUID().toString() + fileExtension;
            Path targetLocation = this.fileStorageLocation.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            if (THUMBNAIL_EXTENSIONS.contains(fileExtension)) {
                createThumbnail(targetLocation, fileName, fileExtension);
            }

            log.info("파일 저장 완료: {} -> {}", originalFileName, fileName);
            return fileName;
        } catch (IOException ex) {
            log.error("파일 저장 실패: {}", originalFileName, ex);
            throw new RuntimeException("파일을 저장할 수 없습니다: " + originalFileName, ex);
        }
    }

    /**
     * 목록/썸네일 스트립용 축소 이미지를 원본 옆에 "&lt;이름&gt;_thumb.&lt;확장자&gt;"로 저장합니다.
     * 실패해도 원본 저장에는 영향을 주지 않습니다 (프론트에서 원본으로 폴백).
     */
    private void createThumbnail(Path source, String fileName, String extension) {
        try {
            int orientation = readExifOrientation(source);
            BufferedImage original;
            try (InputStream in = Files.newInputStream(source)) {
                original = ImageIO.read(in);
            }
            if (original == null) {
                return;
            }

            BufferedImage oriented = applyOrientation(original, orientation);
            int w = oriented.getWidth();
            int h = oriented.getHeight();

            // 원본이 이미 작아도 축소본을 만들어 둡니다.
            // 프론트는 항상 "<이름>_thumb" 을 먼저 요청하므로, 없으면 이미지마다
            // 실패 요청이 한 번씩 발생합니다. (EXIF 회전도 여기서 반영됩니다)
            double ratio = Math.min(1.0, (double) THUMB_MAX_WIDTH / w);
            int tw = Math.max(1, (int) Math.round(w * ratio));
            int th = Math.max(1, (int) Math.round(h * ratio));

            boolean hasAlpha = oriented.getColorModel().hasAlpha();
            BufferedImage thumb = new BufferedImage(tw, th,
                    hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
            Graphics2D g = thumb.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(oriented, 0, 0, tw, th, null);
            g.dispose();

            String format = ".png".equals(extension) ? "png" : "jpg";
            Path thumbPath = this.fileStorageLocation.resolve(thumbnailName(fileName));
            if (!ImageIO.write(thumb, format, thumbPath.toFile())) {
                Files.deleteIfExists(thumbPath);
            }
        } catch (Exception ex) {
            log.warn("썸네일 생성 실패 (원본은 정상 저장됨): {} - {}", fileName, ex.getMessage());
        }
    }

    public static String thumbnailName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return fileName + THUMB_SUFFIX;
        }
        return fileName.substring(0, dot) + THUMB_SUFFIX + fileName.substring(dot);
    }

    private static BufferedImage applyOrientation(BufferedImage img, int orientation) {
        if (orientation <= 1 || orientation > 8) {
            return img;
        }
        int w = img.getWidth();
        int h = img.getHeight();
        AffineTransform t = new AffineTransform();
        int outW = w;
        int outH = h;
        switch (orientation) {
            case 2: t.scale(-1, 1); t.translate(-w, 0); break;
            case 3: t.translate(w, h); t.rotate(Math.PI); break;
            case 4: t.scale(1, -1); t.translate(0, -h); break;
            case 5: t.rotate(-Math.PI / 2); t.scale(-1, 1); outW = h; outH = w; break;
            case 6: t.translate(h, 0); t.rotate(Math.PI / 2); outW = h; outH = w; break;
            case 7: t.scale(-1, 1); t.translate(-h, 0); t.translate(0, w); t.rotate(3 * Math.PI / 2); outW = h; outH = w; break;
            case 8: t.translate(0, w); t.rotate(3 * Math.PI / 2); outW = h; outH = w; break;
            default: return img;
        }
        int type = img.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage out = new BufferedImage(outW, outH, type);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, t, null);
        g.dispose();
        return out;
    }

    /**
     * JPEG의 EXIF Orientation(0x0112) 값을 읽습니다. 없거나 읽을 수 없으면 1(정방향).
     * 스마트폰 사진이 옆으로 누운 채 썸네일이 되는 것을 막기 위한 최소 파서입니다.
     */
    static int readExifOrientation(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            byte[] data = in.readNBytes(128 * 1024);
            if (data.length < 4 || (data[0] & 0xFF) != 0xFF || (data[1] & 0xFF) != 0xD8) {
                return 1; // JPEG 아님
            }
            int pos = 2;
            while (pos + 4 <= data.length) {
                if ((data[pos] & 0xFF) != 0xFF) {
                    return 1;
                }
                int marker = data[pos + 1] & 0xFF;
                if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                    pos += 2;
                    continue;
                }
                if (marker == 0xDA || marker == 0xD9) {
                    return 1; // 이미지 데이터 시작: EXIF 없음
                }
                int segLen = ((data[pos + 2] & 0xFF) << 8) | (data[pos + 3] & 0xFF);
                if (marker == 0xE1 && pos + 4 + 6 <= data.length
                        && data[pos + 4] == 'E' && data[pos + 5] == 'x' && data[pos + 6] == 'i' && data[pos + 7] == 'f') {
                    int tiff = pos + 10;
                    if (tiff + 8 > data.length) {
                        return 1;
                    }
                    boolean le = data[tiff] == 'I' && data[tiff + 1] == 'I';
                    int ifdOffset = readInt(data, tiff + 4, le);
                    int ifd = tiff + ifdOffset;
                    if (ifd + 2 > data.length) {
                        return 1;
                    }
                    int entries = readShort(data, ifd, le);
                    for (int i = 0; i < entries; i++) {
                        int entry = ifd + 2 + i * 12;
                        if (entry + 12 > data.length) {
                            return 1;
                        }
                        int tag = readShort(data, entry, le);
                        if (tag == 0x0112) {
                            int value = readShort(data, entry + 8, le);
                            return (value >= 1 && value <= 8) ? value : 1;
                        }
                    }
                    return 1;
                }
                pos += 2 + segLen;
            }
        } catch (Exception ignored) {
            // 방향 정보를 못 읽으면 정방향으로 간주
        }
        return 1;
    }

    private static int readShort(byte[] d, int p, boolean le) {
        int a = d[p] & 0xFF, b = d[p + 1] & 0xFF;
        return le ? (b << 8) | a : (a << 8) | b;
    }

    private static int readInt(byte[] d, int p, boolean le) {
        int a = d[p] & 0xFF, b = d[p + 1] & 0xFF, c = d[p + 2] & 0xFF, e = d[p + 3] & 0xFF;
        return le ? (e << 24) | (c << 16) | (b << 8) | a : (a << 24) | (b << 16) | (c << 8) | e;
    }

    private static String humanSize(long bytes) {
        return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
    }

    public void deleteFile(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return;
        }

        try {
            Path filePath = this.fileStorageLocation.resolve(fileName).normalize();

            if (!filePath.startsWith(this.fileStorageLocation)) {
                throw new RuntimeException("접근이 거부되었습니다: " + fileName);
            }

            Files.deleteIfExists(filePath);
            // 함께 만들어진 썸네일도 정리
            Path thumbPath = this.fileStorageLocation.resolve(thumbnailName(fileName)).normalize();
            if (thumbPath.startsWith(this.fileStorageLocation)) {
                Files.deleteIfExists(thumbPath);
            }
            log.info("파일 삭제 완료: {}", fileName);
        } catch (IOException ex) {
            log.error("파일 삭제 실패: {}", fileName, ex);
            throw new RuntimeException("파일을 삭제할 수 없습니다: " + fileName, ex);
        }
    }

    public void deleteFiles(Iterable<String> fileNames) {
        if (fileNames == null) {
            return;
        }

        for (String fileName : fileNames) {
            deleteFile(fileName);
        }
    }

    public Path getFileStorageLocation() {
        return fileStorageLocation;
    }
}
