# Minecraft 26.3 与旧版（1.21.x）API 差异备忘

这份笔记是**实际编译报错 + Fabric 官方 26.2/26.3 文档 + 官方示例工程**核对出来的结果，
用来解释本工程为什么和常见教程里的写法不一样。下次升级版本时照这张表查即可。

## 0. 最大的变化：26.1 起 Minecraft 不再混淆

- 官方公告：<https://www.minecraft.net/en-us/article/removing-obfuscation-in-java-edition>
- Fabric 的决定：<https://fabricmc.net/2025/10/31/obfuscation.html>（Yarn 停止维护）
- 版本 JSON（26.3）里 `downloads` **只有 `client` / `server`，没有 `client_mappings` / `server_mappings`**。

因此：

| 以前 | 现在（26.x） |
| --- | --- |
| `mappings loom.officialMojangMappings()` | **完全删掉这一行**；留着会报 `Failed to find official mojang mappings for 26.3` |
| `mappings "net.fabricmc:yarn:..."` | 不可用（Yarn 26.3 为空） |
| `modImplementation`（需要重映射） | 官方模板用 **`implementation`**（无混淆就不需要重映射） |
| Loom 插件 id `fabric-loom` | 官方模板为 **`net.fabricmc.fabric-loom`** |

## 1. 渲染 / GUI

| 1.21.x | 26.3 |
| --- | --- |
| `net.minecraft.client.gui.GuiGraphics` | **`net.minecraft.client.gui.GuiGraphicsExtractor`** |
| `Screen#render(GuiGraphics, int, int, float)` | **`Screen#extractRenderState(GuiGraphicsExtractor, int mouseX, int mouseY, float delta)`**（每帧调用，必须 `super`） |
| `graphics.drawString(font, text, x, y, color, shadow)` | **`graphics.text(font, text, x, y, color, shadow)`**（另有不带 shadow 的重载） |
| `graphics.drawCenteredString(...)` | 没有对应方法，自己用 `font.width(...)` 居中 |
| `graphics.fill(...)` / `fillGradient(...)` | 不变 |
| 文本颜色 RGB | **必须 ARGB**（`0xFFFFFFFF`；写 `0xFFFFFF` 会变全透明） |
| `Minecraft#setScreen(...)` | **`Minecraft#gui.setScreen(...)`**（`Minecraft.gui` 是 `net.minecraft.client.gui.Gui`） |
| `Minecraft#getToastManager()` + `SystemToast.addOrUpdate(...)` | **`minecraft.gui.toastManager().addToast(new SystemToast(id, title, msg))`** |
| `player.displayClientMessage(...)` | **`player.sendSystemMessage(...)`** |
| `net.minecraft.resources.ResourceLocation` | **`net.minecraft.resources.Identifier`**（`Identifier.withDefaultNamespace(...)`） |

`GuiGraphicsExtractor` 上还新增了 `outline(x,y,w,h,color)`、`horizontalLine(...)`、`verticalLine(...)`、
`textHighlight(...)`、`textWithBackdrop(...)`、`enableScissor(...)`/`disableScissor()`。

## 2. 输入

| 1.21.x | 26.3 |
| --- | --- |
| GLFW（`org.lwjgl.glfw.GLFW`） | **已被 SDL 取代**，GLFW 不在类路径上；键值必须用原版 `InputConstants.KEY_*` |
| `InputConstants.Type.KEYSYM` | **没有 `KEYSYM` 了**（`Type` 的常量改名），不要硬编码 |
| `mouseClicked(double, double, int)` | **`mouseClicked(net.minecraft.client.input.MouseButtonEvent, boolean)`** |
| `keyPressed(int keyCode, int scanCode, int modifiers)` | **`keyPressed(net.minecraft.client.input.KeyEvent)`** |
| `mouseScrolled(double, double, double, double)` | 不变 |
| `new KeyMapping(name, Type, keyCode, "category.key")` | `new KeyMapping(name, Type, keyCode, KeyMapping.Category)`，分类用 `KeyMapping.Category.register(Identifier)` |

## 3. Fabric API

| 1.21.x | 26.3 |
| --- | --- |
| `net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper` | **`net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper`**（模块 `fabric-key-mapping-api-v1`） |
| `ClientCommandManager.literal(...)` | **`ClientCommands.literal(...)`**（包仍是 `...client.command.v2`） |
| `ClientTickEvents` / `ClientLifecycleEvents` | 包名不变 |
| `ClientCommandRegistrationCallback` / `FabricClientCommandSource` | 不变 |

## 4. 本工程应对策略

- **能确定的就用新 API 直写**：`GuiGraphicsExtractor`、`text(...)`、`extractRenderState(...)`、
  `MouseButtonEvent` / `KeyEvent` 的覆写签名（编译器会校验）。
- **容易再改名的走反射**：`KeyMapping` 的构造（分类/Type 枚举名）、Toast、`net.minecraft.Util`
  （26.3 已删除）、`options.resourcePacks`、`reloadResourcePacks()`、Iris 重载入口、存档路径、
  以及鼠标/键盘事件对象的取值（`eventX/eventY/eventButton/eventKey`）。
  这样即使下个版本又改名，也只是对应功能降级，而不是整个工程编译失败。
