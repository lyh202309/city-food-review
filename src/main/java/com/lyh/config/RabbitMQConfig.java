package com.lyh.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 秒杀订单队列配置
 */
@Configuration
public class RabbitMQConfig {

    public static final String SECKILL_EXCHANGE = "seckill.order.exchange";
    public static final String SECKILL_QUEUE = "seckill.order.queue";
    public static final String SECKILL_ROUTING_KEY = "seckill.order";

    // 交换机
    @Bean
    public DirectExchange seckillExchange() {
        // 持久化，重启不丢
        return new DirectExchange(SECKILL_EXCHANGE, true, false);
    }

    // 队列（只开持久化，无死信）
    @Bean
    public Queue seckillQueue() {
        // durable=true 队列持久化，重启不丢
        return QueueBuilder.durable(SECKILL_QUEUE).build();
    }

    // 绑定
    @Bean
    public Binding seckillBinding() {
        return BindingBuilder.bind(seckillQueue())
                .to(seckillExchange())
                .with(SECKILL_ROUTING_KEY);
    }
}