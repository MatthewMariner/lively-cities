package com.matthewmariner.livelycities;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * The {@code entityType} discriminator used by {@code RegionData/*.json}.
 *
 * <p>The render core (L1) spawns every one of these the same way — stationary,
 * at {@code worldLocation}, playing {@code idleAnimation}. Wandering and
 * scripted behaviour arrives in L3; the type is carried through so nothing has
 * to be re-parsed then.
 */
public enum EntityType
{
	StationaryCitizen(true),
	WanderingCitizen(true),
	ScriptedCitizen(true),
	Scenery(false);

	private static final Map<String, EntityType> BY_NAME = new HashMap<>();

	static
	{
		for (EntityType t : values())
		{
			BY_NAME.put(t.name(), t);
		}
	}

	private final boolean citizen;

	EntityType(boolean citizen)
	{
		this.citizen = citizen;
	}

	/**
	 * @param name the {@code entityType} string from the region JSON, may be null
	 * @return the matching constant, or {@code null} for a missing/unknown type.
	 * Never throws.
	 */
	@Nullable
	public static EntityType fromName(@Nullable String name)
	{
		if (name == null)
		{
			return null;
		}

		String trimmed = name.trim();
		if (trimmed.isEmpty())
		{
			return null;
		}

		return BY_NAME.get(trimmed);
	}

	/**
	 * @return true for the three citizen flavours, false for scenery.
	 */
	public boolean isCitizen()
	{
		return citizen;
	}
}
