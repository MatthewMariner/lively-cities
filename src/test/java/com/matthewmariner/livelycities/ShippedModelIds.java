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
import java.util.TreeSet;

/**
 * Pulls the raw {@code modelIds} arrays straight out of the shipped JSON, one
 * entry per roster record.
 *
 * <p>Deliberately bypasses {@link EntityDefinition}, the same way
 * {@link ShippedAnimationNames} and {@link ShippedWanderBoxes} do and for the
 * same reason: {@code EntityDefinition.usableModelIds} already drops a
 * non-positive id and {@code EntityDefinition.fromRecord} already skips a
 * record whose {@code modelIds} came out empty. Asking the validated definitions
 * whether the dataset ever carries a bad id would only ever see the ids that
 * already survived that filtering — the offline audit's job is to catch the
 * mistake before it is quietly dropped, not to confirm the drop happened.
 */
final class ShippedModelIds
{
	private ShippedModelIds()
	{
	}

	/**
	 * @return one entry per roster record in the shipped dataset, carrying its
	 * raw (unfiltered) {@code modelIds} array exactly as authored
	 */
	static List<Entry> perEntity()
	{
		List<Entry> out = new ArrayList<>();

		for (int regionId : ShippedRegions.ids())
		{
			String resource = RegionDataLoader.DEFAULT_RESOURCE_PREFIX + regionId + ".json";
			InputStream in = ShippedModelIds.class.getClassLoader().getResourceAsStream(resource);
			if (in == null)
			{
				throw new IllegalStateException("missing " + resource);
			}

			try (Reader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
			{
				JsonObject root = TestGson.injected().fromJson(reader, JsonObject.class);
				collect(root, "citizenRoster", regionId, out);
				collect(root, "sceneryRoster", regionId, out);
			}
			catch (IOException e)
			{
				throw new IllegalStateException("could not read " + resource, e);
			}
		}

		if (out.isEmpty())
		{
			throw new IllegalStateException("no roster records found in the shipped dataset");
		}

		return out;
	}

	/**
	 * @return every distinct raw {@code modelIds} value across the whole shipped
	 * dataset — the figure the plan calls "384 distinct model ids"
	 */
	static TreeSet<Integer> distinct()
	{
		TreeSet<Integer> ids = new TreeSet<>();
		for (Entry entry : perEntity())
		{
			for (int id : entry.modelIds)
			{
				ids.add(id);
			}
		}
		return ids;
	}

	private static void collect(JsonObject root, String rosterKey, int regionId, List<Entry> into)
	{
		JsonElement roster = root.get(rosterKey);
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
			into.add(new Entry(regionId, label(record), modelIds(record)));
		}
	}

	private static String label(JsonObject record)
	{
		JsonElement name = record.get("name");
		if (name != null && name.isJsonPrimitive())
		{
			return name.getAsString();
		}

		JsonElement type = record.get("entityType");
		return type != null && type.isJsonPrimitive() ? type.getAsString() : "entity";
	}

	private static int[] modelIds(JsonObject record)
	{
		JsonElement value = record.get("modelIds");
		if (value == null || !value.isJsonArray())
		{
			return new int[0];
		}

		JsonArray array = value.getAsJsonArray();
		int[] ids = new int[array.size()];
		for (int i = 0; i < array.size(); i++)
		{
			ids[i] = array.get(i).getAsInt();
		}
		return ids;
	}

	/** One roster record's raw, unfiltered {@code modelIds}, as authored. */
	static final class Entry
	{
		final int regionId;
		final String label;
		final int[] modelIds;

		Entry(int regionId, String label, int[] modelIds)
		{
			this.regionId = regionId;
			this.label = label;
			this.modelIds = modelIds;
		}

		@Override
		public String toString()
		{
			return "region " + regionId + " '" + label + "'";
		}
	}
}
