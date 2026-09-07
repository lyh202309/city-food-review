package com.lyh.consumer;

import com.lyh.config.RabbitMQConfig;
import com.lyh.entity.VoucherOrder;
import com.lyh.service.IVoucherOrderServiceEnhanced;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 秒杀订单 RabbitMQ 消费者
 * 单线程消费（和原Redis Stream逻辑一致，保证顺序，防止超卖）
 */
@Slf4j
@Component
public class SeckillOrderConsumer {

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 监听秒杀订单队列
     */
    @RabbitListener(queues = RabbitMQConfig.SECKILL_QUEUE)
    public void listenSeckillOrder(VoucherOrder voucherOrder, Channel channel, Message message){
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            log.info("开始处理MQ订单，订单ID：{}", voucherOrder.getId());

            // 从Spring容器获取代理对象（保证事务生效）
            IVoucherOrderServiceEnhanced proxy =
                    applicationContext.getBean("VoucherOrderServiceByRabbitMQImpl", IVoucherOrderServiceEnhanced.class);
            proxy.createOrder(voucherOrder);

            // 手动确认消息（消息消费成功）
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("订单处理失败，订单ID：{}，异常：", voucherOrder.getId(), e);
        }
    }
}