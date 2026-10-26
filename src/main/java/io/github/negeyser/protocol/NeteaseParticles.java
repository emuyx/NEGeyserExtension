/*
 * Copyright (c) 2026 NEGeyserExtension contributors
 * Released under the MIT License.
 */

package io.github.negeyser.protocol;

import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.BedrockPacketDefinition;
import org.cloudburstmc.protocol.bedrock.codec.EntityDataTypeMap;
import org.cloudburstmc.protocol.bedrock.data.LevelEventType;
import org.cloudburstmc.protocol.bedrock.data.ParticleType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket;
import org.cloudburstmc.protocol.bedrock.transformer.TypeMapTransformer;
import org.cloudburstmc.protocol.common.util.TypeMap;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

/**
 * NetEase particle id translation.
 *
 * <p>NetEase clients insert one extra particle after {@code SNEEZE}, so every particle whose
 * vanilla wire id is greater than {@code SNEEZE}'s id is sent with {@code id + 1}. This mirrors
 * Nukkit-MOT's {@code Particle#getMultiversionId}: only defined particle ids are shifted, and
 * {@code SNEEZE} itself is not. Nukkit-MOT applies the shift in exactly two places, and so does
 * this class:</p>
 * <ul>
 *     <li>particle {@code LevelEventPacket}s (the {@code 0x4000 | particleId} event ids);</li>
 *     <li>the {@code AREA_EFFECT_CLOUD_PARTICLE} entity metadata value.</li>
 * </ul>
 *
 * <p>Instead of rewriting each outgoing packet, the NetEase codec's particle {@link TypeMap}s are
 * replaced with shifted copies. Encoding then emits the shifted ids directly and does not depend
 * on the session (the NetEase codecs are only used by NetEase clients, so the vanilla codecs are
 * untouched).</p>
 */
public final class NeteaseParticles {

    /** Vanilla wire ids for particle level events are offset by this value. */
    private static final int LEVEL_EVENT_PARTICLE_BASE = 0x4000;

    private static volatile boolean warned;

    private NeteaseParticles() {
    }

    /**
     * Shifted maps for one NetEase codec, or {@code null} when the codec could not be inspected.
     */
    public static final class Maps {
        private final TypeMap<LevelEventType> levelEvents;
        private final TypeMap<ParticleType> particles;

        private Maps(TypeMap<LevelEventType> levelEvents, TypeMap<ParticleType> particles) {
            this.levelEvents = levelEvents;
            this.particles = particles;
        }

        public TypeMap<LevelEventType> levelEvents() {
            return levelEvents;
        }

        public TypeMap<ParticleType> particles() {
            return particles;
        }
    }

    /**
     * Builds shifted particle maps from the codec's vanilla {@code LEVEL_EVENTS} map.
     *
     * @return the shifted maps, or {@code null} if the codec layout could not be read
     */
    public static Maps build(BedrockCodec codec) {
        try {
            //noinspection unchecked,rawtypes
            BedrockPacketDefinition<LevelEventPacket> definition =
                (BedrockPacketDefinition) codec.getPacketDefinition(LevelEventPacket.class);
            if (definition == null) {
                return null;
            }
            //noinspection unchecked
            TypeMap<LevelEventType> original =
                (TypeMap<LevelEventType>) findLevelEventTypeMap(definition.getSerializer());
            if (original == null) {
                return null;
            }
            int sneezeId = original.getIdUnsafe(ParticleType.SNEEZE);
            if (sneezeId < 0) {
                return null;
            }

            TypeMap.Builder<LevelEventType> levelEvents = TypeMap.builder(LevelEventType.class);
            TypeMap.Builder<ParticleType> particles = TypeMap.builder(ParticleType.class);
            original.forEach((id, type) -> {
                if (id >= LEVEL_EVENT_PARTICLE_BASE && type instanceof ParticleType particle) {
                    int particleId = id - LEVEL_EVENT_PARTICLE_BASE;
                    // Nukkit-MOT: particles after SNEEZE move up by one on NetEase clients.
                    int targetId = particleId > sneezeId - LEVEL_EVENT_PARTICLE_BASE
                        ? id + 1 : id;
                    levelEvents.insert(targetId, particle);
                    particles.insert(targetId - LEVEL_EVENT_PARTICLE_BASE, particle);
                } else {
                    levelEvents.insert(id, type);
                }
            });

            if (io.github.negeyser.NEGeyserExtension.LOG != null) {
                io.github.negeyser.NEGeyserExtension.LOG.info(
                    "[NETEASE-PARTICLE] installed NetEase particle ids (shift after "
                        + ParticleType.SNEEZE + "=" + (sneezeId - LEVEL_EVENT_PARTICLE_BASE) + ")");
            }
            return new Maps(levelEvents.build(), particles.build());
        } catch (Throwable e) {
            if (!warned) {
                warned = true;
                if (io.github.negeyser.NEGeyserExtension.LOG != null) {
                    io.github.negeyser.NEGeyserExtension.LOG.warning(
                        "Could not install the NetEase particle map; particle ids stay unshifted: "
                            + e.getMessage());
                }
            }
            return null;
        }
    }

