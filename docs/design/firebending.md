# Firebending — Class Design (draft v0.1)

> "Fire is the element of power." Aggressive, fast, and fueled by breath and the Sun.
> All numbers below are first-pass tuning values. Minecraft health is 20 HP (10 hearts); 2 HP = 1 heart.

---

## 1. Shared class rules (all four elements)

**Choosing a class**
- On first join, the player gets a **Bending Selection screen**: Fire, Water, Earth, Air.
- The choice is permanent by default. Changing it later costs something rare (e.g. a "Spirit Scroll" item), and resets that player's progress.
- The Avatar is **not** selectable. It may become a special unlock much later.

**The loadout: 4 slots, 1 ability each**
Each element has a pool of abilities. Each ability belongs to one slot, and the player puts one ability in each slot.

| Slot | Role | Rule of thumb |
|---|---|---|
| 1 | **Projectile** | Cheap to cast; the bread-and-butter attack |
| 2 | **Defense** | **Must block in some way:** shields, walls, parries |
| 3 | **Mobility** | Dash, flight, leap, float |
| 4 | **Ultimate** | Powerful single-target *or* area attack; expensive in chi |

- At the start, the player has 1 ability unlocked per slot. Progression unlocks the alternatives (see section 5).
- The loadout can be changed outside combat (e.g. not after taking damage in the last 10 seconds).

**Chi (resource)**
- Every bender has a **Chi** bar: base 100, regenerates 5 per second after 1.5 seconds without casting.
- Abilities cost chi. **There are no cooldowns:** chi cost and chi regen are the only limits. Gems or the skill tree raise max chi, regen and power.
- **Testing:** `/bending infinitechi` (op) toggles a mode where abilities cost no chi.
- **Balance baseline:** Fire Blast (the innate left-click attack) kills a normal 20-health mob in **2 hits**, day or night. Defensive and mobility damage is a bonus (roughly 4–8 per hit). Ultimates **one-shot normal mobs**: Firestorm across an area, Combustion Beam on a single target at full charge.

**Controls (rebindable)**
- `R` = toggle **bending mode** (the bending stance). Abilities and Fire Hop only work in it. While it's on, both hands show empty (both bare arms in first person, no held items in third person, for everyone watching), the HUD appears, and left/right click don't attack, mine or use items. Leaving the stance drops anything you were channeling.
- **Left click** (in bending mode) = **Fire Blast**, the innate attack every firebender always has.
- **Ctrl+Space** (in bending mode) = **Fire Jet Dash**, the innate movement. Keep holding **Ctrl** in the air to channel it; let go of Ctrl (or land) to stop. In bending mode this replaces the vanilla sprint-jump.
- `Z` `X` `C` `V` = cast slot 1–4. Each ability defines whether it uses a **tap**, a **hold** (channel), or a **hold-and-release** (charge).
- `B` = open the loadout and skill screen.

**HUD**
- A chi bar above the hotbar and 4 ability icons (the held one is outlined in white). Shows ∞ in infinite-chi mode.

---

## 2. Firebending show rules → game rules

| Show rule | Game effect |
|---|---|
| Fire comes from the Sun | **Daytime:** +20% damage, +25% chi regen. **Night:** −15% damage |
| Fire comes from breath | Each cast adds a little hunger exhaustion. Firebending is **impossible underwater** |
| Water douses fire | **In rain:** −25% damage, and fire projectiles shrink over distance |
| The Nether | +30% damage and +50% chi regen (the land of fire) |
| Sozin's Comet | Rare world event (about 1 in-game day every 60–100 days, announced in chat the night before): **firebending ×2**, chi costs halved |
| Solar eclipse | Rare world event (a few minutes long): **firebending disabled** for everyone |
| Lightning needs a calm mind | Lightning can't be charged if you took damage in the last 3 s. Getting hit while charging makes it blow up on you |
| Bending hits land in full | All bending damage skips vanilla's half-second damage immunity, so rapid follow-up hits do their full damage |
| Fire propels you | **Passive "Fire Hop":** press jump in mid-air for a small fire-powered hop, up to **3 times** before you land. Each hop barely climbs (less than half a normal jump) but resets your fall, so it breaks your fall rather than flying you upward. Free to use |
| Terrain feels your power | Fire Blast, the dash launch, Flame Burst and Firestorm **break blocks** with real explosions. The explosions never hurt or launch you; the abilities do their own damage. Terrain damage, fire placement and melting all follow the `mobGriefing` game rule (a mod config option later) |
| Firebenders master heat | **Passive "Inner Fire":** 50% less damage from fire and burning, immune to freezing in powder snow, and you can't catch fire from your own abilities |

