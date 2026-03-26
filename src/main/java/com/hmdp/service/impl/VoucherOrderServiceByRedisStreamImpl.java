package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderServiceEnhanced;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 *  服务实现类,基于数据库操作和redis实现的分布式锁和lua脚本
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service("VoucherOrderServiceByRedisStreamImpl")
public class VoucherOrderServiceByRedisStreamImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderServiceEnhanced {
    @Autowired
    ISeckillVoucherService seckillVoucherService;
    @Autowired
    RedisIdWorker redisIdWorker;
    @Autowired
    StringRedisTemplate stringRedisTemplate;
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    // 🔥 核心：单线程线程池（专门处理消费，长期运行）
    private static final ExecutorService CONSUMER_EXECUTOR = Executors.newSingleThreadExecutor();
    //为啥这个有效，是因为真正在IOC容器里面的Bean是这个类的代理对象
    //而且ApplicationContext在另一个线程可以拿，AopContext在另一个线程拿不到，因为AopContext底层是ThreadLocal
    @Autowired
    private ApplicationContext applicationContext;
    @PostConstruct
    public void startConsumerThread(){
        //@PostConstruct是在spring完成注入之后才会执行这个方法，所以不用担心拿不到stringRedisTemplate
        //这里启动线程，这个线程专门负责处理消息队列里面的消息

        CONSUMER_EXECUTOR.submit(()->{
            while (true) {
                try {
                    log.info("正在同步订单");
                    // 1.获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK STREAMS s1 >
                    //我们这个组的名字叫g1有点难听，而且c1是消费者1，不同的jvm的这个线程的消费者要不同，组要一样
                    List<MapRecord<String, Object, Object>> mapRecordList = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1","c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            //ReadOffset.lastConsumed()是读消费者组中所有人都没有消费过的消息，如果处理失败了进pending-list了那只有这个线程知道，这个线程处理吧
                            StreamOffset.create("stream.orders", ReadOffset.lastConsumed())
                    );
                    if (mapRecordList == null || mapRecordList.isEmpty()){
                        continue;
                    }
                    //如果不是因为异常读到空队列，那就是有新的订单需要处理了
                    MapRecord<String, Object, Object> mapRecord = mapRecordList.get(0);
                    //直接把订单对象给到我们的订单创建方法
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(mapRecord.getValue(),new VoucherOrder(),true);
                    IVoucherOrderServiceEnhanced proxy = applicationContext.getBean(IVoucherOrderServiceEnhanced.class);
                    proxy.createOrder(voucherOrder);
                    stringRedisTemplate.opsForStream().acknowledge("stream.orders", "g1", mapRecord.getId());
                }catch (Exception e){
                    //出异常了，重试,从pending-list里面取
                    log.error("订单处理异常，真实错误：", e);
                    handlePendingList();
                }
            }
        });
    }

    private void handlePendingList() {
        //给我一直重试处理直到成功为止，但是这里是不是会处理别的订单的异常啊？这个应该也不冲突吧？
        int count = 0;
        while (count++ < 100){
            try{
                log.info("重试中");
                List<MapRecord<String, Object, Object>> mapRecordList = stringRedisTemplate.opsForStream().read(
                        Consumer.from("g1","c1"),
                        StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                        StreamOffset.create("stream.orders", ReadOffset.from("0"))
                );
                if (mapRecordList == null || mapRecordList.isEmpty()){
                    continue;
                }
                MapRecord<String, Object, Object> mapRecord = mapRecordList.get(0);
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(mapRecord.getValue(),new VoucherOrder(),true);
                IVoucherOrderServiceEnhanced proxy = applicationContext.getBean(IVoucherOrderServiceEnhanced.class);
                proxy.createOrder(voucherOrder);
                stringRedisTemplate.opsForStream().acknowledge("stream.orders", "g1", mapRecord.getId());
            }catch (Exception e){
                try {
                    Thread.sleep(200);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
        log.info("未知错误，尝试了100次重试均失败");
    }

    @Override
    public Result seckillVoucherPerPerson(Long voucherId) {
        //获取用户
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        int r = result.intValue();
        // 2.判断结果是否为0
        if (r != 0) {
            // 2.1.不为0 ，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        // 3.返回订单id
        return Result.ok(orderId);
    }
    @Transactional
    @Override
    public Result seckillVoucher(Long voucherId) {
        //更新成功了，那这个请求就成了，给他新建订单吧
        //这里不担心先后顺序，新建订单这里没有线程安全问题，别人先建就先建呗
        //涉及两个表的操作加个 事务？
        VoucherOrder voucherOrder = new VoucherOrder();
        long voucherOrderId = redisIdWorker.nextId("order");
        voucherOrder.setId(voucherOrderId);
        voucherOrder.setVoucherId(voucherId);
        //用户id设置在拦截器里面了，拦截器帮你放线程里面了，你这里直接取就行了
        /*(只要登录，用户信息就会被放到缓存里面，缓存过期前多个请求之间互通，登录成功自动有一个请求，必然过全局拦截器
        过全局拦截器更新你的token过期时间顺便把用户DTO存线程里面了，所以不需要来回传参。token对应redis里面的用户DTO对象)
         */
        voucherOrder.setUserId(UserHolder.getUser().getId());
        this.save(voucherOrder);
        return Result.ok(voucherOrderId);
    }
    @Transactional
    public void createOrder(VoucherOrder voucherOrder) {
//        Long userId = voucherOrder.getUserId();
//        // 5.1.查询订单
//        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
//        // 5.2.判断是否存在
//        if (count > 0) {
//            // 用户已经购买过了
//            log.error("用户已经购买过了");
//            return ;
//        }

        // 6.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1") // set stock = stock - 1
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0) // where id = ? and stock > 0
                .update();
//        if (!success) {
//            // 扣减失败
//            log.error("库存不足");
//            return ;
//        }
        save(voucherOrder);
    }
}
