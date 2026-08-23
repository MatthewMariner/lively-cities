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
 * That forces 24 hand-written methods on {@link LivelyCitiesConfig} — but it does
 * not force the region lists to be written twice. They live here, once; each
 * constant knows which of those 22 methods is its own, through
 * {@link #enabledIn(LivelyCitiesConfig)}. So adding a region to a city is a
 * one-line change in one file, and {@code CityTest} fails if the two halves ever
 * stop lining up.
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
 * Ranging Guild (10549) filed under Catherby, roughly 160 tiles away. Both are
 * corrected below, and the {@code cityDigsite} key is retired rather than reused
 * — see {@link LivelyCitiesConfig} for why a {@code keyName} is permanent.
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
	BARROWS("Barrows", 14131)
		{
			@Override
			boolean enabledIn(LivelyCitiesConfig config)
			{
				return config.cityBarrows();
			}
		},
	/**
	 * Region 11062, and it really is Camelot rather than Seers' Village.
	 *
	 * <p>The wiki puts Camelot's map centre at (2758, 3507), which computes to
	 * 11062; Seers' Village proper is centred at (2710, 3485), which is region
	 * 10806 — not in this dataset at all. An earlier pass shipped this as
	 * {@code citySeersVillage}, the same mistake as the retired
	 * {@code cityDigsite}: naming a region after the famous place next door
	 * instead of what stands inside the square. Both keys are retired rather
	 * than renamed, for the reason given on {@link LivelyCitiesConfig}.
	 */
	CAMELOT("Camelot", 11062)
		{
			@Override
			boolean enabledIn(LivelyCitiesConfig config)
			{
				return config.cityCamelot();
			}
		},
	CANIFIS("Canifis", 13878)
		{
			@Override
			boolean enabledIn(LivelyCitiesConfig config)
			{
				return config.cityCanifis();
			}
		},
	CASTLE_WARS("Castle Wars", 9776)
		{
			@Override
			boolean enabledIn(LivelyCitiesConfig config)
			{
				return config.cityCastleWars();
			}
		},
	// 11061 and 11317 are the two halves of Catherby; 10549 used to be in here
	// and is not — see RANGING_GUILD.
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
	FARMING_GUILD("Farming Guild", 4922)
		{
			@Override
			boolean enabledIn(LivelyCitiesConfig config)
			{
				return config.cityFarmingGuild();
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
	/**
	 * Region 13110. The Lumber Yard's own map polygon runs x 3293..3326,
	 * y 3492..3518, every corner of which is region 13110, and the wiki files it
	 * under Varrock — but it is a 50-tile walk outside the city wall and a
	 * destination in its own right, so it gets its own checkbox rather than being
	 * switched on and off by "Varrock".
	 */
	LUMBER_YARD("Lumber Yard", 13110)
		{
			@Override
			boolean enabledIn(LivelyCitiesConfig config)
			{
				return config.cityLumberYard();
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
	MOTHERLODE_MINE("Motherlode Mine", 14936)
		{
			@Override
			boolean enabledIn(LivelyCitiesConfig config)
			{
				return config.cityMotherlodeMine();
			}
		},
	MUSA_POINT("Musa Point", 11569)
		{
			@Override
			boolean enabledIn(LivelyCitiesConfig config)
			{
				return config.cityMusaPoint();
			}
		},
	OTTOS_GROTTO("Otto's Grotto", 10038)
		{
			@Override
			boolean enabledIn(LivelyCitiesConfig config)
			{
				return config.cityOttosGrotto();
			}
		},
	/**
	 * Region 13622. Paterdomus Temple's map centre is (3416, 3487) — region 13622
	 * — and the wiki puts the temple in Silvarea, whose own centre (3375, 3500) is
	 * the region next door (13366), which this plugin ships no file for. So the
	 * region is named after the landmark that is actually inside it.
	 */
	PATERDOMUS("Paterdomus", 13622)
		{
			@Override
			boolean enabledIn(LivelyCitiesConfig config)
			{
				return config.cityPaterdomus();
			}
		},
	PISCATORIS("Piscatoris", 9016, 9272, 9273)
		{
			@Override
			boolean enabledIn(LivelyCitiesConfig config)
			{
				return config.cityPiscatoris();
			}
		},
	/**
	 * Region 10549. The Ranging Guild's map polygon runs x 2651..2686,
	 * y 3411..3446, every corner of which is region 10549. It shipped under
	 * "Catherby" (11061, x 2752..2815), about 160 tiles east.
	 */
	RANGING_GUILD("Ranging Guild", 10549)
		{
			@Override
			boolean enabledIn(LivelyCitiesConfig config)
			{
				return config.cityRangingGuild();
			}
		},
	RIMMINGTON("Rimmington", 11826)
		{
			@Override
			boolean enabledIn(LivelyCitiesConfig config)
			{
				return config.cityRimmington();
			}
		},
	TAVERLEY("Taverley", 11573, 11318)
		{
			@Override
			boolean enabledIn(LivelyCitiesConfig config)
			{
				return config.cityTaverley();
			}
		},
	TROLLHEIM("Trollheim", 11577)
		{
			@Override
			boolean enabledIn(LivelyCitiesConfig config)
			{
				return config.cityTrollheim();
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
	 * with a config whose 22 getters are individually distinguishable, so a
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
