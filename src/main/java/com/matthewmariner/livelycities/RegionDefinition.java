package com.matthewmariner.livelycities;

import java.util.Collections;
import java.util.List;

/**
 * One parsed {@code RegionData/<regionId>.json}: what loaded, and what did not.
 *
 * <p>{@link #getSkippedRecords()} exists so the "fail soft" promise is
 * observable rather than a claim — the log line at load time reports it, and the
 * unit tests assert on it.
 */
public final class RegionDefinition
{
	private final int regionId;
	private final float version;
	private final List<EntityDefinition> entities;
	private final int skippedRecords;

	RegionDefinition(int regionId, float version, List<EntityDefinition> entities, int skippedRecords)
	{
		this.regionId = regionId;
		this.version = version;
		this.entities = Collections.unmodifiableList(entities);
		this.skippedRecords = skippedRecords;
	}

	public int getRegionId()
	{
		return regionId;
	}

	public float getVersion()
	{
		return version;
	}

	public List<EntityDefinition> getEntities()
	{
		return entities;
	}

	public int getSkippedRecords()
	{
		return skippedRecords;
	}

	public int getEntityCount()
	{
		return entities.size();
	}

	public int getCitizenCount()
	{
		int n = 0;
		for (EntityDefinition e : entities)
		{
			if (e.getType().isCitizen())
			{
				n++;
			}
		}
		return n;
	}

	public int getSceneryCount()
	{
		return entities.size() - getCitizenCount();
	}

	@Override
	public String toString()
	{
		return "RegionDefinition{" + regionId
			+ ", v" + version
			+ ", " + getCitizenCount() + " citizens"
			+ ", " + getSceneryCount() + " scenery"
			+ ", " + skippedRecords + " skipped}";
	}
}
