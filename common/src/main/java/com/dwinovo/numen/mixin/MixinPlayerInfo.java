package com.dwinovo.numen.mixin;

import com.dwinovo.numen.client.agent.NumenRoster;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.SkinManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 同伴皮肤跳过 authlib 安全校验——<b>1.20.1 专属</b>。旧版 authlib 4 在装载玩家
 * 皮肤时强制验签(且校验纹理归属账号),MineSkin 代签的自定义皮肤在假人档案上
 * 必然被拒("Property textures has been tampered with"),皮肤整个不加载。
 * 1.20.2 起 SkinManager 重写为"验签失败仅降级标记、照常渲染",无此问题。
 * 同伴是本 mod 自己的假人、皮肤数据来自主人本地皮肤库,安全校验无意义——
 * 名册内的档案改走 insecure 路径,真实玩家不受影响。
 */
@Mixin(PlayerInfo.class)
public class MixinPlayerInfo {

    @Redirect(method = "registerTextures",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/resources/SkinManager;registerSkins(Lcom/mojang/authlib/GameProfile;Lnet/minecraft/client/resources/SkinManager$SkinTextureCallback;Z)V"))
    private void numen$companionSkinsInsecure(SkinManager manager, GameProfile profile,
                                              SkinManager.SkinTextureCallback callback, boolean requireSecure) {
        boolean companion = NumenRoster.instance().entries().stream()
                .anyMatch(e -> e.uuid().equals(profile.getId()));
        manager.registerSkins(profile, callback, requireSecure && !companion);
    }
}
