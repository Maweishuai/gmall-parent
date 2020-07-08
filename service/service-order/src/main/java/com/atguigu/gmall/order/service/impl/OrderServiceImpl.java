package com.atguigu.gmall.order.service.impl;


import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.common.util.HttpClientUtil;
import com.atguigu.gmall.model.enums.OrderStatus;
import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.order.mapper.OrderDetailMapper;
import com.atguigu.gmall.order.mapper.OrderInfoMapper;
import com.atguigu.gmall.order.service.OrderService;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;

/**
 * @author Administrator
 * @create 2020-06-28 20:07
 */
@Service
public class OrderServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderService {

    @Autowired
    private OrderInfoMapper orderInfoMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RabbitService rabbitService;

    @Value("${ware.url}")
    private String WARE_URL;

    /**
     * 保存订单
     * @param orderInfo
     * @return
     */
    @Override
    public Long saveOrderInfo(OrderInfo orderInfo) {

        // 检查页面提交过来的数据，与数据库表中的字段是否完全吻合！
        // 提交数据的时候，缺少的字段数据
        // 补缺字段：
        // 总金额，订单状态，用户Id，订单的交易编号，订单描述，创建时间，过期时间，进度的状态，
        // 以下先不写：
        // 物流单号{物流系统} ，父订单Id{系统发生拆单的时候}，图片路径{可以忽略}
        
        // 获取计算总金额
        orderInfo.sumTotalAmount();
        // 赋值订单状态，初始化时都是未支付
        orderInfo.setOrderStatus(OrderStatus.UNPAID.name());
        // 用户Id 可以在控制器中获取！暂时先不获取！
        // 订单的交易编号 {对接支付的} 确保不能重复就行了。
        String outTradeNo="Mwsuai"+System.currentTimeMillis()+""+new Random().nextInt(1000);
        orderInfo.setOutTradeNo(outTradeNo);
        // 订单描述 简单的写，就行 主要为了测试
        // orderInfo.setTradeBody("给我们每个人买礼物");
        // 根据订单明细的中的商品名称进行拼接
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        StringBuffer tradeBody  = new StringBuffer();
        for (OrderDetail orderDetail : orderDetailList) {
            tradeBody.append(orderDetail.getSkuName()+" ");
        }
        if (tradeBody.toString().length()>100){
            orderInfo.setTradeBody(tradeBody.toString().substring(0,100));
        }else {
            orderInfo.setTradeBody(tradeBody.toString());
        }
        // 创建时间
        orderInfo.setCreateTime(new Date());
        // 过期时间 默认一天时间
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE,1);
        orderInfo.setExpireTime(calendar.getTime());
        //进度的状态 初始化时都是未支付
        orderInfo.setProcessStatus(ProcessStatus.UNPAID.name());

        //保存订单表orderInfo
        orderInfoMapper.insert(orderInfo);
        //保存订单明细表orderDetail
        if (!CollectionUtils.isEmpty(orderDetailList)){
            for (OrderDetail orderDetail : orderDetailList) {
                orderDetail.setOrderId(orderInfo.getId());
                orderDetailMapper.insert(orderDetail);
            }
        }

