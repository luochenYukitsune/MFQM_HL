# 审查范围和文件清单

本轮针对整个工作区源码审查，基线为 `17e0349f1706545aaab5ef5d6f4b25fdd71edd0a`。下列清单由当前文件树生成，包含本轮新增修复和回归。没有把只搜索关键词当作完整源码读取。

## 人工读取与复核

- Java 主源码 141 个文件，含生产代码及通过显式选项启用的运行回归夹具；独立 Java 测试 12 个文件。
- Python 工具和测试 23 个文件，PowerShell 检查脚本 4 个文件；同时读取 Gradle 构建、版本属性、Mixin、访问转换和模组元数据。
- 主代理读取客户端几何、资源缓存、可选模组桥、网络与配置、工具和所有差异；复用一个只读审查代理检查玩法、实体、物品、世界生成、方块实体及相关测试。修复后的完整差异再次复核，无新增阻断项。
- 检查的行为包括：服务端权限和频率限制、客户端预测、地面行走和短跳、附着根部、下陷与呼吸、挣扎与救援、装备及桶的转移、实体保存和移除、取消／跨维度传送、生成边界、可选依赖缺席和资源重载。

## 资源检查

运行资源包含主资源 JSON 1655 个、生成 JSON 19 个、PNG 339 张。资源采用全量解析、路径与引用校验、JAR 内容比对、模型组合验证、透明度与解剖蒙版验证及实际客户端加载；没有逐张人工查看所有历史图片。

128 种相邻粘鼠板模型组合、50 张按接触高度裁切的身上覆盖、100 张历史材料、16 张新增胶水材料、256 格透明度查找图通过专用检查。339 张运行 PNG 与修改前备份逐字节相同。

没有重新逐包运行所有光影组合，没有为 8192 根极限显示数做性能基准；这些限制保留在最终报告中。

## 当前源码文件

以下为逐包文件清单，路径相对于仓库根目录。

### src/main/java (.java)

