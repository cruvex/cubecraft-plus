package com.cruvex.cubecraftplus.cubesocket;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import com.cruvex.cubecraftplus.cubesocket.protocol.Packet;
import com.cruvex.cubecraftplus.cubesocket.protocol.Protocol;
import com.cruvex.cubecraftplus.cubesocket.protocol.packets.PacketDisconnect;
import com.cruvex.cubecraftplus.cubesocket.protocol.packets.PacketHelloPing;
import com.cruvex.cubecraftplus.events.CubeEvents;
import com.cruvex.cubecraftplus.events.CubeSocketEvents;
import com.cruvex.cubecraftplus.managers.CubeCraftManager;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Connection to Cubepanion's CubeSocket. The API only accepts leaderboard submissions from
 * clients it has seen connect here, which is what makes falsified data harder to upload.
 */
public class CubeSocket {

  private static final Logger LOGGER = CubeCraftPlusClient.LOGGER;

  private static final String HOST = "cubesocket.ameliah.art";
  private static final int PORT = 30527;

  private static final long KEEP_ALIVE_TIMEOUT_MS = 25000L;
  private static final int MAX_CONNECT_TRIES = 5;

  private static final ThreadFactory THREADS = runnable -> {
    Thread thread = new Thread(runnable, "CubeSocket");
    thread.setDaemon(true);
    return thread;
  };

  private static CubeSocket instance;

  private final Protocol protocol = new Protocol();
  private final EventLoopGroup eventLoopGroup = new MultiThreadIoEventLoopGroup(0, THREADS, NioIoHandler.newFactory());
  private final ExecutorService executor = Executors.newFixedThreadPool(2, THREADS);
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(THREADS);

  private CubeSocketSession session;
  private CubeSocketHandler channelHandler;
  private volatile CubeSocketState state = CubeSocketState.OFFLINE;
  private long timeLastKeepAlive;
  private long timeNextConnect = Instant.now().toEpochMilli();
  private int connectTries;
  private String lastDisconnectReason;

  public static CubeSocket getInstance() {
    if (instance == null) {
      instance = new CubeSocket();
    }
    return instance;
  }

  public void init() {
    new CubeSocketNotifications();

    CubeEvents.CUBE_JOIN.register(this::connect);
    ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onNetworkDisconnect());

    scheduler.scheduleWithFixedDelay(this::checkConnection, 0L, 5L, TimeUnit.SECONDS);
  }

  /** Shared with the session, so a reconnect doesn't leave a scheduler thread behind. */
  public void schedule(Runnable task, long delay, TimeUnit unit) {
    scheduler.schedule(task, delay, unit);
  }

  private void checkConnection() {
    try {
      if (!CubeCraftManager.getInstance().isOnCubeCraft()) {
        return;
      }

      long sinceKeepAlive = Instant.now().toEpochMilli() - this.timeLastKeepAlive;
      long untilConnect = this.timeNextConnect - Instant.now().toEpochMilli();

      if (this.state != CubeSocketState.OFFLINE && sinceKeepAlive > KEEP_ALIVE_TIMEOUT_MS) {
        this.disconnect("Connection timed out");
      }

      if (this.state == CubeSocketState.OFFLINE && untilConnect < 0L) {
        this.connect();
      }
    } catch (Exception e) {
      LOGGER.error("Error in CubeSocket keep alive", e);
    }
  }

  private void connect() {
    if (this.connectTries >= MAX_CONNECT_TRIES) {
      return;
    }

    executor.execute(() -> {
      synchronized (this) {
        if (this.state != CubeSocketState.OFFLINE) {
          return;
        }

        this.keepAlive();
        this.updateState(CubeSocketState.HELLO);
        this.connectTries++;

        this.session = new CubeSocketSession(this);
        this.channelHandler = new CubeSocketHandler(this, this.session);
        this.lastDisconnectReason = null;

        Bootstrap bootstrap = new Bootstrap()
            .group(this.eventLoopGroup)
            .channel(NioSocketChannel.class)
            .handler(this.channelHandler);

        try {
          bootstrap.connect(HOST, PORT).syncUninterruptibly();
          this.sendPacket(new PacketHelloPing(Instant.now().toEpochMilli()));
        } catch (Exception e) {
          this.updateState(CubeSocketState.OFFLINE);
          LOGGER.warn("Failed to connect to CubeSocket", e);
        }
      }
    });
  }

  private void onNetworkDisconnect() {
    if (this.isConnected()) {
      this.disconnect("Logged off CubeCraft");
    }
    this.connectTries = 0;
  }

  private void disconnect(String reason) {
    // Spread reconnects out so everyone kicked at once doesn't come back at once
    long delay = (long) (1000.0 * Math.random() * 60.0);
    this.timeNextConnect = Instant.now().toEpochMilli() + 10000L + delay;
    this.lastDisconnectReason = reason;
    if (this.state == CubeSocketState.OFFLINE) {
      return;
    }

    CubeSocketEvents.SOCKET_DISCONNECT.invoker().onDisconnected(reason);
    this.updateState(CubeSocketState.OFFLINE);
    this.sendPacket(new PacketDisconnect("logout"), channel -> {
      if (channel.isOpen()) {
        channel.close();
      }
    });
    this.session = null;
  }

  public void updateState(CubeSocketState state) {
    synchronized (this) {
      this.state = state;
    }
    CubeSocketEvents.SOCKET_STATE_UPDATE.invoker().onStateUpdated(state);
  }

  public void keepAlive() {
    this.timeLastKeepAlive = Instant.now().toEpochMilli();
  }

  public void sendPacket(Packet packet) {
    this.sendPacket(packet, null);
  }

  public void sendPacket(Packet packet, @Nullable Consumer<Channel> callback) {
    if (packet == null) {
      LOGGER.warn("Tried to send a null packet");
      return;
    }

    NioSocketChannel channel = this.getChannel();
    if (channel == null || !channel.isActive()) {
      return;
    }

    EventLoop loop = channel.eventLoop();
    if (loop.inEventLoop()) {
      write(channel, packet, callback);
      return;
    }

    loop.execute(() -> write(channel, packet, callback));
  }

  private static void write(NioSocketChannel channel, Packet packet, @Nullable Consumer<Channel> callback) {
    channel.writeAndFlush(packet).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
    if (callback != null) {
      callback.accept(channel);
    }
  }

  public NioSocketChannel getChannel() {
    return this.channelHandler == null ? null : this.channelHandler.getChannel();
  }

  public boolean isConnected() {
    return this.state == CubeSocketState.CONNECTED;
  }

  public CubeSocketState getState() {
    return this.state;
  }

  public @Nullable CubeSocketSession getSession() {
    return this.session;
  }

  public String getLastDisconnectReason() {
    return this.lastDisconnectReason;
  }

  public Protocol getProtocol() {
    return this.protocol;
  }

  public void setConnectTries(int connectTries) {
    this.connectTries = connectTries;
  }
}
