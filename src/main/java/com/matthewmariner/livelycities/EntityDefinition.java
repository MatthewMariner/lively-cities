package com.matthewmariner.livelycities;

import com.matthewmariner.livelycities.data.EntityRecord;
import com.matthewmariner.livelycities.data.MergedObjectRecord;
import com.matthewmariner.livelycities.data.PointRecord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

/**
 * A validated, render-ready entity — everything the spawner needs and nothing
 * that still has to be checked.
 *
 * <p>{@link #fromRecord(EntityRecord, int)} is the only gate between the JSON
 * and the scene, and it is deliberately fail-soft in two different ways:
 *
 * <ul>
 *   <li><b>Skip</b> (returns null + warn) only when the record cannot possibly
 *       render: no known type, no location, or neither a usable model nor a
 *       plausible {@code npcAppearanceId}.</li>
 *   <li><b>Degrade</b> (keeps the entity + warn) for everything else: an unknown
 *       animation name becomes a static model, a lopsided recolour pair list is
 *       truncated to the matched pairs, a malformed scale/translate is dropped.</li>
 * </ul>
 *
 * <p>What it never does is reject the whole region. The predecessor gated on
 * {@code version != 0.8f} and returned null for the entire file, so one bumped
 * version number silently emptied a city.
 */
@Slf4j
public final class EntityDefinition
{
	private static final int JAU_FULL_ROTATION = 2048;
	private static final int MAX_PLANE = 3;

	/** Shared, so 142 of the 181 entities do not each allocate an empty array. */
	private static final String[] NO_REMARKS = new String[0];

	private final UUID uuid;
	private final int regionId;
	private final int tileRegionId;
	private final int cityRegionId;
	private final EntityType type;
	private final String name;
	private final String examineText;
	private final String[] remarks;
	private final WorldPoint worldLocation;
	private final int orientation;
	private final int[] modelIds;

	/**
	 * The NPC whose appearance this entity wears, or {@code 0} for one dressed from
	 * {@link #modelIds}.
	 *
	 * <p>Resolved at spawn time rather than here — it needs a {@link net.runelite.api.Client},
	 * and this class is deliberately client-free so the whole dataset can be
	 * validated without a game running. See {@link NpcAppearance}.
	 */
	private final int npcAppearanceId;

	/** See {@link #isCameo()}. */
	private final boolean cameo;

	private final short[] recolorFind;
	private final short[] recolorReplace;
	private final float[] scale;
	private final float[] translate;
	private final List<MergedObject> mergedObjects;
	private final LivelyAnimation idleAnimation;
	private final LivelyAnimation moveAnimation;
	private final WanderBox wanderBox;

	/**
	 * The uuid of the authored citizen this one was derived from, or {@code null}
	 * for an authored entity — which is what {@link #isEcho()} reads.
	 *
	 * <p>Kept rather than merely used once, because "which citizen is this a copy
	 * of?" is the question a log line and a test both want to ask, and rederiving
	 * it is impossible: the derivation is one-way (see {@link CitizenEcho}).
	 */
	@Nullable
	private final UUID echoSourceUuid;

	/**
	 * For an echo only: whether its tile came from inside its source's authored
	 * wander box, i.e. from ground a human already decided a citizen could pace.
	 *
	 * <p>This is the fallback signal {@link CitizenEcho} falls back <i>to</i> when
	 * the live collision map has no answer. Always {@code false} for an authored
	 * entity, which never consults either.
	 */
	private final boolean echoOnAuthoredGround;

	private EntityDefinition(
		UUID uuid,
		int regionId,
		int tileRegionId,
		int cityRegionId,
		EntityType type,
		@Nullable String name,
		@Nullable String examineText,
		String[] remarks,
		WorldPoint worldLocation,
		int orientation,
		int[] modelIds,
		int npcAppearanceId,
		boolean cameo,
		short[] recolorFind,
		short[] recolorReplace,
		@Nullable float[] scale,
		@Nullable float[] translate,
		List<MergedObject> mergedObjects,
		@Nullable LivelyAnimation idleAnimation,
		@Nullable LivelyAnimation moveAnimation,
		@Nullable WanderBox wanderBox,
		@Nullable UUID echoSourceUuid,
		boolean echoOnAuthoredGround)
	{
		this.echoSourceUuid = echoSourceUuid;
		this.echoOnAuthoredGround = echoOnAuthoredGround;
		this.uuid = uuid;
		this.regionId = regionId;
		this.tileRegionId = tileRegionId;
		this.cityRegionId = cityRegionId;
		this.type = type;
		this.name = name;
		this.examineText = examineText;
		this.remarks = remarks;
		this.worldLocation = worldLocation;
		this.orientation = orientation;
		this.modelIds = modelIds;
		this.npcAppearanceId = npcAppearanceId;
		this.cameo = cameo;
		this.recolorFind = recolorFind;
		this.recolorReplace = recolorReplace;
		this.scale = scale;
		this.translate = translate;
		this.mergedObjects = mergedObjects;
		this.idleAnimation = idleAnimation;
		this.moveAnimation = moveAnimation;
		this.wanderBox = wanderBox;
	}

