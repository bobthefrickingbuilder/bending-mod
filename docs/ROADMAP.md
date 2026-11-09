# Bending Mod — Roadmap

NeoForge 1.21.1 · Java 21 · Avatar: The Last Airbender–inspired bending.
Detailed firebending design: [design/firebending.md](design/firebending.md)

---

## ✅ Phase 0 — Setup (done)
- Java 21 (Temurin), official NeoForge MDK, mod id `bending`, first build passing.

## ✅ Phase 1 — Core bending system (done)
- Per-player bending data: element, 4-slot loadout, chi, XP / level / skill points, unlocks. Saved, kept on death, synced to the client.
- Casting engine: tap, hold, charge and tap-or-hold abilities; chi costs (no cooldowns: chi is the only limit), hunger cost ("fire comes from the breath").
- Keybinds Z / X / C / V (ability slots), B (menu) and R (bending mode), all rebindable.
- **Bending mode:** R toggles the bending stance. Abilities only work in it; both hands show empty (both arms visible in first person, items hidden in third person for all players); HUD only shows in it; left click throws Fire Blast and Ctrl+Space launches Fire Jet Dash (both innate), freeing slots 1 and 3; right click doesn't use items.
- **Asset hooks:** 18 named sounds (`sounds.json`) and 5 named particles (`particles/*.json`) used by every ability, pointing at vanilla assets for now. Custom audio and art drop in without code changes.
- HUD: chi bar + 4 ability slots. Shows ∞ in infinite-chi mode.
- Element selection screen on first join.
- Commands: `/bending choose <element>`, `/bending reset`, `/bending xp <amount>`, `/bending equip <ability>`, `/bending infinitechi [true|false]`.

## ✅ Phase 2 — Firebending starting kit (done, play-tested)
- Fire Blast (explodes on impact and leaves fire) · Fire Shield / Flame Burst (blocks hits from the front) · Fire Jet Dash (explosive launch, hold to steer) · Firestorm.
- Slot 2 is the **Defense** slot: every slot-2 ability must block.
- Fire world rules: stronger by day and in the Nether, weaker at night and in rain, no bending underwater.
- Passive "Inner Fire": immune to fire and burning (no burning effect shown), half lava damage, immune to freezing.
- Bending XP from landing hits.

---

## ⏭️ Phase 3 — Skill tree + full firebending pool (next)
- ✅ **Skill tree screen** (B, Skill Tree tab): spend skill points to learn nodes; learned abilities auto-equip into an empty slot. Free in creative. Unbuilt abilities and master techniques show as "coming soon". Still to do: make it look prettier, and build the stat nodes.
- 3 firebending branches:
  - **Aggression:** damage, burn time, explosion size. Capstone: ✅ **Blue Fire** (+25% damage, longer burns, blue flames).
  - **Breath:** max chi, chi regen, lower costs. Capstones: ✅ **Breath of Fire** (burn hunger when out of chi), then ✅ **Dancing Dragon** (day regen, hit combo).
  - **Discipline:** unlocks the alternative abilities, Lightning chaining. Capstone: ✅ **Lightning Redirection** (hold Fire Shield to catch lightning and fire it back).
- ✅ **Loadout screen** (B): pick one unlocked ability per slot, with tooltips (cast type, chi, description) and the innate abilities listed. Locked abilities can be equipped in creative for testing. ✅ Locked for 5 s after dealing or taking a hit.
- ✅ **Over-the-shoulder camera**: the third-person back view sits off to one side (O swaps shoulders), with a crosshair.
- ✅ **Meteor Slam** (innate, Shift in mid-air): directional dive; explodes from above dash height.
- ✅ **All firebending abilities built** (✅ = built; equip from the loadout screen, or `/bending equip <name>`):
  - Slot 1: ✅ Fire Arc, ✅ Fireball Charge.
  - Slot 2 (Defense, every option blocks): ✅ Wall of Fire, ✅ Flame Parry.
  - Slot 3: ✅ Jet Propulsion, ✅ Blazing Leap.
  - Slot 4: ✅ **Lightning** (with the calm-mind and rain rules), ✅ Dragon's Breath, ✅ Combustion Beam, ✅ Firestorm.
- Still to do: stat bonuses from the tree feed max chi, regen and power. Gems will plug into the same hooks later.

## Phase 4 — Polish pass: look, sound, feel
- **Custom sounds:** fill the existing hooks with OGG files (several random variants each): fire whooshes, crackles, the lightning charge.
- **Custom particles:** new textures (and providers where the motion should change) for the existing hooks, plus new ones: blue fire, lightning arcs, heat shimmer.
- Optional: let mouse buttons cast abilities while in bending mode.
- Proper visuals for projectiles (glowing sprites or models instead of only particles).
- A custom damage type, so death messages read like "burned by Steve's firebending".
- A **config file** for damage multipliers, block griefing (fire placement, explosions), PvP rules and event frequency.
- HUD polish: key labels and ability tooltips.

## Phase 5 — Gems & the Imbuing Altar
- Gem items:
  - **Universal:** Chi Crystal (max chi), Flow Stone (regen), Focus Gem (chi costs).
  - **Fire:** Sunstone, Ember Garnet, Comet Shard.
- Sources: a new ore, structure loot, boss and mob drops.
- **Imbuing Altar** block with its own screen to socket and remove gems. Removing a gem has a chance to break it.
- Socket slots by armor tier: leather/gold/chain/iron 1, diamond 2, netherite 3.
- Stored on armor with Data Components (the 1.21 replacement for item NBT).

## Phase 6 — World events
- **Sozin's Comet:** a rare day with doubled fire power and halved chi costs, announced the night before.
- **Solar eclipse:** a few minutes of no firebending at all.
- (Later, for water) full-moon boost and lunar eclipse.

## Phase 7 — The other three elements
Each one gets 12 abilities (3 per slot), its own world rules, a skill tree, sub-skills and element gems.
Suggested order, simplest first:
1. **Airbending:** mobility and knockback; air is everywhere, so no source rules. Air scooter, glider, tornado.
2. **Waterbending:** needs water sources; the Moon cycle (Minecraft has moon phases); healing; ice. Sub-skills: bloodbending, plantbending.
3. **Earthbending:** has to be standing on earth; moves real blocks, which is the hardest technically. Sub-skills: metalbending, sandbending, seismic sense, lavabending.

## Phase 8 — Animations
- Pick an animation library (e.g. playerAnimator; check its 1.21.1 support at the time).
- Full-body Blockbench animations: punches and kicks, the shield pose, the Lightning circle, the Firestorm spin.
- Sync animations so other players see them too.

## Phase 9 — Multiplayer & release
- Test on a dedicated server; PvP balance pass.
- Original textures and sounds throughout (no assets ripped from the show).
- Publish on CurseForge / Modrinth.

---

## 💡 Idea backlog (unscheduled)
- **Chi-blockers:** a non-bender class whose pressure-point strikes temporarily disable bending.
- **The Avatar:** all four elements, the Avatar State, energybending. A very late, special unlock.
- **NPC benders:** Fire Nation soldiers, Water Tribe warriors, and so on.
- **Themed structures:** fire temples and air temples, with bending scrolls as loot.
- **Training:** master NPCs who teach sub-skills.

## 🧰 Housekeeping
- ✅ Project is on git and GitHub (bobthefrickingbuilder/bending-mod).
- Gradle warns that some features it uses will break in Gradle 10. This comes from the template, so it's harmless for now.
