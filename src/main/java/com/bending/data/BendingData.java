package com.bending.data;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Everything the mod stores per player: chosen element, loadout, chi and progression.
 * Attached to players through {@link ModAttachments#BENDING} and synced to the owning client.
 */
public class BendingData {
    public static final int SLOTS = 4;
    public static final float BASE_MAX_CHI = 100f;

    public static final Codec<BendingData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Element.CODEC.optionalFieldOf("element").forGetter(d -> Optional.ofNullable(d.element)),
            Codec.STRING.listOf().optionalFieldOf("loadout", List.of()).forGetter(d -> List.of(d.loadout)),
            Codec.FLOAT.optionalFieldOf("chi", BASE_MAX_CHI).forGetter(d -> d.chi),
            Codec.INT.optionalFieldOf("xp", 0).forGetter(d -> d.xp),
            Codec.INT.optionalFieldOf("level", 0).forGetter(d -> d.level),
            Codec.INT.optionalFieldOf("skill_points", 0).forGetter(d -> d.skillPoints),
            Codec.STRING.listOf().optionalFieldOf("unlocked", List.of()).forGetter(d -> List.copyOf(d.unlocked)),
            Codec.BOOL.optionalFieldOf("infinite_chi", false).forGetter(d -> d.infiniteChi)
    ).apply(i, BendingData::new));

    @Nullable
    private Element element;
    private final String[] loadout = new String[SLOTS];
    private float chi = BASE_MAX_CHI;
    private int xp;
    private int level;
    private int skillPoints;
    private final Set<String> unlocked = new HashSet<>();
    /** Testing mode: abilities cost no chi. */
    private boolean infiniteChi;

    // ---- Runtime state (never saved) ----
    /** Slot currently being held or charged, or -1. */
    public int activeSlot = -1;
    public int activeTicks;
    public long lastCastTime;
    public long noFallUntil;
    public int dashTrailTicks;
    public final Set<Integer> dashTrailHits = new HashSet<>();
    public boolean altHand;
    /** Mid-air hops used since last touching the ground (server-side count). */
    public int airJumpsUsed;
    /** Bending stance: abilities only work while this is on, and both hands show empty. */
    public boolean bendingMode;
    /** True once the client has received data from the server. */
    public boolean synced;
    /** True when something changed that the client should hear about. */
    public boolean dirty;

    public BendingData() {
        Arrays.fill(loadout, "");
    }

    private BendingData(Optional<Element> element, List<String> loadout, float chi, int xp, int level, int skillPoints,
                        List<String> unlocked, boolean infiniteChi) {
        this();
        this.element = element.orElse(null);
        for (int s = 0; s < SLOTS && s < loadout.size(); s++) {
            this.loadout[s] = loadout.get(s);
        }
        this.chi = chi;
        this.xp = xp;
        this.level = level;
        this.skillPoints = skillPoints;
        this.unlocked.addAll(unlocked);
        this.infiniteChi = infiniteChi;
    }

    /** A detached copy, used for network sync so client and server never share one object. */
    public BendingData copy() {
        return new BendingData(Optional.ofNullable(element), List.of(loadout), chi, xp, level, skillPoints,
                List.copyOf(unlocked), infiniteChi);
    }

    // ---- Element & loadout ----

    @Nullable
    public Element getElement() {
        return element;
    }

    public void setElement(@Nullable Element element) {
        this.element = element;
        dirty = true;
    }

    public String getLoadout(int slot) {
        return loadout[slot];
    }

    public void setLoadout(int slot, String abilityId) {
        loadout[slot] = abilityId;
        dirty = true;
    }

    public boolean isUnlocked(String abilityId) {
        return unlocked.contains(abilityId);
    }

    public void unlock(String abilityId) {
        unlocked.add(abilityId);
        dirty = true;
    }

    // ---- Chi ----

    public float getChi() {
        return chi;
    }

    public float getMaxChi() {
        // Skill tree and gem bonuses will be added here.
        return BASE_MAX_CHI;
    }

    public void setChi(float value) {
        float clamped = Math.max(0f, Math.min(getMaxChi(), value));
        if (clamped != chi) {
            chi = clamped;
            dirty = true;
        }
    }

    public void addChi(float amount) {
        setChi(chi + amount);
    }

    public boolean hasInfiniteChi() {
        return infiniteChi;
    }

    public void setInfiniteChi(boolean infiniteChi) {
        this.infiniteChi = infiniteChi;
        if (infiniteChi) {
            setChi(getMaxChi());
        }
        dirty = true;
    }

    /** Spends chi if there is enough. Returns false (and spends nothing) otherwise. */
    public boolean trySpend(float amount) {
        if (infiniteChi) {
            return true;
        }
        if (chi < amount) {
            return false;
        }
        setChi(chi - amount);
        return true;
    }

    // ---- Progression (skill tree) ----

    public int getXp() {
        return xp;
    }

    public int getLevel() {
        return level;
    }

    public int getSkillPoints() {
        return skillPoints;
    }

    public static int xpForNextLevel(int level) {
        return 50 + 25 * level;
    }

    /** Adds bending XP. Each level gained gives one skill point. Returns the number of levels gained. */
    public int addXp(int amount) {
        xp += amount;
        int gained = 0;
        while (xp >= xpForNextLevel(level)) {
            xp -= xpForNextLevel(level);
            level++;
            skillPoints++;
            gained++;
        }
        dirty = true;
        return gained;
    }
}
