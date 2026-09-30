package dev.piny.write.network;

import dev.piny.pineLib.network.RouteContext;
import dev.piny.write.util.HostablePack;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;

import java.io.File;

@ChannelHandler.Sharable
public class ResourcePackRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest req) {
        String plugin = RouteContext.getParams().get("plugin");
        File file = HostablePack.resolvePackFile(plugin);

        if (file.exists() && !file.isDirectory()) {
            ctx.writeAndFlush(file);
        }
    }
}