package com.xr.positiveaicode.core.builder;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class VueProjectBuilder {

    /**
     * 同时允许的 npm 构建数量（默认 1，避免多任务叠满内存）
     */
    @Value("${code.vue-build.max-concurrent:1}")
    private int maxConcurrent;

    /**
     * 等待获取构建锁的超时时间（秒）
     */
    @Value("${code.vue-build.acquire-timeout-seconds:120}")
    private long acquireTimeoutSeconds;

    /**
     * Node 堆上限（MB）。小内存服务器务必压低，避免 npm 与 JVM 抢内存触发 OOM Killer。
     */
    @Value("${code.vue-build.node-max-old-space-size-mb:512}")
    private int nodeMaxOldSpaceSizeMb;

    private Semaphore buildLock;

    @PostConstruct
    public void init() {
        int permits = Math.max(1, maxConcurrent);
        this.buildLock = new Semaphore(permits, true);
        log.info("Vue 构建并发锁已初始化: maxConcurrent={}, acquireTimeoutSeconds={}, nodeMaxOldSpaceSizeMb={}",
                permits, acquireTimeoutSeconds, nodeMaxOldSpaceSizeMb);
    }

    /**
     * 异步构建项目（不阻塞主流程）
     *
     * @param projectPath 项目路径
     */
    public void buildProjectAsync(String projectPath) {
        Thread.ofVirtual().name("vue-builder-" + System.currentTimeMillis()).start(() -> {
            try {
                buildProject(projectPath);
            } catch (Exception e) {
                log.error("异步构建 Vue 项目时发生异常: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * 构建 Vue 项目（全局并发锁保护，同一时间最多 max-concurrent 个 npm 任务）
     *
     * @param projectPath 项目根目录路径
     * @return 是否构建成功
     */
    public boolean buildProject(String projectPath) {
        boolean acquired = false;
        try {
            log.info("等待获取 Vue 构建锁: path={}, availablePermits={}",
                    projectPath, buildLock.availablePermits());
            acquired = buildLock.tryAcquire(acquireTimeoutSeconds, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("Vue 构建繁忙，获取锁超时（{}秒），跳过本次构建: {}",
                        acquireTimeoutSeconds, projectPath);
                return false;
            }
            log.info("已获取 Vue 构建锁，开始构建: {}", projectPath);
            return doBuildProject(projectPath);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("等待 Vue 构建锁被中断: {}", projectPath);
            return false;
        } finally {
            if (acquired) {
                buildLock.release();
                log.info("已释放 Vue 构建锁: path={}, availablePermits={}",
                        projectPath, buildLock.availablePermits());
            }
        }
    }

    private boolean doBuildProject(String projectPath) {
        File projectDir = new File(projectPath);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            log.error("项目目录不存在: {}", projectPath);
            return false;
        }
        File packageJson = new File(projectDir, "package.json");
        if (!packageJson.exists()) {
            log.error("package.json 文件不存在: {}", packageJson.getAbsolutePath());
            return false;
        }
        log.info("开始构建 Vue 项目: {}", projectPath);
        if (!executeNpmInstall(projectDir)) {
            log.error("npm install 执行失败（若进程被系统直接杀掉，请检查服务器内存/swap，并查看 dmesg | grep -i oom）");
            return false;
        }
        if (!executeNpmBuild(projectDir)) {
            log.error("npm run build 执行失败");
            return false;
        }
        File distDir = new File(projectDir, "dist");
        if (!distDir.exists()) {
            log.error("构建完成但 dist 目录未生成: {}", distDir.getAbsolutePath());
            return false;
        }
        log.info("Vue 项目构建成功，dist 目录: {}", distDir.getAbsolutePath());
        return true;
    }

    private boolean executeNpmInstall(File projectDir) {
        // 已有完整依赖则跳过，减少重复占内存
        File nodeModules = new File(projectDir, "node_modules");
        String[] installed = nodeModules.isDirectory() ? nodeModules.list() : null;
        if (installed != null && installed.length > 0) {
            log.info("检测到已有 node_modules，跳过 npm install: {}", projectDir.getAbsolutePath());
            return true;
        }
        log.info("执行 npm install（限制 Node 堆约 {}MB）...", nodeMaxOldSpaceSizeMb);
        List<String> command = new ArrayList<>();
        command.add(buildCommand("npm"));
        command.add("install");
        command.add("--no-audit");
        command.add("--no-fund");
        command.add("--progress=false");
        command.add("--prefer-offline");
        return executeCommand(projectDir, command, 300);
    }

    private boolean executeNpmBuild(File projectDir) {
        log.info("执行 npm run build（限制 Node 堆约 {}MB）...", nodeMaxOldSpaceSizeMb);
        List<String> command = new ArrayList<>();
        command.add(buildCommand("npm"));
        command.add("run");
        command.add("build");
        return executeCommand(projectDir, command, 180);
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    private String buildCommand(String baseCommand) {
        if (isWindows()) {
            return baseCommand + ".cmd";
        }
        return baseCommand;
    }

    /**
     * 安全执行外部命令：排空输出避免管道堵死；限制 Node 内存；Linux 下提高子进程 OOM 分数，
     * 内存不足时优先杀 npm 而不是 JVM。
     */
    private boolean executeCommand(File workingDir, List<String> command, int timeoutSeconds) {
        Process process = null;
        try {
            log.info("在目录 {} 中执行命令: {}", workingDir.getAbsolutePath(), String.join(" ", command));
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workingDir);
            pb.redirectErrorStream(true);
            applyBuildEnvironment(pb.environment());

            process = pb.start();
            preferKillChildOnOom(process);

            AtomicReference<String> tailRef = new AtomicReference<>("");
            Process running = process;
            Thread drainer = Thread.ofVirtual().name("npm-out-drainer").start(() ->
                    tailRef.set(drainProcessOutput(running)));

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                log.error("命令执行超时（{}秒），强制终止进程树", timeoutSeconds);
                destroyProcessTree(process);
                drainer.join(2000);
                log.error("超时前输出尾部:\n{}", tailRef.get());
                return false;
            }
            drainer.join(5000);
            int exitCode = process.exitValue();
            if (exitCode == 0) {
                log.info("命令执行成功: {}", String.join(" ", command));
                return true;
            }
            // 137/143 常见于被 SIGKILL/SIGTERM（含 OOM Killer）
            if (exitCode == 137 || exitCode == 143) {
                log.error("命令被系统终止（exit={}），高度疑似内存不足被 OOM Killer 杀掉。"
                                + "请给服务器加 swap/内存，或调低 code.vue-build.node-max-old-space-size-mb。"
                                + "输出尾部:\n{}",
                        exitCode, tailRef.get());
            } else {
                log.error("命令执行失败，退出码: {}，输出尾部:\n{}", exitCode, tailRef.get());
            }
            return false;
        } catch (Exception e) {
            log.error("执行命令失败: {}, 错误信息: {}", String.join(" ", command), e.getMessage(), e);
            if (process != null) {
                destroyProcessTree(process);
            }
            return false;
        }
    }

    private void applyBuildEnvironment(Map<String, String> env) {
        int heapMb = Math.max(256, nodeMaxOldSpaceSizeMb);
        String nodeOpt = "--max-old-space-size=" + heapMb;
        String existing = env.get("NODE_OPTIONS");
        if (existing == null || existing.isBlank()) {
            env.put("NODE_OPTIONS", nodeOpt);
        } else if (!existing.contains("max-old-space-size")) {
            env.put("NODE_OPTIONS", existing.trim() + " " + nodeOpt);
        }
        // 减少 npm 额外开销
        env.put("NPM_CONFIG_FUND", "false");
        env.put("NPM_CONFIG_AUDIT", "false");
        env.put("NPM_CONFIG_PROGRESS", "false");
        env.put("CI", "true");
        // 避免部分环境下打开过多并行网络请求撑爆内存
        env.putIfAbsent("npm_config_maxsockets", "3");
    }

    /**
     * Linux：把子进程标成更易被 OOM 选中的目标，保护后端 JVM。
     */
    private void preferKillChildOnOom(Process process) {
        if (isWindows()) {
            return;
        }
        try {
            long pid = process.pid();
            Path scorePath = Path.of("/proc", String.valueOf(pid), "oom_score_adj");
            if (Files.isWritable(scorePath)) {
                Files.writeString(scorePath, "800");
                log.info("已提升 npm 子进程 OOM 分数: pid={}, oom_score_adj=800", pid);
            }
        } catch (Exception e) {
            log.debug("无法调整子进程 oom_score_adj（可忽略）: {}", e.getMessage());
        }
    }

    private String drainProcessOutput(Process process) {
        StringBuilder tail = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // 只保留尾部，避免日志/内存暴涨
                if (tail.length() > 12000) {
                    tail.delete(0, tail.length() - 8000);
                }
                tail.append(line).append('\n');
                if (line.toLowerCase().contains("error") || line.toLowerCase().contains("killed")) {
                    log.warn("[npm] {}", line);
                }
            }
        } catch (Exception e) {
            log.debug("读取命令输出中断: {}", e.getMessage());
        }
        return tail.toString();
    }

    private void destroyProcessTree(Process process) {
        try {
            process.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);
        } catch (Exception ignored) {
            // ignore
        }
        process.destroyForcibly();
    }
}
