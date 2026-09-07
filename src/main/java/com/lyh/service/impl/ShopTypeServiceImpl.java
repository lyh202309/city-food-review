package com.lyh.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.lyh.dto.Result;
import com.lyh.entity.ShopType;
import com.lyh.mapper.ShopTypeMapper;
import com.lyh.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.lyh.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryTypeList() {
        //1.查缓存
        String key = CACHE_SHOP_TYPE_KEY;
        //用String的话，我们一般使用Json格式来存对象
        String shopTypeJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否命中
        if(StrUtil.isNotBlank(shopTypeJson)){
            //3.命中直接返回
            List<ShopType> shopTypeList = JSONUtil.toList(shopTypeJson, ShopType.class);
            return Result.ok(shopTypeList);
        }
        //4.没有命中去数据库查
        List<ShopType> shopTypeList = this.list();
        //5.数据库不存在则返回错误404
        if(shopTypeList.isEmpty()) {
            return Result.fail("商家不存在");
        }
        //6.存在就存缓存里并返回
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shopTypeList));
        return Result.ok(shopTypeList);
    }
}
