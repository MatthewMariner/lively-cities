package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.runelite.api.Client;

/**
 * The cache-backed half of the durability tooling: walks every distinct cache id
 * the shipped dataset references — model ids, merged-object ids,
 * {@code npcAppearanceId}s and animation ids — and asks a live {@link Client}
 * whether each one still resolves.
 *
 * <p><b>Why this cannot be a normal test.</b> {@code client.loadModelData(id)},
 * {@code client.getNpcDefinition(id)} and {@code client.loadAnimation(id)} are the
 * only ground truth for whether an id is still valid, and all require a real,
 * cache-loaded client — there is no
 * offline substitute (see {@link CacheIdPlausibility}, which is as far as pure
 * data invariants can go). {@code LivelyCitiesPlugin} only ever calls
 * {@link #run} when {@code developerMode} is on <b>and</b> the
 * {@code livelycities.validateCacheIds} system property is set — which
 * {@code ./gradlew test} never does — so this class is never exercised by the
 * normal build. The Gradle {@code auditCacheIds} task sets both.
 *
 * <p><b>Everything else here is plain data-in/data-out</b>, and is exercised by
 * {@code CacheIdAuditTest} against {@code FakeClient} — the algorithm that
 * decides what counts as broken is exactly the kind of thing that must not be
 * the untested half.
 */
final class CacheIdAudit
{
	/**
	 * {@code LivelyAnimation.BeeIdle}'s id. {@code client.loadAnimation(0)}
	 * returns null by design in every cache state — the real
	 * {@code loadAnimation} returns null for a sequence with no frame lengths
	 * that is also not a Maya animation, and id 0 is exactly that — not because
	 * of a broken or renumbered cache entry. Reported in its own bucket so a
	 * real regression is never lost inside an expected, permanent null.
	 */
	static final int KNOWN_PERMANENT_NULL_ANIMATION_ID = LivelyAnimation.BeeIdle.getId();

	private CacheIdAudit()
	{
	}

	/**
	 * Walks every region {@link City} claims — which {@code CityTest} keeps
	 * exactly equal to the 45 shipped region files — loading each through
	 * {@code loader} and collecting the ids the render core would actually ask
	 * the client to resolve.
	 *
	 * <p>Deliberately not a classpath directory listing (the way the test-only
	 * {@code ShippedRegions} works): that relies on the resource being an
	 * exploded directory on disk, which is true for a test run but not
	 * guaranteed once the plugin's resources are packed inside a shipped jar.
	 * {@link City} is main-source, already required to name exactly the shipped
	 * regions, and works the same way whether the classpath entry is a directory
	 * or a jar.
	 */
	static DatasetIds collect(RegionDataLoader loader)
	{
		TreeSet<Integer> modelIds = new TreeSet<>();
		TreeSet<Integer> mergedObjectIds = new TreeSet<>();
		TreeSet<Integer> npcAppearanceIds = new TreeSet<>();
		TreeMap<String, Integer> animationIdsByName = new TreeMap<>();
		List<String> regionSummaries = new ArrayList<>();
		int regionsLoaded = 0;

		for (int regionId : shippedRegionIds())
		{
			RegionDefinition region = loader.loadRegion(regionId);
			if (region == null)
			{
				regionSummaries.add("region " + regionId + ": failed to load");
				continue;
			}

			regionsLoaded++;
			TreeSet<Integer> regionModelIds = new TreeSet<>();

			for (EntityDefinition entity : region.getEntities())
			{
				for (int id : entity.getModelIds())
				{
					modelIds.add(id);
					regionModelIds.add(id);
				}

				for (EntityDefinition.MergedObject mergedObject : entity.getMergedObjects())
				{
					mergedObjectIds.add(mergedObject.getObjectId());
				}

				// The whole point of preferring an NPC id to raw model ids is that it
				// is still auditable. An entity dressed this way contributes no model
				// ids at all, so without this line it would be the one part of the
				// dataset the durability tooling could not see.
				if (entity.getNpcAppearanceId() != 0)
				{
					npcAppearanceIds.add(entity.getNpcAppearanceId());
				}

				addIfPresent(entity.getIdleAnimation(), animationIdsByName);
				addIfPresent(entity.getMoveAnimation(), animationIdsByName);
			}

			regionSummaries.add(String.format(
				"region %d: %d entities (%d citizens, %d scenery), %d distinct model id(s)",
				regionId, region.getEntityCount(), region.getCitizenCount(),
				region.getSceneryCount(), regionModelIds.size()));
		}

		return new DatasetIds(modelIds, mergedObjectIds, npcAppearanceIds,
			animationIdsByName, regionSummaries, regionsLoaded);
	}

