package com.xr.positiveaicode.controller;

import com.xr.positiveaicode.common.BaseResponse;
import com.xr.positiveaicode.common.ResultUtils;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
@EnableAspectJAutoProxy(exposeProxy = true)
public class HealthController {

    @GetMapping("/")
    public BaseResponse<String> healthCheck() {
        return ResultUtils.success("ok");
    }
}
