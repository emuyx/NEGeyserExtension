/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.handler;

import io.github.negeyser.NEGeyserExtension;
import io.github.negeyser.codec.NeteaseCodecs;
import io.github.negeyser.codec.NeteaseCompression;
import io.github.negeyser.protocol.BlockMappingsPatcher;
import io.github.negeyser.protocol.NeteaseAuth;
import io.github.negeyser.protocol.NeteaseContainers;
import io.github.negeyser.protocol.NeteaseCrypto;
import io.github.negeyser.protocol.NeteaseNameTranslator;
import io.github.negeyser.protocol.NeteaseProfile;
import io.github.negeyser.protocol.NeteaseRegistries;
import io.github.negeyser.protocol.NeteaseSession;
import io.github.negeyser.protocol.SpoofedUpstream;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.data.auth.CertificateChainPayload;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryActionData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventorySource;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.SimpleCompressionStrategy;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.data.inventory.FullContainerName;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequest;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequestSlotData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ConsumeAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.CraftCreativeAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.DestroyAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.DropAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ItemStackRequestAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.PlaceAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.SwapAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.TakeAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.TransferItemStackRequestAction;
import org.cloudburstmc.protocol.bedrock.packet.BookEditPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientToServerHandshakePacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemStackRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestNetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackChunkRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackClientResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackStackPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePacksInfoPacket;
import org.cloudburstmc.protocol.bedrock.packet.ServerToClientHandshakePacket;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.event.bedrock.SessionInitializeEvent;
import org.geysermc.geyser.api.network.AuthType;
import org.geysermc.geyser.event.type.SessionLoadResourcePacksEventImpl;
import org.geysermc.geyser.inventory.InventoryHolder;
import org.geysermc.geyser.pack.GeyserResourcePack;
import org.geysermc.geyser.pack.ResourcePackHolder;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.registry.type.BlockMappings;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.auth.AuthData;
import org.geysermc.geyser.session.auth.BedrockClientData;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerAction;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;

import javax.crypto.SecretKey;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Packet handler for connections that arrived over RakNet protocol 8 (NetEase clients).
 */
public final class NeteaseUpstreamHandler extends UpstreamHandlerBase {

    private final Queue<ResourcePackChunkRequestPacket> chunkRequests = new ConcurrentLinkedQueue<>();
    private boolean sendingChunks;
    private NeteaseSession neteaseSession;

    /** Whether this session authenticated with a single-entry (relayed) certificate chain. */
    private boolean singleChainLogin;
    /** Packs advertised by the most recent ResourcePacksInfo packet. */
    private List<ResourcePacksInfoPacket.Entry> advertisedPacks = List.of();
    private boolean resourcePacksInfoHasPacks;
    /** Set once a ResourcePackDataInfo or ChunkData packet has been sent for that Info. */
    private boolean resourcePackTransferStarted;
    /** Early-3 download flow is running; duplicate status 3 packets must be ignored. */
    private boolean earlyResourcePackActive;
    private ScheduledFuture<?> earlyResourcePackStackTask;
    private long earlyResourcePackGeneration;
    private long lastEarlyPackCompletionNanos;
    private boolean earlyResourcePackStackSent;
    /** Requested packs that have not finished downloading yet. */
    private final Set<UUID> earlyResourcePacksPending = new HashSet<>();
    /** Packs that received at least one chunk request in the early-3 flow. */
    private final Set<UUID> earlyResourcePacksRequested = new HashSet<>();

    public NeteaseUpstreamHandler(GeyserImpl geyser, GeyserSession session) {
        super(geyser, session);
    }

    public NeteaseSession neteaseSession() {
        return neteaseSession;
    }

