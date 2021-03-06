package cn.ceres.did.server.sdk;

import cn.ceres.did.common.Constants;
import cn.ceres.did.common.NettyUtil;
import cn.ceres.did.core.SnowFlake;
import io.netty.channel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 通过雪花算法生成唯一 ID，写入 Channel 返回
 *
 * @author ehlxr
 */
public class SdkServerHandler extends SimpleChannelInboundHandler {
    private static final Logger logger = LoggerFactory.getLogger(SdkServerHandler.class);
    /**
     * 通过信号量来控制流量
     */
    private Semaphore semaphore = new Semaphore(Constants.HANDLE_SDKS_TPS);
    private SnowFlake snowFlake;

    SdkServerHandler(SnowFlake snowFlake) {
        this.snowFlake = snowFlake;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof SdkProto) {
            SdkProto sdkProto = (SdkProto) msg;
            if (semaphore.tryAcquire(Constants.ACQUIRE_TIMEOUTMILLIS, TimeUnit.MILLISECONDS)) {
                try {
                    sdkProto.setDid(snowFlake.nextId());
                    ctx.channel().writeAndFlush(sdkProto).addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture channelFuture) {
                            semaphore.release();
                        }
                    });
                } catch (Exception e) {
                    semaphore.release();
                    logger.error("SdkServerhandler error", e);
                }
            } else {
                sdkProto.setDid(-1);
                ctx.channel().writeAndFlush(sdkProto);
                String info = String.format("SdkServerHandler tryAcquire semaphore timeout, %dms, waiting thread " + "nums: %d availablePermit: %d",
                        Constants.ACQUIRE_TIMEOUTMILLIS, this.semaphore.getQueueLength(), this.semaphore.availablePermits());
                logger.warn(info);
                throw new Exception(info);
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        Channel channel = ctx.channel();
        logger.error("SdkServerHandler channel [{}] error and will be closed", NettyUtil.parseRemoteAddr(channel), cause);
        NettyUtil.closeChannel(channel);
    }
}
