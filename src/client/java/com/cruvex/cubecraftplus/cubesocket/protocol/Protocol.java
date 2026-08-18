package com.cruvex.cubecraftplus.cubesocket.protocol;

import com.cruvex.cubecraftplus.cubesocket.protocol.packets.PacketDisconnect;
import com.cruvex.cubecraftplus.cubesocket.protocol.packets.PacketHelloPing;
import com.cruvex.cubecraftplus.cubesocket.protocol.packets.PacketHelloPong;
import com.cruvex.cubecraftplus.cubesocket.protocol.packets.PacketLocationUpdate;
import com.cruvex.cubecraftplus.cubesocket.protocol.packets.PacketLogin;
import com.cruvex.cubecraftplus.cubesocket.protocol.packets.PacketLoginComplete;
import com.cruvex.cubecraftplus.cubesocket.protocol.packets.PacketPing;
import com.cruvex.cubecraftplus.cubesocket.protocol.packets.PacketPong;
import com.cruvex.cubecraftplus.cubesocket.protocol.packets.PacketReload;
import com.cruvex.cubecraftplus.cubesocket.protocol.packets.PacketSetProtocol;

import java.util.HashMap;
import java.util.Map;

public class Protocol {

    private final Map<Integer, Class<? extends Packet>> packetsById = new HashMap<>();
    private final Map<Class<? extends Packet>, Integer> idsByPacket = new HashMap<>();

    public Protocol() {
        this.register(0, PacketPing.class);
        this.register(1, PacketPong.class);
        this.register(2, PacketHelloPing.class);
        this.register(3, PacketHelloPong.class);
        this.register(4, PacketLocationUpdate.class);
        // 5 is PacketPerkUpdate, not implemented
        this.register(6, PacketDisconnect.class);
        this.register(7, PacketLogin.class);
        this.register(8, PacketLoginComplete.class);
        this.register(9, PacketSetProtocol.class);
        // 10 is PacketGameStatUpdate, not implemented
        this.register(11, PacketReload.class);
    }

    private void register(int id, Class<? extends Packet> clazz) {
        this.packetsById.put(id, clazz);
        this.idsByPacket.put(clazz, id);
    }

    public Packet getPacket(int id) throws Exception {
        Class<? extends Packet> clazz = this.packetsById.get(id);
        if (clazz == null) {
            throw new IllegalArgumentException("Packet with id " + id + " is not registered.");
        }

        return clazz.getConstructor().newInstance();
    }

    public int getPacketId(Packet packet) {
        Integer id = this.idsByPacket.get(packet.getClass());
        if (id == null) {
            throw new IllegalArgumentException("Packet " + packet + " is not registered.");
        }

        return id;
    }

}