	private static void addIfPresent(LivelyAnimation animation, Map<String, Integer> into)
	{
		if (animation != null)
		{
			into.put(animation.name(), animation.getId());
		}
	}

	/**
	 * @return every region id any {@link City} claims — the same 45 the dataset
	 * ships, per {@code CityTest.everyShippedRegionBelongsToExactlyOneCity}
	 */
	static Set<Integer> shippedRegionIds()
	{
		TreeSet<Integer> ids = new TreeSet<>();
		for (City city : City.values())
		{
			for (int id : city.getRegionIds())
			{
				ids.add(id);
			}
		}
		return ids;
	}

	/**
	 * Asks {@code client} about every id in {@code dataset} and buckets the
	 * results. Never throws: a client call that blows up is recorded as a
	 * failure for that one id rather than aborting the rest of the walk — the
	 * same fail-soft principle {@link EntityDefinition} applies to parsing
	 * applies here to validation, and for the same reason: one bad id must not
	 * hide the report on every other one.
	 */
	static Report run(Client client, DatasetIds dataset)
	{
		List<Integer> failingModelIds = new ArrayList<>();
		for (int id : dataset.modelIds)
		{
			if (!resolvesAsModel(client, id))
			{
				failingModelIds.add(id);
			}
		}

		List<Integer> failingMergedObjectIds = new ArrayList<>();
		for (int id : dataset.mergedObjectIds)
		{
			if (!resolvesAsModel(client, id))
			{
				failingMergedObjectIds.add(id);
			}
		}

		List<Integer> failingNpcAppearanceIds = new ArrayList<>();
		for (int id : dataset.npcAppearanceIds)
		{
			if (!resolvesAsNpcAppearance(client, id))
			{
				failingNpcAppearanceIds.add(id);
			}
		}

		List<String> failingAnimations = new ArrayList<>();
		List<String> knownPermanentNullAnimations = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : dataset.animationIdsByName.entrySet())
		{
			if (!resolvesAsAnimation(client, entry.getValue()))
			{
				String label = entry.getKey() + "=" + entry.getValue();
				if (entry.getValue() == KNOWN_PERMANENT_NULL_ANIMATION_ID)
				{
					knownPermanentNullAnimations.add(label);
				}
				else
				{
					failingAnimations.add(label);
				}
			}
		}

