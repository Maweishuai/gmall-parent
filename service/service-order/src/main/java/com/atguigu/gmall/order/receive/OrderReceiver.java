package com.atguigu.gmall.order.receive;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.order.service.OrderService;
import com.atguigu.gmall.payment.client.PaymentFeignClient;
import com.rabbitmq.client.Channel;;
import lombok.SneakyThrows;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import springfox.documentation.spring.web.json.Json;

import java.io.IOException;
import java.util.Map;

/**
 * @author mqx
 * @date 2020/6/29 14:01
 */
@Component
public class OrderReceiver {

    @Autowired
    private OrderService orderService;

    @Autowired
    private RabbitService rabbitService;

    @Autowired
    private PaymentFeignClient paymentFeignClient;

    /**
     * 监听消息时获取订单Id-关闭订单相关
     * @param orderId
     * @param message
     * @param channel
     */
    @SneakyThrows
    @RabbitListener(queues = MqConst.QUEUE_ORDER_CANCEL)
    public void orderCancel(Long orderId, Message message , Channel channel){
        // 判断订单Id 是否为空！
        if (null!=orderId){
            // 为了防止重复消息这个消息。判断订单状态
            // 通过订单Id 来获取订单表中是否有当前记录 select * from orderInfo where id = orderId
            OrderInfo orderInfo = orderService.getById(orderId);
//            OrderInfo orderInfo = orderService.getOrderInfo(orderId);
            // 如果订单状态是未支付
            if (null!= orderInfo && orderInfo.getOrderStatus().equals(ProcessStatus.UNPAID.getOrderStatus().name())){
                // 关闭订单涉及到关闭 orderInfo ,paymentInfo ,aliPay
                // 步骤：先关闭paymentInfo{交易记录} 后关闭orderInfo{订单信息},最后关闭aliPay{支付宝}
                //      因为支付成功之后，异步回调先修改的paymentInfo,然后再发送异步通知修改订单的状态。
//               关闭过期订单
//              orderService.execExpiredOrder(orderId);
                // 订单创建时就是未付款，判断是否有交易记录产生
                // 创建一个paymentInfo{交易记录}
                PaymentInfo paymentInfo = paymentFeignClient.getPaymentInfo(orderInfo.getOutTradeNo());
                // 判断电商交易记录 ,交易记录表中有数据，那么用户一定走到了二维码那一步。
                if (null!=paymentInfo && paymentInfo.getPaymentStatus().equals(ProcessStatus.UNPAID.name())){
                    // 检查支付宝中是否有交易记录，
                    Boolean flag = paymentFeignClient.checkPayment(orderId);
                    if (flag){
                        //如果用户扫了二维码，就会产生交易记录
                        //若有交易记录 就关闭支付宝-- 防止用户在过期时间到的一瞬间付款
                        Boolean result = paymentFeignClient.closePay(orderId);
                        // 判断是否关闭成功
                        if (result){
                            // 用户未付款-关闭支付宝成功
                            // 关闭 OrderInfo 表,paymentInfo 2:表示要关闭交易记录paymentInfo 中有数据
                            orderService.execExpiredOrder(orderId,"2");
                        }else {
                            // 用户已付款-关闭支付宝失败
                            // 关闭支付宝的订单失败，如果用户付款成功了，那么我们调用关闭接口是失败！
                            // 发送消息
                            rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_PAY,MqConst.ROUTING_PAYMENT_PAY,orderId);
                        }
                    }else {
                        //如果用户没有扫了二维码，没有产生交易记录
                        //关闭支付宝的订单成功 关闭 OrderInfo 表,paymentInfo
                        orderService.execExpiredOrder(orderId,"2");
                    }
                }else {
                    // 说明paymentInfo 中根本就没有数据 ，没有数据，那么就只需要关闭orderInfo,
                    orderService.execExpiredOrder(orderId,"1");
                }
            }
        }
        // 手动确认消息已经处理了。
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
    }

    /**
     * 订单支付
     * 1更改订单状态与2通知扣减库存
     * @param orderId
     * @param message
     * @param channel
     * @throws IOException
     */
    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_PAYMENT_PAY,durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_PAYMENT_PAY),
            key = {MqConst.ROUTING_PAYMENT_PAY}
    ))
    public void paySuccess(Long orderId, Message message, Channel channel) throws IOException {
        // 判断orderId 不为空
        if (null!=orderId){
            // 防止重复消费
            OrderInfo orderInfo = orderService.getById(orderId);
            // 判断状态    更新订单的状态和进度的状态
            if (null!= orderInfo &&
                    orderInfo.getOrderStatus().equals(ProcessStatus.UNPAID.getOrderStatus().name())){
                // 1支付成功！修改订单状态为已支付
                orderService.updateOrderStatus(orderId,ProcessStatus.PAID);
                // 2发送消息通知库存，准备减库存
                orderService.sendOrderStatus(orderId);
            }
        }
        // 手动确认
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
    }

    /**
     * 扣减库存成功，更新订单状态
     * @param msgJson
     * @throws IOException
     */
    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_WARE_ORDER, durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_WARE_ORDER),
            key = {MqConst.ROUTING_WARE_ORDER}
    ))
    public void updateOrderStatus(String msgJson,Message message,Channel channel){
        //获取json字符串 判断是否为空
        if (!StringUtils.isEmpty(msgJson)){
            //若不为空，将josn转化为map 获取数据
            Map<String,Object> map = JSON.parseObject(msgJson, Map.class);
            String orderId = (String) map.get("orderId");
            String status = (String) map.get("status");

            //根据status判断减库存的结果：1.状态： ‘DEDUCTED’  (已减库存)
            //                          2.状态： ‘OUT_OF_STOCK’  (库存超卖)
            //如果状态为 DEDUCTED(已减库存)
            if ("DEDUCTED".equals(status)){
                //减库存成功！
                //更新订单状态为 WAITING_DELEVER（已支付，待发货）
                orderService.updateOrderStatus(Long.parseLong(orderId),ProcessStatus.WAITING_DELEVER);
            }else {
                //减库存失败！
                // 解决方案：1.远程调用其他仓库查看是否有库存！补货，补库存。
                //          2.人工客服介入，给你退款{昨天用的功能}
                //更新订单状态为 STOCK_EXCEPTION（库存异常）
                orderService.updateOrderStatus(Long.parseLong(orderId),ProcessStatus.STOCK_EXCEPTION);
            }
            //手动确认
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);

        }
    }
}