	/**
	 * Validates one parsed roster entry.
	 *
	 * @param record       the parsed record, may be null
	 * @param fileRegionId the region id the containing file is named after —
	 *                     authoritative for identity, because the file name is
	 *                     what the loader keys on. Where the entity actually
	 *                     stands is a separate question, answered by
	 *                     {@link #getTileRegionId()}
	 * @return a render-ready definition, or {@code null} if the record is
	 * unusable. Never throws.
	 */
	@Nullable
	public static EntityDefinition fromRecord(@Nullable EntityRecord record, int fileRegionId)
	{
		if (record == null)
		{
			log.warn("region {}: null roster entry skipped", fileRegionId);
			return null;
		}

		String label = describe(record, fileRegionId);

		EntityType type = EntityType.fromName(record.entityType);
		if (type == null)
		{
			log.warn("{}: unknown entityType '{}', skipped", label, record.entityType);
			return null;
		}

		WorldPoint location = toWorldPoint(record.worldLocation);
		if (location == null)
		{
			log.warn("{}: missing or invalid worldLocation, skipped", label);
			return null;
		}

		// Two ways of being dressed, and exactly one of them has to work out. The
		// NPC id is only a *claim* here — whether it resolves is a question for a
		// live cache, and LivelyEntity asks it — so what this gate checks is that
		// the record named a plausible one, the same standard the offline audit
		// holds a modelId to.
		int npcAppearanceId = npcAppearanceId(record.npcAppearanceId, label);
		int[] modelIds = usableModelIds(record.modelIds, label);
		if (npcAppearanceId == 0 && modelIds.length == 0)
		{
			log.warn("{}: no usable modelIds and no npcAppearanceId, skipped", label);
			return null;
		}

		if (npcAppearanceId != 0 && modelIds.length > 0)
		{
			// Both. The NPC appearance wins, models and recolours alike — see
			// EntityRecord.npcAppearanceId. Loud rather than silent: carrying both is
			// an authoring mistake, and the half that is ignored is the half a human
			// typed.
			log.warn("{}: carries both npcAppearanceId {} and {} modelIds — the NPC appearance wins, "
					+ "the modelIds and any modelRecolor* are ignored",
				label, npcAppearanceId, modelIds.length);
		}

		if (record.regionId != null && record.regionId != fileRegionId)
		{
			log.warn("{}: record claims regionId {} but lives in {}.json — using {}",
				label, record.regionId, fileRegionId, fileRegionId);
		}

		// The record's own regionId field is a claim; the tile is the fact. The
		// two disagree in the shipped data — "Dark wizard" is filed under 12853
		// and stands in 12852 — and it is the tile that decides whether the
		// client has the region loaded, so the scene keys visibility on this and
		// not on the file name.
		int tileRegionId = RenderPolicy.regionIdOf(location.getX(), location.getY());
		if (tileRegionId != fileRegionId)
		{
			log.warn("{}: tile {},{} is in region {}, not the {} its file is named after — "
					+ "it can only be found while region {} is in the scene too",
				label, location.getX(), location.getY(), tileRegionId, fileRegionId, fileRegionId);
		}

		UUID uuid = parseUuid(record.uuid, label);
		int orientation = orientation(record.baseOrientation, label);

		int pairs = recolorPairCount(record, label);
		short[] find = new short[pairs];
		short[] replace = new short[pairs];
		for (int i = 0; i < pairs; i++)
		{
			find[i] = (short) record.modelRecolorFind[i];
			replace[i] = (short) record.modelRecolorReplace[i];
		}

		LivelyAnimation idle = animation(record.idleAnimation, "idleAnimation", label);
		LivelyAnimation move = animation(record.moveAnimation, "moveAnimation", label);

		return new EntityDefinition(
			uuid,
			fileRegionId,
			tileRegionId,
			// An authored entity answers to the city containing the tile it stands on,
			// and it is the only kind of entity that answers for itself.
			tileRegionId,
			type,
			record.name,
			record.examineText,
			usableRemarks(record.remarks, type, label),
			location,
			orientation,
			modelIds,
			npcAppearanceId,
			cameo(record, type, label),
			find,
			replace,
			vector3(record.scale, "scale", label),
			vector3(record.translate, "translate", label),
			mergedObjects(record.mergedObjects, label),
			idle,
			move,
			wanderBox(record, type, location, label),
			null,
			false);
	}

