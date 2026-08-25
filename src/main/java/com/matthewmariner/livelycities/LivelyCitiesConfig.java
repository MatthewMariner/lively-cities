package com.matthewmariner.livelycities;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

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
 * {@code cityLumberYard}, {@code cityPaterdomus}, the last two of which have since
 * been retired in their own right (below) — and the old key is left to rot
 * in whichever profiles already hold it. RuneLite ignores a key with no
 * {@code @ConfigItem}, so a stale {@code livelycities.cityDigsite=false} is inert;
 * the same goes for the equally-retired {@code citySeersVillage}, which named
 * region 11062 after the village next door when the square is Camelot. Reusing
 * either name for some other place is what would silently switch that place
 * off for those users.
 *
 * <p><b>Fifteen more keys were retired on 2026-08-24, and the same rule binds
 * them.</b> The dataset was cut from 24 places to 9 (see {@link City} for the
 * rule and the reason), and the places that went took their checkboxes with
 * them. Every one of these is now a name RuneLite will find no
 * {@code @ConfigItem} for, so a profile still holding
 * {@code livelycities.cityBarrows=false} is inert — and none of them may ever be
 * reused for a different place:
 *
 * <p>{@code cityBarrows}, {@code cityCamelot}, {@code cityCanifis},
 * {@code cityCastleWars}, {@code cityFarmingGuild}, {@code cityLumberYard},
 * {@code cityMotherlodeMine}, {@code cityMusaPoint}, {@code cityOttosGrotto},
 * {@code cityPaterdomus}, {@code cityPiscatoris}, {@code cityRangingGuild},
 * {@code cityRimmington}, {@code cityTaverley}, {@code cityTrollheim}.
 *
 * <p>Note what is <i>not</i> needed here: a migration. A migration exists to
 * carry a user's saved answer to a question that is still being asked. These
 * questions are not being asked any more — the content they governed is gone
 * from the jar — so there is nothing for the old value to mean, and the honest
 * outcome is the inert key.
 *
 * <p><b>Why 9 hand-written city checkboxes.</b> {@code @ConfigItem} is an
 * annotation on an interface method: the set of items is fixed at compile time
 * and cannot be generated from {@link City#values()}. What can be avoided is
 * writing the region ids twice — those live only in {@link City}, and each
 * constant names its getter here through {@code City.enabledIn}.
 *
 * <p><b>What is deliberately absent.</b>
 * <ul>
 *   <li><b>The object cap.</b> {@link RenderPolicy#MAX_ACTIVE_OBJECTS} is 80. It
 *       is not exposed because its job is to stop a future region file asking the
 *       client to build hundreds of models in one tick — a guard, not a
 *       preference. The margin is thinner than it looks: the densest
 *       neighbourhood in the shipped data holds 76 entities at the widest render
 *       distance, four slots spare.</li>
 *   <li><b>A master on/off.</b> That is the plugin's own toggle in the
 *       plugin list. A second one would only be a way for the two to disagree.</li>
 *   <li><b>A density <i>count</i>.</b> The dataset has no density field, so
 *       {@link CrowdDensity} thins proportionally instead of pretending to know
 *       how crowded a street was meant to be.</li>
 *   <li><b>The "this is fake" colour.</b> RuneLite supports {@code Color} config
 *       items, so exposing it would be cheap — and it is deliberately not exposed.
 *       Its whole job is to be a colour the game never uses for a real menu
 *       target, and a user-settable one can be set to the yellow real NPCs use.
 *       See {@link CitizenLabel}.</li>
 *   <li><b>A "mute everyone" switch.</b> {@link #overheadText()} already is one.
 *       Two global switches spelled differently would be the same setting twice,
 *       and no test could tell them apart. The second granularity is per citizen,
 *       through its own right-click menu — see {@link CitizenOverrides}.</li>
 *   <li><b>A remark-chance dial.</b> {@link CitizenChatter#REMARK_CHANCE_PERCENT}
 *       is fixed. The two things worth controlling are how often a citizen gets a
 *       chance and how many can be on screen at once; a third multiplier
 *       interacting with both would make neither predictable.</li>
 * </ul>
 */
@ConfigGroup(LivelyCitiesConfig.GROUP)
public interface LivelyCitiesConfig extends Config
{
	String GROUP = "livelycities";

	@ConfigSection(
		name = "Chatter",
		description = "What citizens say over their heads, how often, and how to make them stop.",
		position = 10
	)
	String chatterSection = "chatter";

	@ConfigSection(
		name = "Individuals",
		description = "Citizens you have hidden or muted with their own right-click menu.",
		position = 15
	)
	String individualsSection = "individuals";

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
			+ "so a street looks the same every time you walk down it. "
			+ "\"Crowded\" goes the other way and roughly doubles the crowd: every hand-placed "
			+ "citizen stays, and extra ones are derived from them — differently dressed, "
			+ "generically named, silent, and only ever on ground the game's own collision map "
			+ "says a person could stand on. They are always the same extra people, and hiding "
			+ "one does not hide the citizen it came from.",
		position = 2
	)
	default CrowdDensity crowdDensity()
	{
		return CrowdDensity.FULL;
	}

	/**
	 * The cameo checkbox's label and description, pulled out as constants so
	 * {@code CameoPlacementTest} can assert on the exact words without reflection.
	 *
	 * <p>Unannotated interface fields, exactly like {@link #GROUP} — RuneLite reads
	 * {@code @ConfigItem} off methods and {@code @ConfigSection} off annotated fields,
	 * so these are inert to the settings panel and are only here to be the single
	 * source of the string.
	 *
	 * <p><b>The description is load-bearing text, not a tooltip.</b> It has to tell a
	 * user — and a hub reviewer — what the feature adds, that it is player-shaped, and
	 * that being off is the deliberate default rather than an accident. Trimming it to
	 * a label is what the test prevents.
	 *
	 * <p><b>The costume list is part of the promise, not decoration.</b> It said "a
	 * barbarian" for a while after Peter stopped being one, because the guard checked
	 * the length and several phrases and never the costumes. It does now: every word in
	 * the list is tied to the actual roster in {@code CameoPlacementTest}. Change a
	 * cameo's body and this string has to change with it.
	 */
	String CAMEOS_NAME = "Friend cameos (off by default)";

	String CAMEOS_DESCRIPTION =
		"Adds six named, human-looking figures posing as a group on the north-west side of the "
			+ "Grand Exchange: caricatures of the plugin author's friends, dressed as a wizard, a "
			+ "sailor, a Shayzien soldier, a rogue, a butler and a White Knight. This is player-shaped "
			+ "content and it is deliberately switched off by default — everything else in this "
			+ "plugin is a townsperson, and a cluster of named humans at the busiest bank in the "
			+ "game is exactly what gets mistaken for real players. They are cosmetic and local "
			+ "like every other figure here, they obey the Grand Exchange checkbox as well as this "
			+ "one, Examine says outright that each is a likeness and not a player, and you can "
			+ "hide or mute any of them individually. Turn it on if you want them; nobody will "
			+ "ever see them by accident.";

	@ConfigItem(
		keyName = "cameos",
		name = CAMEOS_NAME,
		description = CAMEOS_DESCRIPTION,
		position = 3
	)
	default boolean cameos()
	{
		// False, and this default is not a preference — see the description above and
		// CameoPlacementTest. The predecessor plugin's own content rule was "no fake
		// players", and named human figures at a classic bank spot reproduce exactly
		// the confusion that got it disabled. A fresh install and a hub reviewer both
		// see none of them.
		return false;
	}

	// --- Chatter -------------------------------------------------------------
	//
	// These are here first, and they exist before the feature they control, for a
	// documented reason. Overhead-text spam was the predecessor's single loudest
	// complaint — 144 upvotes on "please add an option to shut them up" — its author
	// promised a mute toggle and never shipped one, and upstream issue #35 has been
	// open since release day. So this is not a settings page for a feature; the
	// feature is what is left of CitizenChatter once these are all satisfied.

	@ConfigItem(
		keyName = "overheadText",
		name = "Overhead chatter",
		description = "Whether citizens say anything at all over their heads. "
			+ "Unticking this is the hard off switch: nothing is drawn, nothing is "
			+ "rolled, and anything already on screen goes away on the click rather "
			+ "than when it would have expired.",
		position = 1,
		section = chatterSection
	)
	default boolean overheadText()
	{
		return true;
	}

	@ConfigItem(
		keyName = "remarkIntervalTicks",
		name = "Chance every",
		description = "How often each citizen gets a chance to say something, in game ticks. "
			+ "One game tick is 0.6 seconds, so the default of 100 is one minute. It is a "
			+ "chance, not a turn — roughly one in four — so at the default a citizen with "
			+ "something to say speaks about once every four minutes. Higher means quieter.",
		position = 2,
		section = chatterSection
	)
	@Range(min = CitizenChatter.MIN_ROLL_INTERVAL_TICKS, max = CitizenChatter.MAX_ROLL_INTERVAL_TICKS)
	@Units(Units.TICKS)
	default int remarkIntervalTicks()
	{
		return CitizenChatter.DEFAULT_ROLL_INTERVAL_TICKS;
	}

	@ConfigItem(
		keyName = "remarkDwellTicks",
		name = "Stays up for",
		description = "How long a remark stays on screen once it appears, in game ticks. "
			+ "One game tick is 0.6 seconds, so the default of 8 is just under five seconds "
			+ "and the maximum of 30 is eighteen. A remark is always taken down before that "
			+ "citizen's next chance to speak, so if you set this longer than \"Chance every\" "
			+ "above it is shortened to one tick less — otherwise the text would never clear.",
		position = 3,
		section = chatterSection
	)
	@Range(min = CitizenChatter.MIN_DWELL_TICKS, max = CitizenChatter.MAX_DWELL_TICKS)
	@Units(Units.TICKS)
	default int remarkDwellTicks()
	{
		return CitizenChatter.DEFAULT_DWELL_TICKS;
	}

	@ConfigItem(
		keyName = "chatterRadius",
		name = "Chatter distance",
		description = "How close a citizen has to be, in tiles, before it will start talking. "
			+ "Deliberately tighter than the render distance: a citizen twenty tiles away "
			+ "is scenery.",
		position = 4,
		section = chatterSection
	)
	@Range(min = CitizenChatter.MIN_RADIUS_TILES, max = RenderPolicy.MAX_CULL_RADIUS)
	default int chatterRadius()
	{
		return CitizenChatter.DEFAULT_RADIUS_TILES;
	}

	@ConfigItem(
		keyName = "maxConcurrentRemarks",
		name = "At most on screen",
		description = "How many remarks may be on screen at once. This is the setting that stops "
			+ "a crowd becoming a wall of text — Varrock square holds forty citizens, and at "
			+ "the tightest cadence the dials above allow, about nine of them would be talking "
			+ "at any moment without a cap.",
		position = 5,
		section = chatterSection
	)
	@Range(min = CitizenChatter.MIN_MAX_CONCURRENT, max = CitizenChatter.MAX_MAX_CONCURRENT)
	default int maxConcurrentRemarks()
	{
		return CitizenChatter.DEFAULT_MAX_CONCURRENT;
	}

	// --- Individual citizens -------------------------------------------------
	//
	// Two hidden strings and two buttons. RuneLite has no dynamic checkbox list —
	// @ConfigItem is an annotation on an interface method, fixed at compile time —
	// so a per-citizen opt-out cannot be a checkbox per citizen, let alone one per
	// uuid in a data file. The established shape is a hidden string that a RUNELITE
	// menu entry appends to, plus a visible control for the way back. See
	// CitizenOverrides.
	//
	// The two "clear the list" items are checkboxes that untick themselves: 1.12.36
	// has no Button config type (verified — there is no net.runelite.client.config
	// .Button class in the client jar), so the plugin answers the ConfigChanged,
	// empties the list, and unsets the key again. Ticking it is therefore a press
	// rather than a state.

	@ConfigItem(
		keyName = CitizenOverrides.UNHIDE_ALL_KEY,
		name = "Unhide all citizens",
		description = "Tick to bring back every citizen you have hidden with its right-click "
			+ "\"Hide\" option. It unticks itself.",
		position = 1,
		section = individualsSection
	)
	default boolean unhideAll()
	{
		return false;
	}

	@ConfigItem(
		keyName = CitizenOverrides.UNMUTE_ALL_KEY,
		name = "Unmute all citizens",
		description = "Tick to let every citizen you have muted with its right-click \"Mute\" "
			+ "option talk again. It unticks itself.",
		position = 2,
		section = individualsSection
	)
	default boolean unmuteAll()
	{
		return false;
	}

	@ConfigItem(
		keyName = CitizenOverrides.HIDDEN_KEY,
		name = "Hidden citizens",
		description = "The uuids of citizens hidden from their own right-click menu. "
			+ "Not shown: there is no dynamic list control to show it in.",
		position = 3,
		section = individualsSection,
		hidden = true
	)
	default String hiddenCitizens()
	{
		return "";
	}

	@ConfigItem(
		keyName = CitizenOverrides.MUTED_KEY,
		name = "Muted citizens",
		description = "The uuids of citizens muted from their own right-click menu.",
		position = 4,
		section = individualsSection,
		hidden = true
	)
	default String mutedCitizens()
	{
		return "";
	}

	// --- One checkbox per city. Order matches City's declaration order. -------
	// position starts at 100 so a later non-city item can be slotted in above
	// without renumbering 9 annotations.

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

	@ConfigItem(keyName = "cityCatherby", name = "Catherby", description = "Populate Catherby",
		position = 102, section = citiesSection)
	default boolean cityCatherby()
	{
		return true;
	}

	@ConfigItem(keyName = "cityDraynor", name = "Draynor", description = "Populate Draynor",
		position = 103, section = citiesSection)
	default boolean cityDraynor()
	{
		return true;
	}

	@ConfigItem(keyName = "cityEdgeville", name = "Edgeville", description = "Populate Edgeville",
		position = 104, section = citiesSection)
	default boolean cityEdgeville()
	{
		return true;
	}

	@ConfigItem(keyName = "cityFalador", name = "Falador", description = "Populate Falador",
		position = 105, section = citiesSection)
	default boolean cityFalador()
	{
		return true;
	}

	@ConfigItem(keyName = "cityGrandExchange", name = "Grand Exchange", description = "Populate the Grand Exchange",
		position = 106, section = citiesSection)
	default boolean cityGrandExchange()
	{
		return true;
	}

	@ConfigItem(keyName = "cityLumbridge", name = "Lumbridge", description = "Populate Lumbridge",
		position = 107, section = citiesSection)
	default boolean cityLumbridge()
	{
		return true;
	}

	@ConfigItem(keyName = "cityVarrock", name = "Varrock",
		description = "Populate Varrock, including the road outside the east gate",
		position = 108, section = citiesSection)
	default boolean cityVarrock()
	{
		return true;
	}
}
