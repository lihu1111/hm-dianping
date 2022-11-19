package com.hmdp.service.impl;

import cn.hutool.Hutool;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * <p>
 * 服务实现类
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
        // 1. 查询缓存
        List<String> shopTypes = stringRedisTemplate.opsForList().range(RedisConstants.CACHE_SHOP_TYPE_KEY, 0, -1);
        // 2. 缓存命中
        if(!shopTypes.isEmpty()) {
            ArrayList<ShopType> list = new ArrayList<>();
            for (String type : shopTypes) {
                ShopType shopType = JSONUtil.toBean(type, ShopType.class);
                list.add(shopType);
            }
            return Result.ok(list);
        }
        // 3. 缓存不命中， 查询数据库
        List<ShopType> list =
                query().orderByAsc("sort").list();
        // 5. 数据库存在， 添加缓存， 返回数据
        ArrayList<String> jsonString = new ArrayList<>();
        for (ShopType type : list) {
            String typeJson = JSONUtil.toJsonStr(type);
            jsonString.add(typeJson);
        }
        stringRedisTemplate.opsForList().leftPushAll(RedisConstants.CACHE_SHOP_TYPE_KEY, jsonString);
        return Result.ok(list);
        // set完成
//        Set<String> shopTypes = stringRedisTemplate.opsForSet().members(RedisConstants.CACHE_SHOP_TYPE_KEY);
//        if (!shopTypes.isEmpty()) {
//            ArrayList<ShopType> shopTypeList = new ArrayList<>();
//            for (String shopType : shopTypes) {
//                ShopType type = JSONUtil.toBean(shopType, ShopType.class);
//                shopTypeList.add(type);
//            }
//            return Result.ok(shopTypeList);
//        }
//        List<ShopType> list =
//                query().orderByAsc("sort").list();
//        Set<String> jsonStrings = new HashSet<>();
//        for (ShopType shopType : list) {
//            String s = JSONUtil.toJsonStr(shopType);
//            jsonStrings.add(s);
//        }
//////
//        return Result.ok(list);
    }
}
