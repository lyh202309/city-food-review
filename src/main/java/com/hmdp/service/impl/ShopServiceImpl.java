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

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

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
        //4.没有命中去数据库查
        Shop shop =getById(id);
        //5.数据库不存在则返回错误404
        if(shop == null) {
            return Result.fail("商家不存在");
        }
        //6.存在就存缓存里并返回
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop));
        return Result.ok(shop);
    }
}