- `src/main/java/com/mfqm/morefunquicksandmod/block/LegacyStateBlock.java`
- `src/main/java/com/mfqm/morefunquicksandmod/block/MechanismBlock.java`
- `src/main/java/com/mfqm/morefunquicksandmod/block/StickyBoardBlock.java`
- `src/main/java/com/mfqm/morefunquicksandmod/block/StickyBoardConnections.java`
- `src/main/java/com/mfqm/morefunquicksandmod/block/StickyBoardPortChecks.java`
- `src/main/java/com/mfqm/morefunquicksandmod/blockentity/MechanismBlockEntity.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/AdhesiveClientScene.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/AdhesiveDisplayBudget.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/AdhesiveFeetSampler.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/AdhesiveTetherRenderer.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/AdhesiveVisualClientChecks.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/BlobRenderer.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/BlobRenderState.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/BoundedAdhesionClientChecks.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/ChineseLanguageChecks.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/ClientNetworking.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/ClientPortChecks.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/CoatingAppearance.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/CoatingClientChecks.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/CoatingVoxels.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/CompactStrandStyle.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/DynamicAdhesionClientChecks.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/FirstPersonCompatibility.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/FirstPersonTetherProof.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/FlatCoatingTextures.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/GlueCoatingRenderer.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/HelperRenderer.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/HelperRenderState.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/IterationFluidCompatibility.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/IterationShaderBridge.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/IterationShaderContracts.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/LegacyBeeModel.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/LegacyBlobModel.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/LegacyCoatingModel.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/LegacyFluidRendering.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/MechanismRenderer.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/MfqmBeeRenderer.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/MfqmClient.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/MudBubbleParticle.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/MuddyPlayerLayer.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/RenderedAdhesiveSurface.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/ShaderClientChecks.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/ShaderCompatibility.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/SkinLayerClearance.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/StruggleClient.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/StruggleEnumParams.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/StrugglePose.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/StruggleVisualChecks.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/StuckBootsRenderer.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/SurfaceBubbleStyle.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/TranslucentGeometry.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/ViscosityActionChecks.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/ViscosityClientChecks.java`
- `src/main/java/com/mfqm/morefunquicksandmod/client/WetAdhesiveStyle.java`
- `src/main/java/com/mfqm/morefunquicksandmod/compat/CompatRecipeChecks.java`
- `src/main/java/com/mfqm/morefunquicksandmod/compat/CompatRecipes.java`
- `src/main/java/com/mfqm/morefunquicksandmod/data/DataGenerators.java`
- `src/main/java/com/mfqm/morefunquicksandmod/data/ModBiomeTagProvider.java`
- `src/main/java/com/mfqm/morefunquicksandmod/data/ModDamageTypeBootstrap.java`
- `src/main/java/com/mfqm/morefunquicksandmod/data/ModDamageTypeTagProvider.java`
- `src/main/java/com/mfqm/morefunquicksandmod/data/ModFluidTagProvider.java`
- `src/main/java/com/mfqm/morefunquicksandmod/data/ModTagProvider.java`
- `src/main/java/com/mfqm/morefunquicksandmod/entity/AdhesiveTetherEntity.java`
- `src/main/java/com/mfqm/morefunquicksandmod/entity/BlobEntity.java`
- `src/main/java/com/mfqm/morefunquicksandmod/entity/BootPersistenceChecks.java`
- `src/main/java/com/mfqm/morefunquicksandmod/entity/BootsPortChecks.java`
- `src/main/java/com/mfqm/morefunquicksandmod/entity/CaptureEvents.java`
- `src/main/java/com/mfqm/morefunquicksandmod/entity/ConnectorEntity.java`
- `src/main/java/com/mfqm/morefunquicksandmod/entity/EntityPortChecks.java`
- `src/main/java/com/mfqm/morefunquicksandmod/entity/LiquidProjectileEntity.java`
- `src/main/java/com/mfqm/morefunquicksandmod/entity/MfqmBeeEntity.java`
- `src/main/java/com/mfqm/morefunquicksandmod/entity/StuckBootsEntity.java`
- `src/main/java/com/mfqm/morefunquicksandmod/entity/SurfaceEffectEntity.java`
- `src/main/java/com/mfqm/morefunquicksandmod/entity/TentacleEntity.java`
- `src/main/java/com/mfqm/morefunquicksandmod/fluid/SinkingLiquidBlock.java`
- `src/main/java/com/mfqm/morefunquicksandmod/gameplay/AdhesionController.java`
- `src/main/java/com/mfqm/morefunquicksandmod/gameplay/AdhesionPortChecks.java`
- `src/main/java/com/mfqm/morefunquicksandmod/gameplay/AdhesiveContact.java`
- `src/main/java/com/mfqm/morefunquicksandmod/gameplay/AdhesiveMotion.java`
- `src/main/java/com/mfqm/morefunquicksandmod/gameplay/AdhesiveRules.java`
- `src/main/java/com/mfqm/morefunquicksandmod/gameplay/AdhesiveSurface.java`
- `src/main/java/com/mfqm/morefunquicksandmod/gameplay/CoatingPortChecks.java`
- `src/main/java/com/mfqm/morefunquicksandmod/gameplay/LegacyResistance.java`
- `src/main/java/com/mfqm/morefunquicksandmod/gameplay/MediumReactions.java`
- `src/main/java/com/mfqm/morefunquicksandmod/gameplay/PhysicsPortChecks.java`
- `src/main/java/com/mfqm/morefunquicksandmod/gameplay/QuicksandPhysics.java`
- `src/main/java/com/mfqm/morefunquicksandmod/gameplay/SinkingMaterial.java`
- `src/main/java/com/mfqm/morefunquicksandmod/gameplay/SinkingMotion.java`
- `src/main/java/com/mfqm/morefunquicksandmod/gameplay/SinkingState.java`
- `src/main/java/com/mfqm/morefunquicksandmod/gameplay/StruggleRules.java`
- `src/main/java/com/mfqm/morefunquicksandmod/gameplay/ViscosityPortChecks.java`
- `src/main/java/com/mfqm/morefunquicksandmod/item/ConnectorItem.java`
- `src/main/java/com/mfqm/morefunquicksandmod/item/FertilizerItem.java`
- `src/main/java/com/mfqm/morefunquicksandmod/item/GlueBucketRecipe.java`
- `src/main/java/com/mfqm/morefunquicksandmod/item/ItemGameplayHooks.java`
- `src/main/java/com/mfqm/morefunquicksandmod/item/ItemPortChecks.java`
- `src/main/java/com/mfqm/morefunquicksandmod/item/ItemRecipeCondition.java`
- `src/main/java/com/mfqm/morefunquicksandmod/item/LegacyBlockItem.java`
- `src/main/java/com/mfqm/morefunquicksandmod/item/LegacyBucketItem.java`
- `src/main/java/com/mfqm/morefunquicksandmod/item/LegacyFuelItem.java`
- `src/main/java/com/mfqm/morefunquicksandmod/item/LiquidGunItem.java`
- `src/main/java/com/mfqm/morefunquicksandmod/item/SinkingDrinkItem.java`
- `src/main/java/com/mfqm/morefunquicksandmod/item/SplashSinkingPotionItem.java`
- `src/main/java/com/mfqm/morefunquicksandmod/item/StickyBoardItem.java`
- `src/main/java/com/mfqm/morefunquicksandmod/MFQM.java`
- `src/main/java/com/mfqm/morefunquicksandmod/mixin/CaptureEntityTeleportMixin.java`
- `src/main/java/com/mfqm/morefunquicksandmod/mixin/CaptureTeleportMixin.java`
- `src/main/java/com/mfqm/morefunquicksandmod/mixin/GlueJumpMixin.java`
- `src/main/java/com/mfqm/morefunquicksandmod/mixin/IrisIdMapMixin.java`
- `src/main/java/com/mfqm/morefunquicksandmod/mixin/IrisWaterProgramMixin.java`
- `src/main/java/com/mfqm/morefunquicksandmod/ModConfig.java`
- `src/main/java/com/mfqm/morefunquicksandmod/network/ModNetworking.java`
- `src/main/java/com/mfqm/morefunquicksandmod/registry/ModAttachmentTypes.java`
- `src/main/java/com/mfqm/morefunquicksandmod/registry/ModBlockEntities.java`
- `src/main/java/com/mfqm/morefunquicksandmod/registry/ModBlocks.java`
- `src/main/java/com/mfqm/morefunquicksandmod/registry/ModCreativeTabs.java`
- `src/main/java/com/mfqm/morefunquicksandmod/registry/ModDamageTypes.java`
- `src/main/java/com/mfqm/morefunquicksandmod/registry/ModDataComponents.java`
- `src/main/java/com/mfqm/morefunquicksandmod/registry/ModEntities.java`
- `src/main/java/com/mfqm/morefunquicksandmod/registry/ModFluids.java`
- `src/main/java/com/mfqm/morefunquicksandmod/registry/ModItems.java`
- `src/main/java/com/mfqm/morefunquicksandmod/registry/ModMobEffects.java`
- `src/main/java/com/mfqm/morefunquicksandmod/registry/ModParticles.java`
- `src/main/java/com/mfqm/morefunquicksandmod/registry/ModRecipes.java`
- `src/main/java/com/mfqm/morefunquicksandmod/registry/ModSounds.java`
- `src/main/java/com/mfqm/morefunquicksandmod/tags/MfqmTags.java`
- `src/main/java/com/mfqm/morefunquicksandmod/validation/AuditPortChecks.java`
- `src/main/java/com/mfqm/morefunquicksandmod/validation/ModPortChecks.java`
- `src/main/java/com/mfqm/morefunquicksandmod/worldgen/DesertTombPiece.java`
- `src/main/java/com/mfqm/morefunquicksandmod/worldgen/DesertTombStructure.java`
- `src/main/java/com/mfqm/morefunquicksandmod/worldgen/GluePoolFeature.java`
- `src/main/java/com/mfqm/morefunquicksandmod/worldgen/GlueWorldgenPortChecks.java`
- `src/main/java/com/mfqm/morefunquicksandmod/worldgen/LegacyChunkConversions.java`
- `src/main/java/com/mfqm/morefunquicksandmod/worldgen/LegacyStructures.java`
- `src/main/java/com/mfqm/morefunquicksandmod/worldgen/LegacyTerrainFeature.java`
- `src/main/java/com/mfqm/morefunquicksandmod/worldgen/ModWorldgen.java`
- `src/main/java/com/mfqm/morefunquicksandmod/worldgen/MucusBlossomLayout.java`
- `src/main/java/com/mfqm/morefunquicksandmod/worldgen/NaturalGlueChecks.java`
- `src/main/java/com/mfqm/morefunquicksandmod/worldgen/RegistryBonusLootEntry.java`
- `src/main/java/com/mfqm/morefunquicksandmod/worldgen/SwampColorModifier.java`
- `src/main/java/com/mfqm/morefunquicksandmod/worldgen/TerrainBiomeModifier.java`

