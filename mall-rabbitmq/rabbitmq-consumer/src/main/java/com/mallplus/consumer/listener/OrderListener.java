package com.mallplus.consumer.listener;

import com.alibaba.fastjson.JSONObject;
//import com.mallplus.consumer.service.OrderService;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConfirmListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.ChannelAwareMessageListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

//import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;

/**
 * 消息监听器（消费者）
 */
@Component
public class OrderListener implements ChannelAwareMessageListener {

    private static final Logger logger = LoggerFactory.getLogger(OrderListener.class);
//    @Autowired
//    private OrderService orderService;

//    @Value("${order.mq.queue.name}")
//    private String orderQueue;
    /**
     * 处理接收到的消息
     * @param message 消息体
     * @param channel 通道，确认消费用
     * @throws Exception
     */
    @RabbitListener(queues = {"${order.mq.queue.name}"},containerFactory = "orderListenerContainer")
    @Override
    public void onMessage(Message message, Channel channel) throws Exception {
        //获取交付tag
        long tag = message.getMessageProperties().getDeliveryTag();
        try{
            String str = new String(message.getBody(),"utf-8");
//            logger.info("接收到的消息：{}",str);
            JSONObject obj = JSONObject.parseObject(str);
            //下单，操作数据库
//            orderService.order(obj.getString("userId"),obj.getString("goodsId"));
//            int i = 1 / 0;
            //确认消费
            channel.basicAck(tag,false);//false: 手动应答， true:自动应答
        }catch(Exception e){
            //把消费失败的消息重新放回队列中
            channel.basicNack(tag,false,true);
            logger.error("消息监听确认机制发生异常：",e.fillInStackTrace());
            // TODO  确认消费
//            channel.basicReject(tag,false);
        }

    }
}
