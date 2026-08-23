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
import java.util.Set;
import java.util.TreeSet;

/**
 * Pulls the raw {@code idleAnimation} / {@code moveAnimation} strings straight
 * out of the shipped JSON.
 *
 * <p>Deliberately bypasses {@link EntityDefinition}: that class has already
 * resolved names to enum constants and dropped the ones it could not, so asking
 * it which names the dataset uses would only ever return names that resolve.
 */
final class ShippedAnimationNames
{
	private ShippedAnimationNames()
	{
	}

	static Set<String> all()
	{
		Set<String> names = new TreeSet<>();

		for (int regionId : ShippedRegions.ids())
		{
			String resource = RegionDataLoader.DEFAULT_RESOURCE_PREFIX + regionId + ".json";
			InputStream in = ShippedAnimationNames.class.getClassLoader().getResourceAsStream(resource);
			if (in == null)
			{
				throw new IllegalStateException("missing " + resource);
			}

			try (Reader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
			{
				JsonObject root = TestGson.injected().fromJson(reader, JsonObject.class);
				collect(root, "citizenRoster", names);
				collect(root, "sceneryRoster", names);
			}
			catch (IOException e)
			{
				throw new IllegalStateException("could not read " + resource, e);
			}
		}

		if (names.isEmpty())
		{
			throw new IllegalStateException("no animation names found in the shipped dataset");
		}

		return names;
	}

	private static void collect(JsonObject root, String rosterKey, Set<String> into)
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
			addIfPresent(record, "idleAnimation", into);
			addIfPresent(record, "moveAnimation", into);
		}
	}

	private static void addIfPresent(JsonObject record, String key, Set<String> into)
	{
		JsonElement value = record.get(key);
		if (value != null && value.isJsonPrimitive())
		{
			into.add(value.getAsString());
		}
	}
}