    @Override
    public PacketSignal handle(RequestNetworkSettingsPacket packet) {
        if (!isNeteaseConnection()) {
            return super.handle(packet);
        }

        BedrockCodec codec = NeteaseCodecs.get(packet.getProtocolVersion());
        if (codec == null) {
            session.disconnect(GeyserLocale.getLocaleStringLog("geyser.network.outdated.client",
                "1.21.93 / 1.21.124 / 1.21.130 (NetEase)"));
            return PacketSignal.HANDLED;
        }

        session.getUpstream().getSession().setCodec(codec);
        this.neteaseSession = new NeteaseSession(session, packet.getProtocolVersion(), codec);

        NetworkSettingsPacket response = new NetworkSettingsPacket();
        response.setCompressionAlgorithm(PacketCompressionAlgorithm.ZLIB);
        response.setCompressionThreshold(512);
        session.sendUpstreamPacketImmediately(response);

        NeteaseCompression compression = new NeteaseCompression();
        compression.setLevel(geyser.config().advanced().bedrock().compressionLevel());
        session.getUpstream().getSession().getPeer().setCompression(new SimpleCompressionStrategy(compression));

        networkSettingsRequested = true;
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(LoginPacket loginPacket) {
        if (neteaseSession == null) {
            // This handler is only installed for NetEase channels, but be defensive.
            return super.handle(loginPacket);
        }

        if (geyser.isShuttingDown() || geyser.isReloading()) {
            session.disconnect(GeyserLocale.getLocaleStringLog("geyser.core.shutdown.kick.message"));
            return PacketSignal.HANDLED;
        }
        if (!networkSettingsRequested) {
            session.disconnect("Protocol error: login before network settings");
            return PacketSignal.HANDLED;
        }
        if (receivedLoginPacket) {
            session.disconnect("Received duplicate login packet!");
            session.forciblyCloseUpstream();
            return PacketSignal.HANDLED;
        }
        receivedLoginPacket = true;

        try {
            authenticate(loginPacket);
        } catch (Exception e) {
            if (geyser.config().debugMode()) {
                geyser.getLogger().error("NetEase login failed", e);
            } else {
                geyser.getLogger().warning("NetEase login failed: " + e.getMessage());
            }
            session.disconnect("disconnectionScreen.internalError.cantConnect");
            return PacketSignal.HANDLED;
        }

        if (session.isClosed()) {
            return PacketSignal.HANDLED;
        }

        // Register NetEase item/block/tag registries for this protocol version and use them
        // natively from here on.
        BlockMappings mappings;
        try {
            NeteaseRegistries.ensureInitialized();
            mappings = BlockRegistries.BLOCKS.forVersion(neteaseSession.protocolVersion());
        } catch (Throwable e) {
            geyser.getLogger().error("Could not prepare NetEase block mappings", e);
            session.disconnect("disconnectionScreen.internalError.cantConnect");
            return PacketSignal.HANDLED;
        }
        session.setBlockMappings(mappings);
        session.setItemMappings(Registries.ITEMS.forVersion(neteaseSession.protocolVersion()));

        SpoofedUpstream spoofed = new SpoofedUpstream(session, session.getUpstream(), mappings,
            session.getAuthData() == null ? "" : session.getAuthData().xuid());
        SpoofedUpstream.inject(session, spoofed);

        if (geyser.getSessionManager().isXuidAlreadyPending(session.xuid())
            || geyser.getSessionManager().sessionByXuid(session.xuid()) != null) {
            session.disconnect(GeyserLocale.getLocaleStringLog("geyser.auth.already_loggedin", session.bedrockUsername()));
            return PacketSignal.HANDLED;
        }

        geyser.getSessionManager().addPendingSession(session);
        geyser.eventBus().fire(new SessionInitializeEvent(session));
        GeyserLocale.loadGeyserLocale(session.locale());

        // NetEase clients send ClientToServerHandshake after enabling encryption. Resource pack
        // negotiation starts there so both sides agree on the encryption state.
        neteaseSession.setLoginDeferred(true);
        return PacketSignal.HANDLED;
    }

    private void authenticate(LoginPacket loginPacket) throws Exception {
        if (!(loginPacket.getAuthPayload() instanceof CertificateChainPayload chain)) {
            throw new IllegalStateException("NetEase login must use a certificate chain");
        }

        boolean allowSingleChain = NEGeyserExtension.CONFIG != null
            && NEGeyserExtension.CONFIG.allowSingleChain();
        boolean singleChain = chain.getChain() != null && chain.getChain().size() == 1;
        this.singleChainLogin = singleChain;
        if (singleChain) {
            if (!allowSingleChain) {
                throw new IllegalStateException("NetEase login sent a single-entry certificate chain; "
                    + "enable allow-single-chain to accept it");
            }
            checkSingleChainRelay();
        }

        NeteaseProfile profile = NeteaseAuth.validate(chain,
            NEGeyserExtension.CONFIG != null && NEGeyserExtension.CONFIG.requireObtEnvironment(),
            allowSingleChain);
        neteaseSession.setUid(profile.uid());

        if (singleChain) {
            NEGeyserExtension.LOG.info("[NETEASE-AUTH] Accepted single-chain NetEase login for "
                + profile.displayName() + " from "
                + sourceAddress(session.getUpstream().getAddress()));
        }

        String displayName = profile.displayName();
        if (NEGeyserExtension.CONFIG != null && NEGeyserExtension.CONFIG.translateNeteaseNames()) {
            String asciiName = NeteaseNameTranslator.toAscii(displayName);
            if (debug() && !asciiName.equals(displayName)) {
                NEGeyserExtension.LOG.info("[NETEASE-NAME] translated " + displayName
                    + " -> " + asciiName);
            }
            displayName = asciiName;
        }

        session.setAuthData(new AuthData(displayName, profile.identity(), profile.xuid(),
            profile.issuedAt(), ""));
        session.setCertChainData(chain.getChain());

        BedrockClientData clientData = null;
        try {
            byte[] clientDataPayload = NeteaseAuth.verifyClientData(loginPacket.getClientJwt(), profile.identityPublicKey());
            if (clientDataPayload != null && clientDataPayload.length > 0) {
                String payloadJson = new String(clientDataPayload, StandardCharsets.UTF_8);
                JsonObject json = JsonParser.parseString(payloadJson).getAsJsonObject();
                if (!json.has("ServerAddress") || json.get("ServerAddress").isJsonNull()) {
                    // Geyser's joinAddress() requires this field; some NetEase clients omit it.
                    InetSocketAddress address = session.getUpstream().getAddress();
                    json.addProperty("ServerAddress", address.getHostString() + ":" + address.getPort());
                }
                clientData = GeyserImpl.GSON.fromJson(json, BedrockClientData.class);
                clientData.setOriginalString(loginPacket.getClientJwt());
            }
        } catch (Exception e) {
            if (geyser.config().debugMode()) {
                geyser.getLogger().debug("NetEase client data could not be verified/parsed: " + e.getMessage());
            }
        }
        if (clientData == null) {
            clientData = buildFallbackClientData(displayName);
        }
        session.setClientData(clientData);

        PublicKey clientKey = NeteaseCrypto.parseKey(profile.identityPublicKey());
        startEncryption(session, clientKey);
    }

    /**
     * Single-chain logins can only be produced by a relay that terminates the NetEase login.
     * Restrict them to the configured relay addresses when possible; an empty whitelist is
     * allowed for trusted direct connections but warns loudly.
     */
    private void checkSingleChainRelay() {
        String source = sourceAddress(session.getUpstream().getAddress());
        java.util.Set<String> trusted = NEGeyserExtension.CONFIG == null
            ? java.util.Set.of() : NEGeyserExtension.CONFIG.trustedRelayAddresses();

        if (trusted.isEmpty()) {
            geyser.getLogger().warning("allow-single-chain is enabled but trusted-relay-addresses "
                + "is empty; accepting a single-chain NetEase login from " + source + ". Do not "
                + "expose this port directly to the public internet; restrict it to your relay.");
            return;
        }

        if (!trusted.contains(source)) {
            throw new IllegalStateException("Single-chain NetEase login from untrusted address "
                + source + "; add it to trusted-relay-addresses");
        }
    }

    private static String sourceAddress(InetSocketAddress address) {
        if (address == null) {
            return "unknown";
        }
        return address.getAddress() == null
            ? address.getHostString()
            : address.getAddress().getHostAddress();
    }

    private void startEncryption(GeyserSession session, PublicKey clientKey) throws Exception {
        KeyPair serverKeyPair = NeteaseCrypto.createKeyPair();
        byte[] token = NeteaseCrypto.generateToken();

        ServerToClientHandshakePacket handshake = new ServerToClientHandshakePacket();
        handshake.setJwt(NeteaseCrypto.createHandshakeJwt(serverKeyPair, token));
        session.sendUpstreamPacketImmediately(handshake);

        SecretKey encryptionKey = NeteaseCrypto.getSecretKey(serverKeyPair, clientKey, token);
        session.getUpstream().getSession().enableEncryption(encryptionKey);
    }

    @Override
    public PacketSignal handle(ClientToServerHandshakePacket packet) {
        if (neteaseSession == null || !neteaseSession.loginDeferred()) {
            return super.handle(packet);
        }
        neteaseSession.setLoginDeferred(false);

        PlayStatusPacket loginSuccess = new PlayStatusPacket();
        loginSuccess.setStatus(PlayStatusPacket.Status.LOGIN_SUCCESS);
        session.sendUpstreamPacket(loginSuccess);

        this.resourcePackLoadEvent = new SessionLoadResourcePacksEventImpl(session);
        this.geyser.eventBus().fireEventElseKick(this.resourcePackLoadEvent, session);
        if (session.isClosed()) {
            return PacketSignal.HANDLED;
        }
        session.integratedPackActive(resourcePackLoadEvent.isIntegratedPackActive());

        ResourcePacksInfoPacket info = new ResourcePacksInfoPacket();
        info.getResourcePackInfos().addAll(this.resourcePackLoadEvent.infoPacketEntries());
        info.setVibrantVisualsForceDisabled(!session.isAllowVibrantVisuals());
        info.setForcedToAccept(geyser.config().gameplay().forceResourcePacks()
            || resourcePackLoadEvent.isIntegratedPackActive());
        // NetEase clients require a non-zero template id/version.
        info.setWorldTemplateId(UUID.randomUUID());
        info.setWorldTemplateVersion("*");

        // Track what this Info advertised so the single-chain early-3 flow can send DataInfo
        // for exactly those packs if the client acknowledges without downloading first.
        this.advertisedPacks = List.copyOf(info.getResourcePackInfos());
        this.resourcePacksInfoHasPacks = !this.advertisedPacks.isEmpty();
        this.resourcePackTransferStarted = false;
        cancelScheduledStack("new resource pack info");
        this.earlyResourcePackActive = false;
        this.lastEarlyPackCompletionNanos = 0L;
        this.earlyResourcePackStackSent = false;
        this.earlyResourcePacksPending.clear();
        this.earlyResourcePacksRequested.clear();

        session.sendUpstreamPacket(info);
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(ResourcePackClientResponsePacket packet) {
        if (neteaseSession == null) {
            return super.handle(packet);
        }
        if (session.getUpstream().isClosed() || session.isClosed()) {
            return PacketSignal.HANDLED;
        }
        if (finishedResourcePackSending) {
            return PacketSignal.HANDLED;
        }

        if (singleChainLogin || earlyResourcePackActive) {
            NEGeyserExtension.LOG.info("[NETEASE-RESOURCE] response status=" + packet.getStatus()
                + " packIds=" + (packet.getPackIds() == null ? 0 : packet.getPackIds().size())
                + " early=" + earlyResourcePackActive);
        }

        switch (packet.getStatus()) {
            case COMPLETED -> {
                if (singleChainLogin || earlyResourcePackActive) {
                    NEGeyserExtension.LOG.info("[NETEASE-RESOURCE] COMPLETED received; stackSent="
                        + earlyResourcePackStackSent + " pending=" + earlyResourcePacksPending
                        + " requested=" + earlyResourcePacksRequested);
                }
                finishedResourcePackSending = true;
                if (geyser.config().java().authType() != AuthType.ONLINE) {
                    session.authenticate(session.getAuthData().name());
                } else if (!couldLoginUserByName(session.getAuthData().name())) {
                    session.connect();
                }
                neteaseSession.setAuthenticated(true);
                geyser.getLogger().info(GeyserLocale.getLocaleStringLog("geyser.network.connect",
                    session.getAuthData().name() + " (" + neteaseSession.protocolVersion() + ", NetEase)"));
            }
            case SEND_PACKS -> {
                if (!packet.getPackIds().isEmpty()) {
                    packsToSend.addAll(packet.getPackIds());
                    sendPackDataInfo(packsToSend.pop());
                }
            }
            case HAVE_ALL_PACKS -> {
                if (earlyResourcePackActive) {
                    // The relay may acknowledge packs more than once while chunks are in flight.
                    if (debug()) {
                        NEGeyserExtension.LOG.info("[NETEASE-RESOURCE] ignoring duplicate status 3 "
                            + "during single-chain pack download");
                    }
                    return PacketSignal.HANDLED;
                }
                if (singleChainLogin && resourcePacksInfoHasPacks
                    && !resourcePackTransferStarted && packet.getPackIds().isEmpty()) {
                    startSingleChainPackDownload();
                } else {
                    sendResourcePackStack();
                }
            }
            case REFUSED -> session.disconnect("disconnectionScreen.resourcePack");
            default -> session.disconnect("disconnectionScreen.resourcePack");
        }
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(ResourcePackChunkRequestPacket packet) {
        if (neteaseSession == null) {
            return super.handle(packet);
        }
        chunkRequests.add(packet);
        if (!sendingChunks) {
            sendingChunks = true;
            processNextChunk();
        }
        return PacketSignal.HANDLED;
    }

    private void processNextChunk() {
        ResourcePackChunkRequestPacket packet = chunkRequests.poll();
        if (packet == null || session.isClosed()) {
            sendingChunks = false;
            return;
        }

        boolean early = earlyResourcePackActive;
        UUID packId = packet.getPackId();
        if (early) {
            // Any chunk activity restarts the debounce: the client may be about to request
            // the next pack, so an already-scheduled stack must not fire yet.
            cancelScheduledStack("chunk request");
            markEarlyPackRequested(packId);
        }

        boolean duplicate = early && isDuplicateChunk(packet);
        boolean resend = duplicate && NEGeyserExtension.CONFIG != null
            && NEGeyserExtension.CONFIG.singleChainResendChunks();
        if (duplicate && debug()) {
            NEGeyserExtension.LOG.info("[NETEASE-RESOURCE] duplicate chunk request pack=" + packId
                + " index=" + packet.getChunkIndex() + " resend=" + resend);
        }

        resourcePackTransferStarted = true;
        super.sendChunk(packet, resend);
        if (early) {
            trackEarlyPackProgress(packId);
            scheduleStackIfIdle();
        }

        int delay = NEGeyserExtension.CONFIG == null ? 0 : NEGeyserExtension.CONFIG.resourcePackChunkDelayMs();
        session.scheduleInEventLoop(this::processNextChunk, Math.max(1, delay), TimeUnit.MILLISECONDS);
    }

    private void markEarlyPackRequested(UUID packId) {
        if (resourcePackLoadEvent == null || !resourcePackLoadEvent.getPacks().containsKey(packId)) {
            return;
        }
        if (earlyResourcePacksRequested.add(packId)) {
            earlyResourcePacksPending.add(packId);
            if (debug()) {
                NEGeyserExtension.LOG.info("[NETEASE-RESOURCE] first chunk request for pack " + packId);
            }
        }
    }

    private boolean isDuplicateChunk(ResourcePackChunkRequestPacket packet) {
        int index = packet.getChunkIndex();
        if (index < 0) {
            return false;
        }
        BitSet sent = sentChunks.get(packet.getPackId());
        return sent != null && sent.get(index);
    }

    @Override
    protected void sendPackDataInfo(String id) {
        resourcePackTransferStarted = true;
        super.sendPackDataInfo(id);
    }

    /**
     * Single-chain relay sessions acknowledge the pack list (status 3) before the client has
     * downloaded anything. Send one DataInfo per advertised pack, then wait only for packs the
     * client actually requests. Clients with cached packs may request nothing at all.
     */
    private void startSingleChainPackDownload() {
        earlyResourcePackActive = true;
        NEGeyserExtension.LOG.info("[NETEASE-RESOURCE] single-chain early-3: sending pack data "
            + "info for " + advertisedPacks.size() + " pack(s) before stack");

        for (ResourcePacksInfoPacket.Entry entry : advertisedPacks) {
            UUID packId = entry.getPackId();
            ResourcePackHolder holder = resourcePackLoadEvent == null
                ? null : resourcePackLoadEvent.getPacks().get(packId);
            long size = holder == null ? -1 : holder.codec().size();
            int chunkCount = size <= 0 ? 0
                : (int) Math.ceil(size / (double) GeyserResourcePack.CHUNK_SIZE);
            NEGeyserExtension.LOG.info("[NETEASE-RESOURCE] pack " + packId
                + " version=" + entry.getPackVersion() + " size=" + size
                + " chunks=" + chunkCount);
            sendPackDataInfo(packId + "_" + entry.getPackVersion());
        }

        int fallbackMs = NEGeyserExtension.CONFIG == null
            ? 3000 : Math.max(0, NEGeyserExtension.CONFIG.singleChainPackFallbackMs());
        int maxWaitMs = NEGeyserExtension.CONFIG == null
            ? 10000 : Math.max(0, NEGeyserExtension.CONFIG.singleChainPackMaxWaitMs());

        NEGeyserExtension.LOG.info("[NETEASE-RESOURCE] waiting up to " + fallbackMs
            + "ms for the first chunk request, and at most " + maxWaitMs
            + "ms for requested packs");
        session.scheduleInEventLoop(this::onEarlyPackFallback, fallbackMs, TimeUnit.MILLISECONDS);
        session.scheduleInEventLoop(this::onEarlyPackMaxWait, maxWaitMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Some clients already have every advertised pack cached and never request chunks. Do not
     * block the stack waiting for chunks that will never arrive.
     */
    private void onEarlyPackFallback() {
        if (!earlyResourcePackActive || earlyResourcePackStackSent
            || finishedResourcePackSending || session.isClosed()) {
            return;
        }
        if (!earlyResourcePacksRequested.isEmpty()) {
            if (debug()) {
                NEGeyserExtension.LOG.info("[NETEASE-RESOURCE] fallback skipped: chunk requests "
                    + "were received; waiting for completion or max wait");
            }
            return;
        }

        int fallbackMs = NEGeyserExtension.CONFIG == null
            ? 3000 : Math.max(0, NEGeyserExtension.CONFIG.singleChainPackFallbackMs());
        NEGeyserExtension.LOG.info("[NETEASE-RESOURCE] fallback after " + fallbackMs
            + "ms without chunk requests; treating all packs as cached");
        logEarlyPackSummary("fallback");
        sendSingleChainStack("fallback: no chunk requests (cached)");
    }

    /**
     * Hard upper bound: never wait forever on a stuck or slow client.
     */
    private void onEarlyPackMaxWait() {
        if (!earlyResourcePackActive || earlyResourcePackStackSent
            || finishedResourcePackSending || session.isClosed()) {
            return;
        }

        int maxWaitMs = NEGeyserExtension.CONFIG == null
            ? 10000 : Math.max(0, NEGeyserExtension.CONFIG.singleChainPackMaxWaitMs());
        NEGeyserExtension.LOG.info("[NETEASE-RESOURCE] max wait " + maxWaitMs
            + "ms exceeded; sending stack with pending packs, if any");
        logEarlyPackSummary("max-wait");
        sendSingleChainStack("max-wait exceeded");
    }

    private void trackEarlyPackProgress(UUID packId) {
        if (earlyResourcePacksPending.isEmpty() || !earlyResourcePacksPending.contains(packId)
            || resourcePackLoadEvent == null) {
            return;
        }

        ResourcePackHolder holder = resourcePackLoadEvent.getPacks().get(packId);
        if (holder == null) {
            earlyResourcePacksPending.remove(packId);
            return;
        }

        int chunkCount = (int) Math.ceil(
            holder.codec().size() / (double) GeyserResourcePack.CHUNK_SIZE);
        BitSet sent = sentChunks.get(packId);
        if (sent != null && sent.cardinality() >= chunkCount) {
            earlyResourcePacksPending.remove(packId);
            if (debug()) {
                NEGeyserExtension.LOG.info("[NETEASE-RESOURCE] chunks complete for pack " + packId
                    + " (" + sent.cardinality() + "/" + chunkCount + ")");
            }
            if (earlyResourcePacksPending.isEmpty()) {
                lastEarlyPackCompletionNanos = System.nanoTime();
            }
        }
    }

    private void logEarlyPackSummary(String reason) {
        List<UUID> requested = new ArrayList<>(earlyResourcePacksRequested);
        List<UUID> cached = new ArrayList<>();
        for (ResourcePacksInfoPacket.Entry entry : advertisedPacks) {
            if (!earlyResourcePacksRequested.contains(entry.getPackId())) {
                cached.add(entry.getPackId());
            }
        }
        NEGeyserExtension.LOG.info("[NETEASE-RESOURCE] " + reason + ": requested=" + requested
            + " cached=" + cached + " pending=" + earlyResourcePacksPending);
    }

    /**
     * Called after a chunk request has been processed. Starts/restarts the stack debounce only
     * when every pack that was actually requested is complete. Cached (never requested) packs
     * do not block the stack.
     */
    private void scheduleStackIfIdle() {
        if (earlyResourcePackStackSent || !earlyResourcePacksPending.isEmpty()
            || earlyResourcePacksRequested.isEmpty()) {
            return;
        }
        logEarlyPackSummary("requested packs complete; (re)starting stack debounce");
        scheduleSingleChainStack();
    }

    private void scheduleSingleChainStack() {
        if (earlyResourcePackStackSent || !earlyResourcePacksPending.isEmpty()
            || earlyResourcePacksRequested.isEmpty()
            || session.isClosed() || finishedResourcePackSending) {
            return;
        }

        cancelScheduledStack("reschedule");
        long generation = ++earlyResourcePackGeneration;

        int delay = NEGeyserExtension.CONFIG == null
            ? 2000 : Math.max(0, NEGeyserExtension.CONFIG.singleChainPackStackDelayMs());
        NEGeyserExtension.LOG.info("[NETEASE-RESOURCE] scheduling stack in " + delay
            + "ms (generation=" + generation + ", requested="
            + earlyResourcePacksRequested.size() + ", pending="
            + earlyResourcePacksPending.size() + ")");

        earlyResourcePackStackTask = session.scheduleInEventLoop(() -> {
            if (generation != earlyResourcePackGeneration) {
                if (debug()) {
                    NEGeyserExtension.LOG.info("[NETEASE-RESOURCE] stale stack timer ignored "
                        + "(generation=" + generation + ", current="
                        + earlyResourcePackGeneration + ")");
                }
                return;
            }
            if (earlyResourcePackStackSent || session.isClosed() || finishedResourcePackSending) {
                return;
            }
            if (!earlyResourcePacksPending.isEmpty()) {
                NEGeyserExtension.LOG.info("[NETEASE-RESOURCE] stack timer fired but pending is "
                    + "not empty; waiting for max-wait instead: " + earlyResourcePacksPending);
                return;
            }
            sendSingleChainStack("debounce expired after " + delay + "ms");
        }, delay, TimeUnit.MILLISECONDS);
    }

    private void cancelScheduledStack(String reason) {
        ScheduledFuture<?> task = earlyResourcePackStackTask;
        earlyResourcePackStackTask = null;
        if (task != null && !task.isDone() && task.cancel(false)) {
            NEGeyserExtension.LOG.info("[NETEASE-RESOURCE] cancelled pending stack timer ("
                + reason + ")");
        }
        earlyResourcePackGeneration++;
    }

    private void sendSingleChainStack(String reason) {
        if (earlyResourcePackStackSent || session.isClosed() || finishedResourcePackSending) {
            return;
        }
        earlyResourcePackStackSent = true;
        earlyResourcePackStackTask = null;
        long elapsedMs = lastEarlyPackCompletionNanos == 0L ? -1L
            : (System.nanoTime() - lastEarlyPackCompletionNanos) / 1_000_000L;
        sendResourcePackStack();
        NEGeyserExtension.LOG.info("[NETEASE-RESOURCE] single-chain resource pack stack sent ("
            + reason + ", sinceLastPackCompletion="
            + (elapsedMs < 0L ? "n/a" : elapsedMs + "ms") + ")");
    }

    private void sendResourcePackStack() {
        ResourcePackStackPacket stack = new ResourcePackStackPacket();
        stack.setExperimentsPreviouslyToggled(false);
        stack.setForcedToAccept(true);
        stack.setGameVersion(session.getClientData() != null
            ? session.getClientData().getGameVersion() : "*");
        stack.getResourcePacks().addAll(this.resourcePackLoadEvent.orderedPacks());
        session.sendUpstreamPacket(stack);
    }

    @Override
    public PacketSignal handle(PlayerAuthInputPacket packet) {
        ItemStackRequest request = packet.getItemStackRequest();
        if (request != null) {
            if (debug()) {
                logRequest(request);
            }
            ItemStackRequest remapped = remapCreativeContainers(request);
            if (remapped != request) {
                packet.setItemStackRequest(remapped);
                if (debug()) {
                    logRequest(remapped);
                }
            }
        }
        if (debug() && packet.getItemUseTransaction() != null) {
            var use = packet.getItemUseTransaction();
            String item = use.getItemInHand() == null || use.getItemInHand().getDefinition() == null
                ? "null" : use.getItemInHand().getDefinition().getIdentifier()
                    + "x" + use.getItemInHand().getCount();
            NEGeyserExtension.LOG.info("[NETEASE-USE] hotbar=" + use.getHotbarSlot()
                + " pos=" + use.getBlockPosition() + " face=" + use.getBlockFace()
                + " action=" + use.getActionType() + " item=" + item);
        }
        return super.handle(packet);
    }

    @Override
    public PacketSignal handle(BookEditPacket packet) {
        if (debug()) {
            var inHand = session.getPlayerInventory().getItemInHand();
            String held = inHand == null || inHand.isEmpty() ? "empty"
                : session.getItemMappings().getMapping(inHand).getBedrockIdentifier();
            NEGeyserExtension.LOG.info("[NETEASE-BOOK] action=" + packet.getAction()
                + " slot=" + packet.getInventorySlot()
                + " heldSlot=" + session.getPlayerInventory().getHeldItemSlot()
                + " inHand=" + held
                + " page=" + packet.getPageNumber()
                + " textLen=" + (packet.getText() == null ? 0 : packet.getText().length())
                + " title=" + packet.getTitle()
                + " author=" + packet.getAuthor());
        }
        return super.handle(packet);
    }

    @Override
    public PacketSignal handle(ItemStackRequestPacket packet) {
        List<ItemStackRequest> requests = packet.getRequests();
        for (int i = 0; i < requests.size(); i++) {
            ItemStackRequest request = requests.get(i);
            if (debug()) {
                logRequest(request);
            }
            ItemStackRequest remapped = remapCreativeContainers(request);
            if (remapped != request) {
                requests.set(i, remapped);
                if (debug()) {
                    logRequest(remapped);
                }
            }
        }
        return super.handle(packet);
    }

    /**
     * NetEase encodes CREATED_OUTPUT as a different container slot type value than vanilla, so the
     * client's creative take request decodes as SMITHING_TABLE_TEMPLATE (slot 50). Remap it back
     * before Geyser's creative request translator sees it.
     */
    private ItemStackRequest remapCreativeContainers(ItemStackRequest request) {
        boolean creative = false;
        for (ItemStackRequestAction action : request.getActions()) {
            if (action instanceof CraftCreativeAction) {
                creative = true;
                break;
            }
        }

        ItemStackRequestAction[] actions = request.getActions();
        ItemStackRequestAction[] replaced = null;
        for (int i = 0; i < actions.length; i++) {
            ItemStackRequestAction action = actions[i];
            ItemStackRequestAction fixed = remapAction(action, creative);
            if (fixed != action) {
                if (replaced == null) {
                    replaced = actions.clone();
                }
                replaced[i] = fixed;
            }
        }
        if (replaced == null) {
            return request;
        }
        return new ItemStackRequest(request.getRequestId(), replaced, request.getFilterStrings(),
            request.getTextProcessingEventOrigin());
    }

    private ItemStackRequestAction remapAction(ItemStackRequestAction action, boolean creative) {
        if (action instanceof TakeAction take) {
            ItemStackRequestSlotData source = remapSlot(take.getSource(), creative);
            ItemStackRequestSlotData destination = remapSlot(take.getDestination(), creative);
            return source == take.getSource() && destination == take.getDestination()
                ? action : new TakeAction(take.getCount(), source, destination);
        }
        if (action instanceof PlaceAction place) {
            ItemStackRequestSlotData source = remapSlot(place.getSource(), creative);
            ItemStackRequestSlotData destination = remapSlot(place.getDestination(), creative);
            return source == place.getSource() && destination == place.getDestination()
                ? action : new PlaceAction(place.getCount(), source, destination);
        }
        if (action instanceof SwapAction swap) {
            ItemStackRequestSlotData source = remapSlot(swap.getSource(), creative);
            ItemStackRequestSlotData destination = remapSlot(swap.getDestination(), creative);
            return source == swap.getSource() && destination == swap.getDestination()
                ? action : new SwapAction(source, destination);
        }
        if (action instanceof DropAction drop) {
            ItemStackRequestSlotData source = remapSlot(drop.getSource(), creative);
            return source == drop.getSource() ? action
                : new DropAction(drop.getCount(), source, drop.isRandomly());
        }
        if (action instanceof DestroyAction destroy) {
            ItemStackRequestSlotData source = remapSlot(destroy.getSource(), creative);
            return source == destroy.getSource() ? action
                : new DestroyAction(destroy.getCount(), source);
        }
        if (action instanceof ConsumeAction consume) {
            ItemStackRequestSlotData source = remapSlot(consume.getSource(), creative);
            return source == consume.getSource() ? action
                : new ConsumeAction(consume.getCount(), source);
        }
        return action;
    }

    private ItemStackRequestSlotData remapSlot(ItemStackRequestSlotData slot, boolean creative) {
        ContainerSlotType container = slot.getContainerName().getContainer();

        // NetEase inserts an extra RECIPE_CUSTOM value at wire index 17, shifting every
        // container at or above RECIPE_ITEMS by one.
        ContainerSlotType mapped = NeteaseContainers.fromWireDecoded(container);

        // Every output/result slot decoded from NetEase uses the same high value (which
        // vanilla maps to SMITHING_TABLE_TEMPLATE until shifted). Map it to the result
        // container of the inventory that is currently open.
        if (slot.getSlot() == 50
            && (mapped == ContainerSlotType.CREATED_OUTPUT
                || mapped == ContainerSlotType.SMITHING_TABLE_TEMPLATE
                || mapped == ContainerSlotType.CRAFTER_BLOCK_CONTAINER
                || mapped == ContainerSlotType.DYNAMIC_CONTAINER)) {
            mapped = outputContainer(creative);
        }
        if (mapped == container) {
            return slot;
        }
        return new ItemStackRequestSlotData(mapped, slot.getSlot(), slot.getStackNetworkId(),
            new FullContainerName(mapped, slot.getContainerName().getDynamicId()));
    }

    private ContainerSlotType outputContainer(boolean creative) {
        if (creative) {
            return ContainerSlotType.CREATED_OUTPUT;
        }
        InventoryHolder<?> holder = session.getInventoryHolder();
        if (holder == null) {
            return ContainerSlotType.CREATED_OUTPUT;
        }
        String translator = holder.translator().getClass().getSimpleName();
        if (translator.contains("Anvil")) {
            return ContainerSlotType.ANVIL_RESULT;
        }
        if (translator.contains("Smithing")) {
            return ContainerSlotType.SMITHING_TABLE_RESULT;
        }
        if (translator.contains("Stonecutter")) {
            return ContainerSlotType.STONECUTTER_RESULT;
        }
        if (translator.contains("Cartography")) {
            return ContainerSlotType.CARTOGRAPHY_RESULT;
        }
        if (translator.contains("Loom")) {
            return ContainerSlotType.LOOM_RESULT;
        }
        return ContainerSlotType.CREATED_OUTPUT;
    }

    private static boolean debug() {
        return NEGeyserExtension.CONFIG != null && NEGeyserExtension.CONFIG.debugMode();
    }

    private static void logRequest(ItemStackRequest request) {
        StringBuilder message = new StringBuilder("[NETEASE-REQ] id=")
            .append(request.getRequestId()).append(" actions=[");
        ItemStackRequestAction[] actions = request.getActions();
        for (int i = 0; i < actions.length; i++) {
            if (i > 0) {
                message.append(", ");
            }
            ItemStackRequestAction action = actions[i];
            message.append(action.getType());
            if (action instanceof CraftCreativeAction creative) {
                message.append("(creative=").append(creative.getCreativeItemNetworkId())
                    .append(", crafts=").append(creative.getNumberOfRequestedCrafts())
                    .append(')');
            } else if (action instanceof TransferItemStackRequestAction transfer) {
                message.append("(count=").append(transfer.getCount())
                    .append(", ").append(describeSlot(transfer.getSource()))
                    .append(" -> ").append(describeSlot(transfer.getDestination()))
                    .append(')');
            }
        }
        if (request.getFilterStrings() != null && request.getFilterStrings().length > 0) {
            message.append(" filters=").append(java.util.Arrays.toString(request.getFilterStrings()));
        }
        NEGeyserExtension.LOG.info(message.append(']').toString());
    }

    private static String describeSlot(
        org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequestSlotData slot) {
        return slot.getContainerName().getContainer() + ":" + slot.getSlot()
            + "/net=" + slot.getStackNetworkId();
    }

    @Override
    public PacketSignal handle(InventoryTransactionPacket packet) {
        if (debug()) {
            String item = packet.getItemInHand() == null || packet.getItemInHand().getDefinition() == null
                ? "null" : packet.getItemInHand().getDefinition().getIdentifier()
                    + "x" + packet.getItemInHand().getCount();
            var inHand = session.getPlayerInventory().getItemInHand();
            String held = inHand == null || inHand.isEmpty() ? "empty"
                : session.getItemMappings().getMapping(inHand).getBedrockIdentifier();
            NEGeyserExtension.LOG.info("[NETEASE-TX] type=" + packet.getTransactionType()
                + " action=" + packet.getActionType()
                + " actions=" + packet.getActions().size()
                + " item=" + item
                + " hotbar=" + packet.getHotbarSlot()
                + " heldSlot=" + session.getPlayerInventory().getHeldItemSlot()
                + " inHand=" + held
                + " pos=" + packet.getBlockPosition() + " face=" + packet.getBlockFace());
        }
        // NetEase clients implement dropping an item with a WORLD_INTERACTION + CONTAINER pair.
        if (neteaseSession != null && packet.getTransactionType() == InventoryTransactionType.NORMAL
            && packet.getActions().size() == 2) {
            List<InventoryActionData> actions = packet.getActions();
            InventoryActionData first = actions.get(0);
            InventoryActionData second = actions.get(1);
            InventoryActionData world = first.getSource().getType() == InventorySource.Type.WORLD_INTERACTION ? first : second;
            InventoryActionData container = world == first ? second : first;

            if (world.getSource().getType() == InventorySource.Type.WORLD_INTERACTION
                && container.getSource().getType() == InventorySource.Type.CONTAINER) {
                org.geysermc.geyser.inventory.GeyserItemStack inHand = session.getPlayerInventory().getItemInHand();
                if (inHand.isEmpty()) {
                    return PacketSignal.HANDLED;
                }
                boolean dropAll = world.getToItem() != null && world.getToItem().getCount() > 1;
                session.sendDownstreamGamePacket(new ServerboundPlayerActionPacket(
                    dropAll ? PlayerAction.DROP_ITEM_STACK : PlayerAction.DROP_ITEM,
                    Vector3i.ZERO, Direction.DOWN, 0));
                if (dropAll) {
                    session.getPlayerInventory().setItemInHand(org.geysermc.geyser.inventory.GeyserItemStack.EMPTY);
                } else {
                    session.getPlayerInventory().getItemInHand().sub(1);
                }
                return PacketSignal.HANDLED;
            }
        }
        return super.handle(packet);
    }

    private boolean isNeteaseConnection() {
        try {
            Integer version = session.getUpstream().getSession().getPeer().getChannel()
                .config().getOption(RakChannelOption.RAK_PROTOCOL_VERSION);
            return version != null && version == 8;
        } catch (Exception ignored) {
            return false;
        }
    }

    private BedrockClientData buildFallbackClientData(String displayName) {
        BedrockCodec codec = neteaseSession == null ? null : neteaseSession.codec();
        String version = codec == null ? "1.21.130" : codec.getMinecraftVersion();
        String json = "{\"GameVersion\":\"" + version + "\",\"LanguageCode\":\"en_us\",\"DeviceOS\":1,"
            + "\"DeviceModel\":\"NetEase\",\"ThirdPartyName\":\"" + displayName.replace("\"", "") + "\","
            + "\"UIProfile\":0,\"CurrentInputMode\":1,\"DefaultInputMode\":1,\"GuiScale\":0,"
            + "\"SkinId\":\"\",\"SkinData\":\"\",\"SkinImageWidth\":64,\"SkinImageHeight\":64,"
            + "\"CapeId\":\"\",\"CapeData\":\"\",\"CapeImageWidth\":64,\"CapeImageHeight\":32,"
            + "\"SkinGeometryData\":\"\",\"SkinResourcePatch\":\"\",\"PersonaSkin\":false,\"PremiumSkin\":false,"
            + "\"ArmSize\":\"wide\",\"SkinColor\":\"#0\"}";
        return GeyserImpl.GSON.fromJson(json, BedrockClientData.class);
    }

}
