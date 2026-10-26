# Registry Builders

`ModEntryBuilder` exposes one-call factories for several vanilla registries plus per-world / per-entity persistent data. These are **leaf registrations**: they return their own `DeferredHolder` directly and are **not** funneled through the `ModEntry` record. Store the returned holder in a `static final` field, just like a normal `DeferredRegister` entry.

All of these register into `DeferredRegister`s declared in `setup/Registers.java` (`PARTICLE_TYPES`, `ENTITY_TYPES`, `SOUND_EVENTS`, `MOB_EFFECTS`, `ATTACHMENT_TYPES`), which are registered to the mod event bus in `Registers.init`.

## Factory methods

| Method | Returns | Registry |
|---|---|---|
| `addParticleType(name, overrideLimiter)` | `DeferredHolder<ParticleType<?>, SimpleParticleType>` | `PARTICLE_TYPES` |
| `addEntityType(name, EntityType.Builder<T>)` | `DeferredHolder<EntityType<?>, EntityType<T>>` | `ENTITY_TYPES` |
| `addSoundEvent(name)` | `DeferredHolder<SoundEvent, SoundEvent>` | `SOUND_EVENTS` |
| `addMobEffect(name, Supplier<MobEffect>)` | `DeferredHolder<MobEffect, MobEffect>` | `MOB_EFFECTS` |
| `addWorldCapability(name, Supplier<AttachmentType<T>>)` | `DeferredHolder<AttachmentType<?>, AttachmentType<T>>` | `ATTACHMENT_TYPES` |
| `addEntityCapability(name, Supplier<AttachmentType<T>>)` | `DeferredHolder<AttachmentType<?>, AttachmentType<T>>` | `ATTACHMENT_TYPES` |

## Examples

Define holders in `setup/ModEntries.java` (or any class loaded during registration):

```java
import static igentuman.modtemplate.registration.ModEntryBuilder.*;

// Particle - overrideLimiter=true ignores the client particle limit
public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPARK =
        addParticleType("spark", true);

// Sound
public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_HUM =
        addSoundEvent("machine_hum");

// Mob effect - MobEffect's constructor is protected, so subclass it
public static final DeferredHolder<MobEffect, MobEffect> IRRADIATED =
        addMobEffect("irradiated", () -> new MobEffect(MobEffectCategory.HARMFUL, 0x33FF33) {});

// Entity - pass a configured EntityType.Builder; build(name) is called for you
public static final DeferredHolder<EntityType<?>, EntityType<MyProjectile>> MY_PROJECTILE =
        addEntityType("my_projectile",
                EntityType.Builder.<MyProjectile>of(MyProjectile::new, MobCategory.MISC)
                        .sized(0.25f, 0.25f));
```

## World and entity data (AttachmentType)

NeoForge 1.21.1 has no "level capability" type; per-world and per-entity persistent data both use `AttachmentType`. The holder is attached at the **use site**, not at registration:

```java
// Register the attachment (serializable so it persists)
public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> MANA =
        addEntityCapability("mana", () ->
                AttachmentType.builder(() -> 0)
                        .serialize(Codec.INT)
                        .build());

// Read / write per-entity
int mana = entity.getData(MANA.get());
entity.setData(MANA.get(), mana + 1);
```

`addWorldCapability` is identical in mechanics - use it for data you attach to `Level` / `LevelChunk` (`level.getData(holder)` / `chunk.getData(holder)`). The two methods differ only by intent.

## Client-side wiring is your responsibility

These builders register the **types** only. Anything client-side must still be wired by you in your own setup listeners - the template does not auto-wire it:

- **Particles** - register a `ParticleProvider` in `RegisterParticleProvidersEvent`.
- **Entities** - register a renderer in `EntityRenderersEvent.RegisterRenderers` and attributes (for living entities) in `EntityAttributeCreationEvent`.
- **Mob effects / sounds** - no extra client wiring required beyond translation keys.
