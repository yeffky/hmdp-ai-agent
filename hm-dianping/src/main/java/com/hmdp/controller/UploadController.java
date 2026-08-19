package com.hmdp.controller;

import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("upload")
public class UploadController {

    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024;
    private static final Set<String> ALLOWED_SUFFIXES = Set.of("jpg", "jpeg", "png", "gif", "webp");

    @Value("${hmdp.storage.image-upload-dir:./nginx-1.18.0/html/hmdp/imgs}")
    private String imageUploadDir;

    @PostMapping("blog")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        if (image == null || image.isEmpty()) {
            return Result.fail("图片不能为空");
        }
        if (image.getSize() > MAX_IMAGE_SIZE) {
            return Result.fail("图片不能超过 10MB");
        }
        String suffix = getSafeSuffix(image.getOriginalFilename());
        if (suffix == null || image.getContentType() == null
                || !image.getContentType().toLowerCase(Locale.ROOT).startsWith("image/")) {
            return Result.fail("仅支持 jpg、jpeg、png、gif、webp 图片");
        }

        try {
            String relativeFileName = createNewFileName(suffix);
            Path target = uploadRoot().resolve(relativeFileName).normalize();
            Files.createDirectories(target.getParent());
            image.transferTo(target.toFile());
            String publicFileName = "/" + relativeFileName;
            log.debug("文件上传成功：{}", publicFileName);
            return Result.ok(publicFileName);
        } catch (IOException e) {
            log.error("文件上传失败", e);
            return Result.fail("文件上传失败");
        }
    }

    @DeleteMapping("/blog")
    public Result deleteBlogImg(@RequestParam("name") String filename) {
        if (StrUtil.isBlank(filename)) {
            return Result.fail("文件名不能为空");
        }
        try {
            Path root = uploadRoot();
            String relativeFilename = filename.replaceFirst("^[\\\\/]+", "");
            Path relativePath = Paths.get(relativeFilename);
            if (relativePath.isAbsolute()) {
                return Result.fail("错误的文件名称");
            }
            Path target = root.resolve(relativePath).normalize();
            if (!target.startsWith(root) || !Files.isRegularFile(target)) {
                return Result.fail("错误的文件名称");
            }
            Files.delete(target);
            return Result.ok();
        } catch (IOException e) {
            log.error("文件删除失败：{}", filename, e);
            return Result.fail("文件删除失败");
        }
    }

    private String createNewFileName(String suffix) {
        String name = UUID.randomUUID().toString();
        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;
        return StrUtil.format("blogs/{}/{}/{}.{}", d1, d2, name, suffix);
    }

    private String getSafeSuffix(String originalFilename) {
        if (StrUtil.isBlank(originalFilename)) {
            return null;
        }
        String suffix = StrUtil.subAfter(originalFilename, ".", true);
        suffix = suffix == null ? "" : suffix.toLowerCase(Locale.ROOT);
        return ALLOWED_SUFFIXES.contains(suffix) ? suffix : null;
    }

    private Path uploadRoot() {
        return Paths.get(imageUploadDir).toAbsolutePath().normalize();
    }
}
