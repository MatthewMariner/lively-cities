package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * A settable {@link LivelyCitiesConfig}.
 *
 * <p>No mocking framework needed and none available: a RuneLite config is an
 * interface of {@code default} methods, so the defaults come for free and only
 * the parts a test cares about have to be written.
 *
 * <p><b>All 9 city getters are overridden individually, on purpose.</b> The
 * cheap version of this class would return one field from all of them — and it
 * would then be a fixture too uniform to distinguish outcomes: every city would
 * answer the same, so {@code City.VARROCK.enabledIn(..)} calling
 * {@code cityLumbridge()} would be invisible. Wiring each getter to its own
 * {@link City} constant makes the composition of the two mappings an identity
 * that {@code CityTest} can assert, so a copy-paste in either direction is a red
 * test.
 *
 * <p><b>The two hidden uuid strings go through a real store.</b>
 * {@link #hiddenCitizens()} and {@link #mutedCitizens()} read the same map
 * {@link #writer()} writes, so "hide this citizen" in a test travels the whole
 * way a real one does: serialise to a string, store it under the key, read it
 * back, parse it. A pair of {@code Set<UUID>} fields would have tested
 * {@link UuidSetting}'s intent and skipped its only interesting failure mode.
 * The map also stands in for {@code ConfigManager}'s absence-versus-default
 * distinction: a key that was never written, or was unset, reads as the
 * interface default.
 */
final class FakeConfig implements LivelyCitiesConfig
{
	private final Set<City> disabled = EnumSet.noneOf(City.class);

	/** What {@link #writer()} has written, keyed on {@code keyName}. */
	private final Map<String, String> stored = new HashMap<>();

	/** Every write, in order — see {@link #writes()}. */
	private final List<String> writes = new ArrayList<>();

	private int cullRadius = RenderPolicy.DEFAULT_CULL_RADIUS;
	private CrowdDensity crowdDensity = CrowdDensity.FULL;

	/**
	 * Starts at the real default, which is <b>off</b>. Deliberately not flipped to
	 * true for convenience: a fixture that opted every test in would make "a fresh
	 * install shows no cameos" a claim no test could check.
	 */
	private boolean cameos = LivelyCitiesConfig.super.cameos();

	private boolean overheadText = true;
	private int remarkIntervalTicks = CitizenChatter.DEFAULT_ROLL_INTERVAL_TICKS;
	private int remarkDwellTicks = CitizenChatter.DEFAULT_DWELL_TICKS;
	private int chatterRadius = CitizenChatter.DEFAULT_RADIUS_TILES;
	private int maxConcurrentRemarks = CitizenChatter.DEFAULT_MAX_CONCURRENT;

	@Nullable
	private CitizenOverrides overrides;

	FakeConfig disable(City... cities)
	{
		for (City city : cities)
		{
			disabled.add(city);
		}
		return this;
	}

	FakeConfig enable(City... cities)
	{
		for (City city : cities)
		{
			disabled.remove(city);
		}
		return this;
	}

	FakeConfig disableOnly(City city)
	{
		disabled.clear();
		disabled.add(city);
		return this;
	}

	FakeConfig setCullRadius(int cullRadius)
	{
		this.cullRadius = cullRadius;
		return this;
	}

	FakeConfig setCrowdDensity(CrowdDensity crowdDensity)
	{
		this.crowdDensity = crowdDensity;
		return this;
	}

	FakeConfig setCameos(boolean cameos)
	{
		this.cameos = cameos;
		return this;
	}

	FakeConfig setOverheadText(boolean overheadText)
	{
		this.overheadText = overheadText;
		return this;
	}

	FakeConfig setRemarkIntervalTicks(int ticks)
	{
		this.remarkIntervalTicks = ticks;
		return this;
	}

	FakeConfig setRemarkDwellTicks(int ticks)
	{
		this.remarkDwellTicks = ticks;
		return this;
	}

	FakeConfig setChatterRadius(int tiles)
	{
		this.chatterRadius = tiles;
		return this;
	}

	FakeConfig setMaxConcurrentRemarks(int max)
	{
		this.maxConcurrentRemarks = max;
		return this;
	}

	/**
	 * The write half of the round trip.
	 *
	 * <p>{@code null} removes the key, which is what {@link ConfigWriter}'s contract
	 * says and what the real one does with {@code unsetConfiguration} — so a cleared
	 * list comes back as the interface default rather than as the empty string, and a
	 * test that asserted on the difference would see it.
	 */
	ConfigWriter writer()
	{
		return (key, value) ->
		{
			writes.add(key + "=" + value);
			if (value == null)
			{
				stored.remove(key);
			}
			else
			{
				stored.put(key, value);
			}
		};
	}

	/**
	 * Every write, in order, as {@code key=value}.
	 *
	 * <p>Counted as well as applied, because "this must not write twice" is a real
	 * requirement in two places: {@code ConfigManager} posts a {@code ConfigChanged}
	 * per write and this plugin answers each one with a full visibility pass, and a
	 * self-unticking button that rewrote itself on its own echo would never stop.
	 */
	List<String> writes()
	{
		return writes;
	}

	/** @return the raw stored value, or {@code null} if the key is not set */
	@Nullable
	String stored(String key)
	{
		return stored.get(key);
	}

	/**
	 * The {@link CitizenOverrides} that reads and writes this config — one instance,
	 * memoised.
	 *
	 * <p>Memoised because it has to be shared. {@link CitizenOverrides} caches its
	 * parse, and the scene and the menu must be looking at the same cache: a test
	 * where the menu hid somebody and the scene held a different {@code UuidSetting}
	 * would pass or fail on which copy happened to re-read the string first, which is
	 * exactly the kind of test that is green for the wrong reason.
	 */
	CitizenOverrides overrides()
	{
		if (overrides == null)
		{
			overrides = new CitizenOverrides(this, writer());
		}
		return overrides;
	}

	private boolean on(City city)
	{
		return !disabled.contains(city);
	}

	@Override
	public int cullRadius()
	{
		return cullRadius;
	}

	@Override
	public CrowdDensity crowdDensity()
	{
		return crowdDensity;
	}

	@Override
	public boolean cameos()
	{
		return cameos;
	}

	@Override
	public boolean overheadText()
	{
		return overheadText;
	}

	@Override
	public int remarkIntervalTicks()
	{
		return remarkIntervalTicks;
	}

	@Override
	public int remarkDwellTicks()
	{
		return remarkDwellTicks;
	}

	@Override
	public int chatterRadius()
	{
		return chatterRadius;
	}

	@Override
	public int maxConcurrentRemarks()
	{
		return maxConcurrentRemarks;
	}

	@Override
	public String hiddenCitizens()
	{
		return stored.getOrDefault(CitizenOverrides.HIDDEN_KEY,
			LivelyCitiesConfig.super.hiddenCitizens());
	}

	@Override
	public String mutedCitizens()
	{
		return stored.getOrDefault(CitizenOverrides.MUTED_KEY,
			LivelyCitiesConfig.super.mutedCitizens());
	}

	@Override
	public boolean unhideAll()
	{
		return Boolean.parseBoolean(stored.getOrDefault(CitizenOverrides.UNHIDE_ALL_KEY, "false"));
	}

	@Override
	public boolean unmuteAll()
	{
		return Boolean.parseBoolean(stored.getOrDefault(CitizenOverrides.UNMUTE_ALL_KEY, "false"));
	}

	// --- One override per checkbox, each pointing at exactly one City. --------

	@Override
	public boolean cityAlKharid()
	{
		return on(City.AL_KHARID);
	}

	@Override
	public boolean cityArdougne()
	{
		return on(City.ARDOUGNE);
	}

	@Override
	public boolean cityCatherby()
	{
		return on(City.CATHERBY);
	}

	@Override
	public boolean cityDraynor()
	{
		return on(City.DRAYNOR);
	}

	@Override
	public boolean cityEdgeville()
	{
		return on(City.EDGEVILLE);
	}

	@Override
	public boolean cityFalador()
	{
		return on(City.FALADOR);
	}

	@Override
	public boolean cityGrandExchange()
	{
		return on(City.GRAND_EXCHANGE);
	}

	@Override
	public boolean cityLumbridge()
	{
		return on(City.LUMBRIDGE);
	}

	@Override
	public boolean cityVarrock()
	{
		return on(City.VARROCK);
	}
}