	/**
	 * Builds one procedurally-derived citizen — an "echo" — from an authored one.
	 *
	 * <p>Every decision behind the arguments is {@link CitizenEcho}'s; this method
	 * only assembles them, so that the one place that may construct an
	 * {@link EntityDefinition} stays this class. What it does add is the three things
	 * that are properties of <i>being</i> a definition rather than of being an echo:
	 * the tile's own region id (recomputed, because an echo can stand a few tiles
	 * over a region border exactly like the shipped "Dark wizard" does), the region
	 * whose city checkbox governs it (its <b>source's</b> — see
	 * {@link #getCityRegionId()}, and it is emphatically not the recomputed one), and
	 * the {@link EntityType#StationaryCitizen} type.
	 *
	 * <p><b>The type is forced rather than inherited</b>, and that is the whole of
	 * "echoes do not wander": {@link CitizenWalk#forDefinition} needs a
	 * {@link WanderBox}, {@code wanderBox} is always {@code null} here, and a
	 * {@code WanderingCitizen} that stands still would be a lie in every log line.
	 *
	 * <p>What is inherited is the body: model ids, merged objects, scale, translate
	 * and the idle animation. What is not: the name, the examine text, the remarks
	 * (an echo is silent — see {@link CitizenEcho}), the move animation (nothing
	 * moves), and the recolour, which is the source's own palette re-dealt.
	 *
	 * @param source               the authored citizen this is derived from
	 * @param uuid                 the echo's own uuid, derived from the source's
	 * @param tile                 the echo's tile, already at least
	 *                             {@link CitizenEcho#MIN_SEPARATION_TILES} from the
	 *                             source and from its siblings
	 * @param orientation          already inside 0..2047
	 * @param recolorFind          the source's find array, as-is
	 * @param recolorReplace       the source's replace array, re-dealt
	 * @param name                 an honestly generic name — never the source's
	 * @param examineText          truthful about what this is — never the source's
	 * @param onAuthoredGround     whether {@code tile} came from inside the source's
	 *                             authored wander box
	 */
	static EntityDefinition echoOf(
		EntityDefinition source,
		UUID uuid,
		WorldPoint tile,
		int orientation,
		short[] recolorFind,
		short[] recolorReplace,
		String name,
		String examineText,
		boolean onAuthoredGround)
	{
		return new EntityDefinition(
			uuid,
			source.regionId,
			RenderPolicy.regionIdOf(tile.getX(), tile.getY()),
			// Its source's city, not its own tile's. See getCityRegionId().
			source.cityRegionId,
			EntityType.StationaryCitizen,
			name,
			examineText,
			NO_REMARKS,
			tile,
			orientation,
			source.modelIds,
			source.npcAppearanceId,
			// Never a cameo, however it was derived. An echo carries none of its
			// source's identity (see CitizenEcho), so it cannot be a likeness of
			// anybody — and inheriting the flag would put an extra body next to a
			// cameo whenever the cameos checkbox happened to be on. The stronger half
			// of the same guarantee is that CitizenEcho refuses to derive anything
			// from a cameo at all, so this line is unreachable for a cameo source;
			// it is written down because "an echo is not a cameo" is a property of
			// being an echo, and a future second derivation path would need it too.
			false,
			recolorFind,
			recolorReplace,
			source.scale == null ? null : source.scale.clone(),
			source.translate == null ? null : source.translate.clone(),
			source.mergedObjects,
			source.idleAnimation,
			null,
			null,
			source.uuid,
			onAuthoredGround);
	}