### src/test/java (.java)

- `src/test/java/com/mfqm/morefunquicksandmod/client/CoatingAppearanceTest.java`
- `src/test/java/com/mfqm/morefunquicksandmod/client/CoatingVoxelsTest.java`
- `src/test/java/com/mfqm/morefunquicksandmod/client/CompactStrandStyleTest.java`
- `src/test/java/com/mfqm/morefunquicksandmod/client/IterationFluidCompatibilityTest.java`
- `src/test/java/com/mfqm/morefunquicksandmod/client/StrugglePoseTest.java`
- `src/test/java/com/mfqm/morefunquicksandmod/client/SurfaceBubbleStyleTest.java`
- `src/test/java/com/mfqm/morefunquicksandmod/client/WetAdhesiveStyleTest.java`
- `src/test/java/com/mfqm/morefunquicksandmod/gameplay/AdhesiveContactTest.java`
- `src/test/java/com/mfqm/morefunquicksandmod/gameplay/AdhesiveMotionTest.java`
- `src/test/java/com/mfqm/morefunquicksandmod/gameplay/AdhesiveRulesTest.java`
- `src/test/java/com/mfqm/morefunquicksandmod/gameplay/SinkingMotionTest.java`
- `src/test/java/com/mfqm/morefunquicksandmod/gameplay/StruggleRulesTest.java`

