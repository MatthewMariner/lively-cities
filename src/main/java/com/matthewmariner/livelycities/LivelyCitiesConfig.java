package com.matthewmariner.livelycities;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

/**
 * The plugin's dials.
 *
 * <p><b>{@code keyName}s are permanent.</b> They are what RuneLite writes into
 * the user's profile, so a rename silently resets that setting for everyone who
 * had it. Every key here is therefore a machine name chosen independently of the
 * label above it — {@code cityGrandExchange}, not {@code city_Grand_Exchange} —
 * and no key is ever reused for a different city. Renaming the <i>label</i> is
 * free; renaming a key is not, and needs a migration.
 *
 * <p><b>{@code cityDigsite} is retired and must never come back.</b> It shipped
 * over regions 13109, 13110 and 13622, none of which is the Digsite (that is
 * region 13365, which this plugin has no data for). The regions moved to the
 * checkboxes that actually describe them — {@code cityVarrock},
 * {@code cityLumberYard}, {@code cityPaterdomus} — and the old key is left to rot
 * in whichever profiles already hold it. RuneLite ignores a key with no
 * {@code @ConfigItem}, so a stale {@code livelycities.cityDigsite=false} is inert;
 * reusing the name for some other place is what would silently switch that place
 * off for those users.
 *
 * <p><b>Why 24 hand-written city checkboxes.</b> {@code @ConfigItem} is an
 * annotation on an interface method: the set of items is fixed at compile time
 * and cannot be generated from {@link City#values()}. What can be avoided is
 * writing the region ids twice — those live only in {@link City}, and each
 * constant names its getter here through {@code City.enabledIn}.
 *
 * <p><b>What is deliberately absent.</b>
 * <ul>
 *   <li><b>The object cap.</b> {@link RenderPolicy#MAX_ACTIVE_OBJECTS} is 80 and
 *       three separate field runs peaked at 16 active. A dial that has never
 *       been the constraint is a dial that only lets a user break something —
 *       and the cap is the guard that stops a future region file asking the
 *       client to build hundreds of models in one tick. It stays a constant.</li>
 *   <li><b>A master on/off.</b> That is the plugin's own toggle in the
 *       plugin list. A second one would only be a way for the two to disagree.</li>
 *   <li><b>A density <i>count</i>.</b> The dataset has no density field, so
 *       {@link CrowdDensity} thins proportionally instead of pretending to know
 *       how crowded a street was meant to be.</li>
 * </ul>
 */
@ConfigGroup(LivelyCitiesConfig.GROUP)
public interface LivelyCitiesConfig extends Config
{
	String GROUP = "livelycities";

	@ConfigSection(
		name = "Cities",
		description = "Which places are populated. Unticking one deactivates its citizens straight away.",
		position = 20,
		closedByDefault = true
	)
	String citiesSection = "cities";

	@ConfigItem(
		keyName = "cullRadius",
		name = "Render distance",
		description = "How far away, in tiles, citizens and scenery keep rendering. "
			+ "This is the dial that changes what you actually see: at 25 tiles a "
			+ "typical Varrock spot has most of its crowd culled. "
			+ "Above about 16 tiles, distant citizens will sometimes pop in and out as you "
			+ "walk — the game only keeps a 104x104-tile scene loaded and does not recentre "
			+ "it until you have crossed four chunks, so past 16 tiles there is no promise "
			+ "the ground under a citizen is loaded yet. Lower this if the popping bothers "
			+ "you more than the empty streets do; wandering citizens can pop at any setting.",
		position = 1
	)
	@Range(min = RenderPolicy.MIN_CULL_RADIUS, max = RenderPolicy.MAX_CULL_RADIUS)
	default int cullRadius()
	{
		return RenderPolicy.DEFAULT_CULL_RADIUS;
	}

	@ConfigItem(
		keyName = "crowdDensity",
		name = "Crowd density",
		description = "Thins the roster proportionally. The same people are always the ones kept, "
			+ "so a street looks the same every time you walk down it.",
		position = 2
	)
	default CrowdDensity crowdDensity()
	{
		return CrowdDensity.FULL;
	}

	// --- One checkbox per city. Order matches City's declaration order. -------
	// position starts at 100 so a later non-city item can be slotted in above
	// without renumbering 24 annotations.

	@ConfigItem(keyName = "cityAlKharid", name = "Al Kharid", description = "Populate Al Kharid",
		position = 100, section = citiesSection)
	default boolean cityAlKharid()
	{
		return true;
	}

	@ConfigItem(keyName = "cityArdougne", name = "Ardougne", description = "Populate Ardougne",
		position = 101, section = citiesSection)
	default boolean cityArdougne()
	{
		return true;
	}