	/**
	 * Validates a wandering citizen's patrol box.
	 *
	 * <p>Degrades to {@code null} — i.e. the citizen spawns and stands still —
	 * rather than skipping the record, for every failure. Same reasoning as the
	 * unknown-animation case: a citizen standing in the right place beats no
	 * citizen.
	 *
	 * <p>The clamp at the end is the only place that enforces
	 * {@link RenderPolicy#DATASET_OVERHANG_ALLOWANCE}. The cull check measures
	 * from {@code base}, so a box that reached much further would let a citizen
	 * wander an unbounded distance from the tile the crowd cap and the render
	 * distance were both computed about. Clamping bounds that disagreement for any
	 * dataset rather than merely for this one. It does <b>not</b> keep the citizen
	 * inside the loaded scene — nothing can, see
	 * {@link RenderPolicy#SUSTAINED_SCENE_RADIUS} — and a step onto unloaded ground
	 * is handled where it happens: {@code LocalPoint.fromWorld} returns null and
	 * {@link CitizenWalk#localPoint} leaves the citizen where it was.
	 *
	 * @return the box, or {@code null} for a non-wanderer or an unusable box
	 */
	@Nullable
	private static WanderBox wanderBox(
		EntityRecord record,
		EntityType type,
		WorldPoint base,
		String label)
	{
		if (type != EntityType.WanderingCitizen)
		{
			// ScriptedCitizens carry a startScript, not a box, and L3 leaves them
			// stationary — see the comment in CitizenWalk.
			return null;
		}

		PointRecord bl = record.wanderBoxBL;
		PointRecord tr = record.wanderBoxTR;
		if (bl == null || tr == null
			|| bl.x == null || bl.y == null || tr.x == null || tr.y == null
			|| bl.x <= 0 || bl.y <= 0 || tr.x <= 0 || tr.y <= 0)
		{
			log.warn("{}: WanderingCitizen with no usable wanderBox — spawning it stationary", label);
			return null;
		}

		if (!samePlane(bl, base) || !samePlane(tr, base))
		{
			// A box on another storey is not a walk, it is a fall.
			log.warn("{}: wanderBox is on plane {}/{} but the citizen stands on {} — spawning it stationary",
				label, bl.plane, tr.plane, base.getPlane());
			return null;
		}

		int minX = Math.min(bl.x, tr.x);
		int maxX = Math.max(bl.x, tr.x);
		int minY = Math.min(bl.y, tr.y);
		int maxY = Math.max(bl.y, tr.y);

		if (base.getX() < minX || base.getX() > maxX || base.getY() < minY || base.getY() > maxY)
		{
			// Growing the box to swallow the start tile would quietly widen the
			// authored patrol, and walking the citizen to the nearest corner first
			// would teleport it. Neither is worth it for a case the shipped data
			// does not contain.
			log.warn("{}: stands outside its own wanderBox {},{}..{},{} — spawning it stationary",
				label, minX, minY, maxX, maxY);
			return null;
		}

		int allowance = RenderPolicy.DATASET_OVERHANG_ALLOWANCE;
		int clampedMinX = Math.max(minX, base.getX() - allowance);
		int clampedMaxX = Math.min(maxX, base.getX() + allowance);
		int clampedMinY = Math.max(minY, base.getY() - allowance);
		int clampedMaxY = Math.min(maxY, base.getY() + allowance);

		if (clampedMinX != minX || clampedMaxX != maxX || clampedMinY != minY || clampedMaxY != maxY)
		{
			log.warn("{}: wanderBox {},{}..{},{} reaches further than {} tiles from the tile its cull "
					+ "check is measured from — clamped to {},{}..{},{} to keep the walk near the tile "
					+ "the render distance was measured about",
				label, minX, minY, maxX, maxY, allowance,
				clampedMinX, clampedMinY, clampedMaxX, clampedMaxY);
		}

		if (clampedMinX == clampedMaxX && clampedMinY == clampedMaxY)
		{
			log.debug("{}: wanderBox is a single tile — spawning it stationary", label);
			return null;
		}

		return new WanderBox(clampedMinX, clampedMinY, clampedMaxX, clampedMaxY, base.getPlane());
	}

	private static boolean samePlane(PointRecord corner, WorldPoint base)
	{
		return corner.plane != null && corner.plane == base.getPlane();
	}

	@Nullable
	private static WorldPoint toWorldPoint(@Nullable PointRecord point)
	{
		if (point == null || point.x == null || point.y == null || point.plane == null)
		{
			return null;
		}

		if (point.x <= 0 || point.y <= 0 || point.plane < 0 || point.plane > MAX_PLANE)
		{
			return null;
		}

		return new WorldPoint(point.x, point.y, point.plane);
	}

	/**
	 * Validates the optional {@code npcAppearanceId}.
	 *
	 * <p>Degrades to {@code 0} ("not set") rather than skipping the record, so a
	 * record carrying both a junk NPC id and a usable {@code modelIds} array still
	 * spawns from its models. A record carrying only the junk id is skipped by the
	 * caller's gate, which is the same outcome as a record with no models.
	 *
	 * <p>Bounded by {@link CacheIdPlausibility}, the same ceiling the offline audit
	 * holds a model id to — and it is a real bound here rather than a formality:
	 * {@code gameval.NpcID}'s highest constant in 1.12.36 is 16346, so a pasted
	 * hashcode in this field is caught before the client is ever asked about it.
	 *
	 * @return the id, or {@code 0} for absent/unusable
	 */
	private static int npcAppearanceId(@Nullable Integer raw, String label)
	{
		if (raw == null)
		{
			return 0;
		}

		if (!CacheIdPlausibility.isPlausible(raw))
		{
			log.warn("{}: npcAppearanceId {} is not a plausible cache id — ignoring it", label, raw);
			return 0;
		}

		return raw;
	}