---

## 3. Ability pool

### Innate — Fire Blast (left click)

Not a loadout slot: every firebender always has it, so slot 1 is free for a second projectile.

**🔥 Fire Blast** *(innate)*: **left click** in bending mode
- Looks like a glowing ball of cel-shaded flame (about 1.6 blocks across) with flame tongues streaming behind it.
- Fast punch that throws a small fireball in a straight line. Alternates left and right hand for a jab rhythm.
- Very fast (3 blocks per tick). 12 damage on a direct hit, so most normal mobs (20 health) go down in **two hits**, day or night. Sets the target on fire for 3 s. Range 40 blocks.
- **Explodes on impact:** 6 splash damage and light knockback within 1.8 blocks, a small crater in the terrain (explosion power 1.6), and **a few fire blocks left burning**.
- **Melts snow and ice** within 2 blocks of the impact. Ice turns to water, or evaporates in the Nether. Packed and blue ice are too dense to melt.
- Cost 6 chi.

### Slot 1 — Projectile

Starts empty. Fire Blast is innate, so this slot is for a second, different projectile.

**🌙 Fire Arc** *(unlock)*: **tap**
- A spinning kick that sends out a wide horizontal crescent of fire. It passes through enemies.
- 10 damage to everything it crosses, width 4 blocks, range 12 blocks, moves slower than Fire Blast.
- Cost 12 chi.

**☄️ Fireball Charge** *(unlock)*: **hold-and-release** (up to 1.5 s)
- Hold to build up a fireball, release to throw it. Explodes on impact.
- 10–24 damage depending on charge, explosion radius 1–3 blocks that breaks terrain.
- Cost 10–25 chi depending on charge.

### Slot 2 — Defense (every option blocks)

