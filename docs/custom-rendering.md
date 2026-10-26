# Custom Rendering: Billboards & Distortion Shader

Two reusable client-side rendering tools live in `client/renderer/`:

- **`BillboardingEffectRenderer`** - draws a camera-facing (billboarded) animated sprite at any world position.
- **`DistortShader`** - a screen-space lens-distortion post effect applied around any world position(s).

Both are generic: bind them to any block, block entity, or arbitrary point. Nothing is hard-coded to a specific feature.

## Components

| File | Role |
|---|---|
| `client/renderer/CustomEffect` | Descriptor for a billboard sprite (texture, grid size, position, color, scale). |
| `client/renderer/BillboardingEffectRenderer` | Submits a `CustomEffect` to be drawn this frame. |
| `client/renderer/DelayedRenderHandler` | Per-frame queue; flushes billboards at `AFTER_TRANSLUCENT_BLOCKS`, depth-sorted. |
| `client/renderer/ModRenderType` | `BILLBOARD` render type (memoized per texture). |
| `client/renderer/ModShaders` | Registers the `BILLBOARD` core shader + the `distort` post chain. |
| `client/renderer/DistortShader` | Registry of distortion sources + the post-process render pass. |
| `assets/modtemplate/shaders/core/rendertype_billboard.*` | Core shader for billboards (`POSITION_TEX_COLOR`). |
| `assets/modtemplate/shaders/post/distort.json`, `shaders/program/distort.*` | Post chain + GLSL for the lens distortion. |

Registration is wired in `setup/Client.java`:

```java
// ModShaders auto-registers via @EventBusSubscriber (RegisterShadersEvent).
// The two game-bus handlers are registered in client setup:
@SubscribeEvent
static void onClientSetup(FMLClientSetupEvent event) {
    event.enqueueWork(() -> {
        DelayedRenderHandler.register();
        DistortShader.register();
    });
}
```

---

## BillboardingEffectRenderer

Call `submit` **every frame** for each effect (the queue is cleared each frame). Typical callers: a `BlockEntityRenderer`, an entity renderer, or a client tick.

```java
CustomEffect fx = new CustomEffect(Main.rl("textures/effect/myfx.png"), 4); // 4x4 sprite sheet
fx.setPos(Vec3.atCenterOf(pos).add(0, 0.6, 0));
fx.setScale(0.5F);
fx.setColor(190, 70, 255, 200); // RGBA, tints every vertex
BillboardingEffectRenderer.submit(fx);
```

- **Texture grid**: the texture is a square `gridSize x gridSize` sheet; the current frame advances with the render tick. Use `gridSize = 1` for a static sprite (e.g. the vanilla `minecraft:textures/misc/white.png` tinted via `setColor`).
- **Batching**: all submissions for the same texture share one render type and are drawn together, sorted far-to-near for correct translucency.

---

## DistortShader

Register a `DistortionSource` keyed by any object (a `BlockPos`, entity UUID, etc.). It stays active until removed.

```java
// Start the effect:
DistortShader.add(pos, new DistortShader.DistortionSource() {
    @Override public Vec3 position(float partialTick) { return Vec3.atCenterOf(pos); }
    @Override public float radius()        { return 120F; } // base lens radius (px, scaled by distance)
    @Override public float magnification() { return 4F; }   // > 1 magnifies the center
});

// Stop the effect:
DistortShader.remove(pos);
```

`DistortionSource` is a functional interface (only `position` is required); override `radius`, `magnification`, `isActive`, or `maxDistance` to tune. Toggle the whole effect with `DistortShader.enabled`.

The shader samples the depth buffer, so geometry in front of the source occludes the effect. Visual constants (glow color, swirl, dark core) live in `shaders/program/distort.fsh` - edit them freely.

---

## Worked example: an "Anomaly" block

A full chain - custom block + block entity + custom renderer - that shows the visible billboard core (BlockEntityRenderer) and the lens distortion (driven by the BE lifecycle).

### 1. Block - `block/AnomalyBlock.java`

```java
public class AnomalyBlock extends BaseEntityBlock {
    public static MapCodec<AnomalyBlock> CODEC;
    private final String name;

    public AnomalyBlock(String name) {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PURPLE).strength(3.5F)
                .requiresCorrectToolForDrops().lightLevel(s -> 7).sound(SoundType.AMETHYST));
        this.name = name;
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() {
        if (CODEC == null) CODEC = simpleCodec(props -> new AnomalyBlock(name));
        return CODEC;
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AnomalyBE(pos, state, name);
    }

    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
}
```

### 2. Block entity - `block_entity/AnomalyBE.java`

The BE owns the distortion source: it adds it on client load and removes it on unload/break. All client calls are guarded by `level.isClientSide` so nothing runs on a dedicated server.

```java
public class AnomalyBE extends GlobalBlockEntity {

    public AnomalyBE(BlockPos pos, BlockState state, String name) {
        super(ModEntries.get(name).blockEntity().get(), pos, state, name);
    }

    @Override public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) {
            DistortShader.add(getBlockPos(), new DistortShader.DistortionSource() {
                @Override public Vec3 position(float partialTick) { return Vec3.atCenterOf(getBlockPos()); }
                @Override public float radius()        { return 120F; }
                @Override public float magnification() { return 4F; }
            });
        }
    }

    @Override public void setRemoved() {
        if (level != null && level.isClientSide) DistortShader.remove(getBlockPos());
        super.setRemoved();
    }
}
```

> Using `GlobalBlockEntity` here is optional - a plain `BlockEntity` works too, since this example defines no item/fluid/energy caps. The base is null-cap-safe.

### 3. Renderer - `client/renderer/AnomalyRenderer.java`

The custom renderer submits the billboard core each frame. (The distortion is global post-processing, managed by the BE above.)

```java
public class AnomalyRenderer implements BlockEntityRenderer<AnomalyBE> {
    private static final ResourceLocation CORE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");

    public AnomalyRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(AnomalyBE be, float partialTick, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
        CustomEffect core = new CustomEffect(CORE, 1);
        core.setPos(Vec3.atCenterOf(be.getBlockPos()).add(0, 0.6, 0));
        core.setScale(0.5F);
        core.setColor(190, 70, 255, 200);
        BillboardingEffectRenderer.submit(core);
    }

    @Override public boolean shouldRenderOffScreen(AnomalyBE be) { return true; }
    @Override public int getViewDistance() { return 128; }
}
```

### 4. Register the entry - `setup/ModEntries.java`

```java
public static final ModEntry ANOMALY = add("anomaly")
        .block(AnomalyBlock::new)
        .blockEntity(AnomalyBE::new)
        .build();
```

### 5. Register the renderer - `setup/Client.java`

```java
@SuppressWarnings("unchecked")
@SubscribeEvent
static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
    event.registerBlockEntityRenderer(
            (BlockEntityType<AnomalyBE>) (BlockEntityType<?>) ModEntries.ANOMALY.blockEntity().get(),
            AnomalyRenderer::new);
}
```

### 6. Assets

Hand-write the blockstate + block model so the datagen `BlockStateProvider` skips generation (no texture PNG required - it reuses a vanilla texture). Item model, lang, and loot are generated automatically by running `./gradlew runData`.

```jsonc
// assets/modtemplate/blockstates/anomaly.json
{ "variants": { "": { "model": "modtemplate:block/anomaly" } } }

// assets/modtemplate/models/block/anomaly.json
{ "parent": "block/cube_all", "textures": { "all": "minecraft:block/amethyst_block" } }
```

Place the block (Creative → Functional Blocks → **Anomaly**): a purple cube with a camera-facing core and a lens distortion warping the scene around it.