	/**
	 * Whether this record is a cameo — see {@link EntityRecord#cameo} and
	 * {@link #isCameo()}.
	 *
	 * <p>Refused for scenery, and loudly. The flag's whole job is to keep
	 * player-shaped named likenesses behind an opt-in, and a crate is neither; a
	 * crate carrying it would be a crate a checkbox nobody expects switches off.
	 * Same reasoning, and the same place, as {@link #usableRemarks} silencing a
	 * talking crate.
	 */
	private static boolean cameo(EntityRecord record, EntityType type, String label)
	{
		if (record.cameo == null || !record.cameo)
		{
			return false;
		}

		if (!type.isCitizen())
		{
			log.warn("{}: {} is not a citizen but is flagged as a cameo — ignoring the flag", label, type);
			return false;
		}

		return true;
	}

	private static int[] usableModelIds(@Nullable int[] ids, String label)
	{
		if (ids == null || ids.length == 0)
		{
			return new int[0];
		}

		int[] kept = new int[ids.length];
		int n = 0;
		for (int id : ids)
		{
			if (id <= 0)
			{
				log.warn("{}: dropping non-positive model id {}", label, id);
				continue;
			}
			kept[n++] = id;
		}

		if (n == ids.length)
		{
			return ids;
		}

		int[] trimmed = new int[n];
		System.arraycopy(kept, 0, trimmed, 0, n);
		return trimmed;
	}

	/**
	 * The one-liners this entity may say, with the four ways of having nothing to
	 * say flattened into one empty array.
	 *
	 * <p>All four occur in the shipped data: 39 citizens carry remarks, 54 carry
	 * {@code "remarks": []}, 42 carry no {@code remarks} field at all, and all 46
	 * scenery records omit it. {@link CitizenRemarks#forDefinition} then has one
	 * condition to check rather than four, and {@link CitizenChatter} never has to
	 * ask what kind of silence it is looking at.
	 *
	 * <p><b>Scenery is silenced here rather than downstream.</b> A talking crate is
	 * an authoring mistake, and the place to refuse it is the validation gate — the
	 * alternative is every later reader remembering to ask
	 * {@code getType().isCitizen()} first, and one of them eventually not.
	 *
	 * <p>Degrades rather than skipping, like every other soft failure in this class:
	 * a blank or null entry is dropped and the rest are kept, because a citizen with
	 * two of its three lines is a working citizen.
	 */
	private static String[] usableRemarks(@Nullable String[] raw, EntityType type, String label)
	{
		if (raw == null || raw.length == 0)
		{
			return NO_REMARKS;
		}

		if (!type.isCitizen())
		{
			log.debug("{}: {} is not a citizen but carries {} remark(s) — ignoring them",
				label, type, raw.length);
			return NO_REMARKS;
		}

		String[] kept = new String[raw.length];
		int n = 0;
		for (String remark : raw)
		{
			if (remark == null || remark.trim().isEmpty())
			{
				continue;
			}
			kept[n++] = remark.trim();
		}

		if (n == 0)
		{
			log.warn("{}: every one of its {} remark(s) was blank — it will not talk",
				label, raw.length);
			return NO_REMARKS;
		}

		if (n < raw.length)
		{
			log.warn("{}: dropped {} blank remark(s), keeping {}", label, raw.length - n, n);
		}

		if (n == raw.length)
		{
			return kept;
		}

		String[] trimmed = new String[n];
		System.arraycopy(kept, 0, trimmed, 0, n);
		return trimmed;
	}

	private static int recolorPairCount(EntityRecord record, String label)
	{
		int findLen = record.modelRecolorFind == null ? 0 : record.modelRecolorFind.length;
		int replaceLen = record.modelRecolorReplace == null ? 0 : record.modelRecolorReplace.length;

		if (findLen != replaceLen)
		{
			log.warn("{}: recolour arrays are {} find vs {} replace — using the first {} pair(s)",
				label, findLen, replaceLen, Math.min(findLen, replaceLen));
		}

		return Math.min(findLen, replaceLen);
	}

