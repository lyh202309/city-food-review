package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.config.RabbitMQConfig;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderServiceEnhanced;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;

@Slf4j
@Service("VoucherOrderServiceByRabbitMQImpl")
public class VoucherOrderServiceByRabbitMQImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderServiceEnhanced {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    // 注入 RabbitMQ 模板
    @Resource
    private RabbitTemplate rabbitTemplate;

    // ==================== 保留原 Lua 脚本 ====================
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckillWithRabbit.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // ==================== 核心：秒杀接口（发送MQ消息） ====================
    @Override
    public Result seckillVoucherPerPerson(Long voucherId) {
        // 1. 获取用户ID、生成订单ID
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");

        // 2. 执行Lua脚本（Redis校验库存+一人一单，原子操作）
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        int r = result.intValue();

        // 3. 无购买资格，直接返回
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }

        // 4. 封装订单对象，发送消息到 RabbitMQ（异步处理）
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);

        // 发送消息
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.SECKILL_EXCHANGE,
                RabbitMQConfig.SECKILL_ROUTING_KEY,
                voucherOrder
        );
        log.info("秒杀成功，订单已发送至MQ，订单ID：{}", orderId);

        // 5. 返回订单ID
        return Result.ok(orderId);
    }

    // ==================== 保留原无用方法（兼容接口） ====================
    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {
        return Result.fail("请使用异步秒杀接口");
    }

    // ==================== 核心：订单创建（数据库事务） ====================
    @Transactional
    public void createOrder(VoucherOrder voucherOrder) {
        // 扣减数据库库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();

        if (!success) {
            log.error("库存扣减失败，订单ID：{}", voucherOrder.getId());
            throw new RuntimeException("库存不足");
        }

        // 保存订单
        save(voucherOrder);
        log.info("订单创建成功，订单ID：{}", voucherOrder.getId());
    }
}