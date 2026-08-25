package com.matthewmariner.livelycities;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

/**
 * The places the dataset covers, and the single source of truth for which region
 * ids belong to which of them.
 *
 * <p><b>Why an enum and not a table in the config interface.</b> RuneLite config
 * items are static declarations: {@code @ConfigItem} annotates a method on an
 * interface, so there is no way to generate one checkbox per city at runtime.
 * That forces 9 hand-written methods on {@link LivelyCitiesConfig} — but it does
 * not force the region lists to be written twice. They live here, once; each
 * constant knows which of those 9 methods is its own, through
 * {@link #enabledIn(LivelyCitiesConfig)}. So adding a region to a city is a
 * one-line change in one file, and {@code CityTest} fails if the two halves ever
 * stop lining up.
 *
 * <p><b>Why nine places and not twenty-four.</b> An earlier revision shipped 24,
 * and thirteen of them held one or two figures each — ten held exactly one, and
 * Varrock alone held 97 of the 181 entities. A city with one citizen is not
 * ambient life: it is a checkbox, a region file, a set of cache ids that break on
 * a game update, and a user who ticks "Canifis", sees one person, and concludes
 * the plugin is broken. The rule applied on 2026-08-24 was <b>a real city with at
 * least three figures</b>; fifteen places failed it and were removed outright,
 * dataset and all. Varrock is deliberately the dense flagship. Bringing the
 * thinner survivors up is content work, not a reason to keep shipping the thin
 * ones meanwhile.
 *
 * <p><b>Every id below was checked against a primary source</b> —
 * {@code ((x >> 6) << 8) | (y >> 6)} applied to the {@code {{Map}}} coordinates
 * in the corresponding OSRS Wiki {@code {{Infobox Location}}}, which is the same
 * arithmetic as {@link RenderPolicy#regionIdOf(int, int)}. {@code CityTest}
 * asserts that every shipped {@code RegionData} file maps to exactly one city, so
 * a new region file with no home fails the build instead of quietly becoming
 * untoggleable.
 *
 * <p><b>What that check caught.</b> An earlier revision shipped a "Digsite"
 * checkbox over regions 13109, 13110 and 13622, none of which contains the
 * Digsite: the Digsite's own map centre is (3354, 3420), which is region
 * <b>13365</b>, and the plugin ships no file for it. The same pass found the
 * Ranging Guild (10549) filed under Catherby, roughly 160 tiles away. 13109 is
 * still here, under Varrock, where the arithmetic put it; 13110, 13622 and 10549
 * have since been dropped from the dataset entirely as part of the nine-city cut,
 * so the only trace they leave is their retired {@code keyName}s. The
 * {@code cityDigsite} key stays retired rather than reused — see
 * {@link LivelyCitiesConfig} for why a {@code keyName} is permanent.
 *
 * <p><b>A vague label beats a confident wrong one.</b> Region 13109 has no
 * landmark of its own; it is the strip of road immediately outside Varrock's east
 * gate, and its one entity ("City workman") stands four tiles east of the city
 * wall. It is grouped under Varrock for that reason and not because anything
 * named "Varrock" sits in it.
 *
 * <p><b>An unmapped region stays visible.</b> {@link #isEnabled} answers
 * {@code true} for a region no constant claims. A region file can be added in one
 * commit and its checkbox in the next without the entities vanishing in between;
 * the test is what stops that grace period becoming permanent.
 */