### tools (.py)

- `tools/coating_height.py`
- `tools/connected_boards.py`
- `tools/convert_legacy_tomb.py`
- `tools/fluid_alpha.py`
- `tools/generate_worldgen_resources.py`
- `tools/localize_zh_cn.py`
- `tools/refresh_adhesive_textures.py`
- `tools/refresh_textures.py`
- `tools/restore_legacy_resources.py`
- `tools/smoke_client_port.py`
- `tools/smoke_installed_client.py`
- `tools/smoke_port.py`
- `tools/smoke_shader_client.py`
- `tools/smoke_viscosity_client.py`
- `tools/test_coating_height.py`
- `tools/test_fluid_alpha.py`
- `tools/test_smoke_installed_client.py`
- `tools/test_smoke_port.py`
- `tools/test_smoke_viscosity_client.py`
- `tools/translucent_palette.py`
- `tools/update_adhesive_translations.py`
- `tools/verify_jar.py`
- `tools/verify_port.py`

### tools (.ps1)

- `tools/verify_adhesive_rules.ps1`
- `tools/verify_sinking_motion.ps1`
- `tools/verify_struggle_pose.ps1`
- `tools/verify_struggle_rules.ps1`

### Build and registration

- `build.gradle`
- `settings.gradle`
- `gradle.properties`
- `gradle/wrapper/gradle-wrapper.properties`
- `src/main/resources/mfqm.mixins.json`
- `src/main/resources/META-INF/accesstransformer.cfg`
