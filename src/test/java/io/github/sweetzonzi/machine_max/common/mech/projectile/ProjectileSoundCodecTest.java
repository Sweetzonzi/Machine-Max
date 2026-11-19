package io.github.sweetzonzi.machine_max.common.mech.projectile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType.FmodSounds;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType.Sounds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 弹种侧音效块（{@code sounds.fmod}）的内容包契约。
 *
 * <p>断言只锁三件会静默失效的事：整块可缺省、写进来的要有能播的事件、事件路径必须是 Studio 路径。
 * 至于"哪个档位给哪个弹种"不属于契约——那是作者按听感定的值，改内容不该让单测变红。</p>
 *
 * <p>这里解析 {@link Sounds#CODEC} 而不是整份弹种：整份弹种的 codec 初始化会拉起制导律与
 * 数据注册表，需要游戏总线，单测环境起不来。音效块的契约与它上面的字段无关，单独测反而更准。</p>
 */
class ProjectileSoundCodecTest {

    private static DataResult<Sounds> parse(String json) {
        JsonElement element = JsonParser.parseString(json);
        return Sounds.CODEC.parse(JsonOps.INSTANCE, element);
    }

    /** 不写 {@code sounds} 时没有任何弹种侧音效：命中音全部由材质与方块决定。 */
    @Test
    void omittedSoundsLeaveNoFmodLayer() {
        assertTrue(parse("{}").getOrThrow().fmod().isEmpty());
    }

    /** 两个事件与 bank 原样解码；两层可以只写一层。 */
    @Test
    void decodesBankAndBothEventLayers() {
        FmodSounds both = parse("""
                { "fmod": {
                  "bank": "machine_max:mfx",
                  "hit_armour": "event:/Master_FX/Shells_Hit/Medium/hit_armour",
                  "hit_ground": "event:/Master_FX/Shells_Hit/Medium/hit_ground"
                } }""").getOrThrow().fmod().orElseThrow();
        assertEquals("machine_max:mfx", both.bank().toString());
        assertEquals("event:/Master_FX/Shells_Hit/Medium/hit_armour", both.hitArmour().orElseThrow());
        assertEquals("event:/Master_FX/Shells_Hit/Medium/hit_ground", both.hitGround().orElseThrow());

        FmodSounds groundOnly = parse("""
                { "fmod": {
                  "bank": "machine_max:mfx",
                  "hit_ground": "event:/Master_FX/Shells_Hit/Small/hit_ground"
                } }""").getOrThrow().fmod().orElseThrow();
        assertTrue(groundOnly.hitArmour().isEmpty(), "写空的那一层回到材质命中音");
        assertTrue(groundOnly.hitGround().isPresent());
    }

    /**
     * 事件必须是 {@code event:/} 开头的 Studio 路径：bank 里没有这个事件时客户端静默无声，
     * 所以在解码期就拒掉，而不是等玩家打中装甲才发现没声音。
     */
    @Test
    void eventPathMustLookLikeAStudioEvent() {
        assertTrue(parse("""
                { "fmod": {
                  "bank": "machine_max:mfx",
                  "hit_armour": "Master_FX/Shells_Hit/Big/hit_armour"
                } }""").error().isPresent(), "少了 event:/ 前缀要失败");
        assertTrue(parse("""
                { "fmod": { "bank": "machine_max:mfx", "hit_armour": "event:/" } }""")
                .error().isPresent(), "只有前缀、没有事件名要失败");
    }

    /** bank 是必填：没有 bank 的事件路径无从解析。 */
    @Test
    void bankIsRequired() {
        assertTrue(parse("""
                { "fmod": { "hit_armour": "event:/Master_FX/Shells_Hit/Big/hit_armour" } }""")
                .error().isPresent());
    }

    /**
     * 只写 bank、一个事件都不写要失败：整块的用途就是点名事件，接受它等于接受"看起来配好了、
     * 实际什么都不播"。
     */
    @Test
    void bankWithoutAnyEventIsRejected() {
        assertTrue(parse("{ \"fmod\": { \"bank\": \"machine_max:mfx\" } }").error().isPresent());
    }

    /** 编码方向是原样写回：往返之后仍是同一份配置。 */
    @Test
    void encodesBackToTheSameShape() {
        Sounds sounds = parse("""
                { "fmod": {
                  "bank": "machine_max:mfx",
                  "hit_armour": "event:/Master_FX/Shells_Hit/Big/hit_armour",
                  "hit_ground": "event:/Master_FX/Shells_Hit/Big/hit_ground"
                } }""").getOrThrow();

        JsonElement encoded = Sounds.CODEC.encodeStart(JsonOps.INSTANCE, sounds).getOrThrow();
        JsonObject fmod = encoded.getAsJsonObject().getAsJsonObject("fmod");
        assertEquals("machine_max:mfx", fmod.get("bank").getAsString());
        assertEquals("event:/Master_FX/Shells_Hit/Big/hit_armour", fmod.get("hit_armour").getAsString());
        assertFalse(fmod.get("hit_ground").isJsonNull());
    }
}
