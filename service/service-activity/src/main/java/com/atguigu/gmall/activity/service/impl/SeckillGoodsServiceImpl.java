package com.atguigu.gmall.activity.service.impl;

import com.alibaba.nacos.client.utils.StringUtils;
import com.atguigu.gmall.activity.mapper.SeckillGoodsMapper;
import com.atguigu.gmall.activity.service.SeckillGoodsService;
import com.atguigu.gmall.activity.util.CacheHelper;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import com.atguigu.gmall.common.util.MD5;
import com.atguigu.gmall.model.activity.OrderRecode;
import com.atguigu.gmall.model.activity.SeckillGoods;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Administrator
 * @create 2020-07-04 2:40
 */
@Service
public class SeckillGoodsServiceImpl implements SeckillGoodsService {

    // 因为秒杀商品在凌晨会将数据加载到缓存中，所以此处查询缓存即可
    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    /**
     * 返回全部列表
     * @return
     */
    @Override
    public List<SeckillGoods> findAll() {
        // 商品保存到缓存redis-Hash
        // redis - hash 通过key找value。
        List<SeckillGoods> seckillGoodsList = redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).values();

        return seckillGoodsList;
    }

    /**
     * 根据ID获取实体
     * @param skuId
     * @return
     */
    @Override
    public SeckillGoods getSeckillGoods(Long skuId) {
        // 根据skuId 查询秒杀对象信息 
        // redis - hash 通过key找value。
        SeckillGoods seckillGoods  = (SeckillGoods) redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).get(skuId.toString());
        return seckillGoods;
    }

    /**
     * 创建秒杀订单
     * 根据用户和商品ID实现秒杀下单
     * @param skuId
     * @param userId
     */
    @Override
    public void seckillOrder(Long skuId, String userId) {
        //获取状态位
        String state = (String) CacheHelper.get(skuId.toString());
        //判断商品是可以下单还是售罄
        if ("0".equals(state)){
            return;
        }

        // 判断用户是否已经下单 ，如何防止用户重复下单 setnx
        // 如果用户下单成功，我们会将用户下单信息放入缓存
        // key = seckill:user:userId
        // value = skuId
        //从缓存中获取用户信息,看是否存在
        Boolean isExist = redisTemplate.opsForValue().setIfAbsent(RedisConst.SECKILL_USER + userId, skuId, RedisConst.SECKILL__TIMEOUT, TimeUnit.SECONDS);
        // isExist =false ：表示用户已经存在，说明用户第二次下单
        if (!isExist) {
            return;
        }
        // isExist = true 表示用户在缓存中没有存在！说明用户第一次下单

        //  获取队列中的商品，如果能够获取，则商品存在，有库存，可以下单
        //  seckill:stock:skuId
        // 存储商品库存的时候使用 list - leftPush
        String goodsId = (String) redisTemplate.boundListOps(RedisConst.SECKILL_STOCK_PREFIX + skuId).rightPop();
        if (StringUtils.isEmpty(goodsId)) {
            // 商品售罄，更新状态位
            // 通知其他兄弟节点，更新状态位
            redisTemplate.convertAndSend("seckillpush", skuId + ":0");
            // 商品售罄,不能下单
            return;
        }
        // 如果goodsId不为空！说明有库存！
        // 然后我们需要将信息记录起来。OrderRecode{有关于订单的信息}
        OrderRecode orderRecode = new OrderRecode();
        orderRecode.setUserId(userId);
        orderRecode.setSeckillGoods(this.getSeckillGoods(skuId));
        orderRecode.setNum(1);
        //生成下单码{自己定义}
        orderRecode.setOrderStr(MD5.encrypt(userId+skuId));

        // 将预下单的数据放入缓存！
        // hset(key,field,value)
        // key = seckill:orders field=userId value=orderRecode
        redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).put(orderRecode.getUserId(),orderRecode);

        // 更新库存
        this.updateStockCount(orderRecode.getSeckillGoods().getSkuId());
    }

    /**
     * 查看用户秒杀状态
     * 根据商品id与用户ID查看订单信息
     * @param skuId
     * @param userId
     * @return
     */
    @Override
    public Result checkOrder(Long skuId, String userId) {
        // 用户在缓存中存在，有机会秒杀到商品
        Boolean isExist = redisTemplate.hasKey(RedisConst.SECKILL_USER + userId);
        if (isExist){
            //说明存在，可以秒杀
            // 判断用户是否预下单！
            // redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).put(orderRecode.getUserId(),orderRecode);
            // 查看用户是否有订单生成，正在排队
            Boolean flag = redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).hasKey(userId);
            if (flag){
                // 说明抢单成功！
                OrderRecode orderRecode = (OrderRecode) redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).get(userId);
                // 返回对应的code码！秒杀成功！
                return Result.build(orderRecode, ResultCodeEnum.SECKILL_SUCCESS);
            }
        }
        // 判断是否真正意义的下单
        // 下单成功的话，我们也需要将数据存储在缓存中！
        // key=seckill:orders:users field=userId value=orderId
        boolean isExistOrder = redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS_USERS).hasKey(userId);
        if(isExistOrder) {
            // 获取下单成功的数据
            String orderId = (String)redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS_USERS).get(userId);
            // 表示下单成功
            return Result.build(orderId, ResultCodeEnum.SECKILL_ORDER_SUCCESS);
        }
        // 判断我们的商品对应的状态位，
        String state = (String) CacheHelper.get(skuId.toString());
        // 0 说明售罄
        if ("0".equals(state)){
            //已售罄 抢单失败
            return  Result.build(null, ResultCodeEnum.SECKILL_FAIL);
        }
        // 有库存 正在排队中
        return Result.build(null, ResultCodeEnum.SECKILL_RUN);
    }

    /**
     * 提出方法--更新库存
     * @param skuId
     */
    private void updateStockCount(Long skuId) {
        //更新库存，批量更新，用于页面显示，以实际扣减库存为准
        // 库存存储在redis-list 中，还有数据库中一份
        // redis - list 中的库存数需要更新？ 不需要
        // 数据库中需要更新么？ 需要 数据库更新需要根据缓存的数据
        Long stockCount = redisTemplate.boundListOps(RedisConst.SECKILL_STOCK_PREFIX + skuId).size();
        // 为了避免频繁更新数据库，是2的倍数时，则更新一次数据库
        if (stockCount % 2 == 0){
            //商品售罄,更新数据库
            SeckillGoods seckillGoods = getSeckillGoods(skuId);
            seckillGoods.setStockCount(stockCount.intValue());
            seckillGoodsMapper.updateById(seckillGoods);
            //商品售罄,更新缓存
            redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).put(seckillGoods.getSkuId().toString(), seckillGoods);
        }
    }
}
