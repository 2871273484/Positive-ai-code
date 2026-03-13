package com.xr.positiveaicode;

import com.xr.positiveaicode.ai.AiCodeGeneratorService;
import com.xr.positiveaicode.ai.model.HtmlCodeResult;
import com.xr.positiveaicode.ai.model.MultiFileCodeResult;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

@SpringBootTest
class PositiveAiCodeApplicationTests {

    @Resource
    private AiCodeGeneratorService aiCodeGeneratorService;

    @Test
    void html() {
        HtmlCodeResult stringFlux = aiCodeGeneratorService.generateHtmlCode("做一个博客，不超过二十行代码");
        Assertions.assertNotNull(stringFlux);
    }

    @Test
    void mutlihtml() {
        MultiFileCodeResult stringFlux = aiCodeGeneratorService.generateMultiFileCode("生成一个留言板，不超过二十行代码");
        Assertions.assertNotNull(stringFlux);
    }


}
