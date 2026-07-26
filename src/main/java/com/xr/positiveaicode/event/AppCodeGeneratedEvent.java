package com.xr.positiveaicode.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 应用代码生成完成事件（文件已落盘，可截取封面）
 */
@Getter
public class AppCodeGeneratedEvent extends ApplicationEvent {

    private final Long appId;

    public AppCodeGeneratedEvent(Object source, Long appId) {
        super(source);
        this.appId = appId;
    }
}