        //发送延迟队列，如果定时未支付，就根据订单Id 取消订单。
        rabbitService.sendDelayMessage(
                MqConst.EXCHANGE_DIRECT_ORDER_CANCEL,
                MqConst.ROUTING_ORDER_CANCEL,
                orderInfo.getId(),
                MqConst.DELAY_TIME);
        return orderInfo.getId();
    }

    /**
     * 生产流水号
     * @param userId 目的是用userId 在缓存中充当key保存流水号
     * @return
     */
    @Override
    public String getTradeNo(String userId) {
        // 定义key
        String tradeNoKey = "user:" + userId + ":tradeCode";
        // 定义一个流水号
        String tradeNo = UUID.randomUUID().toString();
        //将流水号放入缓存
        redisTemplate.opsForValue().set(tradeNoKey, tradeNo);
        return tradeNo;
    }

    /**
     * 比较流水号
     * @param userId 获取缓存中的流水号的key
     * @param tradeCodeNo   页面传递过来的流水号
     * @return
     */
    @Override
    public boolean checkTradeCode(String userId, String tradeCodeNo) {
        // 定义key
        String tradeNoKey = "user:" + userId + ":tradeCode";
        // 获取到缓存流水号
        String redisTradeNo = (String) redisTemplate.opsForValue().get(tradeNoKey);
        return tradeCodeNo.equals(redisTradeNo);
    }

    /**
     * 删除流水号
     * @param userId
     */
    @Override
    public void deleteTradeNo(String userId) {
        // 定义key
        String tradeNoKey = "user:" + userId + ":tradeCode";
        // 删除数据
        redisTemplate.delete(tradeNoKey);
    }

    /**
     * 验证库存
     * @param skuId
     * @param skuNum
     * @return
     */
    @Override
    public boolean checkStock(Long skuId, Integer skuNum) {
        // 远程调用http://localhost:9001/hasStock?skuId=10221&num=2
        String result = HttpClientUtil
                .doGet(WARE_URL + "/hasStock?skuId=" + skuId + "&num=" + skuNum);
        return "1".equals(result);
    }

    /**
     * 处理关闭过期订单
     * @param orderId
     */
    @Override
    public void execExpiredOrder(Long orderId) {
        // 更新状态 orderInfo
        updateOrderStatus(orderId, ProcessStatus.CLOSED);
        // paymentInfo
        //paymentFeignClient.closePayment(orderId);
        // 发送消息关闭交易
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_CLOSE, MqConst.ROUTING_PAYMENT_CLOSE, orderId);
    }

    /**
     * 根据订单Id 修改订单的状态
     * @param orderId
     * @param processStatus
     */
    @Override
    public void updateOrderStatus(Long orderId, ProcessStatus processStatus) {
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setId(orderId);
        orderInfo.setProcessStatus(processStatus.name());
        orderInfo.setOrderStatus(processStatus.getOrderStatus().name());
        orderInfoMapper.updateById(orderInfo);
    }

    /**
     * 根据订单Id 查询订单对象
     * @param orderId
     * @return
     */
    @Override
    public OrderInfo getOrderInfo(Long orderId) {
        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        // 查询订单明细
        QueryWrapper<OrderDetail> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("order_id", orderId);
        List<OrderDetail> orderDetailList = orderDetailMapper.selectList(queryWrapper);
        //注意：一定要根据orderId取得到orderDetail
        orderInfo.setOrderDetailList(orderDetailList);
        // 返回数据
        return orderInfo;
    }

    /**
     * 发送消息通知库存，减库存！
     * @param orderId
     */
    @Override
    public void sendOrderStatus(Long orderId) {
        // 更改订单的状态，变成通知仓库准备发货
        updateOrderStatus(orderId,ProcessStatus.NOTIFIED_WARE);
        // 需要参考库存管理文档 根据管理手册。
        // 发送的数据 是 orderInfo 中的部分属性数据，并非全部属性数据！
        // 获取发送的字符串：
        String wareJson = initWareOrder(orderId);
        // 准备发送消息
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_WARE_STOCK,MqConst.ROUTING_WARE_STOCK,wareJson);
    }

    /**
     * 提出方法--根据orderId 获取json 字符串
     * @param orderId
     * @return
     */
    private String initWareOrder(Long orderId) {
        // 首先查询到orderInfo
        OrderInfo orderInfo = getOrderInfo(orderId);
        // 将orderInfo 中的部分属性，放入一个map 集合中。
        Map map = initWareOrder(orderInfo);
        // 返回json 字符串
        return JSON.toJSONString(map);
    }

    /**
     * 将orderInfo中部分数据转换为Map
     * @param orderInfo
     * @return
     */
    @Override
    public Map initWareOrder(OrderInfo orderInfo) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("orderId",orderInfo.getId());
        map.put("consignee", orderInfo.getConsignee());
        map.put("consigneeTel", orderInfo.getConsigneeTel());
        map.put("orderComment", orderInfo.getOrderComment());
        map.put("orderBody", orderInfo.getTradeBody());
        map.put("deliveryAddress", orderInfo.getDeliveryAddress());
        map.put("paymentWay", "2");
        // map.put("wareId", orderInfo.getWareId());// 仓库Id ，减库存拆单时需要使用！
        /*
            details 对应的是订单明细
            details:[{skuId:101,skuNum:1,skuName:’小米手64G’},
                       {skuId:201,skuNum:1,skuName:’索尼耳机’}]
         */
        // 声明一个list 集合 来存储map
        List<Map> maps = new ArrayList<>();
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        for (OrderDetail orderDetail : orderDetailList) {
            // 先声明一个map 集合
            HashMap<String, Object> orderDetailMap = new HashMap<>();
            orderDetailMap.put("skuId",orderDetail.getSkuId());
            orderDetailMap.put("skuNum",orderDetail.getSkuNum());
            orderDetailMap.put("skuName",orderDetail.getSkuName());
            maps.add(orderDetailMap);
        }
