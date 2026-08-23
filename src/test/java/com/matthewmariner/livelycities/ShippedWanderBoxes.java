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
 * Pulls the {@code wanderBox} corners straight out of the shipped JSON, as
 * authored.
 *
 * <p><b>Deliberately bypasses {@link EntityDefinition}</b>, the same way
 * {@link ShippedAnimationNames} does and for the same reason — and here the
 * reason has teeth. {@code EntityDefinition} <i>clamps</i> a wander box to
 * {@link RenderPolicy#DATASET_OVERHANG_ALLOWANCE} tiles around the citizen's
 * start tile. Asking it how far the dataset's boxes reach would therefore always
 * get the allowance back, whatever the allowance was set to: the guard on that
 * number would be measuring its own output and could never go red.
 *
 * <p>A mutation test found exactly that. This class is the fix.
 */
final class ShippedWanderBoxes
{
	private ShippedWanderBoxes()
	{
	}

	/**
	 * @return one entry per {@code WanderingCitizen} in the shipped dataset, with
	 * the box exactly as the file states it
	 */
	static List<Authored> all()
	{
		List<Authored> out = new ArrayList<>();

		for (int regionId : ShippedRegions.ids())
		{
			String resource = RegionDataLoader.DEFAULT_RESOURCE_PREFIX + regionId + ".json";
			InputStream in = ShippedWanderBoxes.class.getClassLoader().getResourceAsStream(resource);
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
			throw new IllegalStateException("no wander boxes found in the shipped dataset");
		}

		return out;
	}

	private static void collect(JsonObject root, int regionId, List<Authored> into)
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
			if (!"WanderingCitizen".equals(string(record, "entityType")))
			{
				continue;
			}

			JsonObject base = object(record, "worldLocation");
			JsonObject bl = object(record, "wanderBoxBL");
			JsonObject tr = object(record, "wanderBoxTR");
			if (base == null || bl == null || tr == null)
			{
				// Reported as-is: a wanderer with no box is a finding, not
				// something to quietly drop.
				into.add(new Authored(regionId, string(record, "name"), 0, 0, 0, 0, 0, 0, false));
				continue;
			}

			into.add(new Authored(
				regionId,
				string(record, "name"),
				base.get("x").getAsInt(),
				base.get("y").getAsInt(),
				bl.get("x").getAsInt(),
				bl.get("y").getAsInt(),
				tr.get("x").getAsInt(),
				tr.get("y").getAsInt(),
				true));
		}
	}

	private static String string(JsonObject record, String key)
	{
		JsonElement value = record.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}

	private static JsonObject object(JsonObject record, String key)
	{
		JsonElement value = record.get(key);
		return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
	}

	/** One wandering citizen's box, exactly as the file states it. */
	static final class Authored
	{
		private final int regionId;
		private final String name;
		private final int baseX;
		private final int baseY;
		private final int blX;
		private final int blY;
		private final int trX;
		private final int trY;
		private final boolean hasBox;

		Authored(int regionId, String name, int baseX, int baseY,
			int blX, int blY, int trX, int trY, boolean hasBox)
		{
			this.regionId = regionId;
			this.name = name;
			this.baseX = baseX;
			this.baseY = baseY;
			this.blX = blX;
			this.blY = blY;
			this.trX = trX;
			this.trY = trY;
			this.hasBox = hasBox;
		}

		boolean hasBox()
		{
			return hasBox;
		}

		/**
		 * @return Chebyshev distance from the citizen's start tile to the furthest
		 * corner of its authored box — i.e. how far from the tile its cull check is
		 * measured against the walk can take it
		 */
		int reach()
		{
			int dx = Math.max(Math.abs(blX - baseX), Math.abs(trX - baseX));
			int dy = Math.max(Math.abs(blY - baseY), Math.abs(trY - baseY));
			return Math.max(dx, dy);
		}

		@Override
		public String toString()
		{
			return "'" + name + "' in " + regionId + ".json at " + baseX + "," + baseY
				+ " pacing " + blX + "," + blY + ".." + trX + "," + trY;
		}
	}
}
