package com.atguigu.gmall.product.service.impl;

import com.atguigu.gmall.product.service.TestService;
import com.atguigu.gmall.product.service.TestService;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author mqx
 * @date 2020/6/15 15:15
 */
@Service
public class TestServiceImpl implements TestService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;
    // 使用redisson 来完成
    @Override
    public void testLock() {

        String skuId = "30";
        String lockKey = "sku:"+skuId+":lock"; // 可以根据自己的规则定义锁的key

        RLock lock = redissonClient.getLock(lockKey);   // sku:30:lock

        // 上锁
        lock.lock(10,TimeUnit.SECONDS);
        // 业务逻辑代码
        // 获取缓存中的key=num
        String num = redisTemplate.opsForValue().get("num");
        if (StringUtils.isEmpty(num)){
            // 为空就返回！
            return;
        }
        // 将num 转换为int 数据类型
        int number = Integer.parseInt(num);
        // int i = 1/0; 如果执行这个代码，后面的代码是不会走的！那么这个锁就会永远存在！导致资源无法释放
        // 那么则将num进行加1操作！ 放入缓存
        redisTemplate.opsForValue().set("num",String.valueOf(++number));

        // 解锁！
        lock.unlock();
    }

    @Override
    public String readLock() {
        // 获取读写锁对象
        RReadWriteLock readWriteLock = redissonClient.getReadWriteLock("readWriteLock");
        RLock rLock = readWriteLock.readLock();
        // 上锁
        rLock.lock(10,TimeUnit.SECONDS);
        // 表示从缓存中获取数据
        String msg = redisTemplate.opsForValue().get("msg");

        return msg;
    }

    @Override
    public String writeLock() {
        // 向缓存中写入数据
        RReadWriteLock readWriteLock = redissonClient.getReadWriteLock("readWriteLock");
        RLock rLock = readWriteLock.writeLock();
        // 上锁
        rLock.lock(10,TimeUnit.SECONDS);
        // 写数据
        redisTemplate.opsForValue().set("msg",UUID.randomUUID().toString());

        return "写入数据成功------";
    }

    // 获取缓存中key为num 的数据，如果num 不为空，那么则将num进行加1操作！为空就返回！
//    @Override
//    public void testLock() {
//        // set k1 v1 px 10000 nx --- 原生命令 是Jedis 能操作的命令。但是我们现在使用的是redisTemplate，没有直接的set命令。
//        // 相当于nx = setnx("lock","atguigu") 当key不存在的时候才会生效。
//        // Boolean lock = redisTemplate.opsForValue().setIfAbsent("lock", "atguigu");
//        // 相当于set set lock atguigu px 30000 nx 具有了原子性 ，如果中间步骤出现错误的话，会自动释放锁资源！
//        // Boolean lock = redisTemplate.opsForValue().setIfAbsent("lock", "atguigu",3, TimeUnit.SECONDS);
//        // 防止误删锁！给value 设置一个UUID 值
//        String uuid = UUID.randomUUID().toString();
////        Boolean lock = redisTemplate.opsForValue().setIfAbsent("lock", uuid,3, TimeUnit.SECONDS);
//        // 模拟用户访问商品详情，通过skuId 访问 item.gmall.com/30.html
//        String skuId = "30";
//        String lockKey = "sku:"+skuId+":lock"; // 可以根据自己的规则定义锁的key
//        Boolean lock = redisTemplate.opsForValue().setIfAbsent(lockKey, uuid, 3, TimeUnit.SECONDS);
//        // 如果返回的true ，那么说明上述命令执行成功，加锁成功！操作资源
//        if (lock){
//            // 获取缓存中的key=num
//            String num = redisTemplate.opsForValue().get("num");
//            if (StringUtils.isEmpty(num)){
//                // 为空就返回！
//                return;
//            }
//            // 将num 转换为int 数据类型
//            int number = Integer.parseInt(num);
//            // int i = 1/0; 如果执行这个代码，后面的代码是不会走的！那么这个锁就会永远存在！导致资源无法释放
//            // 那么则将num进行加1操作！ 放入缓存
//            redisTemplate.opsForValue().set("num",String.valueOf(++number));
//            // 初始化num 为 0 || set num 0
//            // 操作完成资源之后，应该将锁删除！
//            // 线程进来的时候会生产一个uuid ，上锁的时候会将uuid 放入缓存。
//            //            if (uuid.equals(redisTemplate.opsForValue().get("lock"))){
//            //                redisTemplate.delete("lock");
//            //            }
//
//            // 推荐使用lua 脚本
//            String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
//            // 如何操作：
//            // 构建RedisScript 数据类型需要确定一下，默认情况下返回的Object
//            DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
//            // 指定好返回的数据类型
//            redisScript.setResultType(Long.class);
//            // 指定好lua 脚本
//            redisScript.setScriptText(script);
//            // 第一个参数存储的RedisScript  对象，第二个参数指的锁的key，第三个参数指的key所对应的值
//            redisTemplate.execute(redisScript, Arrays.asList(lockKey),uuid);
//        }else{
//            // 说明上锁没有成功，有人在操作资源 ，外面人只能等待
//            try {
//                Thread.sleep(1000);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//            testLock();
//        }
//    }
}
