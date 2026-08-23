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

	// Authoring metadata, not used at render time
	public Integer baseNpcId;
	public Integer removedObject;
}