	private static UUID parseUuid(@Nullable String raw, String label)
	{
		if (raw != null && !raw.trim().isEmpty())
		{
			try
			{
				return UUID.fromString(raw.trim());
			}
			catch (IllegalArgumentException e)
			{
				log.warn("{}: uuid '{}' is not a UUID, generating one", label, raw);
			}
		}
		else
		{
			log.warn("{}: no uuid, generating one", label);
		}

		return UUID.randomUUID();
	}

	private static int orientation(@Nullable Integer raw, String label)
	{
		if (raw == null)
		{
			return 0;
		}

		int wrapped = ((raw % JAU_FULL_ROTATION) + JAU_FULL_ROTATION) % JAU_FULL_ROTATION;
		if (wrapped != raw)
		{
			log.warn("{}: baseOrientation {} is outside 0..{}, wrapped to {}",
				label, raw, JAU_FULL_ROTATION - 1, wrapped);
		}

		return wrapped;
	}

	@Nullable
	private static LivelyAnimation animation(@Nullable String raw, String field, String label)
	{
		if (raw == null || raw.trim().isEmpty())
		{
			return null;
		}

		LivelyAnimation resolved = LivelyAnimation.fromName(raw);
		if (resolved == null)
		{
			log.warn("{}: unknown {} '{}' — spawning without it", label, field, raw);
		}

		return resolved;
	}

	@Nullable
	private static float[] vector3(@Nullable float[] raw, String field, String label)
	{
		if (raw == null)
		{
			return null;
		}

		if (raw.length != 3)
		{
			log.warn("{}: {} has {} components, expected 3 — ignoring it", label, field, raw.length);
			return null;
		}

		return new float[]{raw[0], raw[1], raw[2]};
	}

	private static List<MergedObject> mergedObjects(@Nullable List<MergedObjectRecord> raw, String label)
	{
		if (raw == null || raw.isEmpty())
		{
			return Collections.emptyList();
		}

		List<MergedObject> out = new ArrayList<>(raw.size());
		for (MergedObjectRecord entry : raw)
		{
			if (entry == null || entry.objectId == null || entry.objectId <= 0)
			{
				log.warn("{}: dropping mergedObject with no usable objectID", label);
				continue;
			}

			int rotations = entry.count90CCWRotations == null ? 0 : entry.count90CCWRotations;
			if (rotations < 0)
			{
				log.warn("{}: mergedObject {} has {} rotations, treating as 0",
					label, entry.objectId, rotations);
				rotations = 0;
			}

			out.add(new MergedObject(entry.objectId, rotations % 4));
		}

		return Collections.unmodifiableList(out);
	}

	private static String describe(EntityRecord record, int fileRegionId)
	{
		String who = record.name != null && !record.name.isEmpty()
			? record.name
			: (record.entityType != null ? record.entityType : "entity");
		return "region " + fileRegionId + " '" + who + "'";
	}

	public UUID getUuid()
	{
		return uuid;
	}

	/**
	 * @return the region id of the file this entity was loaded from — which is
	 * how it was found, and not necessarily where it stands
	 */
	public int getRegionId()
	{
		return regionId;
	}

	/**
	 * @return the region the entity's own tile is in. Equal to
	 * {@link #getRegionId()} for all but one shipped entity, and the one the
	 * scene keys visibility on: it is the region the client has to have loaded
	 * for this entity to be placeable.
	 */
	public int getTileRegionId()
	{
		return tileRegionId;
	}

	/**
	 * The region whose {@link City} checkbox governs this entity.
	 *
	 * <p>For an authored entity this is {@link #getTileRegionId()} — which city a
	 * hand-placed citizen belongs to is a question about where it stands.
	 *
	 * <p><b>For an echo it is its source's, and that is not the same question.</b>
	 * {@link City#isEnabled} fails open for a region no city claims, deliberately, so
	 * that a new region file can ship one commit before its checkbox. Judging an echo
	 * by its own tile therefore let four shipped echoes out through that door: three
	 * derived from Piscatoris citizens land in region 9271 and one derived from a
	 * Camelot citizen lands in 10806, neither of which any city claims and neither of
	 * which ships a region file — so unticking Piscatoris or Camelot left them
	 * standing in an empty village. An echo is a derivative of one authored citizen
	 * and has no independent existence: whatever switches that citizen off switches it
	 * off too. The fail-open rule is untouched for the authored entity that earns it,
	 * and {@code CitizenEchoTest} asserts both halves over the shipped files.
	 */
	public int getCityRegionId()
	{
		return cityRegionId;
	}

	public EntityType getType()
	{
		return type;
	}

	@Nullable
	public String getName()
	{
		return name;
	}

	@Nullable
	public String getExamineText()
	{
		return examineText;
	}

