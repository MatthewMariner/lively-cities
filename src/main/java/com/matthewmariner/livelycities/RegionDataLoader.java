package com.matthewmariner.livelycities;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.matthewmariner.livelycities.data.EntityRecord;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads {@code RegionData/<regionId>.json} off the classpath and turns it into
 * validated {@link EntityDefinition}s.
 *
 * <p>Parsing is per-record on purpose. Binding the whole roster to a DTO array
 * in one call means a single bad element aborts the file, which is how the
 * predecessor managed to lose entire cities; here each roster entry gets its own
 * try/catch, so a malformed record costs exactly that record.
 *
 * <p>The {@code Gson} comes in through Guice — RuneLite provides a configured
 * one, and {@code new Gson()} is on the Plugin Hub's disallowed-API list.
 */
@Slf4j
@Singleton
public class RegionDataLoader
{
	/**
	 * The schema version every shipped file carries. A mismatch is reported and
	 * then ignored: the point is to notice a format change, not to blank a city
	 * because a number moved.
	 */
	static final float EXPECTED_VERSION = 0.8f;

	static final String DEFAULT_RESOURCE_PREFIX = "RegionData/";

	private final Gson gson;
	private final String resourcePrefix;

	@Inject
	RegionDataLoader(Gson gson)
	{
		this(gson, DEFAULT_RESOURCE_PREFIX);
	}

	RegionDataLoader(Gson gson, String resourcePrefix)
	{
		this.gson = gson;
		this.resourcePrefix = resourcePrefix;
	}

	/**
	 * @param regionId the region id, which is also the file name
	 * @return the parsed region, or {@code null} when there is no file for this
	 * region or the file is not parseable JSON at all. Never throws.
	 */
	@Nullable
	public RegionDefinition loadRegion(int regionId)
	{
		String resource = resourcePrefix + regionId + ".json";
		InputStream in = RegionDataLoader.class.getClassLoader().getResourceAsStream(resource);
		if (in == null)
		{
			// Most regions simply have no citizens. Not an error.
			log.trace("no region data for {}", regionId);
			return null;
		}

		try (Reader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
		{
			return parse(reader, regionId);
		}
		catch (IOException e)
		{
			log.warn("region {}: could not read {}", regionId, resource, e);
			return null;
		}
	}

	/**
	 * Parses region JSON from an already-open reader. Split out from
	 * {@link #loadRegion(int)} and left package-visible so the parser can be
	 * driven from a string rather than the classpath.
	 *
	 * @return the parsed region, or {@code null} if the document is not a JSON
	 * object.
	 */
	@Nullable
	RegionDefinition parse(Reader reader, int regionId)
	{
		JsonObject root;
		try
		{
			JsonElement parsed = gson.fromJson(reader, JsonElement.class);
			if (parsed == null || !parsed.isJsonObject())
			{
				log.warn("region {}: data is not a JSON object, skipped", regionId);
				return null;
			}
			root = parsed.getAsJsonObject();
		}
		catch (JsonParseException e)
		{
			log.warn("region {}: malformed JSON, skipped ({})", regionId, e.getMessage());
			return null;
		}

		float version = readVersion(root, regionId);
		checkDeclaredRegionId(root, regionId);

		List<EntityDefinition> entities = new ArrayList<>();
		Set<UUID> seen = new HashSet<>();
		int skipped = 0;

		skipped += readRoster(root, "citizenRoster", regionId, entities, seen);
		skipped += readRoster(root, "sceneryRoster", regionId, entities, seen);

		RegionDefinition region = new RegionDefinition(regionId, version, entities, skipped);
		if (skipped > 0)
		{
			log.warn("region {}: {} record(s) skipped, {} loaded", regionId, skipped, entities.size());
		}

		return region;
	}

	private float readVersion(JsonObject root, int regionId)
	{
		JsonElement el = root.get("version");
		if (el == null || !el.isJsonPrimitive())
		{
			log.warn("region {}: no version field, assuming {}", regionId, EXPECTED_VERSION);
			return EXPECTED_VERSION;
		}

		float version;
		try
		{
			version = el.getAsFloat();
		}
		catch (NumberFormatException | UnsupportedOperationException e)
		{
			log.warn("region {}: version '{}' is not a number, loading anyway", regionId, el);
			return 0f;
		}

		if (version != EXPECTED_VERSION)
		{
			// Deliberately not a bail-out. See the class javadoc.
			log.warn("region {}: schema version {} differs from the expected {} — loading anyway",
				regionId, version, EXPECTED_VERSION);
		}

		return version;
	}

	private void checkDeclaredRegionId(JsonObject root, int regionId)
	{
		JsonElement el = root.get("regionId");
		if (el == null || !el.isJsonPrimitive())
		{
			return;
		}

		try
		{
			int declared = el.getAsInt();
			if (declared != regionId)
			{
				log.warn("region {}: file declares regionId {} — the file name wins", regionId, declared);
			}
		}
		catch (NumberFormatException | UnsupportedOperationException e)
		{
			log.warn("region {}: regionId field '{}' is not a number", regionId, el);
		}
	}

	/**
	 * @return the number of records skipped in this roster
	 */
	private int readRoster(
		JsonObject root,
		String key,
		int regionId,
		List<EntityDefinition> out,
		Set<UUID> seen)
	{
		JsonElement el = root.get(key);
		if (el == null || el.isJsonNull())
		{
			return 0;
		}

		if (!el.isJsonArray())
		{
			log.warn("region {}: {} is not an array, ignored", regionId, key);
			return 0;
		}

		JsonArray array = el.getAsJsonArray();
		int skipped = 0;

		for (JsonElement element : array)
		{
			EntityRecord record;
			try
			{
				record = gson.fromJson(element, EntityRecord.class);
			}
			catch (JsonParseException e)
			{
				log.warn("region {}: unparseable {} entry skipped ({})", regionId, key, e.getMessage());
				skipped++;
				continue;
			}

			EntityDefinition definition = EntityDefinition.fromRecord(record, regionId);
			if (definition == null)
			{
				skipped++;
				continue;
			}

			if (!seen.add(definition.getUuid()))
			{
				log.warn("region {}: duplicate uuid {} on {} — keeping both",
					regionId, definition.getUuid(), definition.label());
			}

			out.add(definition);
		}

		return skipped;
	}
}
