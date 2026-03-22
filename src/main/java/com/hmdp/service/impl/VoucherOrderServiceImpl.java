package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    ISeckillVoucherService seckillVoucherService;
    @Autowired
    RedisIdWorker redisIdWorker;
    public Result seckillVoucherPerPerson(Long voucherId){
        //一人一单的逻辑
        //查询用户的订单数量
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()){
            //应该是同一个用户的被锁住只能查一次
            int count = this.query().eq("voucher_id",voucherId).eq("user_id",userId).count();
            //判断是否买过
            if(count > 0) {
                //买过，返回错误
                return Result.fail("不可重复购买！");
            }
            //没买过，可购买
            //同一个用户只能购买1次，所以对用户id的真实字符串值加悲观锁。
            //其次，为了防止事务失效，我们调用的是代理类的seckillVoucher方法
            //记得依赖AspectJ，方便你调用暴露代理对象的方法
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.seckillVoucher(voucherId);
        }
    }
    @Transactional
    @Override
    public Result seckillVoucher(Long voucherId) {
        //查那个秒杀券是需要用那个秒杀券的服务层的mybatis-plus来查的，我们这个类有的是查秒杀券订单的
        //1.先去查那个秒杀券
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        //判断是否是属于有效时间
        if(seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())){
            //还没开始
            return Result.fail("秒杀还没开始");
        }
        if(seckillVoucher.getEndTime().isBefore(LocalDateTime.now())){
            //已经结束
            return Result.fail("秒杀已经结束");
        }
        //判断是否还有剩余
        if(seckillVoucher.getStock() <= 0){
            return Result.fail("券已售罄");
        }
        //还有剩余，更新吧，乐观锁
        //要学这里的sql语句，还有就是mybatis-plus知识，还可以学到时间的api,还有.eq("voucher_id", voucherId)的意思
        //关注这里的链式调用的返回值就看懂了
        if(!seckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id",voucherId).gt("stock",0).update()){
            //如果这条语句能够更新失败了，那就是没有库存了，这句话本身就是乐观锁了，不要怕
            return Result.fail("券已售罄");
        }
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
}
