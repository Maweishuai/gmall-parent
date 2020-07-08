package com.atguigu.gmall.user.service.impl;

import com.atguigu.gmall.model.user.UserInfo;
import com.atguigu.gmall.user.mapper.UserInfoMapper;
import com.atguigu.gmall.user.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

/**
 * @author Administrator
 * @create 2020-06-23 0:18
 */
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserInfoMapper userInfoMapper;

    @Override
    public UserInfo login(UserInfo userInfo) {

        String passwd = userInfo.getPasswd();
        //Md5加密后获取新密码
        String newPasswd = DigestUtils.md5DigestAsHex(passwd.getBytes());
        QueryWrapper<UserInfo> userInfoQueryWrapper = new QueryWrapper<>();
        userInfoQueryWrapper.eq("login_name",userInfo.getLoginName());
        userInfoQueryWrapper.eq("passwd",newPasswd);
        UserInfo info = userInfoMapper.selectOne(userInfoQueryWrapper);
        if (null!=info){
            return info;
        }
        return null;
    }
}