	@ConfigItem(keyName = "cityBarrows", name = "Barrows", description = "Populate the Barrows",
		position = 102, section = citiesSection)
	default boolean cityBarrows()
	{
		return true;
	}

	@ConfigItem(keyName = "cityCanifis", name = "Canifis", description = "Populate Canifis",
		position = 103, section = citiesSection)
	default boolean cityCanifis()
	{
		return true;
	}

	@ConfigItem(keyName = "cityCastleWars", name = "Castle Wars", description = "Populate Castle Wars",
		position = 104, section = citiesSection)
	default boolean cityCastleWars()
	{
		return true;
	}

	@ConfigItem(keyName = "cityCatherby", name = "Catherby", description = "Populate Catherby",
		position = 105, section = citiesSection)
	default boolean cityCatherby()
	{
		return true;
	}

	@ConfigItem(keyName = "cityDraynor", name = "Draynor", description = "Populate Draynor",
		position = 106, section = citiesSection)
	default boolean cityDraynor()
	{
		return true;
	}

	@ConfigItem(keyName = "cityEdgeville", name = "Edgeville", description = "Populate Edgeville",
		position = 107, section = citiesSection)
	default boolean cityEdgeville()
	{
		return true;
	}

	@ConfigItem(keyName = "cityFalador", name = "Falador", description = "Populate Falador",
		position = 108, section = citiesSection)
	default boolean cityFalador()
	{
		return true;
	}

	@ConfigItem(keyName = "cityFarmingGuild", name = "Farming Guild", description = "Populate the Farming Guild",
		position = 109, section = citiesSection)
	default boolean cityFarmingGuild()
	{
		return true;
	}

	@ConfigItem(keyName = "cityGrandExchange", name = "Grand Exchange", description = "Populate the Grand Exchange",
		position = 110, section = citiesSection)
	default boolean cityGrandExchange()
	{
		return true;
	}

	@ConfigItem(keyName = "cityLumberYard", name = "Lumber Yard", description = "Populate the Lumber Yard",
		position = 111, section = citiesSection)
	default boolean cityLumberYard()
	{
		return true;
	}

	@ConfigItem(keyName = "cityLumbridge", name = "Lumbridge", description = "Populate Lumbridge",
		position = 112, section = citiesSection)
	default boolean cityLumbridge()
	{
		return true;
	}

	@ConfigItem(keyName = "cityMotherlodeMine", name = "Motherlode Mine", description = "Populate the Motherlode Mine",
		position = 113, section = citiesSection)
	default boolean cityMotherlodeMine()
	{
		return true;
	}

	@ConfigItem(keyName = "cityMusaPoint", name = "Musa Point", description = "Populate Musa Point",
		position = 114, section = citiesSection)
	default boolean cityMusaPoint()
	{
		return true;
	}

	@ConfigItem(keyName = "cityOttosGrotto", name = "Otto's Grotto", description = "Populate Otto's Grotto",
		position = 115, section = citiesSection)
	default boolean cityOttosGrotto()
	{
		return true;
	}

	@ConfigItem(keyName = "cityPaterdomus", name = "Paterdomus",
		description = "Populate Paterdomus Temple and the Salve crossing",
		position = 116, section = citiesSection)
	default boolean cityPaterdomus()
	{
		return true;
	}

	@ConfigItem(keyName = "cityPiscatoris", name = "Piscatoris", description = "Populate Piscatoris",
		position = 117, section = citiesSection)
	default boolean cityPiscatoris()
	{
		return true;
	}

	@ConfigItem(keyName = "cityRangingGuild", name = "Ranging Guild", description = "Populate the Ranging Guild",
		position = 118, section = citiesSection)
	default boolean cityRangingGuild()
	{
		return true;
	}

	@ConfigItem(keyName = "cityRimmington", name = "Rimmington", description = "Populate Rimmington",
		position = 119, section = citiesSection)
	default boolean cityRimmington()
	{
		return true;
	}

	@ConfigItem(keyName = "citySeersVillage", name = "Seers' Village", description = "Populate Seers' Village",
		position = 120, section = citiesSection)
	default boolean citySeersVillage()
	{
		return true;
	}

	@ConfigItem(keyName = "cityTaverley", name = "Taverley", description = "Populate Taverley",
		position = 121, section = citiesSection)
	default boolean cityTaverley()
	{
		return true;
	}

	@ConfigItem(keyName = "cityTrollheim", name = "Trollheim", description = "Populate Trollheim",
		position = 122, section = citiesSection)
	default boolean cityTrollheim()
	{
		return true;
	}

	@ConfigItem(keyName = "cityVarrock", name = "Varrock",
		description = "Populate Varrock, including the road outside the east gate",
		position = 123, section = citiesSection)
	default boolean cityVarrock()
	{
		return true;
	}
}
