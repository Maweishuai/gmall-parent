package com.atguigu.gmall.product.service;

/**
 * @author Administrator
 * @create 2020-06-16 19:58
 */
public interface TestService {
    // 测试锁
    void testLock();

    String readLock();

    String writeLock();
}
