/*
 * Copyright 2018-present yangguo@outlook.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package info.yangguo.yfs.request;

import com.google.common.cache.*;
import com.google.common.util.concurrent.RateLimiter;
import info.yangguo.yfs.Constant;
import info.yangguo.yfs.util.GatewayHttpHeaderNames;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * @author:杨果
 * @date:2017/5/12 上午11:37
 * <p>
 * Description:
 * cc拦截
 */
public class CCHttpRequestFilter extends HttpRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(CCHttpRequestFilter.class);
    private LoadingCache loadingCache;
    private static final ThreadLocal tl = new ThreadLocal();


    public CCHttpRequestFilter() {
        loadingCache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(2, TimeUnit.SECONDS)
                .removalListener(new RemovalListener() {
                    @Override
                    public void onRemoval(RemovalNotification notification) {
                        logger.debug("key:{} remove from cache", notification.getKey());
                    }
                })
                .build(new CacheLoader<String, RateLimiter>() {
                    @Override
                    public RateLimiter load(String key) throws Exception {
                        RateLimiter rateLimiter = RateLimiter.create(Integer.parseInt(Constant.gatewayConfs.get("gateway.cc.rate")));
                        logger.debug("RateLimiter for key:{} have been created", key);
                        return rateLimiter;
                    }
                });
    }

    @Override
    public boolean doFilter(HttpRequest originalRequest, HttpObject httpObject, ChannelHandlerContext channelHandlerContext) {
        if (httpObject instanceof HttpRequest) {
            logger.debug("filter:{}", this.getClass().getName());
            String realIp = originalRequest.headers().get(GatewayHttpHeaderNames.X_REAL_IP);
            RateLimiter rateLimiter = null;
            try {
                rateLimiter = (RateLimiter) loadingCache.get(realIp);
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
            if (rateLimiter.tryAcquire()) {
                tl.set(false);
                return false;
            } else {
                hackLog(logger, realIp, "cc", Constant.gatewayConfs.get("gateway.cc.rate"));
                tl.set(true);
                return true;
            }
        }
        return false;
    }
}