		return new Report(dataset, failingModelIds, failingMergedObjectIds, failingNpcAppearanceIds,
			failingAnimations, knownPermanentNullAnimations);
	}

	private static boolean resolvesAsModel(Client client, int id)
	{
		try
		{
			return client.loadModelData(id) != null;
		}
		catch (RuntimeException e)
		{
			return false;
		}
	}

	/**
	 * Asks the same question {@link NpcAppearance#resolve} asks, through the same
	 * class, so the audit and the render core can never disagree about what "this
	 * NPC id still works" means.
	 *
	 * <p>Going through {@code resolve} rather than calling {@code getNpcDefinition}
	 * here is the point: an id that resolves to a composition with <i>no models</i>
	 * renders nothing, so an audit that only checked the lookup would report a green
	 * id for an invisible citizen. The label is the audit's own, not an entity's —
	 * the same NPC id can be shared by several records.
	 */
	private static boolean resolvesAsNpcAppearance(Client client, int id)
	{
		try
		{
			return NpcAppearance.resolve(client, id, "cache id audit") != null;
		}
		catch (RuntimeException e)
		{
			// resolve() already contains its own throws; this is the backstop for
			// anything thrown on the way in, on the same terms as the two above.
			return false;
		}
	}

	private static boolean resolvesAsAnimation(Client client, int id)
	{
		try
		{
			return client.loadAnimation(id) != null;
		}
		catch (RuntimeException e)
		{
			return false;
		}
	}

	/** Every cache id the dataset references, gathered without touching a client. */
	static final class DatasetIds
	{
		final Set<Integer> modelIds;
		final Set<Integer> mergedObjectIds;

		/**
		 * Every distinct {@code npcAppearanceId} the dataset references — the ids
		 * whose compositions supply a body instead of a {@code modelIds} array.
		 */
		final Set<Integer> npcAppearanceIds;

		final Map<String, Integer> animationIdsByName;
		final List<String> regionSummaries;
		final int regionsLoaded;

		DatasetIds(
			Set<Integer> modelIds,
			Set<Integer> mergedObjectIds,
			Set<Integer> npcAppearanceIds,
			Map<String, Integer> animationIdsByName,
			List<String> regionSummaries,
			int regionsLoaded)
		{
			this.modelIds = Collections.unmodifiableSet(modelIds);
			this.mergedObjectIds = Collections.unmodifiableSet(mergedObjectIds);
			this.npcAppearanceIds = Collections.unmodifiableSet(npcAppearanceIds);
			this.animationIdsByName = Collections.unmodifiableMap(animationIdsByName);
			this.regionSummaries = Collections.unmodifiableList(regionSummaries);
			this.regionsLoaded = regionsLoaded;
		}
	}

	/** The result of one validation pass, and the diffable text form of it. */
	static final class Report
	{
		final DatasetIds dataset;
		final List<Integer> failingModelIds;
		final List<Integer> failingMergedObjectIds;
		final List<Integer> failingNpcAppearanceIds;
		final List<String> failingAnimations;
		final List<String> knownPermanentNullAnimations;

		Report(
			DatasetIds dataset,
			List<Integer> failingModelIds,
			List<Integer> failingMergedObjectIds,
			List<Integer> failingNpcAppearanceIds,
			List<String> failingAnimations,
			List<String> knownPermanentNullAnimations)
		{
			this.dataset = dataset;
			this.failingModelIds = Collections.unmodifiableList(failingModelIds);
			this.failingMergedObjectIds = Collections.unmodifiableList(failingMergedObjectIds);
			this.failingNpcAppearanceIds = Collections.unmodifiableList(failingNpcAppearanceIds);
			this.failingAnimations = Collections.unmodifiableList(failingAnimations);
			this.knownPermanentNullAnimations = Collections.unmodifiableList(knownPermanentNullAnimations);
		}

		boolean hasUnexpectedFailures()
		{
			return !failingModelIds.isEmpty()
				|| !failingMergedObjectIds.isEmpty()
				|| !failingNpcAppearanceIds.isEmpty()
				|| !failingAnimations.isEmpty();
		}

		/**
		 * A stable, sorted, plain-text report — every list here is already sorted
		 * by its collection type ({@link TreeSet}/{@link TreeMap}), so two runs
		 * against the same dataset produce byte-identical text and a real
		 * regression shows up as a small, reviewable diff rather than a reordered
		 * wall of ids.
		 */
		String toReportText()
		{
			StringBuilder sb = new StringBuilder();
			sb.append("# Lively Cities cache id audit\n");
			sb.append("# model ids checked: ").append(dataset.modelIds.size()).append('\n');
			sb.append("# model ids failing: ").append(failingModelIds.size()).append('\n');
			for (int id : failingModelIds)
			{
				sb.append(id).append('\n');
			}
			sb.append('\n');

			sb.append("# merged-object ids checked: ").append(dataset.mergedObjectIds.size()).append('\n');
			sb.append("# merged-object ids failing: ").append(failingMergedObjectIds.size()).append('\n');
			for (int id : failingMergedObjectIds)
			{
				sb.append(id).append('\n');
			}
			sb.append('\n');

			sb.append("# npc appearance ids checked: ").append(dataset.npcAppearanceIds.size()).append('\n');
			sb.append("# npc appearance ids failing: ").append(failingNpcAppearanceIds.size()).append('\n');
			for (int id : failingNpcAppearanceIds)
			{
				sb.append(id).append('\n');
			}
			sb.append('\n');

			sb.append("# animation ids checked: ").append(dataset.animationIdsByName.size()).append('\n');
			sb.append("# animation ids failing: ").append(failingAnimations.size()).append('\n');
			for (String failure : failingAnimations)
			{
				sb.append(failure).append('\n');
			}
			sb.append('\n');

			sb.append("# known-permanent-null (expected, not a failure): ")
				.append(knownPermanentNullAnimations.size()).append('\n');
			for (String known : knownPermanentNullAnimations)
			{
				sb.append(known).append('\n');
			}
			sb.append('\n');

			sb.append("# regions loaded: ").append(dataset.regionsLoaded).append('\n');
			for (String summary : dataset.regionSummaries)
			{
				sb.append("# ").append(summary).append('\n');
			}

			return sb.toString();
		}
	}
}
