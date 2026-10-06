> Historical record: these checks targeted Minecraft 26.2. The current target is Minecraft 1.21.5; use ../2026-10-05-minecraft-1.21.5-repair/ and the root README for current instructions.

# MFQM 26.2 基础框架修复计划

设计依据：同目录 design.md，以及用户对全面审查结果的修复授权。

1. 更新 build.gradle、settings.gradle、gradle.properties 和 gradle/wrapper；补齐 gradlew、gradlew.bat。固定官方 MDK 版本、接入生成资源、改用 clientData、配置资源模板展开和无 GUI 服务器运行。用 Wrapper --version 与编译验证。
2. 修复 MFQM.java、registry/ModDamageTypes.java、tags/MfqmTags.java；移除失效的 registry/ModArmorMaterials.java 及其注册调用。保留全部有效注册和配置。用 Java 25 编译验证新 API。
3. 更新 data/DataGenerators.java、ModDamageTypeProvider.java、ModTagProvider.java、模型提供器；为动态伤害类型增加 bootstrap 和独立标签提供器。移除仅生成一条翻译且会遮蔽现有语言资源的提供器。用 runData 验证定义、标签和语言资源。
4. 更新 src/main/resources/META-INF/neoforge.mods.toml、.gitignore；删除过时 pack.mcmeta；提交生成 JSON。验证没有未展开的变量，五种伤害定义和标签进入构建产物。
5. 补充 README 构建/运行说明，为原移植计划增加 API 过时提示。运行 build、重复数据生成和启动烟雾检查，并独立审查 diff，记录 review.md 与实际验证结论。

任务 3 交由独立子代理处理数据 API/定义，其余任务由主代理执行；共享目录中各自只编辑负责的文件。集成编译与最终审查由主代理完成。
