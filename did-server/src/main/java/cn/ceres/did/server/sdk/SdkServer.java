package cn.ceres.did.server.sdk;

import cn.ceres.did.common.Constants;
import cn.ceres.did.server.BaseServer;
import cn.ceres.did.core.SnowFlake;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.net.InetSocketAddress;

/**
 * @author ehlxr
 */
public class SdkServer extends BaseServer {
    private SnowFlake snowFlake;

    public SdkServer(SnowFlake snowFlake) {
        this.snowFlake = snowFlake;
        this.port = "".equals(Constants.getEnv("SDKS_PORT")) ? Constants.SDKS_PORT : Integer.valueOf(Constants.getEnv("SDKS_PORT"));
    }

    @Override
    public void init() {
        super.init();
        serverBootstrap.group(bossGroup, workGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_BACKLOG, 1024)
                .localAddress(new InetSocketAddress(port))
                .childHandler(new ChannelInitializer<SocketChannel>() {

                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(defLoopGroup,
                                new SdkServerDecoder(12),
                                new SdkServerEncoder(),
                                new SdkServerHandler(snowFlake)
                        );
                    }
                });
    }

    @Override
    public void start() {
        try {
            channelFuture = serverBootstrap.bind().sync();
            InetSocketAddress addr = (InetSocketAddress) channelFuture.channel().localAddress();
            logger.info("SdkServer start success, port is:{}", addr.getPort());
        } catch (InterruptedException e) {
            logger.error("SdkServer start fail,", e);
        }
    }
}