    /**
     * Copies the helper's entity data map with a particle collection that uses the shifted ids,
     * so {@code AREA_EFFECT_CLOUD_PARTICLE} is encoded the way the NetEase client expects.
     */
    public static EntityDataTypeMap shiftedEntityData(BedrockCodecHelper helper,
                                                      TypeMap<ParticleType> particles) {
        EntityDataTypeMap original = findEntityDataTypeMap(helper);
        if (original == null || particles == null) {
            return null;
        }
        try {
            return original.toBuilder()
                .update(EntityDataTypes.AREA_EFFECT_CLOUD_PARTICLE,
                    new TypeMapTransformer<>(particles))
                .build();
        } catch (Throwable e) {
            if (io.github.negeyser.NEGeyserExtension.LOG != null) {
                io.github.negeyser.NEGeyserExtension.LOG.warning(
                    "Could not shift AREA_EFFECT_CLOUD_PARTICLE ids: " + e.getMessage());
            }
            return null;
        }
    }

    /** Replaces the helper's entity data map. No-op when {@code map} is {@code null}. */
    public static void installEntityData(BedrockCodecHelper helper, EntityDataTypeMap map) {
        if (map == null) {
            return;
        }
        try {
            Field field = null;
            for (Class<?> type = helper.getClass(); type != null; type = type.getSuperclass()) {
                try {
                    field = type.getDeclaredField("entityData");
                    break;
                } catch (NoSuchFieldException ignored) {
                    // Continue up the hierarchy.
                }
            }
            if (field == null) {
                throw new NoSuchFieldException("entityData");
            }
            UnsafeHolder.UNSAFE.putObject(helper,
                UnsafeHolder.UNSAFE.objectFieldOffset(field), map);
        } catch (Throwable e) {
            if (!warned) {
                warned = true;
                if (io.github.negeyser.NEGeyserExtension.LOG != null) {
                    io.github.negeyser.NEGeyserExtension.LOG.warning(
                        "Could not install the NetEase particle metadata map: " + e.getMessage());
                }
            }
        }
    }

    private static TypeMap<?> findLevelEventTypeMap(Object serializer) {
        if (serializer == null) {
            return null;
        }
        for (Class<?> type = serializer.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField("typeMap");
                field.setAccessible(true);
                Object value = field.get(serializer);
                return value instanceof TypeMap<?> map ? map : null;
            } catch (NoSuchFieldException ignored) {
                // Continue up the hierarchy.
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static EntityDataTypeMap findEntityDataTypeMap(BedrockCodecHelper helper) {
        if (helper == null) {
            return null;
        }
        for (Class<?> type = helper.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField("entityData");
                field.setAccessible(true);
                Object value = field.get(helper);
                return value instanceof EntityDataTypeMap map ? map : null;
            } catch (NoSuchFieldException ignored) {
                // Continue up the hierarchy.
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static final class UnsafeHolder {
        private static final Unsafe UNSAFE;

        static {
            try {
                Field field = Unsafe.class.getDeclaredField("theUnsafe");
                field.setAccessible(true);
                UNSAFE = (Unsafe) field.get(null);
            } catch (Throwable e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }
}
