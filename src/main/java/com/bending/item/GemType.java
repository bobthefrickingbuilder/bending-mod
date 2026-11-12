package com.bending.item;

import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import com.bending.data.Element;
import com.bending.skill.SkillStat;

/**
 * Every gem and what it adds while socketed into worn armor. Universal gems work for any bender;
 * element gems only for that element.
 */
public enum GemType {
    CHI_CRYSTAL(null, 0x7FE8FF, new Bonus(SkillStat.MAX_CHI, 15f)),
    FLOW_STONE(null, 0x5CE0A0, new Bonus(SkillStat.REGEN, 0.15f)),
    FOCUS_GEM(null, 0xC08CFF, new Bonus(SkillStat.CHI_COST, -0.08f)),
    SUNSTONE(Element.FIRE, 0xFFC23A, new Bonus(SkillStat.DAMAGE, 0.08f)),
    EMBER_GARNET(Element.FIRE, 0xE0302A, new Bonus(SkillStat.BURN, 0.25f)),
    COMET_SHARD(Element.FIRE, 0xFF6AD0, new Bonus(SkillStat.RANGE, 0.10f), new Bonus(SkillStat.SIZE, 0.10f));

    public record Bonus(SkillStat stat, float amount) {}

    @Nullable
    private final Element element;
    private final int color;
    private final List<Bonus> bonuses;

    GemType(@Nullable Element element, int color, Bonus... bonuses) {
        this.element = element;
        this.color = color;
        this.bonuses = List.of(bonuses);
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Nullable
    public Element element() {
        return element;
    }

    public int color() {
        return color;
    }

    public List<Bonus> bonuses() {
        return bonuses;
    }

    public boolean worksFor(@Nullable Element bender) {
        return element == null || element == bender;
    }

    @Nullable
    public static GemType byId(String id) {
        for (GemType type : values()) {
            if (type.id().equals(id)) {
                return type;
            }
        }
        return null;
    }
}
