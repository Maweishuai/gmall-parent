package com.atguigu.gmall.order.service;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderInfo;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * @author Administrator
 * @create 2020-06-28 20:05
 */
public interface OrderService extends IService<OrderInfo>{

    /**
     * 保存订单
     * @param orderInfo
     * @return
     */
    Long saveOrderInfo(OrderInfo orderInfo);

    /**
     * 生产流水号
     * @param userId 目的是用userId 在缓存中充当key保存流水号
     * @return
     */
    String getTradeNo(String userId);

    /**
     * 比较流水号
     * @param userId 获取缓存中的流水号
     * @param tradeCodeNo 页面传递过来的流水号
     * @return
     */
    boolean checkTradeCode(String userId, String tradeCodeNo);

    /**
     * 删除流水号
     * @param userId
     */
    void deleteTradeNo(String userId);

    /**
     * 验证库存
     * @param skuId
     * @param skuNum
     * @return
     */
    boolean checkStock(Long skuId, Integer skuNum);

    /**
     * 处理关闭过期订单
     * @param orderId
     */
    void execExpiredOrder(Long orderId);

    /**
     * 根据订单Id 修改订单的状态
     * @param orderId
     * @param processStatus
     */
    void updateOrderStatus(Long orderId, ProcessStatus processStatus);

    /**
     * 根据订单Id 查询订单对象
     * @param orderId
     * @return
     *  OrderInfo orderInfo = orderService.getById(orderId); 只能单独查询OrderInfo、
     *  getOrderInfo 这个方法可以在里面查询订单明细
     */
    OrderInfo getOrderInfo(Long orderId);

    /**
     * 发送消息通知库存，减库存！
     * @param orderId
     */
    void sendOrderStatus(Long orderId);

    /**
     * 将orderInfo中部分数据转换为Map
     * @param orderInfo
     * @return
     */
    Map initWareOrder(OrderInfo orderInfo);

    /**
     * 拆单方法
     * @param orderId
     * @param wareSkuMap
     * @return
     */
    List<OrderInfo> orderSplit(Long orderId, String wareSkuMap);

    /**
     * 更新过期订单
     * @param orderId
     * @param flag
     */
    void execExpiredOrder(Long orderId,String flag);
}
