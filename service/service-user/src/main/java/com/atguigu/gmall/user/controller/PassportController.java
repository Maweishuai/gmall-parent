package com.atguigu.gmall.user.controller;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.util.IpUtil;
import com.atguigu.gmall.model.user.UserInfo;
import com.atguigu.gmall.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author Administrator
 * @create 2020-06-23 0:35
 */
@RestController
@RequestMapping("/api/user/passport")
public class PassportController {

    @Autowired
    private UserService userService;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 登录
     * @param userInfo
     * @param request
     * @param response
     * @return
     */
    // 登录的控制器 url 是谁以及提交方式是什么？
    // login.html 中login 方法得出 登录控制器
    // @RequestBody 接收数据，将json 字符串转化为java 对象
    @PostMapping("login")
    public Result login(@RequestBody UserInfo userInfo,
                        HttpServletRequest request,
                        HttpServletResponse response){
        // login.login(this.user)
        UserInfo info = userService.login(userInfo);
        // 判断查询出来的数据是否为空！
        if (null!=info){
            // 登录成功之后，返回一个token ，token由 一个UUID 组成
            String token = UUID.randomUUID().toString();
            // 页面中 auth.setToken(response.data.data.token)
            // 将token 放入cookie 中!
            // 声明一个map,就是上面页面中的第一个data---response.data
            HashMap<String, Object> map = new HashMap<>();
            map.put("token",token);
            // 还需要做一件事 ：登录成功之后，页面上方需要显示一个用户昵称的！
            map.put("nickName",info.getNickName());

            // 如果登录成功，我们需要将用户信息存储缓存！
            // 只需要通过一个 userId 就可以了！
            // 声明一个对象
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("userId",info.getId().toString());
            // 将此时登录的用户IP 地址放入缓存！使用工具类
            jsonObject.put("ip", IpUtil.getIpAddress(request));
            // 定义key
            String userKey = RedisConst.USER_LOGIN_KEY_PREFIX+ token;
            // 将数据放入缓存
            redisTemplate.opsForValue().set(userKey,jsonObject.toJSONString(),RedisConst.USERKEY_TIMEOUT, TimeUnit.SECONDS);

            return Result.ok(map);
        }else {
            // 如果没用户信息
            return Result.fail().message("用户名密码不匹配！");
        }
    }

    /**
     * 退出登录
     * @param request
     * @return
     */
    @GetMapping("logout")
    public Result logout(HttpServletRequest request){
        //登录时，将token 放入了cookie 中，同时放入了header 中！
        //所以在退出时同样也要删除header中的token
        String token = request.getHeader("token");
        String userKey = RedisConst.USER_LOGIN_KEY_PREFIX+token;
        redisTemplate.delete(userKey);

        return Result.ok();
    }
}
