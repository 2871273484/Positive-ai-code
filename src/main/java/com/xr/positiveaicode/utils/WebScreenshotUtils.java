package com.xr.positiveaicode.utils;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import cn.hutool.http.HttpUtil;
import com.xr.positiveaicode.exception.BusinessException;
import com.xr.positiveaicode.exception.ErrorCode;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 网页截图工具。
 * ChromeDriver 优先使用本地文件，必要时从国内镜像下载，避免访问 Google 被重置。
 */
@Slf4j
public class WebScreenshotUtils {

    private static final AtomicBoolean DRIVER_READY = new AtomicBoolean(false);
    private static final Object DRIVER_LOCK = new Object();

    /** 国内 Chrome for Testing 镜像（npmmirror） */
    private static final String NPMMIRROR_CFT_BASE = "https://cdn.npmmirror.com/binaries/chrome-for-testing/";

    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+\\.\\d+\\.\\d+\\.\\d+)");

    public static void cleanupTempFiles() {
        FileUtil.clean(System.getProperty("user.dir") + File.separator + "tmp" + File.separator + "screenshots");
    }

    @PreDestroy
    public void destroy() {
        cleanupTempFiles();
    }

    /**
     * 初始化 Chrome 浏览器驱动
     */
    public static WebDriver initChromeDriver(int width, int height) {
        try {
            ensureChromeDriver();
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless=new");
            options.addArguments("--disable-gpu");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--allow-file-access-from-files");
            options.addArguments("--disable-web-security");
            options.addArguments(String.format("--window-size=%d,%d", width, height));
            options.addArguments("--disable-extensions");
            options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            String chromeBinary = resolveChromeBinary();
            if (StrUtil.isNotBlank(chromeBinary)) {
                options.setBinary(chromeBinary);
            }
            WebDriver driver = new ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(15));
            driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(10));
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(2));
            return driver;
        } catch (Exception e) {
            log.error("初始化 Chrome 浏览器失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "初始化 Chrome 浏览器失败，请将匹配的 chromedriver 放到项目 drivers/ 目录，或设置环境变量 CHROMEDRIVER_PATH");
        }
    }

    /**
     * 准备 ChromeDriver：本地路径 > 缓存驱动 > 国内镜像下载
     * 不再调用 WebDriverManager 访问 Google。
     */
    private static void ensureChromeDriver() {
        if (DRIVER_READY.get()) {
            return;
        }
        synchronized (DRIVER_LOCK) {
            if (DRIVER_READY.get()) {
                return;
            }
            File driverFile = resolveExistingDriver();
            if (driverFile == null) {
                driverFile = downloadDriverFromNpmMirror();
            }
            if (driverFile == null || !driverFile.exists()) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                        "未找到 ChromeDriver。请下载后放到 drivers/chromedriver.exe，或设置 CHROMEDRIVER_PATH");
            }
            System.setProperty("webdriver.chrome.driver", driverFile.getAbsolutePath());
            // 避免 Selenium Manager 再去访问 Google 下载驱动
            System.setProperty("SE_OFFLINE", "true");
            DRIVER_READY.set(true);
            log.info("ChromeDriver 已就绪: {}", driverFile.getAbsolutePath());
        }
    }

    private static File resolveExistingDriver() {
        String envPath = System.getenv("CHROMEDRIVER_PATH");
        if (StrUtil.isNotBlank(envPath) && FileUtil.exist(envPath)) {
            log.info("使用环境变量 CHROMEDRIVER_PATH: {}", envPath);
            return new File(envPath);
        }
        String sysProp = System.getProperty("webdriver.chrome.driver");
        if (StrUtil.isNotBlank(sysProp) && FileUtil.exist(sysProp)) {
            log.info("使用系统属性 webdriver.chrome.driver: {}", sysProp);
            return new File(sysProp);
        }
        String projectRoot = System.getProperty("user.dir");
        String userHome = System.getProperty("user.home");
        String chromeVersion = detectChromeVersion();
        if (StrUtil.isNotBlank(chromeVersion)) {
            File[] versioned = {
                    new File(projectRoot,
                            "tmp/chromedriver/" + chromeVersion + "/chromedriver-win64/chromedriver.exe"),
                    // Selenium Manager 本机缓存
                    new File(userHome,
                            ".cache/selenium/chromedriver/win64/" + chromeVersion + "/chromedriver.exe"),
            };
            for (File cached : versioned) {
                if (cached.exists()) {
                    log.info("使用已缓存 ChromeDriver: {}", cached.getAbsolutePath());
                    return cached;
                }
            }
            // 同主版本号的 Selenium 缓存（如 144.0.7559.133 可匹配 144.x）
            File seleniumRoot = new File(userHome, ".cache/selenium/chromedriver/win64");
            if (seleniumRoot.isDirectory()) {
                String major = chromeVersion.split("\\.")[0];
                File[] dirs = seleniumRoot.listFiles(File::isDirectory);
                if (dirs != null) {
                    File matched = Arrays.stream(dirs)
                            .filter(d -> d.getName().startsWith(major + "."))
                            .max(Comparator.comparing(File::getName))
                            .map(d -> new File(d, "chromedriver.exe"))
                            .filter(File::exists)
                            .orElse(null);
                    if (matched != null) {
                        log.info("使用同主版本 Selenium 缓存 ChromeDriver: {}", matched.getAbsolutePath());
                        return matched;
                    }
                }
            }
        }
        File[] candidates = {
                new File(projectRoot, "drivers/chromedriver.exe"),
                new File(projectRoot, "drivers/chromedriver"),
                new File(projectRoot, "tmp/chromedriver/chromedriver.exe"),
                new File(projectRoot, "tmp/chromedriver/chromedriver-win64/chromedriver.exe"),
        };
        for (File candidate : candidates) {
            if (candidate.exists() && candidate.isFile()) {
                log.info("使用本地 ChromeDriver: {}", candidate.getAbsolutePath());
                return candidate;
            }
        }
        return null;
    }

    /**
     * 从 npmmirror 下载与本机 Chrome 匹配的 ChromeDriver
     */
    private static File downloadDriverFromNpmMirror() {
        String chromeVersion = detectChromeVersion();
        if (StrUtil.isBlank(chromeVersion)) {
            log.error("无法检测本机 Chrome 版本，跳过自动下载 ChromeDriver");
            return null;
        }
        log.info("检测到 Chrome 版本: {}，尝试从国内镜像下载 ChromeDriver", chromeVersion);

        File targetDir = new File(System.getProperty("user.dir"), "tmp/chromedriver/" + chromeVersion);
        File targetExe = new File(targetDir, "chromedriver-win64/chromedriver.exe");
        if (targetExe.exists()) {
            return targetExe;
        }
        FileUtil.mkdir(targetDir);

        String zipUrl = NPMMIRROR_CFT_BASE + chromeVersion + "/win64/chromedriver-win64.zip";
        File zipFile = new File(targetDir, "chromedriver-win64.zip");
        try {
            log.info("下载 ChromeDriver: {}", zipUrl);
            long size = HttpUtil.downloadFile(zipUrl, zipFile);
            if (size <= 0 || !zipFile.exists()) {
                // 精确版本不存在时，尝试用主版本号在镜像目录探测不可行，直接失败提示
                log.error("ChromeDriver 下载失败或文件为空: {}", zipUrl);
                return null;
            }
            ZipUtil.unzip(zipFile, targetDir);
            if (targetExe.exists()) {
                return targetExe;
            }
            // 兼容解压后直接落在 targetDir 下的情况
            File flat = FileUtil.loopFiles(targetDir, file ->
                    "chromedriver.exe".equalsIgnoreCase(file.getName())).stream().findFirst().orElse(null);
            if (flat != null) {
                return flat;
            }
            log.error("ChromeDriver 解压后未找到可执行文件, dir={}", targetDir.getAbsolutePath());
            return null;
        } catch (Exception e) {
            log.error("从国内镜像下载 ChromeDriver 失败, version={}, url={}: {}", chromeVersion, zipUrl, e.getMessage(), e);
            return tryDownloadByMajorVersion(chromeVersion, targetDir);
        } finally {
            FileUtil.del(zipFile);
        }
    }

    /**
     * 精确版本失败时，尝试用 major.0.0.0 附近常见构建（读 Chrome 安装目录下最新版本文件夹）
     */
    private static File tryDownloadByMajorVersion(String chromeVersion, File targetDir) {
        String altVersion = resolveInstalledChromeFolderVersion();
        if (StrUtil.isBlank(altVersion) || altVersion.equals(chromeVersion)) {
            return null;
        }
        log.info("尝试备用 Chrome 版本目录下载 ChromeDriver: {}", altVersion);
        String zipUrl = NPMMIRROR_CFT_BASE + altVersion + "/win64/chromedriver-win64.zip";
        File zipFile = new File(targetDir, "chromedriver-alt.zip");
        try {
            HttpUtil.downloadFile(zipUrl, zipFile);
            ZipUtil.unzip(zipFile, targetDir);
            return FileUtil.loopFiles(targetDir, file ->
                    "chromedriver.exe".equalsIgnoreCase(file.getName())).stream().findFirst().orElse(null);
        } catch (Exception e) {
            log.error("备用版本下载失败: {}", e.getMessage());
            return null;
        } finally {
            FileUtil.del(zipFile);
        }
    }

    private static String detectChromeVersion() {
        // 注册表最稳（Win 用户安装路径不一定在 Program Files）
        try {
            String reg = RuntimeUtil.execForStr("reg", "query",
                    "HKEY_CURRENT_USER\\Software\\Google\\Chrome\\BLBeacon", "/v", "version");
            Matcher matcher = VERSION_PATTERN.matcher(StrUtil.blankToDefault(reg, ""));
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            log.warn("通过注册表检测 Chrome 版本失败: {}", e.getMessage());
        }
        String fromFolder = resolveInstalledChromeFolderVersion();
        if (StrUtil.isNotBlank(fromFolder)) {
            return fromFolder;
        }
        try {
            String chromeBinary = resolveChromeBinary();
            if (StrUtil.isNotBlank(chromeBinary)) {
                String output = RuntimeUtil.execForStr(chromeBinary, "--version");
                Matcher matcher = VERSION_PATTERN.matcher(StrUtil.blankToDefault(output, ""));
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        } catch (Exception e) {
            log.warn("通过 chrome --version 检测版本失败: {}", e.getMessage());
        }
        return null;
    }

    private static String resolveInstalledChromeFolderVersion() {
        for (String base : new String[]{
                "C:\\Program Files\\Google\\Chrome\\Application",
                "C:\\Program Files (x86)\\Google\\Chrome\\Application"
        }) {
            File dir = new File(base);
            if (!dir.isDirectory()) {
                continue;
            }
            File[] versionDirs = dir.listFiles(f -> f.isDirectory() && VERSION_PATTERN.matcher(f.getName()).matches());
            if (versionDirs == null || versionDirs.length == 0) {
                continue;
            }
            return Arrays.stream(versionDirs)
                    .map(File::getName)
                    .max(Comparator.naturalOrder())
                    .orElse(null);
        }
        return null;
    }

    private static String resolveChromeBinary() {
        String localAppData = System.getenv("LOCALAPPDATA");
        String[] paths = {
                "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
                "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
                StrUtil.isNotBlank(localAppData)
                        ? localAppData + "\\Google\\Chrome\\Application\\chrome.exe"
                        : null
        };
        for (String path : paths) {
            if (StrUtil.isNotBlank(path) && FileUtil.exist(path)) {
                return path;
            }
        }
        return null;
    }

    private static void saveImage(byte[] imageBytes, String imagePath) {
        try {
            FileUtil.writeBytes(imageBytes, imagePath);
        } catch (Exception e) {
            log.error("保存图片失败: {}", imagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存图片失败");
        }
    }

    private static void compressImage(String originalImagePath, String compressedImagePath) {
        final float COMPRESSION_QUALITY = 0.3f;
        try {
            ImgUtil.compress(
                    FileUtil.file(originalImagePath),
                    FileUtil.file(compressedImagePath),
                    COMPRESSION_QUALITY
            );
        } catch (Exception e) {
            log.error("压缩图片失败: {} -> {}", originalImagePath, compressedImagePath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "压缩图片失败");
        }
    }

    private static void waitForPageLoad(WebDriver driver) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(8));
            wait.until(webDriver ->
                    ((JavascriptExecutor) webDriver).executeScript("return document.readyState")
                            .equals("complete")
            );
            // 短等即可，避免封面截图长时间占用 CPU 影响用户浏览
            Thread.sleep(500);
            log.info("页面加载完成");
        } catch (Exception e) {
            log.error("等待页面加载时出现异常，继续执行截图", e);
        }
    }

    public static String saveWebPageScreenshot(String webUrl, WebDriver webDriver) {
        if (StrUtil.isBlank(webUrl)) {
            log.error("网页URL不能为空");
            return null;
        }
        try {
            String rootPath = System.getProperty("user.dir") + File.separator + "tmp" + File.separator + "screenshots"
                    + File.separator + UUID.randomUUID().toString().substring(0, 8);
            FileUtil.mkdir(rootPath);
            String imageSavePath = rootPath + File.separator + cn.hutool.core.util.RandomUtil.randomNumbers(5) + ".png";
            webDriver.get(webUrl);
            waitForPageLoad(webDriver);
            byte[] screenshotBytes = ((TakesScreenshot) webDriver).getScreenshotAs(OutputType.BYTES);
            saveImage(screenshotBytes, imageSavePath);
            log.info("原始截图保存成功: {}", imageSavePath);
            String compressedImagePath = rootPath + File.separator
                    + cn.hutool.core.util.RandomUtil.randomNumbers(5) + "_compressed.jpg";
            compressImage(imageSavePath, compressedImagePath);
            log.info("压缩图片保存成功: {}", compressedImagePath);
            FileUtil.del(imageSavePath);
            return compressedImagePath;
        } catch (Exception e) {
            log.error("网页截图失败: {}", webUrl, e);
            return null;
        }
    }

    public static String saveWebPageScreen(String webUrl) {
        final int DEFAULT_WIDTH = 1600;
        final int DEFAULT_HEIGHT = 900;
        WebDriver webDriver = initChromeDriver(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        try {
            return saveWebPageScreenshot(webUrl, webDriver);
        } finally {
            try {
                webDriver.quit();
            } catch (Exception e) {
                log.warn("关闭 ChromeDriver 失败: {}", e.getMessage());
            }
        }
    }
}
