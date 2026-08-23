package com.matthewmariner.livelycities;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Pulls {@code citizenRoster} entries straight out of the shipped JSON, one
 * per record, carrying exactly the fields the placement lint needs: which
 * file it shipped in (the id {@link City} and the checkbox key on), its uuid,
 * and its name/examine text.
 *
 * <p>Deliberately bypasses {@link EntityDefinition}, the same way
 * {@link ShippedAnimationNames}, {@link ShippedModelIds} and
 * {@link ShippedWanderBoxes} do and for the same reason: this is an offline
 * audit over the authored data, not over what already survived validation.
 *
 * <p><b>Scenery is out of scope.</b> A {@code sceneryRoster} record carries no
 * {@code name} or {@code examineText} — {@link EntityRecord}'s own javadoc
 * says as much — so there is no signal to hand-author a theme from and no
 * name to write a table entry against. All 46 shipped scenery records are
 * generic props (market stalls, planters — see the placement lint report),
 * and {@code PlacementLintTest} explains this rather than guessing a theme
 * from a bare {@code modelIds} array.
 *
 * <p><b>The file's own region id is authoritative</b>, not the {@code
 * regionId} field inside the record. {@link EntityDefinition} treats that
 * field as a claim that can disagree with where the file actually lives (see
 * its {@code fromRecord} javadoc — "Dark wizard" claims 12853 but stands in
 * 12852) and resolves visibility on the tile instead. This class does not need
 * that distinction: {@link City}'s checkbox, and therefore the theme a citizen
 * is judged against, is keyed on which <i>file</i> shipped the record, exactly
 * like {@link ShippedAnimationNames} and its siblings.
 */
final class ShippedCitizens
{
	private ShippedCitizens()
	{
	}

	static List<Entry> all()
	{
		List<Entry> out = new ArrayList<>();

		for (int regionId : ShippedRegions.ids())
		{
			String resource = RegionDataLoader.DEFAULT_RESOURCE_PREFIX + regionId + ".json";
			InputStream in = ShippedCitizens.class.getClassLoader().getResourceAsStream(resource);
			if (in == null)
			{
				throw new IllegalStateException("missing " + resource);
			}

			try (Reader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
			{
				JsonObject root = TestGson.injected().fromJson(reader, JsonObject.class);
				collect(root, regionId, out);
			}
			catch (IOException e)
			{
				throw new IllegalStateException("could not read " + resource, e);
			}
		}

		if (out.isEmpty())
		{
			throw new IllegalStateException("no citizens found in the shipped dataset");
		}

		return out;
	}

	private static void collect(JsonObject root, int fileRegionId, List<Entry> into)
	{
		JsonElement roster = root.get("citizenRoster");
		if (roster == null || !roster.isJsonArray())
		{
			return;
		}

		JsonArray array = roster.getAsJsonArray();
		for (JsonElement element : array)
		{
			if (!element.isJsonObject())
			{
				continue;
			}

			JsonObject record = element.getAsJsonObject();
			into.add(new Entry(
				fileRegionId,
				string(record, "uuid"),
				string(record, "name"),
				string(record, "examineText"),
				string(record, "entityType")));
		}
	}

	private static String string(JsonObject record, String key)
	{
		JsonElement value = record.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}

	/** One shipped {@code citizenRoster} record, exactly as authored. */
	static final class Entry
	{
		final int fileRegionId;
		final String uuid;
		final String name;
		final String examineText;
		final String entityType;

		Entry(int fileRegionId, String uuid, String name, String examineText, String entityType)
		{
			this.fileRegionId = fileRegionId;
			this.uuid = uuid;
			this.name = name;
			this.examineText = examineText;
			this.entityType = entityType;
		}

		@Override
		public String toString()
		{
			return "'" + name + "' (" + examineText + ") in " + fileRegionId + ".json, uuid=" + uuid;
		}
	}
}