	/**
	 * @return the one-liners this entity may say, never null and never containing a
	 * blank. Empty for scenery and for the 96 shipped citizens with nothing
	 * authored.
	 *
	 * <p>The array itself, not a copy — the same call this class already makes for
	 * {@link #getModelIds()}. Its only reader is
	 * {@link CitizenRemarks#forDefinition}, which indexes it and never writes to it.
	 */
	public String[] getRemarks()
	{
		return remarks;
	}

	public WorldPoint getWorldLocation()
	{
		return worldLocation;
	}

	public int getPlane()
	{
		return worldLocation.getPlane();
	}

	public int getOrientation()
	{
		return orientation;
	}

	/**
	 * @return the raw model ids to build, which is <b>empty</b> for an entity dressed
	 * from an NPC id — see {@link #getNpcAppearanceId()}. Never null. Not a copy: the
	 * only readers iterate it.
	 */
	public int[] getModelIds()
	{
		return modelIds;
	}

	/**
	 * @return the NPC whose appearance this entity wears, or {@code 0} for one built
	 * from {@link #getModelIds()}.
	 *
	 * <p>Read in two places, and each is a rule rather than a convenience:
	 * {@link LivelyEntity#loadParts()} sources the model parts and the recolours from
	 * it instead of from this record when it is set, and {@code CacheIdAudit} walks it
	 * so an NPC id that stops resolving after a game update is reported like any other
	 * cache id. The second is the whole reason for preferring it over raw model ids —
	 * a mechanism that could not be audited would be a new way to die quietly.
	 */
	public int getNpcAppearanceId()
	{
		return npcAppearanceId;
	}

	/**
	 * @return true if this is a <b>cameo</b>: a named, player-shaped likeness of a
	 * real person, which the {@code cameos} config item switches on and which is off
	 * by default.
	 *
	 * <p>Read in three places, and each is load-bearing:
	 * {@code EntityScene.allowedByConfig} refuses it unless {@code cameos} <i>and</i>
	 * its city's checkbox are both on, {@code EntityScene}'s ground gate makes the
	 * collision map vouch for its tile (a cameo's tile was chosen off a wiki map, not
	 * by somebody standing on it), and {@link CitizenEcho} refuses to derive anything
	 * from one — so the "Crowded" dial can never add a body the {@code cameos}
	 * checkbox does not govern.
	 *
	 * <p>Always false for an echo and for scenery, by construction.
	 */
	public boolean isCameo()
	{
		return cameo;
	}

	public short[] getRecolorFind()
	{
		return recolorFind;
	}

	public short[] getRecolorReplace()
	{
		return recolorReplace;
	}

	@Nullable
	public float[] getScale()
	{
		return scale;
	}

	@Nullable
	public float[] getTranslate()
	{
		return translate;
	}

	public List<MergedObject> getMergedObjects()
	{
		return mergedObjects;
	}

	@Nullable
	public LivelyAnimation getIdleAnimation()
	{
		return idleAnimation;
	}

	@Nullable
	public LivelyAnimation getMoveAnimation()
	{
		return moveAnimation;
	}

	/**
	 * @return the patrol box for a wandering citizen, or {@code null} for anything
	 * that stands still — which includes a {@code WanderingCitizen} whose box did
	 * not validate
	 */
	@Nullable
	public WanderBox getWanderBox()
	{
		return wanderBox;
	}

	/**
	 * @return true if this citizen was derived from an authored one by
	 * {@link CitizenEcho} rather than read out of a region file.
	 *
	 * <p>Read in three places, and each is a rule rather than a nicety:
	 * {@link CrowdDensity#includesEchoes()} decides whether it may be shown at all,
	 * {@link StandableGround} has to be satisfied about its tile before it spawns,
	 * and the crowd cap sorts authored citizens ahead of echoes so a procedural one
	 * can never displace an authored one.
	 */
	public boolean isEcho()
	{
		return echoSourceUuid != null;
	}

	/**
	 * @return the uuid of the authored citizen this echo was derived from, or
	 * {@code null} if this is an authored entity. Its own {@link #getUuid()} is
	 * different, deliberately and permanently: {@link CitizenOverrides} hides and
	 * mutes by uuid, so hiding a source must not hide its echo.
	 */
	@Nullable
	public UUID getEchoSourceUuid()
	{
		return echoSourceUuid;
	}

	/**
	 * @return for an echo, whether its tile came from inside its source's authored
	 * wander box. Only consulted when the live collision map has no answer — see
	 * {@link StandableGround}.
	 */
	public boolean isEchoOnAuthoredGround()
	{
		return echoOnAuthoredGround;
	}

