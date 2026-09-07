package com.lyh.service.impl;

import com.lyh.entity.Blog;
import com.lyh.entity.User;
import com.lyh.mapper.BlogMapper;
import com.lyh.service.IBlogService;
import com.lyh.service.IUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Override
    public Blog queryById(Long id) {
        Blog blog = getById(id);
        if (blog == null) {
            return null;
        }
        User user = userService.getById(blog.getUserId());
        if (user != null) {
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
        }
        return blog;
    }
}
