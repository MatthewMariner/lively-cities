package com.matthewmariner.livelycities.data;

import java.util.List;

/**
 * Wire shape of one entry in {@code citizenRoster} or {@code sceneryRoster}.
 *
 * <p>One class covers both rosters: a scenery record is simply a citizen record
 * with the name/examine/remarks/animation fields absent, so a second DTO would
 * only duplicate the validation. Every field is nullable — validation happens
 * once, in {@code EntityDefinition.fromRecord}, so there is a single place that
 * decides what "unusable" means.
 *
 * <p>Fields for later milestones ({@code wanderBox*}, {@code moveAnimation},
 * {@code startScript}, {@code remarks}) are parsed but unused by the render
 * core, so enabling L2/L3 does not mean revisiting the schema.
 */
public class EntityRecord
{
	// Identity
	public String uuid;
	public Integer regionId;
	public String entityType;

	// Presentation (citizens only)
	public String name;
	public String examineText;
	public String[] remarks;

	// Placement
	public PointRecord worldLocation;
	public Integer baseOrientation;

	// Model
	public int[] modelIds;
	public int[] modelRecolorFind;
	public int[] modelRecolorReplace;

	/**
	 * Clone an existing NPC's appearance instead of listing raw {@code modelIds}.
	 *
	 * <p>Optional, and <b>it wins when it is present</b>: the models and the
	 * recolour arrays both come from {@code Client.getNpcDefinition(id)}, and this
	 * record's own {@code modelIds} / {@code modelRecolor*} are ignored (with a warn
	 * at validation time, because carrying both is an authoring mistake rather than
	 * a fallback — a recolour pair list authored against one model's palette means
	 * nothing against another's). See {@code NpcAppearance}.
	 *
	 * <p><b>Why it exists.</b> A raw model id is the fragility that killed the
	 * predecessor plugin: an August 2024 update renumbered player-model ids and its
	 * citizens broke visually. An NPC id is a named, generated constant in
	 * {@code net.runelite.api.gameval.NpcID}, is what a game update renumbers last,
	 * and — unlike a model id — can be looked up by anyone with the jar. The vendored
	 * entities keep their {@code modelIds} except where the authored array was itself
	 * the bug ("Rufus", GitHub issue #1, whose twelve ids contained no footwear);
	 * anything authored here should prefer this.
	 */
	public Integer npcAppearanceId;
	public float[] scale;
	public float[] translate;
	public List<MergedObjectRecord> mergedObjects;

	// Animation, by symbolic name
	public String idleAnimation;
	public String moveAnimation;

	// L3 — wandering
	public PointRecord wanderBoxBL;
	public PointRecord wanderBoxTR;

	// L3 — scripted
	public String startScript;

	/**
	 * Whether this record is a <b>cameo</b>: a named, player-shaped likeness of a
	 * real person, which the {@code cameos} config item switches on and which is
	 * <b>off by default</b>.
	 *
	 * <p>A separate field rather than "anything with an {@link #npcAppearanceId}",
	 * deliberately. The two are unrelated questions — the appearance mechanism is
	 * meant to be the preferred way to dress <i>any</i> future entity, and a market
	 * stall owner sourced from an NPC id must not silently become opt-in content —
	 * so the gate is stated rather than inferred. {@code EntityScene} refuses a
	 * cameo unless both {@code cameos} and its city's checkbox are on, and
	 * {@code CitizenEcho} refuses to derive anything from one.
	 *
	 * <p>Absent (or false) for all 175 vendored entities.
	 */
	public Boolean cameo;

	// Authoring metadata, not used at render time
	public Integer baseNpcId;
	public Integer removedObject;
}
