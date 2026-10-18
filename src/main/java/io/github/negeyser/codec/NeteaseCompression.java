/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.cloudburstmc.protocol.bedrock.data.CompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.BatchCompression;
import org.cloudburstmc.protocol.common.util.Zlib;

import java.util.zip.DataFormatException;

/**
 * NetEase clients advertise ZLIB but send raw deflate, and some builds send a zlib-wrapped
 * stream instead. Encoding always uses raw deflate; decoding accepts both.
 */
public final class NeteaseCompression implements BatchCompression {

    private static final InternalLogger log = InternalLoggerFactory.getInstance(NeteaseCompression.class);
    private static final int MAX_DECOMPRESSED_BYTES = Integer.getInteger("bedrock.maxDecompressedBytes", 10 * 1024 * 1024);

    private volatile int level = 7;

    @Override
    public ByteBuf encode(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        ByteBuf out = ctx.alloc().ioBuffer(Math.max(64, msg.readableBytes() << 3));
        try {
            Zlib.RAW.deflate(msg, out, level);
            return out.retain();
        } finally {
            out.release();
        }
    }

    @Override
    public ByteBuf decode(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        if (!msg.isReadable()) {
            throw new DataFormatException("Cannot decompress empty data");
        }

        msg.markReaderIndex();
        try {
            return Zlib.RAW.inflate(msg, MAX_DECOMPRESSED_BYTES);
        } catch (DataFormatException e) {
            msg.resetReaderIndex();
        }

        try {
            return Zlib.DEFAULT.inflate(msg, MAX_DECOMPRESSED_BYTES);
        } catch (DataFormatException e) {
            msg.resetReaderIndex();
        }

        log.warn("NetEase packet decompression failed; passing data through unchanged");
        return msg.retainedSlice();
    }

    @Override
    public CompressionAlgorithm getAlgorithm() {
        return PacketCompressionAlgorithm.ZLIB;
    }

    @Override
    public void setLevel(int level) {
        this.level = level;
    }

    @Override
    public int getLevel() {
        return level;
    }
}
