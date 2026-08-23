package com.matthewmariner.livelycities;

import java.util.EnumSet;
import java.util.Set;

/**
 * A settable {@link LivelyCitiesConfig}.
 *
 * <p>No mocking framework needed and none available: a RuneLite config is an
 * interface of {@code default} methods, so the defaults come for free and only
 * the parts a test cares about have to be written.
 *
 * <p><b>All 24 city getters are overridden individually, on purpose.</b> The
 * cheap version of this class would return one field from all of them — and it
 * would then be a fixture too uniform to distinguish outcomes: every city would
 * answer the same, so {@code City.VARROCK.enabledIn(..)} calling
 * {@code cityLumbridge()} would be invisible. Wiring each getter to its own
 * {@link City} constant makes the composition of the two mappings an identity
 * that {@code CityTest} can assert, so a copy-paste in either direction is a red
 * test.
 */
final class FakeConfig implements LivelyCitiesConfig
{
	private final Set<City> disabled = EnumSet.noneOf(City.class);

	private int cullRadius = RenderPolicy.DEFAULT_CULL_RADIUS;
	private CrowdDensity crowdDensity = CrowdDensity.FULL;

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
	public boolean cityBarrows()
	{
		return on(City.BARROWS);
	}

	@Override
	public boolean cityCanifis()
	{
		return on(City.CANIFIS);
	}

	@Override
	public boolean cityCastleWars()
	{
		return on(City.CASTLE_WARS);
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
	public boolean cityFarmingGuild()
	{
		return on(City.FARMING_GUILD);
	}

	@Override
	public boolean cityGrandExchange()
	{
		return on(City.GRAND_EXCHANGE);
	}

	@Override
	public boolean cityLumberYard()
	{
		return on(City.LUMBER_YARD);
	}

	@Override
	public boolean cityLumbridge()
	{
		return on(City.LUMBRIDGE);
	}

	@Override
	public boolean cityMotherlodeMine()
	{
		return on(City.MOTHERLODE_MINE);
	}

	@Override
	public boolean cityMusaPoint()
	{
		return on(City.MUSA_POINT);
	}

	@Override
	public boolean cityOttosGrotto()
	{
		return on(City.OTTOS_GROTTO);
	}

	@Override
	public boolean cityPaterdomus()
	{
		return on(City.PATERDOMUS);
	}

	@Override
	public boolean cityPiscatoris()
	{
		return on(City.PISCATORIS);
	}

	@Override
	public boolean cityRangingGuild()
	{
		return on(City.RANGING_GUILD);
	}

	@Override
	public boolean cityRimmington()
	{
		return on(City.RIMMINGTON);
	}

	@Override
	public boolean citySeersVillage()
	{
		return on(City.SEERS_VILLAGE);
	}

	@Override
	public boolean cityTaverley()
	{
		return on(City.TAVERLEY);
	}

	@Override
	public boolean cityTrollheim()
	{
		return on(City.TROLLHEIM);
	}

	@Override
	public boolean cityVarrock()
	{
		return on(City.VARROCK);
	}
}
