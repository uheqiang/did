package cn.ceres.did.server.http;

import cn.ceres.did.common.Constants;
import cn.ceres.did.common.NettyUtil;
import cn.ceres.did.core.SnowFlake;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 自定义的处理器，目前支持三种请求：
 * getTime: 获取服务器当前时间；
 * clientInfo: 获取请求客户端的User-Agent信息
 * 其它： 返回404状态，并且提示404信息
 *
 * @author ehlxr
 */
public class HttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    /**
     * 通过信号量来控制流量
     */
    private Semaphore semaphore = new Semaphore(Constants.HANDLE_HTTP_TPS);
    private SnowFlake snowFlake;

    public HttpServerHandler(SnowFlake snowFlake) {
        this.snowFlake = snowFlake;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        String uri = getUriNoSprit(request);
        logger.info("request uri is: {}", uri);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        // if (Constants.HTTP_REQUEST.equals(uri)) {
        if (semaphore.tryAcquire(Constants.ACQUIRE_TIMEOUTMILLIS, TimeUnit.MILLISECONDS)) {
            try {
                long id = snowFlake.nextId();
                logger.info("HttpServerHandler id is: {}", id);
                response.content().writeBytes(("" + id).getBytes());
            } catch (Exception e) {
                semaphore.release();
                logger.error("HttpServerHandler error", e);
            }
        } else {
            String info = String.format("HttpServerHandler tryAcquire semaphore timeout, %dms, waiting thread " + "nums: %d availablePermit: %d",
                    Constants.ACQUIRE_TIMEOUTMILLIS, this.semaphore.getQueueLength(), this.semaphore.availablePermits());
            logger.warn(info);
            throw new Exception(info);
        }
        // } else {
        //     response.content().writeBytes(("Unsupported uri: " + uri).getBytes());
        // }

        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        Channel channel = ctx.channel();
        logger.error("HttpServerHandler channel [{}] error and will be closed", NettyUtil.parseRemoteAddr(channel), cause);
        NettyUtil.closeChannel(channel);
    }


    private String getUriNoSprit(FullHttpRequest request) {
        String uri = request.uri();
        if (uri.startsWith("/")) {
            uri = uri.substring(1);
        }
        return uri;
    }
}
