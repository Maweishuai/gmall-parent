package com.atguigu.gmall.common.cache;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.cache.GmallCache;
import com.atguigu.gmall.common.constant.RedisConst;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * @author mqx
 * @date 2020/6/16 16:19
 */
@Component
@Aspect// 面向切面工作
public class GmallCacheAspect {

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    // 编写一个环绕通知
    @Around("@annotation(com.atguigu.gmall.common.cache.GmallCache)")
    public Object cacheAroundAdvice(ProceedingJoinPoint point) throws Throwable {
        // BigDecimal getSkuPriceBySkuId(Long skuId); 返回 BigDecimal
        // List<SpuSaleAttr> getSpuSaleAttrListCheckBySku(Long skuId, Long spuId) 返回的是集合
        Object result = null;
        // 获取到传递的参数 方法上的参数
        Object[] args = point.getArgs();

        // 获取方法上的注解
        MethodSignature signature = (MethodSignature) point.getSignature();
        GmallCache gmallCache = signature.getMethod().getAnnotation(GmallCache.class);
        // 获取注解上的prefix
        String prefix = gmallCache.prefix();
        // 定义一个key key=sku[30] SkuInfo getSkuInfo(Long skuId) skuId =30
        String key = prefix+ Arrays.asList(args).toString();

        // 需要将数据存储到缓存中，key=prefix+ Arrays.asList(args).toString();
        // 值getSkuInfo() 方法中执行的放回值数据nn
        /*
        1.  先判断缓存中是否有数据
        2.  缓存有，从缓存中获取
        3.  缓存中没有从数据库中获取并放入缓存

         */
        // 表示根据key 获取缓存中返回的数据
        result = cacheHit(signature,key);

        // 判断缓存中是否获取到了数据
        if (result!=null){
            return result;
        }
        // 如果获取到的数据是空，那么就应该走数据库，并放入缓存 ，自定义一个锁：
        RLock lock = redissonClient.getLock(key+":lock");
        try {
            boolean res = lock.tryLock(RedisConst.SKULOCK_EXPIRE_PX2, RedisConst.SKULOCK_EXPIRE_PX1, TimeUnit.SECONDS);
            try {
                // 返回true 说明上锁成功
                if (res){
                    // 查询数据中数据 相当于执行 getSkuInfo方法并能够得到返回值
                    // 根据注解在哪，如果注解在getSkuInfo(Long skuId) skuId =30
                    // point.getArgs() 相当于获取方法体上的参数。30
                    // point.proceed(point.getArgs()); 表执行带有@GmallCache 的方法体
                    result = point.proceed(point.getArgs());

                    // 判断result 返回的数据是否为空！
                    if (result==null){
                        // 说明在数据库中根本没有这个数据 防止缓穿透的！
                        Object o = new Object();
                        // 为什么这里需要转换成字符串 o 表示祖先，
                        redisTemplate.opsForValue().set(key, JSON.toJSONString(o),RedisConst.SKUKEY_TEMPORARY_TIMEOUT,TimeUnit.SECONDS);
                        // 返回数据
                        return  o;
                    }
                    // 查询出来的数据不是空
                    redisTemplate.opsForValue().set(key,JSON.toJSONString(result),RedisConst.SKUKEY_TIMEOUT,TimeUnit.SECONDS);
                    // 返回数据
                    return  result;
                }else {
                    // 其他线程睡眠
                    Thread.sleep(1000);
                    // 继续获取数据
                    return cacheHit(signature,key);
                }
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            } finally {
                lock.unlock();
            }
        }catch (Exception e){
            e.printStackTrace();
        }

        return result;
    }

    // 这个表示获取缓存中的数据
    private Object cacheHit(MethodSignature signature, String key) {
        // 根据key 获取缓存数据
        // BigDecimal getSkuPriceBySkuId(Long skuId); 返回 BigDecimal
        // List<SpuSaleAttr> getSpuSaleAttrListCheckBySku(Long skuId, Long spuId) 返回的是集合
        // 放入缓存时就是字符串，所以获取出来也需要是字符串
        String object = (String) redisTemplate.opsForValue().get(key);
        // 此时获取返回值应该明确了！
        if (!StringUtils.isEmpty(object)){
            // 表示缓存中有数据，并获取返回值数据类型
            Class returnType = signature.getReturnType();
            // 返回数据
            return JSON.parseObject(object,returnType);
        }

        return null;
    }
}
