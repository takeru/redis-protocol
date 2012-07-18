package redis.netty;

import java.io.IOException;

import com.google.common.base.Charsets;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferIndexFinder;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.replay.ReplayingDecoder;
import org.jboss.netty.handler.codec.replay.VoidEnum;

/**
 * Netty codec for Redis
 */

public class RedisDecoder extends ReplayingDecoder<VoidEnum> {

  public static final char CR = '\r';
  public static final char LF = '\n';
  private static final char ZERO = '0';

  public ChannelBuffer readBytes(ChannelBuffer is) throws IOException {
    int size = readInteger(is);
    if (size == -1) {
      return null;
    }
    ChannelBuffer buffer = is.readSlice(size);
    int cr = is.readByte();
    int lf = is.readByte();
    if (cr != CR || lf != LF) {
      throw new IOException("Improper line ending: " + cr + ", " + lf);
    }
    return buffer;
  }

  public static int readInteger(ChannelBuffer is) throws IOException {
    int size = 0;
    int sign = 1;
    int read = is.readByte();
    if (read == '-') {
      read = is.readByte();
      sign = -1;
    }
    do {
      if (read == CR) {
        if (is.readByte() == LF) {
          break;
        }
      }
      int value = read - ZERO;
      if (value >= 0 && value < 10) {
        size *= 10;
        size += value;
      } else {
        throw new IOException("Invalid character in integer");
      }
      read = is.readByte();
    } while (true);
    return size * sign;
  }

  public Reply receive(final ChannelBuffer is) throws IOException {
    int code = is.readByte();
    switch (code) {
      case StatusReply.MARKER: {
        String status = is.readBytes(is.bytesBefore(ChannelBufferIndexFinder.CRLF)).toString(Charsets.UTF_8);
        is.skipBytes(2);
        return new StatusReply(status);
      }
      case ErrorReply.MARKER: {
        String error = is.readBytes(is.bytesBefore(ChannelBufferIndexFinder.CRLF)).toString(Charsets.UTF_8);
        is.skipBytes(2);
        return new ErrorReply(error);
      }
      case IntegerReply.MARKER: {
        return new IntegerReply(readInteger(is));
      }
      case BulkReply.MARKER: {
        return new BulkReply(readBytes(is));
      }
      case MultiBulkReply.MARKER: {
        return new MultiBulkReply(this, is);
      }
      default: {
        throw new IOException("Unexpected character in stream: " + code);
      }
    }
  }

  @Override
  protected Object decode(ChannelHandlerContext channelHandlerContext, Channel channel, ChannelBuffer channelBuffer, VoidEnum anEnum) throws Exception {
    return receive(channelBuffer);
  }
}
