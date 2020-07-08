package com.atguigu.gmall.activity.controller;

import com.alibaba.nacos.client.utils.StringUtils;
import com.atguigu.gmall.activity.service.SeckillGoodsService;
import com.atguigu.gmall.activity.util.CacheHelper;
import com.atguigu.gmall.activity.util.DateUtil;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.common.util.AuthContextHolder;
import com.atguigu.gmall.common.util.MD5;
import com.atguigu.gmall.model.activity.OrderRecode;
import com.atguigu.gmall.model.activity.SeckillGoods;
import com.atguigu.gmall.model.activity.UserRecode;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.user.UserAddress;
import com.atguigu.gmall.order.client.OrderFeignClient;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.atguigu.gmall.user.client.UserFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * @author Administrator
 * @create 2020-07-04 2:48
 */
@RestController
@RequestMapping("/api/activity/seckill")
public class SeckillGoodsController {

    @Autowired
    private SeckillGoodsService seckillGoodsService;

    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private RabbitService rabbitService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private OrderFeignClient orderFeignClient;

    /**
     * 返回全部列表
     *
     * @return
     */
    @GetMapping("/findAll")
    public Result findAll() {
        List<SeckillGoods> seckillGoodsList = seckillGoodsService.findAll();
        return Result.ok(seckillGoodsList);
    }

    /**
     * 获取实体
     *
     * @param skuId
     * @return
     */
    @GetMapping("/getSeckillGoods/{skuId}")
    public Result getSeckillGoods(@PathVariable Long skuId) {
        SeckillGoods seckillGoods = seckillGoodsService.getSeckillGoods(skuId);
        return Result.ok(seckillGoods);
    }

    /**
     * 获取下单码
     *
     * @param skuId
     * @return
     */
    @GetMapping("auth/getSeckillSkuIdStr/{skuId}")
    public Result getSeckillSkuIdStr(@PathVariable Long skuId, HttpServletRequest request) {
        // 怎么生成下单码 :使用用户Id 来做MD5加密。加密之后的这个字符串就是下单码
        // 用户用户Id
        String userId = AuthContextHolder.getUserId(request);
        // 通过当前skuId查询到当前秒杀商品这个对象，
        // 看当前的这个商品是否正在秒杀，如果正在秒杀，则获取下单码，否则不能获取！
        SeckillGoods seckillGoods = seckillGoodsService.getSeckillGoods(skuId);
        if (null != seckillGoods) {
            // 判断当前商品是否正在参与秒杀 ，可以通过时间判断
            Date curTime = new Date();
            // 判断当前系统时间是否在秒杀时间范围内
            if (DateUtil.dateCompare(seckillGoods.getStartTime(), curTime) &&
                    DateUtil.dateCompare(curTime, seckillGoods.getEndTime())) {
                //可以动态生成下单码，放在redis缓存
                String skuIdStr = MD5.encrypt(userId);
                return Result.ok(skuIdStr);
            }
        }
        return Result.fail().message("获取下单码失败");
    }