**🛡️ Fire Shield** *(starting ability)*: **hold**
- A disc of spinning flame in front of you. It burns up incoming projectiles (arrows, fireballs, other benders' projectiles) and burns anything in front of it (4 damage every 0.5 s).
- **Blocks hits from the front:** 100% of projectile damage, 75% of melee and explosion damage. Hits from behind, and damage from fire or lava, still get through.
- **Tap instead of holding** for a **Flame Burst**: a ring of fire that knocks back everything within 4 blocks (8 damage) and blasts the ground around you (power 2).
- Hold: 8 chi per second · Burst: 15 chi.

**🧱 Wall of Fire** *(unlock)*: **tap**
- A wall of fire 5 wide and 3 high, 3 blocks in front of you. Lasts 6 s. **Burns up any projectile that passes through it.** Mobs try to path around it. Anything that walks through takes 6 damage and is set on fire for 4 s.
- Cost 25 chi.

**⚔️ Flame Parry** *(unlock)*: **tap**
- A split-second block with a fiery counter. For 0.4 s after you tap, **any** hit from the front is blocked completely.
- If something hits you during that window, you counter with a burst of fire at the attacker (10 damage, knockback, sets them on fire) and get 15 chi back.
- High skill, high reward: miss the timing and you get nothing.
- Cost 10 chi.

> *Heat Control* (putting out fires, melting ice, smelting) doesn't block, so it left slot 2. It could come back later as a passive or a master technique.

### Innate — Fire Jet Dash (Ctrl+Space)

Not a loadout slot: every firebender always has it, so slot 3 is free for another mobility ability.

**💨 Fire Jet Dash** *(innate)*: **Ctrl+Space**, then **hold Ctrl** to channel
- **Press:** an explosive burst at your feet launches you where you're looking (Zuko and Azula style). The launch explosion does 8 damage, knocks back anything within 2.5 blocks, and blasts a small crater behind you (power 1.5).
- **Keep holding Ctrl:** ride the fire jets and steer by looking, for up to 2 s. It's a dash, not flight; that's what Jet Propulsion is for.
- Leaves a fire trail that does 5 damage to anything you pass through. No fall damage until 2 s after the jets stop.
- Cost 15 chi to launch, then 12 chi per second while held.

### Slot 3 — Mobility

Starts empty. Fire Jet Dash is innate, so this slot is for a second, different way to move.

**🚀 Jet Propulsion** *(unlock)*: **hold**
- Sustained flight on fire jets. Slower than elytra, but you can hover and steer freely.
- Cost 12 chi per second (drains fast). You can't use Slot 1 while flying.

**🌋 Blazing Leap** *(unlock)*: **hold-and-release**
- Crouch to charge, then rocket straight up (up to 12 blocks), followed by a slow fall.
- **Sneak in mid-air to slam down:** a landing shockwave does 12 damage in a 3-block radius and knocks enemies back.
- Cost 20 chi.

### Slot 4 — Ultimate

**⚡ Lightning** *(single target; unlock, or the starting ultimate; see section 6)*: **hold-and-release**
- Charge for 1.5 s (circular hand motion, crackling particles), then release a lightning bolt along your line of sight (range 30).
- 32 damage to the first target, then **chains** to up to 2 more targets within 5 blocks for 16 damage each.
- **In rain or water:** chains to *everything* nearby, **including you**.
- Calm mind rule: blocked if you took damage in the last 3 s. Getting hit while charging → you take 6 damage and the charge fails.
- Cost 50 chi.

**🌀 Firestorm** *(area; starting ultimate)*: **hold to charge** (1 s, goes off by itself)
- **Charge-up:** flames get pulled in toward you and a ring of small flames marks the blast area. Letting go early cancels it for free.
- **Eruption:** an expanding ring of fire (Ozai and Azula style), then a flaming vortex around you for 4 s.
- **Ring:** 24 damage within 7 blocks (one-shots normal mobs) plus strong knockback. Sets fires as it spreads.
- **Destruction:** a TNT-strength crater under you (power 4), a circle of 6 explosions at 4 blocks (power 2), then 12 explosions at the 7-block edge (power 2.5). No fall damage for 3 s, since you drop into your own crater.
- **Vortex:** 6 damage per second to anyone inside 3 blocks, and it burns up projectiles.
- Cost 60 chi.

**🔆 Combustion Beam** *(single target; unlock)*: **hold-and-release** (Combustion Man style)
- **Charge:** a ball of fire gathers between your hands and grows as it charges (other players see it too), throbbing once it's full. There's also a charge meter above the hotbar. 1.5 s to full charge. Releasing before 0.5 s fizzles out, for free.
- **Release:** an instant beam that strikes the first creature or block in its path, up to 40 blocks away.
- **Damage:** 16 at minimum charge, up to 40 at full charge (kills an enderman), and sets the target on fire for 5 s.
- **Impact:** an explosion that breaks terrain. Its power grows faster near full charge: about 2.5 at minimum, about 3.3 at half, and **7 at full charge** (bigger than a charged creeper). It splashes 30% damage within 2.5 blocks. It also melts snow and ice and leaves fire.
- **Recoil:** pushes you back a little, more at full charge.
- Cost 50 chi.

**🐉 Dragon's Breath** *(area cone; unlock)*: **hold** (up to 3 s)
- A huge, roaring cone of fire, 10 blocks long and 60° wide. 12 damage per second, ignites the ground, pushes enemies back.
- You move at 30% speed while using it.
- Cost 20 chi per second.

---

## 4. Master techniques (late-game sub-skills)

These come from the very end of the skill tree (or from rare gems). They change the abilities you already have instead of taking a slot.

| Technique | Effect |
|---|---|
| **Blue Fire** (Azula) | All fire becomes blue (vanilla *soul fire* visuals). +25% damage, and the burning lasts longer |
| **Lightning Redirection** (Iroh) | While Fire Shield is held, incoming lightning (natural or a bender's) is absorbed and fired back at whoever you're looking at, and refunds 30 chi. *Requires Lightning unlocked* |
| **Breath of Fire** | Being out of chi no longer locks you out: you can cast by spending hunger instead (1 hunger point per 10 chi) |
| **Dancing Dragon** (the original form) | Chi regen +50% during the day, and casting abilities back to back builds a combo that gives +5% damage per hit (max 5 stacks) |

---

## 5. Progression — two options (pick one, or both)

### Option A: Skill tree (gets better through use)
- You earn **Bending XP** by landing abilities on mobs and players. Every level gives 1 skill point.
- The firebending tree has 3 branches:
  - **Aggression:** damage, burn duration, explosion size. Ends in **Blue Fire**.
  - **Breath:** max chi, regen, cheaper costs. Ends in **Breath of Fire**, then **Dancing Dragon**.
  - **Discipline:** unlocks the alternative abilities for each slot, and Lightning's chaining. Ends in **Lightning Redirection**.
- Spending points to unlock abilities means **the skill tree decides *what* you can do.**

### Option B: Gem imbuing (gets better through exploration)
- Gems drop from rare ores, structure loot and bosses. You set them into armor at a new block, the **Imbuing Altar**.
- Socket count depends on the armor tier: leather/gold 1, iron/chain 1, diamond 2, netherite 3. That gives up to 12 sockets across a full set.
- **Universal gems** (any element):
  - **Chi Crystal:** +15 max chi.
  - **Flow Stone:** +10% chi regen.
  - **Focus Gem:** −5% chi costs.
- **Fire gems:**
  - **Sunstone:** +8% fire damage, doubled during the day.
  - **Ember Garnet:** +1 s burn duration.
  - **Comet Shard:** extremely rare; grants a weaker, permanent Sozin's Comet effect (+15%).
- Gems can be removed at the Altar, with a chance of breaking them.
- **Gems decide *how strong* you are.**

### Decision (2026-11-07): both, skill tree first
- **Skill tree = unlocks.** It controls which abilities and master techniques you have, so it rewards practicing your bending.
- **Gems = stats.** They control chi, regen and power numbers, so they reward exploring and gearing up, like armor enchanting does.
- **Build order:** skill tree first, because the loadout needs unlocks. Gems come second; they're a clean, self-contained feature to add afterwards.

---

## 6. Starting kit

A new firebender starts with:
- Innate: **Fire Blast** (left click) and **Fire Jet Dash** (Ctrl+Space)
- Loadout: **(empty slot 1)** / **Fire Shield** / **(empty slot 3)** / **Firestorm**
- Lightning is the first big unlock on the Discipline branch. It's the iconic firebender skill, so it's worth earning.

---

## 7. Technical sketch (NeoForge 1.21.1)

| Piece | How |
|---|---|
| Ability definitions | A custom registry `bending:ability`. Each `Ability` has slot, element, chi cost and cast type (tap/hold/charge) |
| Per-player state | A Data Attachment `BendingData`: element, loadout[4], chi, XP, unlocked skills. Saved and copied on death |
| Casting | Client keybind → `CastAbilityPayload` (client → server) → server checks chi and rules → runs the ability |
| Syncing | `SyncBendingDataPayload` (server → client) for the HUD; `BendingModePayload` (server → that player and everyone tracking them) for the stance |
| Projectiles | Custom entities: `FireBlastEntity`, `FireArcEntity`, `LightningBoltProjectile`, etc. |
| Visuals | Named particle hooks (`bending:fire_wisp`, `fire_spark`, `ember`, `fire_smoke`, `steam`) using vanilla textures for now: swap art in `assets/bending/particles/` |
| Audio | Named sound hooks (`bending:fire_blast.cast`, `fire_shield.block`, …, 18 total) pointing at vanilla sounds in `assets/bending/sounds.json`: swap in .ogg files with no code change |
| Hands | `BendingHands`: first-person bare arms and third-person empty hands in bending mode. Hand animations plug in here |
| World conditions | `level.isDay()`, `level.isRainingAt(pos)`, `player.isInWater()`, Nether dimension check |
| Comet / eclipse events | A saved per-world event scheduler (`SavedData`) |
| Gems (Option B) | Data Components on armor ItemStacks (1.21 replaced item NBT with components) |
| Config | Server config for damage multipliers, block griefing, event frequency |

**Suggested build order**
1. `BendingData` attachment, class selection screen, chi bar HUD.
2. Ability registry, keybinds and the cast packet.
3. Fire Blast (the first real ability: proves the whole pipeline works).
4. Fire Jet Dash, Fire Shield, Firestorm (the starting kit is complete).
5. World rules (day/night, rain, water, Nether).
6. Skill tree and unlocks → the remaining abilities.
7. Gems and the Imbuing Altar.
