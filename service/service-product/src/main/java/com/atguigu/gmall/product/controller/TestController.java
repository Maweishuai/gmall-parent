package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.product.service.TestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * @author Administrator
 * @create 2020-06-16 22:59
 */
public class TestController {

    @Autowired
    private TestService testService;

    @GetMapping("read")
    public Result<String> read(){
        String msg = testService.readLock();

        return Result.ok(msg);
    }

    @GetMapping("write")
    public Result<String> write(){
        String msg = testService.writeLock();

        return Result.ok(msg);
    }
}
