package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryById(Long id) {
        //1.查缓存
        String key = CACHE_SHOP_KEY + id;
        //用String的话，我们一般使用Json格式来存对象
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否命中
        if(StrUtil.isNotBlank(shopJson)){
            //3.命中直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        //如果是""的话也是被isNotBlank判定为blank的，还是会下来的
        if(shopJson != null) return Result.fail("商户不存在！");
        //4.没有命中去数据库查
        Shop shop = getById(id);
        //5.数据库不存在则返回错误
        if(shop == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("商家不存在");
        }
        //6.存在就存缓存里并返回
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop));
        return Result.ok(shop);
    }
    public Result queryByIdWithMutex(Long id) {
        //1.查缓存
        String key = CACHE_SHOP_KEY + id;
        //用String的话，我们一般使用Json格式来存对象
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否命中
        if(StrUtil.isNotBlank(shopJson)){
            //3.命中直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        //如果是""的话也是被isNotBlank判定为blank的，还是会下来的
        if(shopJson != null) return Result.fail("商户不存在！");
        //4.没有命中去数据库查
        //4.1先拿锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try{
            if(!getLock(lockKey)){
                //4.2如果没有拿到锁，说明有人在查了，重新等吧
                Thread.sleep(50);
                return queryByIdWithMutex(id);
            }
            //4.3拿到锁了，查吧
            //这里还可以再放一个逻辑，主要提防你正好拿锁以为不存在，但是别人正好查询出来放了锁？

            shop = getById(id);
            //5.数据库不存在则返回错误
            if(shop == null) {
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return Result.fail("商家不存在");
            }
            //6.存在就存缓存里并返回
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop));
        }catch (Exception e){
            throw new RuntimeException(e);
        }finally {
            unlock(lockKey);
        }
        return Result.ok(shop);
    }

    private boolean getLock(String key) {
        //如何拿锁？
        //查这个商户对应的锁是不是被上锁了。
        //不能直接返回，因为是封装类型?
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key,"1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return flag!=null&&flag;
    }
    private void unlock(String key) {
        //那这里也有可能失败啊,,,有点奇怪这个
        stringRedisTemplate.delete(key);
    }
}
