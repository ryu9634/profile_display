package com.portfolio.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 업로드 파일 저장과 축소본 생성.
 */
class FileStorageServiceTest {

    @TempDir
    Path uploadDir;

    private FileStorageService service() {
        return new FileStorageService(uploadDir.toString());
    }

    private static byte[] jpeg(int width, int height) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }

    private static MockMultipartFile upload(String name, String contentType, byte[] body) {
        return new MockMultipartFile("file", name, contentType, body);
    }

    @Test
    @DisplayName("원본 이름 대신 UUID 로 저장해 이름 충돌과 경로 주입을 막는다")
    void storesWithGeneratedName() throws Exception {
        String stored = service().storeFile(upload("내 사진.jpg", "image/jpeg", jpeg(100, 80)));

        assertThat(stored).endsWith(".jpg").doesNotContain("내 사진");
        assertThat(uploadDir.resolve(stored)).exists();
    }

    @Test
    @DisplayName("큰 이미지는 목록용 축소본을 함께 만든다")
    void createsThumbnailForLargeImage() throws Exception {
        String stored = service().storeFile(upload("big.jpg", "image/jpeg", jpeg(1800, 1200)));

        Path thumb = uploadDir.resolve(FileStorageService.thumbnailName(stored));
        assertThat(thumb).exists();

        BufferedImage thumbImage = ImageIO.read(thumb.toFile());
        assertThat(thumbImage.getWidth()).isEqualTo(900);
        // 가로세로 비율이 유지되어야 합니다 (1800x1200 -> 900x600)
        assertThat(thumbImage.getHeight()).isEqualTo(600);
    }

    @Test
    @DisplayName("원본이 작아도 축소본을 만든다")
    void createsThumbnailEvenForSmallImage() throws Exception {
        // 축소본이 없으면 화면이 이미지마다 실패 요청을 한 번씩 보내게 됩니다.
        String stored = service().storeFile(upload("small.jpg", "image/jpeg", jpeg(320, 240)));

        assertThat(uploadDir.resolve(FileStorageService.thumbnailName(stored))).exists();
    }

    @Test
    @DisplayName("허용하지 않는 확장자는 거부한다")
    void rejectsDisallowedExtension() {
        assertThatThrownBy(() ->
                service().storeFile(upload("hack.html", "text/html", "<script>".getBytes())))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("허용되지 않는 파일 형식");
    }

    @Test
    @DisplayName("확장자가 맞아도 MIME 타입이 다르면 거부한다")
    void rejectsMismatchedMimeType() throws Exception {
        assertThatThrownBy(() ->
                service().storeFile(upload("fake.jpg", "text/html", jpeg(10, 10))))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("허용되지 않는 파일 타입");
    }

    @Test
    @DisplayName("빈 파일은 거부한다")
    void rejectsEmptyFile() {
        assertThatThrownBy(() ->
                service().storeFile(upload("empty.jpg", "image/jpeg", new byte[0])))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("빈 파일");
    }

    @Test
    @DisplayName("파일을 지우면 축소본도 함께 지운다")
    void deletingFileAlsoDeletesThumbnail() throws Exception {
        FileStorageService service = service();
        String stored = service.storeFile(upload("big.jpg", "image/jpeg", jpeg(1800, 1200)));
        Path thumb = uploadDir.resolve(FileStorageService.thumbnailName(stored));
        assertThat(thumb).exists();

        service.deleteFile(stored);

        assertThat(uploadDir.resolve(stored)).doesNotExist();
        assertThat(thumb).doesNotExist();
    }

    @Test
    @DisplayName("저장 폴더 밖으로 나가는 삭제 요청은 막는다")
    void rejectsPathTraversalOnDelete() throws Exception {
        Path outside = uploadDir.getParent().resolve("secret.txt");
        Files.writeString(outside, "건드리면 안 되는 파일");

        assertThatThrownBy(() -> service().deleteFile("../secret.txt"))
                .isInstanceOf(RuntimeException.class);

        assertThat(outside).exists();
    }

    @Test
    @DisplayName("축소본 이름은 확장자 앞에 _thumb 을 붙인다")
    void thumbnailNaming() {
        assertThat(FileStorageService.thumbnailName("abc.jpg")).isEqualTo("abc_thumb.jpg");
        assertThat(FileStorageService.thumbnailName("abc.PNG")).isEqualTo("abc_thumb.PNG");
        assertThat(FileStorageService.thumbnailName("noext")).isEqualTo("noext_thumb");
    }
}