@Slf4j
public enum City
{
	// The order here is the order the checkboxes appear in, so it is alphabetical
	// by display label rather than by region id.
	AL_KHARID("Al Kharid", 13105, 13106, 13361)
		{
			@Override
			boolean enabledIn(LivelyCitiesConfig config)
			{
				return config.cityAlKharid();
			}
		},
	ARDOUGNE("Ardougne", 10290, 10548, 10804)
		{
			@Override
			boolean enabledIn(LivelyCitiesConfig config)
			{
				return config.cityArdougne();
			}
		},
	// 11061 and 11317 are the two halves of Catherby. Region 10549 (the Ranging
	// Guild, ~160 tiles west) was once filed in here by mistake; it was moved to a
	// checkbox of its own, and then dropped with the rest of the nine-city cut.
	CATHERBY("Catherby", 11061, 11317)
		{
			@Override
			boolean enabledIn(LivelyCitiesConfig config)
			{
				return config.cityCatherby();
			}
		},
	DRAYNOR("Draynor", 12338, 12340)
		{
			@Override
			boolean enabledIn(LivelyCitiesConfig config)
			{
				return config.cityDraynor();
			}
		},
	EDGEVILLE("Edgeville", 12342)
		{
			@Override
			boolean enabledIn(LivelyCitiesConfig config)
			{
				return config.cityEdgeville();
			}
		},
	FALADOR("Falador", 11828, 11829, 12083, 11571)
		{
			@Override
			boolean enabledIn(LivelyCitiesConfig config)
			{
				return config.cityFalador();
			}
		},
	GRAND_EXCHANGE("Grand Exchange", 12598)
		{
			@Override
			boolean enabledIn(LivelyCitiesConfig config)
			{
				return config.cityGrandExchange();
			}
		},
	LUMBRIDGE("Lumbridge", 12850, 12594, 12595, 12849)
		{
			@Override
			boolean enabledIn(LivelyCitiesConfig config)
			{
				return config.cityLumbridge();
			}
		},
	/**
	 * The city's own six regions, plus 13109 — the strip east of the east gate.
	 * 13109 covers x 3264..3327, y 3392..3455, which starts one tile past the
	 * eastern wall (12853 ends at x 3263) and stops short of the Digsite's region
	 * (13365, x 3328..3391). Nothing in it has a name of its own; its single
	 * entity, a "City workman", stands at (3268, 3426), i.e. at the gate. Varrock
	 * is where it belongs for that reason and no stronger one.
	 */
	VARROCK("Varrock", 12853, 12852, 12854, 12597, 12596, 12697, 13109)
		{
			@Override
			boolean enabledIn(LivelyCitiesConfig config)
			{
				return config.cityVarrock();
			}
		};

	private static final Map<Integer, City> BY_REGION = index();

	private final String label;
	private final int[] regionIds;

	City(String label, int... regionIds)
	{
		this.label = label;
		this.regionIds = regionIds;
	}
	/**
	 * Which of the {@code enabledIn} config getters is this city's.
	 *
	 * <p>Abstract rather than a lambda field or a name-based lookup: the compiler
	 * then guarantees every constant answers, and {@code CityTest} composes this
	 * with a config whose 9 getters are individually distinguishable, so a
	 * copy-paste that points two cities at the same checkbox is a red test rather
	 * than a checkbox that quietly does someone else's job.
	 */
	abstract boolean enabledIn(LivelyCitiesConfig config);
	/**
	 * @param regionId a region id
	 * @return the city that claims it, or {@code null} if none does
	 */
	@Nullable
	public static City of(int regionId)
	{
		return BY_REGION.get(regionId);
	}
	/**
	 * Whether entities standing in a region should be shown.
	 *
	 * @return the city's checkbox, or {@code true} for a region no city claims —
	 * see the class javadoc for why an unmapped region fails open
	 */
	public static boolean isEnabled(int regionId, LivelyCitiesConfig config)
	{
		City city = BY_REGION.get(regionId);
		return city == null || city.enabledIn(config);
	}

	public String getLabel()
	{
		return label;
	}
	/**
	 * @return this city's region ids. A copy: the array is the enum's state, and
	 * handing out the original would let a caller edit the mapping.
	 */
	public int[] getRegionIds()
	{
		return regionIds.clone();
	}

	@Override
	public String toString()
	{
		return label;
	}
	/**
	 * Builds the region lookup.
	 *
	 * <p>Fail-soft on a duplicate — first constant wins, with a loud line —
	 * because the rest of this plugin refuses to lose a city over one bad record
	 * and a static initialiser that throws would lose all of them. The build-time
	 * guard is {@code CityTest}, which walks the constants' own arrays rather than
	 * this map, so it sees the collision this method swallows.
	 */
	private static Map<Integer, City> index()
	{
		Map<Integer, City> byRegion = new HashMap<>();
		for (City city : values())
		{
			for (int regionId : city.regionIds)
			{
				City clash = byRegion.put(regionId, city);
				if (clash != null && clash != city)
				{
					log.error("Lively Cities: region {} is claimed by both {} and {} — keeping {}",
						regionId, clash.label, city.label, clash.label);
					byRegion.put(regionId, clash);
				}
			}
		}
		return Collections.unmodifiableMap(byRegion);
	}
}
