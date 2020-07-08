package com.atguigu.gmall.activity.receiver;

import com.atguigu.gmall.activity.mapper.SeckillGoodsMapper;
import com.atguigu.gmall.activity.service.SeckillGoodsService;
import com.atguigu.gmall.activity.util.DateUtil;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.model.activity.SeckillGoods;
import com.atguigu.gmall.model.activity.UserRecode;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.rabbitmq.client.Channel;
import jdk.nashorn.internal.ir.CallNode;
import lombok.SneakyThrows;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Date;
import java.util.List;


/**
 * 监听消息
 * @create 2020-07-03 16:36
 */
@Component
public class SeckillReceiver {

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private SeckillGoodsService seckillGoodsService;

    /**
     * 秒杀商品数据放入缓存
     * @param message
     * @param channel
     */
    // rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_TASK,MqConst.ROUTING_TASK_1,"");
    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_TASK_1),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_TASK),
            key = {MqConst.ROUTING_TASK_1}
    ))
    public void importItemToRedis(Message message, Channel channel){
        // 准备查询数据的秒杀商品，将数据放入缓存
        // 什么样的商品是秒杀商品？
        // 秒杀商品：查询审核状态1 并且库存数量大于0，当天的商品
        QueryWrapper<SeckillGoods> seckillGoodsQueryWrapper = new QueryWrapper<>();
        //审核状态为1 表示审核通过,剩余库存数应该大于0
        seckillGoodsQueryWrapper.eq("status",1).gt("stock_count",0);
        //查询当天的秒杀商品 start_time为今天, sql 语句中的格式化
        seckillGoodsQueryWrapper.eq("DATE_FORMAT(start_time,'%Y-%m-%d')", DateUtil.formatDate(new Date()));
        //获取秒杀商品的集合
        List<SeckillGoods> seckillGoodsList = seckillGoodsMapper.selectList(seckillGoodsQueryWrapper);
        // 将获取到秒杀商品将其放入缓存
        if (!CollectionUtils.isEmpty(seckillGoodsList)){
            // 循环遍历 得到每一个秒杀商品
            for (SeckillGoods seckillGoods : seckillGoodsList) {
                // 使用hash 数据类型保存商品
                // hset(key,field,value) key = seckill:goods field = skuId value=秒杀商品字符串
                // 获取key
                Boolean flag = redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).hasKey(seckillGoods.getSkuId().toString());
                // 判断缓存中是否有当前key
                if (flag){
                    // 如果flag=true 当前商品已经在缓存中有了, 所以不需要在放入缓存
                    // continue是跳过当前循环，继续下一个循环
                    continue;
                }
                // 如果flag=false 说明这个秒杀商品没有在缓存，所以应该将其放入缓存
                // 商品id为field ，对象为value 放入缓存
                // hset(key,field,value) key = seckill:goods field = skuId value=秒杀商品字符串
                redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).put(seckillGoods.getSkuId().toString(),seckillGoods);

                //如何控制库存超买？ 将秒杀商品的数量放入到redis - list 这个数据类型中！ lpush ,pop 两个方法具有原子性的！
                //根据每一个商品的数量把商品按队列的形式放进redis中
                    // hset(seckill:goods,1,{" skuNum 10"})
                    // hset(seckill:goods,2,{" skuNum 10"})
                for (Integer  i = 0; i < seckillGoods.getStockCount(); i++) {
                    // 放入的数据 lpush key,value
                    // 此时的 key = seckill:stock:skuId
                    // 此时的 value = skuId
                    // 例：skuId=26的秒杀商品有10个 那么存放的格式如下：
                    //      key                value
                    //      seckill:stock:26   26
                    //                         26
                    //                         26
                    //                         26
                    //                         ..
                    redisTemplate.boundListOps(RedisConst.SECKILL_STOCK_PREFIX+seckillGoods.getSkuId())
                            .leftPush(seckillGoods.getSkuId().toString());

                }
                // 消息发布订阅--通知添加与更新状态位，更新商品为秒杀商品
                // channel 表示发送的频道，message 表示发送的内容
                // skuId:1 表示当前这个商品能够秒杀，skuId:0 表示当前商品不能秒杀
                // 商品放入缓存初始化的时候都能秒杀
                // 译为：publish seckillpush skuId:1
                redisTemplate.convertAndSend("seckillpush",seckillGoods.getSkuId()+":1");
            }
            // 手动确认接收消息
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
        }
    }

    /**
     * 秒杀用户加入队列
     *
     * @param message
     * @param channel
     * @throws IOException
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_SECKILL_USER, durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_SECKILL_USER, type = ExchangeTypes.DIRECT, durable = "true"),
            key = {MqConst.ROUTING_SECKILL_USER}
    ))
    public void seckill(UserRecode userRecode, Message message, Channel channel) throws IOException {
        // 判断
        if (null != userRecode) {
            //Log.info("paySuccess:"+ JSONObject.toJSONString(userRecode));
            //预下单
            seckillGoodsService.seckillOrder(userRecode.getSkuId(), userRecode.getUserId());

            //确认收到消息
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        }
    }

    /**
     * 秒杀结束清空缓存
     * @param message
     * @param channel
     * @throws IOException
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_TASK_18, durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_TASK, type = ExchangeTypes.DIRECT, durable = "true"),
            key = {MqConst.ROUTING_TASK_18}
    ))
    public void clearRedis(Message message, Channel channel) throws IOException{
        // 获取活动结束的商品
        QueryWrapper<SeckillGoods> seckillGoodsQueryWrapper = new QueryWrapper<>();
        seckillGoodsQueryWrapper.eq("status",1);
        seckillGoodsQueryWrapper.le("end_time",new Date());
        //查询得到秒杀商品集合
        List<SeckillGoods> seckillGoodsList = seckillGoodsMapper.selectList(seckillGoodsQueryWrapper);

        //清空缓存
        for (SeckillGoods seckillGoods : seckillGoodsList) {
            // 删除商品库存数量
            redisTemplate.delete(RedisConst.SECKILL_STOCK_PREFIX+seckillGoods.getSkuId());
        }
        redisTemplate.delete(RedisConst.SECKILL_GOODS);
        redisTemplate.delete(RedisConst.SECKILL_ORDERS);
        redisTemplate.delete(RedisConst.SECKILL_ORDERS_USERS);
        //将状态更新为结束
        SeckillGoods seckillGoodsUp = new SeckillGoods();
        seckillGoodsUp.setStatus("2");
        seckillGoodsMapper.update(seckillGoodsUp, seckillGoodsQueryWrapper);

        // 手动确认
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);

    }



}