    /**
     * 根据用户和商品ID实现秒杀下单
     *
     * @param skuId
     * @return
     */
    @PostMapping("auth/seckillOrder/{skuId}")
    public Result seckillOrder(@PathVariable Long skuId, HttpServletRequest request) throws Exception {
        // 获取userId 用来验证下单码
        String userId = AuthContextHolder.getUserId(request);
        // 获取下单码
        String skuIdStr = request.getParameter("skuIdStr");
        if (!skuIdStr.equals(MD5.encrypt(userId))) {
            // 下单码没有验证通过，请求不合法
            return Result.build(null, ResultCodeEnum.SECKILL_ILLEGAL);
        }
        // 验证状态位 获取秒杀商品所对应的状态位
        // 产品标识， 1：可以秒杀 0：秒杀结束
        // CacheHelper 本质就是HashMap map.put(key,value)  split[0] =skuId split[1] =状态位
        String state = (String) CacheHelper.get(skuId.toString());
        if (StringUtils.isEmpty(state)) {
            // 请求不合法
            return Result.build(null, ResultCodeEnum.SECKILL_ILLEGAL);
        }
        // 表示能够下单。
        if ("1".equals(state)) {
            // 记录用户 秒杀的哪个商品！
            UserRecode userRecode = new UserRecode();
            userRecode.setUserId(userId);
            userRecode.setSkuId(skuId);

            // 将信息放到消息队列
            rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_SECKILL_USER, MqConst.ROUTING_SECKILL_USER, userRecode);
        } else {
            // 请求不合法 0 表示没有商品了,//已售罄
            return Result.build(null, ResultCodeEnum.SECKILL_ILLEGAL);
        }
        return Result.ok();
    }

    /**
     * 查询秒杀状态
     *
     * @return
     */
    @GetMapping(value = "auth/checkOrder/{skuId}")
    public Result checkOrder(@PathVariable Long skuId, HttpServletRequest request) {
        //当前登录用户
        String userId = AuthContextHolder.getUserId(request);
        //通过检查订单方法 查看秒杀状态
        Result result = seckillGoodsService.checkOrder(skuId, userId);
        return result;
    }

    /**
     * 秒杀确认订单
     *
     * @param request
     * @return
     */
    @GetMapping("auth/trade")
    public Result trade(HttpServletRequest request) {
        // 获取到用户Id
        String userId = AuthContextHolder.getUserId(request);
        // 先得到用户想要购买的商品
        OrderRecode orderRecode = (OrderRecode) redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).get(userId);
        if (null == orderRecode) {
            return Result.fail().message("非法操作,下单失败!");
        }
        // 获取用户秒杀的商品
        SeckillGoods seckillGoods = orderRecode.getSeckillGoods();
        //获取用户地址
        List<UserAddress> userAddressList = userFeignClient.findUserAddressListByUserId(userId);
        // 声明一个集合来存储订单明细
        List<OrderDetail> orderDetailList = new ArrayList<>();
        OrderDetail orderDetail = new OrderDetail();
        orderDetail.setSkuId(seckillGoods.getSkuId());
        orderDetail.setSkuName(seckillGoods.getSkuName());
        orderDetail.setImgUrl(seckillGoods.getSkuDefaultImg());
        // 秒杀商品的数量
        orderDetail.setSkuNum(orderRecode.getNum());
        // 数据库定义的秒杀价格
        orderDetail.setOrderPrice(seckillGoods.getCostPrice());
        // 添加到集合
        orderDetailList.add(orderDetail);

        // 计算总金额
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderDetailList(orderDetailList);
        orderInfo.sumTotalAmount();

        // 声明一个map 集合将数据分别存储起来！
        // 因为trade.html 订单页面需要这些key。
        Map<String, Object> result = new HashMap<>();
        // 存储收货地址列表
        result.put("userAddressList", userAddressList);
        // 存储订单明细列表
        result.put("detailArrayList", orderDetailList);
        // 总金额
        result.put("totalAmount", orderInfo.getTotalAmount());

        return Result.ok(result);
    }

    /**
     * 秒杀提交订单
     *
     * @param orderInfo
     * @return
     */
    @PostMapping("auth/submitOrder")
    public Result submitOrder(@RequestBody OrderInfo orderInfo, HttpServletRequest request) {
        //获取userId
        String userId = AuthContextHolder.getUserId(request);
        //赋值
        orderInfo.setUserId(Long.parseLong(userId));
        //在缓存中获取预订单信息
        OrderRecode orderRecode = (OrderRecode) redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).get(userId);
        if (null == orderRecode) {
            return Result.fail().message("非法操作");
        }

        //调用提交订单的方法,获取真正意义下单的orderId
        Long orderId = orderFeignClient.submitOrder(orderInfo);
        if (null == orderId) {
            //为空说明下订单失败
            return Result.fail().message("下单失败，请重新操作");
        }
        //不为空说明下订单成功，需要删除预下单在缓存中的记录
        redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).delete(userId);
        //然后保存真正下订单成功的订单记录到缓存
        redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS_USERS).put(userId, orderId.toString());

        // 返回数据
        return Result.ok(orderId);
    }
}
