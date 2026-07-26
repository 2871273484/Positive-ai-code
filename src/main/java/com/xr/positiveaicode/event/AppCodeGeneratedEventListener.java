package com.xr.positiveaicode.event;

import com.xr.positiveaicode.service.AppCoverService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 代码生成完成后异步生成封面
 */
@Component
@Slf4j
public class AppCodeGeneratedEventListener {

    @Resource
    private AppCoverService appCoverService;

    @EventListener
    public void onAppCodeGenerated(AppCodeGeneratedEvent event) {
        Long appId = event.getAppId();
        if (appId == null) {
            return;
        }
        Thread.startVirtualThread(() -> {
            try {
                // 稍等文件句柄释放 / Vue dist 写完
                Thread.sleep(1500L);
                log.info("收到代码生成完成事件，开始生成封面, appId={}", appId);
                appCoverService.generateCover(appId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("事件驱动生成封面失败, appId={}: {}", appId, e.getMessage(), e);
            }
        });
    }
}