	/**
	 * A stable 64-bit hash of this entity's identity.
	 *
	 * <p>Derived from the record's uuid and nothing else, so it is the same value
	 * on every login, in every session, whatever order the region files were read
	 * in and however many times the wrapper cache has been evicted and rebuilt.
	 * That is what {@link CrowdDensity} needs to thin a crowd without the crowd
	 * changing membership between logins, and what {@link CitizenWalk} needs to
	 * give a citizen the same route each session.
	 *
	 * <p>{@code UUID.hashCode()} would nearly do, but it is a 32-bit fold that
	 * leaves sequential uuids sequential — and the low bits are exactly what a
	 * modulo looks at. This runs the two halves through the SplitMix64 finaliser
	 * instead, which is a handful of arithmetic ops and spreads a one-bit input
	 * change across the whole word. It is written out rather than delegated so the
	 * value can never change underneath a saved setting.
	 */
	public long stableHash()
	{
		return stableHashOf(uuid);
	}

	/**
	 * {@link #stableHash()} for a uuid that has no definition yet.
	 *
	 * <p>Needed because {@link CitizenEcho} has to hash an echo's uuid <i>while
	 * deciding</i> what that echo looks like, which is before there is an
	 * {@link EntityDefinition} to ask. Delegating rather than copying the
	 * arithmetic is the point: a second written-out copy of the mixer is a second
	 * thing to keep in step with a value that is baked into saved settings.
	 */
	static long stableHashOf(UUID uuid)
	{
		return mix(uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits());
	}

	/**
	 * The bit mixer behind {@link #stableHash()}: MurmurHash3's 64-bit finaliser,
	 * written out so the value can never change underneath a saved setting.
	 *
	 * <p>Also what derives an echo's uuid from its source's — see
	 * {@link CitizenEcho}. One mixer, one place, so "the same echoes appear every
	 * session" and "the same citizens survive thinning every session" are the same
	 * guarantee rather than two implementations of it.
	 */
	static long mix(long bits)
	{
		bits ^= bits >>> 33;
		bits *= 0xff51afd7ed558ccdL;
		bits ^= bits >>> 33;
		bits *= 0xc4ceb9fe1a85ec53L;
		bits ^= bits >>> 33;
		return bits;
	}

	/**
	 * @return a short, human-readable label for log lines.
	 */
	public String label()
	{
		return (name != null && !name.isEmpty() ? name : type.name())
			+ "@" + worldLocation.getX() + "," + worldLocation.getY() + "," + worldLocation.getPlane();
	}

	@Override
	public String toString()
	{
		return "EntityDefinition{" + label() + ", region=" + regionId + ", type=" + type + '}';
	}

	/**
	 * A wandering citizen's patrol box: an inclusive rectangle of tiles on one
	 * plane, already normalised (min ≤ max), already known to contain the
	 * citizen's start tile, and already clamped to
	 * {@link RenderPolicy#DATASET_OVERHANG_ALLOWANCE} around it.
	 *
	 * <p>Those four guarantees are why {@link CitizenWalk} has no validation in
	 * it: by the time a box exists, every question about it has been answered.
	 */
	public static final class WanderBox
	{
		private final int minX;
		private final int minY;
		private final int maxX;
		private final int maxY;
		private final int plane;

		WanderBox(int minX, int minY, int maxX, int maxY, int plane)
		{
			this.minX = minX;
			this.minY = minY;
			this.maxX = maxX;
			this.maxY = maxY;
			this.plane = plane;
		}

		public int getMinX()
		{
			return minX;
		}

		public int getMinY()
		{
			return minY;
		}

		public int getMaxX()
		{
			return maxX;
		}

		public int getMaxY()
		{
			return maxY;
		}

		public int getPlane()
		{
			return plane;
		}

		public int getWidth()
		{
			return maxX - minX + 1;
		}

		public int getHeight()
		{
			return maxY - minY + 1;
		}

		public boolean contains(int x, int y)
		{
			return x >= minX && x <= maxX && y >= minY && y <= maxY;
		}

		@Override
		public String toString()
		{
			return "WanderBox{" + minX + "," + minY + ".." + maxX + "," + maxY + " plane " + plane + '}';
		}
	}

	/**
	 * An extra model merged into the entity's own, with its pre-rotation.
	 */
	public static final class MergedObject
	{
		private final int objectId;
		private final int rotations;

		MergedObject(int objectId, int rotations)
		{
			this.objectId = objectId;
			this.rotations = rotations;
		}

		public int getObjectId()
		{
			return objectId;
		}

		public int getRotations()
		{
			return rotations;
		}
	}
}
