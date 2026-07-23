package com.dwinovo.numen.mixin;

import com.dwinovo.numen.client.agent.NumenRoster;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 同伴皮肤跳过 authlib 安全校验——<b>1.20.1 专属</b>。旧版 authlib 4 在装载玩家
 * 皮肤时强制验签(MineSkin 代签的自定义皮肤、以及嫁接到同伴 UUID 上的同名正版
 * 皮肤都必被拒,"Property textures has been tampered with"),皮肤整个不加载。
 * 1.20.2 起 SkinManager 重写为"验签失败仅降级标记、照常渲染",无此问题。
 *
 * <p>同伴是本 mod 自己的假人、皮肤数据来自主人本地皮肤库,客户端对它验签没有
 * 安全意义——名册内的档案把 registerSkins 的 requireSecure 参数改为 false
 * (authlib 4 的 insecure 分支经字节码核实仅剩官方域名白名单),真实玩家不受影响。
 *
 * <p>用 {@code @ModifyArg} 而非 {@code @Redirect}:只改布尔实参、不独占调用点,
 * 与其他皮肤类 mod 的注入(哪怕是它们的 Redirect)可叠加共存,不重蹈
 * fabric-sound-api 撞车的覆辙。
 */
@Mixin(PlayerInfo.class)
public class MixinPlayerInfo {

    @Shadow @Final private GameProfile profile;

    @ModifyArg(method = "registerTextures",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/resources/SkinManager;registerSkins(Lcom/mojang/authlib/GameProfile;Lnet/minecraft/client/resources/SkinManager$SkinTextureCallback;Z)V"),
            index = 2)
    private boolean numen$companionSkinsInsecure(boolean requireSecure) {
        return requireSecure && NumenRoster.instance().entries().stream()
                .noneMatch(e -> e.uuid().equals(profile.getId()));
    }
}
