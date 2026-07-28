package com.xr.positiveaicode.controller;

import cn.hutool.core.util.StrUtil;
import com.xr.positiveaicode.constant.AppConstant;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import java.io.File;
import java.nio.file.Path;

/**
 * 提供已部署应用的静态访问（经 Nginx /api 反代即可，无需再配 /apps、/dist 目录）。
 * 访问：{deploy-host}/{deployKey}/ 例如 http://host/api/deployed/abc123/
 */
@RestController
@RequestMapping("/deployed")
public class DeployedResourceController {

    @Value("${code.deploy-root:}")
    private String deployRoot;

    @GetMapping("/{deployKey}/**")
    public ResponseEntity<Resource> serveDeployedResource(
            @PathVariable String deployKey,
            HttpServletRequest request) {
        if (StrUtil.isBlank(deployKey) || !deployKey.matches("^[a-zA-Z0-9_-]{1,64}$")) {
            return ResponseEntity.badRequest().build();
        }
        try {
            String resourcePath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            String prefix = "/deployed/" + deployKey;
            if (resourcePath == null || !resourcePath.startsWith(prefix)) {
                return ResponseEntity.notFound().build();
            }
            resourcePath = resourcePath.substring(prefix.length());
            if (resourcePath.isEmpty()) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("Location", request.getRequestURI() + "/");
                return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
            }
            if (resourcePath.equals("/")) {
                resourcePath = "/index.html";
            }
            if (resourcePath.contains("..")) {
                return ResponseEntity.badRequest().build();
            }

            Path root = Path.of(resolveDeployRootDir()).toAbsolutePath().normalize();
            Path filePath = root.resolve(deployKey + resourcePath).normalize();
            if (!filePath.startsWith(root)) {
                return ResponseEntity.badRequest().build();
            }
            File file = filePath.toFile();
            if (!file.exists() || !file.isFile()) {
                return ResponseEntity.notFound().build();
            }
            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, getContentTypeWithCharset(file.getName()))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private String resolveDeployRootDir() {
        if (StrUtil.isNotBlank(deployRoot)) {
            return deployRoot.trim();
        }
        return AppConstant.CODE_DEPLOY_ROOT_DIR;
    }

    private String getContentTypeWithCharset(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".html")) return "text/html; charset=UTF-8";
        if (lower.endsWith(".css")) return "text/css; charset=UTF-8";
        if (lower.endsWith(".js") || lower.endsWith(".mjs")) return "application/javascript; charset=UTF-8";
        if (lower.endsWith(".json")) return "application/json; charset=UTF-8";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".ico")) return "image/x-icon";
        if (lower.endsWith(".woff")) return "font/woff";
        if (lower.endsWith(".woff2")) return "font/woff2";
        if (lower.endsWith(".ttf")) return "font/ttf";
        if (lower.endsWith(".map")) return "application/json";
        return "application/octet-stream";
    }
}