//        map.put("details", JSON.toJSONString(maps)); 这里不需要转换，库存接受时再转换
        map.put("details",maps);
        // 返回构成好的map集合。
        return map;
    }

    /**
     * 拆单方法
     * @param orderId
     * @param wareSkuMap
     * @return
     */
    @Override
    @Transactional
    public List<OrderInfo> orderSplit(Long orderId, String wareSkuMap) {
    /*
    1.  先获取到原始订单 124
    2.  将wareSkuMap 转换为java可以识别的对象
        [{"wareId":"1","skuIds":["2","10"]},{"wareId":"2","skuIds":["3"]}]
        方案一：class Param{
                    private String wareId;
                    private List<String> skuIds;
                }
        方案二{推荐}：看做一个Map mpa.put("wareId",value); map.put("skuIds",value)

    3.  创建一个新的子订单 125 126
    4.  给子订单赋值
    5.  保存子订单到数据库
    6.  更新原始订单的状态为split
    7.  测试
    */

        // 创建一个存储子订单的集合
        List<OrderInfo> subOrderInfoList = new ArrayList<>();
        //1.  先获取到原始订单 124
        OrderInfo orderInfoOrigin  = getOrderInfo(orderId);
        //2.  将wareSkuMap 转换为java可以识别的对象 Map集合
        List<Map> mapList = JSON.parseArray(wareSkuMap, Map.class);
        //3.  创建一个新的子订单 125 126
        // 子订单根据什么来创建?
        // 根据wareSkuMap中是否有 wareId和skuIds来判断
        if (null!=mapList){

            for (Map map : mapList) {
                // 获取map 中的仓库Id wareId
                String wareId = (String) map.get("wareId");
                // 获取仓库Id所对应的商品Id的集合
                List<String> skuIdList = (List<String>) map.get("skuIds");
                //4.  给子订单赋值
                // 声明一个子订单对象
                OrderInfo subOrderInfo = new OrderInfo();
                // 属性拷贝--将原始订单的基本数据，都可以给子订单使用
                BeanUtils.copyProperties(orderInfoOrigin,subOrderInfo);
                // 防止主键冲突 注意：id不能拷贝，发生主键冲突
                subOrderInfo.setId(null);
                // 自己创建主键Id
                subOrderInfo.setParentOrderId(orderId);
                // 赋值仓库Id
                subOrderInfo.setWareId(wareId);
                // 因为子订单和原始订单的总金额是不同的，所以要计算新的子订单金额
                // 在订单的实体类中有sumTotalAmount() 方法。
                // 计算子订单的金额: 必须有订单明细

                // 获取到子订单明细
                // 声明一个集合来存储子订单明细
                List<OrderDetail> orderDetails = new ArrayList<>();
                // 需要将子订单的名单明细准备好,添加到子订单中
                // 子订单明细应该来自于原始订单明细。
                List<OrderDetail> orderDetailList = orderInfoOrigin.getOrderDetailList();
                if (!CollectionUtils.isEmpty(orderDetailList)){
                    // 遍历原始的订单明细
                    for (OrderDetail orderDetail : orderDetailList) {
                        // 再去遍历仓库中所对应的商品Id
                        for (String skuId : skuIdList) {
                            // 比较两个商品skuId
                            if (Long.parseLong(skuId)==orderDetail.getSkuId()){
                                //如果相同，则这个商品就是子订单明细需要的商品
                                orderDetails.add(orderDetail);
                            }
                        }
                    }
                }
                // 需要将子订单的名单明细准备好,添加到子订单中
                subOrderInfo.setOrderDetailList(orderDetails);
                // 获取到总金额
                subOrderInfo.sumTotalAmount();
                //5.  保存子订单到数据库
                saveOrderInfo(subOrderInfo);
                // 将新的子订单放入集合中
                subOrderInfoList.add(subOrderInfo);
            }
        }
        //6.  更新原始订单的状态为split
        updateOrderStatus(orderId,ProcessStatus.SPLIT);

        return subOrderInfoList;
    }

    @Override
    public void execExpiredOrder(Long orderId, String flag) {
        // 调用方法更新订单状态状态
        updateOrderStatus(orderId,ProcessStatus.CLOSED);
        if ("2".equals(flag)){
            // 发送消息队列，关闭支付宝的交易记录。
            rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_CLOSE,MqConst.ROUTING_PAYMENT_CLOSE,orderId);
        }
    }
}
