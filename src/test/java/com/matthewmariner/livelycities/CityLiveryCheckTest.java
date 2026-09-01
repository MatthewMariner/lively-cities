package com.matthewmariner.livelycities;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Checks {@code docs/CITY-LIVERY-CHECK.md} against the 127 records it describes.
 *
 * <p><b>Why a document needs a test.</b> This file is the only artefact of the
 * 2026-09-01 livery pass a human can act on: none of its 127 tiles has been stood
 * on, and the walk is the thing that will settle them. A checklist whose tile is
 * one square off the record's is worse than no checklist, because somebody walks
 * to the wrong square, sees nothing wrong, and ticks the box. The same argument
 * {@code CityTopUpCheckTest} makes about the 33, and this is deliberately its
 * sibling rather than an extension of it — the two passes have different rules,
 * different markers and different documents, and a single test over both would
 * have to be written in whichever way was weaker.
 *
 * <p>{@code docs/CITY-LIVERY-CHECK.md} is declared an input of the {@code test}
 * task in {@code build.gradle}. Without that declaration Gradle would consider the
 * task up to date after a documentation-only edit and this class would never run.
 */
public class CityLiveryCheckTest
{
	private static final String DOC = "docs/CITY-LIVERY-CHECK.md";

	/** The disclosure that publishes the same colours in prose; {@code AuthoredRecordsTest} owns it. */
	private static final String NOTICE = "NOTICE";

	/** The uuid prefix the livery pass's records carry; {@link AuthoredRecordsTest} owns it. */
	private static final String MARKER = "add2";

	private static final int FIGURES = 127;
	private static final int NEW_BOXES = 12;

	/** Ground Markers' own colour strings, as the block spells them. */
	private static final String YELLOW = "#FFFFFF00";
	private static final String CYAN = "#FF00FFFF";

	private static final Pattern ENTRY = Pattern.compile(
		"^- \\[ \\] \\*\\*(.+?)\\*\\* — (.*?)(?=^- \\[ \\]|^#|^---)",
		Pattern.MULTILINE | Pattern.DOTALL);

	private static final Pattern TILE = Pattern.compile("^`(\\d+), (\\d+), (\\d+)`");
	private static final Pattern FACING = Pattern.compile("facing (north|south|east|west)");
	private static final Pattern BOX = Pattern.compile(
		"\\*\\*wanders\\*\\* `(\\d+),(\\d+) \\.\\. (\\d+),(\\d+)`");
	private static final Pattern EXAMINE = Pattern.compile("Examine: \"(.*?)\"", Pattern.DOTALL);
	private static final Pattern KIT = Pattern.compile("Kit: \"(.+?)\" \\((\\d+)\\) · `(\\w+)`");
	private static final Pattern MARKERS = Pattern.compile(
		"```json\\n(\\[.*?\\])\\n```", Pattern.DOTALL);

	/**
	 * The livery line, in both of the forms the document writes it.
	 *
	 * <p>Group 1 is the city and 2 the cut for a liveried figure; both are absent for an
	 * Edgeville figure, whose line says {@code none} instead. Groups 3 and 4 are the
	 * torso and legs words, group 5 the boots word where there is one.
	 */
	private static final Pattern LIVERY = Pattern.compile(
		"Livery: (?:(\\w[\\w ]*?) cut (\\d) — torso `(\\d+)`, legs `(\\d+)`"
			+ "|none — donor's own colours, darkened)(?:; |, )?(?:boots `(\\d+)`)?");

	private static final Pattern PLACED_NEAR = Pattern.compile(
		"Placed near: ([^,\\n(]+?)( \\(unwalked\\))?, (\\d+) tiles?");

	/** Group 1 is the row number, 2 the figure, 3 its city, 4 the distance, 5 the anchor. */
	private static final Pattern REACH_ROW = Pattern.compile(
		"^\\| (\\d+) \\| \\*\\*(\\w+)\\*\\* \\| ([\\w' ]+?) \\| (\\d+) \\| ([\\w' ]+) \\|$",
		Pattern.MULTILINE);

	/**
	 * A row of the document's own livery table: city, torso, legs, trim.
	 *
	 * <p>This is the palette section of the document — the eight measured colours per
	 * city, written once at the top — and it is deliberately <b>not</b> the per-record
	 * {@code Livery:} lines. See
	 * {@link #theLiveryIsOneSetPerCityAndNoTwoCitiesShareOne} for why the difference is
	 * the whole point.
	 */
	private static final Pattern PALETTE_ROW = Pattern.compile(
		"^\\| ([\\w' ]+?) \\| `(\\d+)` [\\w/]+ \\| `(\\d+)` [\\w/]+ \\| `(\\d+)` [\\w/]+ ?†? \\|$",
		Pattern.MULTILINE);

	/**
	 * A {@code Rows N to M …} sentence from the prose above the reach table, to the end
	 * of its paragraph. Group 1 and 2 are the row numbers, 3 the rest of the paragraph.
	 */
	private static final Pattern ROWS_CLAIM = Pattern.compile(
		"^Rows (\\d+) to (\\d+)\\b(.*?)(?=\\n\\n)", Pattern.MULTILINE | Pattern.DOTALL);

	/** A bold run in that prose, which may hold several comma-separated names. */
	private static final Pattern BOLD_NAMES = Pattern.compile("\\*\\*([\\w'][\\w' ,]*?)\\*\\*");

	/** The number words those sentences count in. */
	private static final TreeMap<String, Integer> NUMBERS = numbers();

	/** The dataset's orientation units, as the document spells them in words. */
	private static final TreeMap<String, Integer> ORIENTATIONS = orientations();

	/** The kit bases this pass repaints. {@link BodySlots} owns the measurement. */
	private static final int TORSO_BASE = BodySlots.TORSO_BASE;
	private static final int LEGS_BASE = BodySlots.LEGS_BASE;
	private static final int BOOT_BASE = BodySlots.BOOT_BASE;

	/**
	 * The one colour the document's livery table publishes that no record wears.
	 *
	 * <p>Varrock's eight figures use three cuts, and a city's trim only ever lands on the
	 * fourth, so {@code 10050} is measured, published and unworn. The document carries a
	 * footnote saying so; this constant is the other half of that sentence, and
	 * {@link #everyPublishedLiveryColourIsWornExceptTheOneNamedHere} is what stops the
	 * list of exceptions growing without anybody deciding to grow it.
	 */
	private static final int VARROCKS_UNWORN_TRIM = 10050;

	/** How far from a proven tile earns a row in the document's reach table. */
	private static final int PROVEN_REACH_THRESHOLD = 9;

	@Test
	public void theChecklistNamesEveryLiveriedFigureExactlyOnce() throws IOException
	{
		TreeSet<String> listed = new TreeSet<>();
		for (Entry entry : entries())
		{
			assertTrue("the checklist lists " + entry.name + " twice", listed.add(entry.name));
		}

		assertEquals("the checklist and the liveried records have to be the same set",
			ours().keySet(), listed);
		assertEquals("and there have to be " + FIGURES + " of them", FIGURES, listed.size());
	}

	/**
	 * Every entry's tile, facing and wander box are the record's own.
	 *
	 * <p>The facing is checked as well as the tile because it is the half a walker
	 * cannot verify from the marker: a yellow square says where to stand, and only this
	 * line says which way the figure should be looking when you get there.
	 */
	@Test
	public void everyFigureStandsWhereItsEntrySaysAndFacesTheWayItSays() throws IOException
	{
		TreeMap<String, EntityDefinition> ours = ours();
		int checked = 0;
		int boxes = 0;

		for (Entry entry : entries())
		{
			EntityDefinition record = ours.get(entry.name);
			assertNotNull(entry.name + " is in the checklist and not in the data", record);
			checked++;

			Matcher tile = TILE.matcher(entry.body);
			assertTrue(entry.name + " has no tile in its entry", tile.find());
			WorldPoint at = record.getWorldLocation();
			assertEquals(entry.name + " x", at.getX(), Integer.parseInt(tile.group(1)));
			assertEquals(entry.name + " y", at.getY(), Integer.parseInt(tile.group(2)));
			assertEquals(entry.name + " plane", at.getPlane(), Integer.parseInt(tile.group(3)));

			Matcher facing = FACING.matcher(entry.body);
			assertTrue(entry.name + " does not say which way it faces", facing.find());
			assertEquals(entry.name + " facing",
				(int) ORIENTATIONS.get(facing.group(1)), record.getOrientation());

			Matcher box = BOX.matcher(entry.body);
			EntityDefinition.WanderBox authored = record.getWanderBox();
			if (box.find())
			{
				boxes++;
				assertNotNull(entry.name + " is listed as a wanderer and carries no box",
					authored);
				assertEquals(entry.name + " box minX", authored.getMinX(),
					Integer.parseInt(box.group(1)));
				assertEquals(entry.name + " box minY", authored.getMinY(),
					Integer.parseInt(box.group(2)));
				assertEquals(entry.name + " box maxX", authored.getMaxX(),
					Integer.parseInt(box.group(3)));
				assertEquals(entry.name + " box maxY", authored.getMaxY(),
					Integer.parseInt(box.group(4)));
			}
			else
			{
				assertEquals(entry.name + " carries a wander box the checklist does not "
					+ "mention, so its rectangle would never be walked", null, authored);
			}
		}

		assertEquals("every figure has to have been checked", FIGURES, checked);
		assertEquals("and the new wander boxes have to be the ones listed", NEW_BOXES, boxes);
	}

	/**
	 * Every entry quotes its record's examine text exactly.
	 *
	 * <p>Not tidiness. The examine line is how a walker knows the figure in front of
	 * them is the one the entry is about — two liveried townsfolk in one city wear the
	 * same colours by design, so the text is the only thing in the entry that tells
	 * them apart on screen.
	 */
	@Test
	public void everyFiguresExamineTextIsQuotedExactly() throws IOException
	{
		TreeMap<String, EntityDefinition> ours = ours();
		int checked = 0;

		for (Entry entry : entries())
		{
			EntityDefinition record = ours.get(entry.name);
			assertNotNull(entry.name, record);

			Matcher examine = EXAMINE.matcher(entry.body);
			assertTrue(entry.name + " quotes no examine text", examine.find());
			assertEquals(entry.name + " examine text",
				record.getExamineText(), examine.group(1).replaceAll("\\s+", " "));
			checked++;
		}

		assertEquals("every figure has to have been checked", FIGURES, checked);
	}

	/**
	 * Every entry names the kit it is wearing, and it is a kit that ships.
	 *
	 * <p>The donor's name and region are what let a walker say "this should look like
	 * <i>that</i> figure in Varrock, in Falador's colours" — which is the only offline
	 * description of what a liveried record ought to look like that exists.
	 */
	@Test
	public void everyEntryNamesADonorThatShipsAndAPoseTheRecordActuallyPlays()
		throws IOException
	{
		TreeMap<String, EntityDefinition> ours = ours();
		TreeMap<String, Integer> donors = new TreeMap<>();
		for (EntityDefinition entity : shipped())
		{
			if (entity.getName() != null && !entity.getUuid().toString().startsWith(MARKER))
			{
				donors.put(entity.getName(), entity.getRegionId());
			}
		}

		int checked = 0;
		for (Entry entry : entries())
		{
			Matcher kit = KIT.matcher(entry.body);
			assertTrue(entry.name + " does not name its kit", kit.find());

			Integer region = donors.get(kit.group(1));
			assertNotNull(entry.name + " names a donor that does not ship: " + kit.group(1),
				region);
			assertEquals(entry.name + "'s donor region", (int) region,
				Integer.parseInt(kit.group(2)));
			assertEquals(entry.name + "'s pose",
				ours.get(entry.name).getIdleAnimation().name(), kit.group(3));
			checked++;
		}

		assertEquals("every figure has to have been checked", FIGURES, checked);
	}

	/**
	 * Every ground marker lands on the tile it is labelled with.
	 *
	 * <p>The block is one long line of JSON a human pastes into Ground Markers without
	 * reading, so it is the part of this document most able to be quietly wrong. A
	 * marker is a region id plus an offset <i>within</i> the region, so a figure whose
	 * region and coordinates disagree produces a square 64 tiles from the citizen and
	 * nothing about the label would say so.
	 */
	@Test
	public void everyGroundMarkerLandsOnTheTileItIsLabelledWith() throws IOException
	{
		Matcher block = MARKERS.matcher(read());
		assertTrue("the checklist has no Ground Markers block, so nobody can import it",
			block.find());

		JsonArray markers = TestGson.injected().fromJson(block.group(1), JsonArray.class);
		TreeMap<String, EntityDefinition> ours = ours();

		TreeMap<String, WorldPoint> figures = new TreeMap<>();
		TreeMap<String, WorldPoint> corners = new TreeMap<>();

		for (JsonElement element : markers)
		{
			JsonObject marker = element.getAsJsonObject();
			String label = marker.get("label").getAsString();
			int regionId = marker.get("regionId").getAsInt();
			int x = ((regionId >> 8) << 6) | marker.get("regionX").getAsInt();
			int y = ((regionId & 0xFF) << 6) | marker.get("regionY").getAsInt();
			WorldPoint at = new WorldPoint(x, y, marker.get("z").getAsInt());
			String colour = marker.get("color").getAsString();

			if (YELLOW.equals(colour))
			{
				assertEquals("two yellow markers labelled " + label, null,
					figures.put(label, at));
			}
			else
			{
				assertEquals("expected only yellow and cyan markers", CYAN, colour);
				assertEquals("two cyan markers labelled " + label, null,
					corners.put(label, at));
			}
		}

		assertEquals("one yellow marker per figure", FIGURES, figures.size());
		assertEquals("two cyan corners per new wander box", NEW_BOXES * 2, corners.size());
		assertEquals("and " + (FIGURES + NEW_BOXES * 2) + " markers in total, which is "
			+ "what the block's caption promises", FIGURES + NEW_BOXES * 2, markers.size());

		for (String name : ours.keySet())
		{
			EntityDefinition record = ours.get(name);
			assertEquals("the yellow marker for " + name, record.getWorldLocation(),
				figures.get(name));

			EntityDefinition.WanderBox box = record.getWanderBox();
			if (box == null)
			{
				continue;
			}

			assertEquals("the south-west corner marker for " + name,
				new WorldPoint(box.getMinX(), box.getMinY(), box.getPlane()),
				corners.get(name + " box SW"));
			assertEquals("the north-east corner marker for " + name,
				new WorldPoint(box.getMaxX(), box.getMaxY(), box.getPlane()),
				corners.get(name + " box NE"));
		}
	}

	/**
	 * <b>Every entry's livery is the colours the record is actually wearing.</b>
	 *
	 * <h2>Why this is the assertion this class was missing</h2>
	 *
	 * <p>A livery is the whole point of the 2026-09-01 pass — it is the one thing that
	 * tells a Falador townsperson from a Lumbridge one — and until 2026-08-30 <b>not one
	 * of the eight torso values appeared anywhere in {@code src/test}</b>. The word
	 * "torso" did not appear in this file. Everything else on an entry was pinned: the
	 * tile, the facing, the wander box, the examine text, the donor kit, the pose, the
	 * ground marker. The colours were not, and colours are the feature.
	 *
	 * <p>That is not a theoretical gap. Three separate mutations of the shipped data —
	 * repainting every Varrock torso Lumbridge blue, swapping Falador's and Lumbridge's
	 * entire liveries, and flattening all 127 records to one grey, which deletes the
	 * feature outright — each passed the whole suite green. This test is what makes each
	 * of them red.
	 *
	 * <h2>What it reads</h2>
	 *
	 * <p>The {@code Livery:} line, in both forms the document writes it: {@code <city>
	 * cut <n> — torso `X`, legs `Y`} for the 109 liveried figures and {@code none —
	 * donor's own colours, darkened} for Edgeville's 18, either of them optionally
	 * followed by {@code boots `Z`}. Each is checked against the record's own recolour
	 * slots — the {@code replace} value on the slot whose {@code find} is
	 * {@link BodySlots#TORSO_BASE}, {@link BodySlots#LEGS_BASE} or
	 * {@link BodySlots#BOOT_BASE} — because a {@code find} slot is the author's
	 * statement of which body part they were aiming at, which is the same property
	 * {@code BodySlotLintTest} leans on.
	 *
	 * <p><b>The boots half matters more than its eight records suggest.</b> The document
	 * used to describe four cuts varying "which accent lands on the boots" and then
	 * print no boot colour anywhere, so a walker had nothing to check a boot against.
	 * Only 8 of the 127 carry a boot slot at all — a record repaints one only where its
	 * donor kit already aimed at {@code 4626} — and the count is asserted here so that
	 * "the cuts do not vary the boots" cannot quietly stop being true.
	 */
	@Test
	public void everyEntrysLiveryIsTheColoursTheRecordActuallyWears() throws IOException
	{
		TreeMap<String, EntityDefinition> ours = ours();
		int liveried = 0;
		int unliveried = 0;
		int boots = 0;

		for (Entry entry : entries())
		{
			EntityDefinition record = ours.get(entry.name);
			assertNotNull(entry.name, record);

			Matcher livery = LIVERY.matcher(entry.body);
			assertTrue(entry.name + " does not say what it is wearing, which is the one "
				+ "thing the walk is looking at", livery.find());

			City city = City.of(record.getCityRegionId());
			Integer torso = painted(record, TORSO_BASE);
			Integer legs = painted(record, LEGS_BASE);
			Integer boot = painted(record, BOOT_BASE);

			if (livery.group(1) == null)
			{
				unliveried++;
				assertEquals(entry.name + " says it has no livery and is not in the one "
					+ "city that has none", City.EDGEVILLE, city);
			}
			else
			{
				liveried++;
				assertNotNull(entry.name + " is in no city and claims a livery", city);
				assertEquals(entry.name + "'s livery names the wrong city",
					city.getLabel(), livery.group(1));
				assertEquals(entry.name + " torso", torso,
					Integer.valueOf(livery.group(3)));
				assertEquals(entry.name + " legs", legs,
					Integer.valueOf(livery.group(4)));
			}

			// Both forms carry the boots clause, and only where the record has the slot.
			if (boot == null)
			{
				assertEquals(entry.name + " quotes a boot colour it is not wearing",
					null, livery.group(5));
			}
			else
			{
				boots++;
				assertNotNull(entry.name + " repaints its boots and the checklist does "
					+ "not say so, so nobody can check them", livery.group(5));
				assertEquals(entry.name + " boots", boot, Integer.valueOf(livery.group(5)));
			}

			// Torso and legs are the pass's rule and are never absent.
			assertNotNull(entry.name + " has no torso slot", torso);
			assertNotNull(entry.name + " has no legs slot", legs);
		}

		assertEquals("liveried figures", 109, liveried);
		assertEquals("Edgeville's figures, which have no livery by design", 18, unliveried);
		assertEquals("figures whose donor kit aimed at the boots base, so the pass had a "
			+ "boot slot to repaint", 8, boots);
	}

	/**
	 * <b>A livery belongs to one city, and no two cities share one.</b>
	 *
	 * <p>The test above proves each entry quotes its own record. It does not stop the
	 * eight liveries drifting into each other, and that is the failure mode the whole
	 * feature has: two cities in the same colour is worse than no livery at all, because
	 * the reader believes the colour means something. So this asserts three things about
	 * the set rather than about any record:
	 *
	 * <ul>
	 *   <li><b>Every liveried record's torso and legs are its own city's published
	 *       colours</b>, measured against the document's <i>livery table</i> rather than
	 *       against the entries. A Varrock figure may wear any of Varrock's cuts and
	 *       nothing else. This is the assertion the "swap Falador and Lumbridge"
	 *       mutation dies on twice over.</li>
	 *   <li><b>No colour is in two cities' tables.</b> Stated as sets so it holds however
	 *       many cuts a city grows.</li>
	 *   <li><b>Each cut number names one outfit</b>, and the cut count per city, because
	 *       a livery whose four cuts collapsed to one would still pass both rules above
	 *       and would be twenty copies of one outfit, which is the thing the cuts exist
	 *       to prevent — and a cut number that drifts onto a different outfit makes the
	 *       label a decoration.</li>
	 * </ul>
	 *
	 * <h2>Why the palette comes from the table and not from the entries</h2>
	 *
	 * <p>Until 2026-08-31 the per-record half of this test built each city's palette by
	 * <b>collecting the colours off the document's own {@code Livery:} lines</b> and then
	 * checking each record's colour for membership in that set. Every record contributes
	 * its own line to the set it is later checked against, so the per-record half could
	 * not fail on a record whose entry had been edited to agree with it. Repainting
	 * "Aldric" to {@code torso 20000, legs 20001} — values in no city's table — in both
	 * the JSON <i>and</i> its {@code Livery:} line left the whole suite green, and the
	 * comment sitting next to that loop claimed the opposite: "a document that agreed with
	 * itself would still fail". It would not. That is the realistic failure, too: a
	 * careless fix for the {@code expected:<10034> but was:<20000>} that
	 * {@link #everyEntrysLiveryIsTheColoursTheRecordActuallyWears} raises is to edit the
	 * document until it agrees, at which point a citizen in a colour no city owns ships.
	 *
	 * <p>So the palette is now read from the eight-row livery table at the top of the
	 * document — a section no entry writes to — and the rule is the one that holds over
	 * all 109 liveried records with no exceptions:
	 *
	 * <ul>
	 *   <li>a liveried <b>torso</b> shares hue and saturation with its city's published
	 *       torso;</li>
	 *   <li>a liveried <b>legs</b> value either shares hue and saturation with the
	 *       published legs, or is the published trim exactly.</li>
	 * </ul>
	 *
	 * <p>Only lightness moves, which is precisely what a "cut" is, and cut 3 is the case
	 * the second clause exists for. Editing an entry cannot satisfy any of it.
	 *
	 * <p><b>The trim is the exception to the overlap rule and it is deliberate.</b> Three
	 * cities answer their cut 3 with the same white {@code 99}, so the overlap check
	 * forgives a colour two cities both publish <i>as their trim</i> and nothing else:
	 * Ardougne, Al Kharid and Lumbridge genuinely do share a trim, and the colour that
	 * tells them apart is the other three-quarters of the outfit. The exemption is derived
	 * from the table rather than written as the literal {@code 99}, so two cities
	 * colliding on that value for any other reason still fails.
	 */
	@Test
	public void theLiveryIsOneSetPerCityAndNoTwoCitiesShareOne() throws IOException
	{
		TreeMap<String, TreeSet<Integer>> byCity = new TreeMap<>();
		TreeMap<String, TreeSet<String>> cutsByCity = new TreeMap<>();
		TreeMap<String, TreeSet<String>> outfitsByCut = new TreeMap<>();
		TreeMap<String, TreeSet<String>> cutsByOutfit = new TreeMap<>();

		for (Entry entry : entries())
		{
			Matcher livery = LIVERY.matcher(entry.body);
			assertTrue(entry.name, livery.find());
			if (livery.group(1) == null)
			{
				continue;
			}

			String city = livery.group(1);
			byCity.computeIfAbsent(city, k -> new TreeSet<>())
				.addAll(Arrays.asList(
					Integer.valueOf(livery.group(3)), Integer.valueOf(livery.group(4))));
			cutsByCity.computeIfAbsent(city, k -> new TreeSet<>()).add(livery.group(2));

			String outfit = "torso " + livery.group(3) + ", legs " + livery.group(4);
			outfitsByCut.computeIfAbsent(city + " cut " + livery.group(2),
				k -> new TreeSet<>()).add(outfit);
			cutsByOutfit.computeIfAbsent(city + " " + outfit, k -> new TreeSet<>())
				.add(livery.group(2));
		}

		assertEquals("cities with a livery", 8, byCity.size());

		// Every record wears its own city's PUBLISHED colours, read out of the livery
		// table rather than off the entries, so a document edited into agreeing with a
		// repainted record still fails. See the javadoc.
		TreeMap<String, Palette> published = published();
		TreeMap<String, EntityDefinition> ours = ours();
		int measured = 0;
		for (String name : ours.keySet())
		{
			EntityDefinition record = ours.get(name);
			City city = City.of(record.getCityRegionId());
			if (city == City.EDGEVILLE)
			{
				continue;
			}
			measured++;

			Palette table = published.get(city.getLabel());
			assertNotNull(name + " is in " + city + " and that city has no row in the "
				+ "livery table", table);

			int torso = painted(record, TORSO_BASE);
			assertTrue(name + " wears " + describe(torso) + " on its torso, and " + city
					+ "'s published torso is " + describe(table.torso) + " — a cut may "
					+ "move the lightness and nothing else",
				sameShade(torso, table.torso));

			int legs = painted(record, LEGS_BASE);
			assertTrue(name + " wears " + describe(legs) + " on its legs, and " + city
					+ " publishes legs " + describe(table.legs) + " and a trim of "
					+ describe(table.trim) + " — a cut-3 figure wears the trim exactly, "
					+ "and every other cut moves the lightness of the legs and nothing else",
				sameShade(legs, table.legs) || legs == table.trim);
		}
		assertEquals("liveried records measured against the published table", 109, measured);

		// And no two cities' sets overlap. The trim white 99 is not in any of these
		// sets on its own — it reaches them only as a cut-3 leg colour — so this is
		// the assertion that would fail if a whole livery were copied across.
		for (String a : byCity.keySet())
		{
			for (String b : byCity.keySet())
			{
				if (a.compareTo(b) >= 0)
				{
					continue;
				}
				TreeSet<Integer> shared = new TreeSet<>(byCity.get(a));
				shared.retainAll(byCity.get(b));
				// Two cities may share the one value they both publish as their trim —
				// 99, for Ardougne, Al Kharid and Lumbridge — and nothing else. Derived
				// from the table rather than hardcoded, so this stays a narrow exemption
				// for a known design decision instead of a blanket amnesty for 99.
				if (published.get(a).trim == published.get(b).trim)
				{
					shared.remove(published.get(a).trim);
				}
				assertTrue(a + " and " + b + " wear the same colours: " + shared,
					shared.isEmpty());
			}
		}

		// A cut number is only worth writing down if it names one outfit. Nothing
		// checked that: the count below is a cardinality, so relabelling one Varrock
		// entry "cut 0" as "cut 2" — colours left truthful — used to pass.
		for (String cut : outfitsByCut.keySet())
		{
			assertEquals(cut + " is written against more than one outfit, so the cut "
				+ "number has stopped naming a colour", 1, outfitsByCut.get(cut).size());
		}
		for (String outfit : cutsByOutfit.keySet())
		{
			assertEquals(outfit + " is written under more than one cut number",
				1, cutsByOutfit.get(outfit).size());
		}
		assertEquals("city-and-cut pairs, each of which has to name exactly one outfit",
			31, outfitsByCut.size());

		TreeMap<String, Integer> cutCounts = new TreeMap<>();
		for (String city : cutsByCity.keySet())
		{
			cutCounts.put(city, cutsByCity.get(city).size());
		}
		TreeMap<String, Integer> expected = new TreeMap<>();
		expected.put("Al Kharid", 4);
		expected.put("Ardougne", 4);
		expected.put("Catherby", 4);
		expected.put("Draynor", 4);
		expected.put("Falador", 4);
		expected.put("Grand Exchange", 4);
		expected.put("Lumbridge", 4);
		// Varrock's eight figures are three cuts. Its trim is measured and unworn —
		// see VARROCKS_UNWORN_TRIM.
		expected.put("Varrock", 3);
		assertEquals("cuts per city", expected, cutCounts);
	}

	/**
	 * <b>No two of the 127 are the same figure.</b>
	 *
	 * <p>The pass's own sentence is that these townsfolk "are told apart from each other
	 * by kit, pose, palette rotation, name and facing". Nothing checked it, and a
	 * mutation that made two Edgeville records exact visual twins — same kit, same pose,
	 * same palette <i>and</i> same facing, five tiles apart in one region — passed the
	 * whole suite. The bigger clone mutations only died on slot-count assertions
	 * elsewhere, which is luck rather than a rule.
	 *
	 * <p>The rule is the weakest one that catches it: <b>no two liveried records share a
	 * kit, a pose and a livery.</b> Facing is deliberately left out, so two figures in
	 * the same outfit looking different ways still fail — a walker cannot tell those
	 * apart either once one of them turns.
	 *
	 * <p>The margins are pinned too, because "no exact duplicate" is a low bar and the
	 * data clears it by a lot: 26 distinct donor kits, no city reusing one more than
	 * twice, and no two records anywhere in the pass sharing even a city, a kit and a
	 * pose. {@code NOTICE} item 12 claims the first two of those in prose and nothing
	 * was checking them.
	 */
	@Test
	public void noTwoLiveriedFiguresAreTheSameFigure()
	{
		TreeMap<String, String> byLook = new TreeMap<>();
		TreeMap<String, String> byCityKitPose = new TreeMap<>();
		TreeSet<String> kits = new TreeSet<>();
		TreeMap<String, Integer> perCityKitUse = new TreeMap<>();

		for (EntityDefinition record : ours().values())
		{
			String kit = Arrays.toString(record.getModelIds());
			String look = kit + " " + record.getIdleAnimation()
				+ " " + painted(record, TORSO_BASE)
				+ "/" + painted(record, LEGS_BASE)
				+ "/" + painted(record, BOOT_BASE);
			String clash = byLook.put(look, record.label());
			assertEquals(record.label() + " is visually identical to " + clash
				+ " — same kit, same pose, same livery", null, clash);

			City city = City.of(record.getCityRegionId());
			String cityKitPose = city + " " + kit + " " + record.getIdleAnimation();
			String poseClash = byCityKitPose.put(cityKitPose, record.label());
			assertEquals(record.label() + " and " + poseClash + " are the same kit in the "
				+ "same pose in one city", null, poseClash);

			kits.add(kit);
			perCityKitUse.merge(city + " " + kit, 1, Integer::sum);
		}

		assertEquals("distinct donor kits, which NOTICE item 12 states as twenty-six",
			26, kits.size());

		int worst = 0;
		for (int uses : perCityKitUse.values())
		{
			worst = Math.max(worst, uses);
		}
		assertEquals("the most times one city reuses one kit, which NOTICE item 12 states "
			+ "as no more than twice", 2, worst);
	}

	/**
	 * Every colour the document's livery table publishes is worn by somebody, except the
	 * one it names as unworn.
	 *
	 * <p>A table of measured colours is a claim that these are the colours the plugin
	 * shows. {@code 10050} is not: Varrock has three cuts, a trim only lands on the
	 * fourth, and so Varrock's trim has been published in two documents and worn by
	 * nobody since the pass shipped. It stays in the table because it is the
	 * measurement — deleting it loses the fact — and the footnote next to it says it is
	 * unworn. This test is the half of that sentence that cannot go stale: a second
	 * unworn colour fails here, and so does {@code 10050} quietly becoming worn.
	 */
	@Test
	public void everyPublishedLiveryColourIsWornExceptTheOneNamedHere() throws IOException
	{
		TreeSet<Integer> published = new TreeSet<>();
		for (Palette palette : published().values())
		{
			published.add(palette.torso);
			published.add(palette.legs);
			published.add(palette.trim);
		}

		TreeSet<Integer> worn = new TreeSet<>();
		for (EntityDefinition record : ours().values())
		{
			for (int base : new int[]{TORSO_BASE, LEGS_BASE, BOOT_BASE})
			{
				Integer colour = painted(record, base);
				if (colour != null)
				{
					worn.add(colour);
				}
			}
		}

		TreeSet<Integer> unworn = new TreeSet<>(published);
		unworn.removeAll(worn);
		assertEquals("colours the livery table publishes that nobody wears",
			new TreeSet<>(Collections.singletonList(VARROCKS_UNWORN_TRIM)), unworn);
		assertTrue("and the table has to say so where a reader will see it",
			read().contains("Varrock's trim is measured and is worn by nobody"));
	}

	/**
	 * <b>Falador stays white-first, Lumbridge stays blue-first, and the four records that
	 * are neither are exactly the four.</b>
	 *
	 * <p>{@code NOTICE} item 12 and this document both used to say the split was
	 * "enforced by construction". It is not. Cut 3 promotes a city's trim to the whole
	 * legs slot, so Falador's cut 3 is a white torso over royal-blue legs and Lumbridge's
	 * is a blue torso over white legs — four records, mirror images of each other, 50/50
	 * rather than dominant-plus-accent. The claim was corrected rather than the data,
	 * because cut 3 does the same thing in all eight liveried cities and each of the four
	 * still wears its own city's colour on the torso; but a false safety argument with
	 * nothing checking it is how the data gets to drift underneath it.
	 *
	 * <p>So what is pinned is what is true: <b>the torso rule holds in every cut</b>, the
	 * mirror is these four records and no others, and the two blues stay far enough apart
	 * to be two colours. Three hue rungs of sixty-four is 16.9°, which is what the
	 * documents now say — they said nineteen degrees, and nothing measured it.
	 *
	 * <p><b>Two things were wrong with this method until 2026-08-31.</b> It never opened
	 * either document, so reverting {@code docs/CITY-LIVERY-CHECK.md} to "nineteen degrees
	 * apart" left the suite green — a test named for a claim in a document that never read
	 * the document. And its four colours were decimal literals, so the three assertions
	 * about them were constant-folded arithmetic: lifted into a standalone class and run
	 * with {@code RegionData/} emptied of all 27 files, they still passed. The constants
	 * are now read out of the document's livery table, which makes them measurements, and
	 * the hue gap is measured the short way round the wheel — a raw subtraction is correct
	 * at h42 against h39 and silently reports 61 for any future pair straddling 63→0.
	 */
	@Test
	public void theFaladorAndLumbridgeBluesStayApartAndTheMirrorIsExactlyFourRecords()
		throws IOException
	{
		TreeMap<String, Palette> published = published();
		final int faladorWhite = published.get(City.FALADOR.getLabel()).torso;
		final int faladorBlue = published.get(City.FALADOR.getLabel()).trim;
		final int lumbridgeBlue = published.get(City.LUMBRIDGE.getLabel()).torso;
		final int lumbridgeWhite = published.get(City.LUMBRIDGE.getLabel()).trim;

		TreeSet<String> mirrored = new TreeSet<>();
		int faladorTorsos = 0;
		int lumbridgeTorsos = 0;

		for (EntityDefinition record : ours().values())
		{
			City city = City.of(record.getCityRegionId());
			int torso = painted(record, TORSO_BASE);
			int legs = painted(record, LEGS_BASE);

			if (city == City.FALADOR)
			{
				faladorTorsos++;
				assertEquals(record.label() + " is a Falador figure whose torso is not "
					+ "white, so Falador has stopped being a white city", 0, hue(torso));
				assertEquals("and its saturation, which is what makes it white rather "
					+ "than merely pale", 0, saturation(torso));
				if (legs == faladorBlue)
				{
					mirrored.add(record.getName());
				}
			}
			else if (city == City.LUMBRIDGE)
			{
				lumbridgeTorsos++;
				assertEquals(record.label() + " is a Lumbridge figure whose torso is not "
					+ "Lumbridge blue", hue(lumbridgeBlue), hue(torso));
				if (legs == lumbridgeWhite)
				{
					mirrored.add(record.getName());
				}
			}
		}

		assertEquals("Falador's liveried figures", 16, faladorTorsos);
		assertEquals("Lumbridge's", 14, lumbridgeTorsos);
		assertEquals("the cut-3 records whose trim is half the outfit",
			new TreeSet<>(Arrays.asList(
				"Berengar", "Editha", "Lisbeth", "Peveril")), mirrored);

		assertEquals("Falador's white is the grey rung", 0, saturation(faladorWhite));
		assertEquals("and Lumbridge's trim is the same rung", 0, saturation(lumbridgeWhite));
		assertEquals("the two blues are three hue rungs of sixty-four apart — 16.9°, and "
				+ "both documents said nineteen degrees until 2026-08-30",
			3, hueGap(faladorBlue, lumbridgeBlue));

		// And the documents have to carry the measured figure. 3/64 of 360° is 16.875,
		// which rounds to 16.9 whichever way round the wheel it is read. Without this
		// the method was named for a claim it never checked: putting "nineteen degrees
		// apart" back into the document left the whole suite green.
		assertTrue("docs/CITY-LIVERY-CHECK.md has to carry the measured separation",
			read().contains("16.9°"));
		assertTrue("and so does NOTICE item 12, which makes the same claim in prose",
			notice().contains("16.9 degrees"));
	}

	/**
	 * <b>Every Edgeville figure is its donor's own colours, darkened.</b>
	 *
	 * <p>Edgeville has no livery, and what stands in for one is a rule: take the donor
	 * kit's colours and push them towards the dark end by a per-figure amount. Nothing
	 * checked that either, and the rule is the only thing that makes "deliberately
	 * unbranded" different from "eighteen arbitrary palettes".
	 *
	 * <p><b>The rule is "no lighter", not "darker", and the difference is two records.</b>
	 * "Bregg" and "Sten" copy donors already sitting at lightness 6, so their legs cannot
	 * go down and do not move; 17 of the 18 torsos are strictly darker than their donor's.
	 * The single exception in the other direction is Sten's boot, four rungs lighter than
	 * its donor's and black either way. All of it is asserted rather than described,
	 * because a reader of the section would otherwise take "each by a different amount"
	 * to mean every slot moved.
	 *
	 * <p>The donor is the record the entry's own {@code Kit:} line names, which
	 * {@link #everyEntryNamesADonorThatShipsAndAPoseTheRecordActuallyPlays} has already
	 * proved ships.
	 */
	@Test
	public void everyEdgevilleFigureIsItsDonorsColoursDarkened() throws IOException
	{
		TreeMap<String, EntityDefinition> ours = ours();
		TreeMap<String, EntityDefinition> donors = donorsByName();

		int figures = 0;
		int strictlyDarkerTorsos = 0;
		TreeSet<String> unmoved = new TreeSet<>();
		TreeSet<String> lighter = new TreeSet<>();

		for (Entry entry : entries())
		{
			EntityDefinition record = ours.get(entry.name);
			if (City.of(record.getCityRegionId()) != City.EDGEVILLE)
			{
				continue;
			}
			figures++;

			Matcher kit = KIT.matcher(entry.body);
			assertTrue(entry.name, kit.find());
			EntityDefinition donor = donors.get(kit.group(1) + "@" + kit.group(2));
			assertNotNull(entry.name + " names a donor that does not ship", donor);

			for (int base : new int[]{TORSO_BASE, LEGS_BASE, BOOT_BASE})
			{
				Integer mine = painted(record, base);
				Integer theirs = painted(donor, base);
				if (mine == null || theirs == null)
				{
					continue;
				}
				if (lightness(mine) > lightness(theirs))
				{
					lighter.add(entry.name + " " + base);
				}
				else if (lightness(mine) == lightness(theirs))
				{
					unmoved.add(entry.name + " " + base);
				}
				else if (base == TORSO_BASE)
				{
					strictlyDarkerTorsos++;
				}
			}
		}

		assertEquals("Edgeville's figures", 18, figures);
		assertEquals("torsos strictly darker than their donor's", 17, strictlyDarkerTorsos);
		assertEquals("slots that could not move because the donor was already at the "
				+ "floor", new TreeSet<>(Arrays.asList(
				"Bregg " + LEGS_BASE, "Bregg " + BOOT_BASE,
				"Sten " + TORSO_BASE, "Sten " + LEGS_BASE)), unmoved);
		assertEquals("the one slot in Edgeville that is lighter than its donor's, four "
				+ "rungs and black either way",
			new TreeSet<>(Collections.singletonList("Sten " + BOOT_BASE)), lighter);
	}

	/**
	 * <b>Every {@code Placed near} line names a record that ships, at the distance it
	 * claims, and says whether that record has been walked.</b>
	 *
	 * <h2>What was wrong with it</h2>
	 *
	 * <p>The document's own summary said each of the 127 "sits within a few tiles of a
	 * figure whose own tile is proven — somebody stood on it in game". <b>55 of the 127
	 * named one of the 33 citizens added on 2026-08-29</b>, which the same document says
	 * are unwalked. An unwalked figure two tiles away is a second guess resting on the
	 * first, and the line read exactly like the other 72.
	 *
	 * <p>Eight of the stated distances were also wrong. Seven overstated harmlessly; one
	 * — "Gorden", stated 6 tiles from "Ilsa", actually 8 — understated, which is the
	 * direction that matters, because the number is the reader's only offline measure of
	 * how much to trust the tile.
	 *
	 * <h2>What it asserts</h2>
	 *
	 * <p>The anchor ships; the distance is the Chebyshev distance to it, which is what
	 * "tiles away" means when you walk it; the {@code (unwalked)} tag is present exactly
	 * when the anchor is one of the 2026-08-29 records; and the two counts are what the
	 * document says they are. Nothing here can be satisfied by editing prose alone.
	 */
	@Test
	public void everyPlacedNearLineNamesARecordThatShipsAtTheDistanceItClaims()
		throws IOException
	{
		TreeMap<String, EntityDefinition> ours = ours();
		TreeMap<String, List<EntityDefinition>> byName = new TreeMap<>();
		for (EntityDefinition entity : shipped())
		{
			if (entity.getName() != null)
			{
				byName.computeIfAbsent(entity.getName(), k -> new ArrayList<>()).add(entity);
			}
		}

		int proven = 0;
		int unwalked = 0;

		for (Entry entry : entries())
		{
			EntityDefinition record = ours.get(entry.name);
			Matcher near = PLACED_NEAR.matcher(entry.body);
			assertTrue(entry.name + " does not say what its tile was reasoned from",
				near.find());

			String anchorName = near.group(1);
			List<EntityDefinition> candidates = byName.get(anchorName);
			assertNotNull(entry.name + " is placed near \"" + anchorName + "\", which is "
				+ "not in the dataset", candidates);

			WorldPoint at = record.getWorldLocation();
			EntityDefinition anchor = null;
			int distance = Integer.MAX_VALUE;
			for (EntityDefinition candidate : candidates)
			{
				WorldPoint there = candidate.getWorldLocation();
				if (there.getPlane() != at.getPlane())
				{
					continue;
				}
				int chebyshev = Math.max(Math.abs(there.getX() - at.getX()),
					Math.abs(there.getY() - at.getY()));
				if (chebyshev < distance)
				{
					distance = chebyshev;
					anchor = candidate;
				}
			}
			assertNotNull(entry.name + "'s anchor is on another plane", anchor);
			assertEquals(entry.name + " says it is " + near.group(3) + " tiles from "
					+ anchorName + " and it is " + distance,
				distance, Integer.parseInt(near.group(3)));

			// An anchor is unwalked if it is one of the 2026-08-29 records — or one of
			// this pass's own, which nothing currently is, but a new entry anchored to
			// a sibling would otherwise score as proven and then be forbidden from
			// carrying the (unwalked) tag it deserves.
			String anchorUuid = anchor.getUuid().toString();
			boolean anchorIsUnwalked =
				anchorUuid.startsWith("add1") || anchorUuid.startsWith(MARKER);
			if (anchorIsUnwalked)
			{
				unwalked++;
				assertNotNull(entry.name + " is anchored to " + anchorName + ", which has "
					+ "never been walked, and the line does not say so", near.group(2));
			}
			else
			{
				proven++;
				assertEquals(entry.name + " marks " + anchorName + " unwalked and it is "
					+ "one of the tiles somebody stood on", null, near.group(2));
			}
		}

		assertEquals("entries anchored to a tile upstream authored", 72, proven);
		assertEquals("entries anchored to a record that has never been walked either",
			55, unwalked);
	}

	/**
	 * <b>The reach table is every figure nine tiles or more from a proven tile, and the
	 * distances in it are measured.</b>
	 *
	 * <p>"Proven" has one meaning here and the table is built from it: the authored tile
	 * of a citizen that came from upstream's dataset, on the same plane. Those are the
	 * only tiles in this repository somebody is known to have stood on — a 2026-08-29 or
	 * 2026-09-01 record is a placement this project guessed, and upstream scenery is an
	 * object rather than a tile anybody walked to.
	 *
	 * <p>The document published <b>seven</b>. Measured this way it is 21, because the
	 * seven were the entries whose <i>anchor</i> was far away rather than the entries far
	 * from anything proven — and 55 of the anchors are themselves unwalked. Fourteen
	 * figures were therefore missing from the list a walker works down, seven of them
	 * (Ferrand at 15, Gorse 13, Gorden 11, Editha 11, Rashid 10, Basma 10, Isabeau 10)
	 * further out than two of the three the document did name.
	 *
	 * <p><b>The table's last column is checked too, and until 2026-08-31 it was not.</b>
	 * {@code REACH_ROW} captured "That tile" and the loop never read the group, so
	 * rewriting {@code | 6 | Gorse | Draynor | 13 | Sailor |} to name "Hans" passed. That
	 * column is the one thing in the row a walker acts on — it says which proven figure to
	 * start from — so it is asserted against the tile the measurement actually won on. It
	 * is asserted as membership of the set of proven figures at that exact distance rather
	 * than as one name, because a tie between two equally near tiles is a real thing that
	 * would otherwise turn on the iteration order of the region files. There are no ties
	 * today: all 21 rows have a single nearest proven figure.
	 */
	@Test
	public void theReachTableIsEveryFigureNineOrMoreTilesFromAProvenTile() throws IOException
	{
		List<EntityDefinition> proven = new ArrayList<>();
		for (EntityDefinition entity : shipped())
		{
			String uuid = entity.getUuid().toString();
			if (entity.getType().isCitizen()
				&& !uuid.startsWith("add1") && !uuid.startsWith(MARKER))
			{
				proven.add(entity);
			}
		}
		assertTrue("there have to be upstream citizens to measure against", proven.size() > 100);

		TreeMap<String, Integer> measured = new TreeMap<>();
		TreeMap<String, TreeSet<String>> nearestNames = new TreeMap<>();
		for (EntityDefinition record : ours().values())
		{
			WorldPoint at = record.getWorldLocation();
			int nearest = Integer.MAX_VALUE;
			for (EntityDefinition tile : proven)
			{
				WorldPoint there = tile.getWorldLocation();
				if (there.getPlane() != at.getPlane())
				{
					continue;
				}
				nearest = Math.min(nearest, Math.max(Math.abs(there.getX() - at.getX()),
					Math.abs(there.getY() - at.getY())));
			}
			if (nearest < PROVEN_REACH_THRESHOLD)
			{
				continue;
			}
			measured.put(record.getName(), nearest);

			// Which proven figures are at that distance — the answer the table's last
			// column has to give, and a set rather than one name so a tie between two
			// equally near tiles does not depend on the order the regions load in.
			TreeSet<String> tied = new TreeSet<>();
			for (EntityDefinition tile : proven)
			{
				WorldPoint there = tile.getWorldLocation();
				if (there.getPlane() == at.getPlane()
					&& Math.max(Math.abs(there.getX() - at.getX()),
						Math.abs(there.getY() - at.getY())) == nearest)
				{
					tied.add(tile.getName());
				}
			}
			nearestNames.put(record.getName(), tied);
		}

		TreeMap<String, Integer> declared = new TreeMap<>();
		Matcher row = REACH_ROW.matcher(read());
		int rows = 0;
		int previous = Integer.MAX_VALUE;
		while (row.find())
		{
			rows++;
			String name = row.group(2);
			assertEquals("the reach table is numbered in one run, and row " + rows
				+ " is written as " + row.group(1), rows, Integer.parseInt(row.group(1)));

			int tiles = Integer.parseInt(row.group(4));
			assertTrue("the reach table is the QA order, so it has to be sorted by "
				+ "distance — " + name + " breaks it", tiles <= previous);
			previous = tiles;
			assertEquals("the table lists " + name + " twice", null,
				declared.put(name, tiles));

			EntityDefinition record = ours().get(name);
			assertNotNull(name + " is in the reach table and not in the data", record);
			assertEquals(name + "'s city", City.of(record.getCityRegionId()).getLabel(),
				row.group(3));

			TreeSet<String> tied = nearestNames.get(name);
			assertNotNull(name + " is in the reach table and is not nine tiles or more "
				+ "from anything proven", tied);
			assertTrue(name + "'s row says to measure from \"" + row.group(5) + "\", and "
					+ "the proven figure at " + tiles + " tiles is " + tied,
				tied.contains(row.group(5)));
		}

		assertEquals("the reach table has to have a row per far figure", measured.size(), rows);
		assertEquals("the reach table and the measurement have to be the same set and the "
			+ "same distances", measured, declared);
		assertEquals("and there have to be 21 of them, not the seven this document "
			+ "published until 2026-08-30", 21, measured.size());
	}

	/**
	 * <b>The prose above the reach table names figures from the rows it claims.</b>
	 *
	 * <p>Three sentences in that section read {@code Rows N to M …} and then name figures
	 * out of that band. Nothing checked them — the reach guard parses the table and stops
	 * — and one of them was wrong from the day it was written: "Rows 15 to 21 are at
	 * exactly nine. Three of them — <b>Thomasin</b>, Thackeray and Rushen …", where
	 * Thomasin is row 13, at ten tiles. A walker reading down the QA order is told which
	 * of the rows in front of them are the softer sort of far away; a name from a
	 * different band makes that advice point at the wrong figure.
	 *
	 * <p>What is asserted is the part a sentence cannot wriggle out of: the bands cover
	 * the table exactly once and in order, every figure named inside a band sits in it,
	 * the count word matches how many were named, and a band that claims a distance is at
	 * that distance in every one of its rows.
	 */
	@Test
	public void theProseAboveTheReachTableNamesFiguresFromTheRowsItClaims() throws IOException
	{
		String text = read();

		TreeMap<String, Integer> rank = new TreeMap<>();
		TreeMap<Integer, Integer> tilesByRow = new TreeMap<>();
		Matcher row = REACH_ROW.matcher(text);
		while (row.find())
		{
			rank.put(row.group(2), Integer.parseInt(row.group(1)));
			tilesByRow.put(Integer.parseInt(row.group(1)), Integer.parseInt(row.group(4)));
		}
		assertEquals("the reach table has to have been found", 21, rank.size());

		int bands = 0;
		int expectedFirstRow = 1;
		Matcher claim = ROWS_CLAIM.matcher(text);
		while (claim.find())
		{
			bands++;
			int from = Integer.parseInt(claim.group(1));
			int to = Integer.parseInt(claim.group(2));
			String prose = claim.group(3);

			assertEquals("the bands have to tile the table in order, and this one starts "
				+ "at row " + from, expectedFirstRow, from);
			expectedFirstRow = to + 1;
			assertTrue("a band cannot run backwards", to >= from);
			assertTrue("row " + to + " is past the end of the table", tilesByRow.containsKey(to));

			// "… are the ten …" has to be the size of the band.
			Matcher size = Pattern.compile("\\bare the ([a-z]+)\\b").matcher(prose);
			if (size.find() && NUMBERS.containsKey(size.group(1)))
			{
				assertEquals("rows " + from + " to " + to + " are called \"the "
						+ size.group(1) + "\"", to - from + 1,
					(int) NUMBERS.get(size.group(1)));
			}

			// "… are at exactly nine." has to hold for every row in the band.
			Matcher exactly = Pattern.compile("\\bat exactly ([a-z]+)\\b").matcher(prose);
			if (exactly.find() && NUMBERS.containsKey(exactly.group(1)))
			{
				for (int n = from; n <= to; n++)
				{
					assertEquals("rows " + from + " to " + to + " are said to be at exactly "
							+ exactly.group(1) + ", and row " + n + " is not",
						(int) NUMBERS.get(exactly.group(1)), (int) tilesByRow.get(n));
				}
			}

			List<String> named = new ArrayList<>();
			Matcher bold = BOLD_NAMES.matcher(prose);
			while (bold.find())
			{
				for (String name : bold.group(1).split(",\\s*|\\s+and\\s+"))
				{
					named.add(name.trim());
				}
			}

			for (String name : named)
			{
				Integer at = rank.get(name);
				assertNotNull("the prose for rows " + from + " to " + to + " names \""
					+ name + "\", which is not in the reach table at all", at);
				assertTrue(name + " is named in the sentence about rows " + from + " to "
						+ to + " and is row " + at + ", at " + tilesByRow.get(at) + " tiles",
					at >= from && at <= to);
			}

			// "Seven of them (**…**)" has to be as many names as it lists. The count
			// word is only the one that introduces the bold run — "if any of them is
			// standing in a pew" is prose, not a tally.
			Matcher howMany = Pattern.compile("\\b([A-Za-z]+) of them\\b(?=[^*]{0,8}\\*\\*)")
				.matcher(prose);
			if (named.isEmpty())
			{
				assertTrue("rows " + from + " to " + to + " name nobody and still count "
					+ "them", !howMany.find());
			}
			else
			{
				assertTrue("the sentence about rows " + from + " to " + to + " names "
					+ named + " and does not say how many of the band that is",
					howMany.find());
				String word = howMany.group(1).toLowerCase();
				assertTrue("\"" + howMany.group(1) + " of them\" is not a number this test "
					+ "knows", NUMBERS.containsKey(word));
				assertEquals("the sentence about rows " + from + " to " + to + " says \""
						+ howMany.group(1) + " of them\" and names " + named,
					(int) NUMBERS.get(word), named.size());
			}
		}

		assertEquals("the bands have to cover the whole table", 22, expectedFirstRow);
		assertEquals("and there have to be three of them", 3, bands);
	}

	/**
	 * The document's own claim about what this pass did not do, checked.
	 *
	 * <p>Three sentences in the "deliberately did not do" section are the ones a reader
	 * would rely on and nothing else here would notice going stale: that the pass added
	 * no model id, no animation name, and nothing to Varrock's three central regions.
	 */
	@Test
	public void theClaimsAboutWhatWasNotDoneAreTrue() throws IOException
	{
		String text = read();
		assertTrue("the document has to state the model-id figure",
			text.contains("**324** distinct"));
		assertEquals("and it has to be the figure that ships",
			324, ShippedModelIds.distinct().size());

		assertTrue("the document has to state the animation figure",
			text.contains("**72** distinct animation"));
		assertEquals("and it has to be the figure that ships",
			72, ShippedAnimationNames.all().size());

		TreeSet<String> inVarrocksCentre = new TreeSet<>();
		for (EntityDefinition entity : shipped())
		{
			int region = entity.getRegionId();
			if ((region == 12852 || region == 12853 || region == 12597)
				&& entity.getUuid().toString().startsWith(MARKER))
			{
				inVarrocksCentre.add(entity.label());
			}
		}
		assertTrue("the document says regions 12852, 12853 and 12597 gained nothing, and "
			+ "they gained: " + inVarrocksCentre, inVarrocksCentre.isEmpty());
	}

	/**
	 * @return the colour this record paints the slot it aimed at {@code base}, or
	 * {@code null} if it aimed at no such slot
	 *
	 * <p>A {@code find} slot is the author's statement of which body part they meant,
	 * which is the property {@link BodySlots#SKIN_BASE}'s javadoc sets out and the reason
	 * "what colour are this citizen's trousers" is answerable from the record alone.
	 */
	private static Integer painted(EntityDefinition record, int base)
	{
		short[] find = record.getRecolorFind();
		short[] replace = record.getRecolorReplace();
		for (int i = 0; i < find.length; i++)
		{
			if ((find[i] & 0xFFFF) == base)
			{
				return replace[i] & 0xFFFF;
			}
		}
		return null;
	}

	/**
	 * The eight rows of the document's livery table, keyed by the city label.
	 *
	 * <p>This is the palette section at the top of the document — measured once per city
	 * and written once — and it is the source
	 * {@link #theLiveryIsOneSetPerCityAndNoTwoCitiesShareOne} measures records against.
	 * It is deliberately a different part of the file from the {@code Livery:} lines: an
	 * entry cannot edit its way into agreeing with it.
	 */
	private static TreeMap<String, Palette> published() throws IOException
	{
		TreeMap<String, Palette> out = new TreeMap<>();
		Matcher row = PALETTE_ROW.matcher(read());
		while (row.find())
		{
			assertEquals("the livery table lists " + row.group(1) + " twice", null,
				out.put(row.group(1), new Palette(
					Integer.parseInt(row.group(2)),
					Integer.parseInt(row.group(3)),
					Integer.parseInt(row.group(4)))));
		}

		assertEquals("the livery table has to have a row per liveried city — a pattern "
			+ "that matched nothing would make every colour assertion in this file "
			+ "vacuous", 8, out.size());
		return out;
	}

	/**
	 * @return whether two packed colours are the same shade at different lightnesses,
	 * which is the only thing a cut is allowed to change
	 */
	private static boolean sameShade(int a, int b)
	{
		return hue(a) == hue(b) && saturation(a) == saturation(b);
	}

	/** @return a packed colour written the way the document's livery table writes it */
	private static String describe(int packed)
	{
		return packed + " (h" + hue(packed) + "/s" + saturation(packed)
			+ "/l" + lightness(packed) + ")";
	}

	/** @return the six hue bits of a packed game colour */
	private static int hue(int packed)
	{
		return (packed & 0xFFFF) >>> 10;
	}

	/**
	 * @return how many rungs apart two hues are, measured the short way round
	 *
	 * <p>Hue is a wheel of sixty-four, so a plain subtraction is only right while both
	 * values sit on the same side of the 63→0 seam. It is right for Falador against
	 * Lumbridge and would report 61 for a pair three rungs apart across the seam, which
	 * is how a separation claim goes quietly wrong rather than red.
	 */
	private static int hueGap(int a, int b)
	{
		int gap = Math.abs(hue(a) - hue(b)) % 64;
		return Math.min(gap, 64 - gap);
	}

	/** @return the three saturation bits */
	private static int saturation(int packed)
	{
		return ((packed & 0xFFFF) >>> 7) & 0x07;
	}

	/** @return the seven lightness bits */
	private static int lightness(int packed)
	{
		return packed & 0x7F;
	}

	/**
	 * Every record a {@code Kit:} line could be naming, keyed the way that line names it.
	 *
	 * <p>Name alone is not a key — the dataset has two "Charlie"s — so the key is the
	 * name and the region, which is exactly the pair the document prints.
	 */
	private static TreeMap<String, EntityDefinition> donorsByName()
	{
		TreeMap<String, EntityDefinition> out = new TreeMap<>();
		for (EntityDefinition entity : shipped())
		{
			if (entity.getName() != null && !entity.getUuid().toString().startsWith(MARKER))
			{
				out.put(entity.getName() + "@" + entity.getRegionId(), entity);
			}
		}
		return out;
	}

	private static TreeMap<String, EntityDefinition> ours()
	{
		TreeMap<String, EntityDefinition> byName = new TreeMap<>();
		for (EntityDefinition entity : shipped())
		{
			if (entity.getUuid().toString().startsWith(MARKER))
			{
				assertEquals("two liveried records named " + entity.getName(), null,
					byName.put(entity.getName(), entity));
			}
		}

		assertEquals("liveried records in the shipped data", FIGURES, byName.size());
		return byName;
	}

	private static List<EntityDefinition> shipped()
	{
		RegionDataLoader loader = new RegionDataLoader(TestGson.injected());
		List<EntityDefinition> out = new ArrayList<>();
		for (int regionId : ShippedRegions.ids())
		{
			RegionDefinition region = loader.loadRegion(regionId);
			if (region != null)
			{
				out.addAll(region.getEntities());
			}
		}
		return out;
	}

	private static List<Entry> entries() throws IOException
	{
		List<Entry> out = new ArrayList<>();
		Matcher matcher = ENTRY.matcher(read());
		while (matcher.find())
		{
			out.add(new Entry(matcher.group(1), matcher.group(2)));
		}

		assertEquals("the checklist parser has to find every entry — a pattern that "
			+ "matched nothing would pass every assertion in this file while reading no "
			+ "part of the document at all", FIGURES, out.size());
		return out;
	}

	private static String read() throws IOException
	{
		File file = new File(DOC);
		assertTrue(file.getAbsolutePath() + " is missing, and it is the walk these 127 "
			+ "records are waiting on", file.isFile());
		return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
	}

	/**
	 * {@code NOTICE}, which item 12 uses to make the same colour claims in prose.
	 *
	 * <p>Declared an input of the {@code test} task in {@code build.gradle} alongside the
	 * checklist, for the same reason: without that, editing it and running
	 * {@code ./gradlew test} reports nothing and exits 0.
	 */
	private static String notice() throws IOException
	{
		File file = new File(NOTICE);
		assertTrue(file.getAbsolutePath() + " is missing, and it is the disclosure this "
			+ "pass's colours are published in", file.isFile());
		return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
	}

	private static TreeMap<String, Integer> orientations()
	{
		TreeMap<String, Integer> out = new TreeMap<>();
		out.put("north", 0);
		out.put("east", 512);
		out.put("south", 1024);
		out.put("west", 1536);
		return out;
	}

	/** The number words the prose above the reach table counts in. */
	private static TreeMap<String, Integer> numbers()
	{
		TreeMap<String, Integer> out = new TreeMap<>();
		String[] words = {
			"zero", "one", "two", "three", "four", "five", "six", "seven", "eight",
			"nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen",
			"sixteen", "seventeen", "eighteen", "nineteen", "twenty",
		};
		for (int i = 0; i < words.length; i++)
		{
			out.put(words[i], i);
		}
		out.put("twenty-one", 21);
		return out;
	}

	/** One city's row of the document's livery table. */
	private static final class Palette
	{
		final int torso;
		final int legs;
		final int trim;

		Palette(int torso, int legs, int trim)
		{
			this.torso = torso;
			this.legs = legs;
			this.trim = trim;
		}
	}

	private static final class Entry
	{
		final String name;
		final String body;

		Entry(String name, String body)
		{
			this.name = name;
			this.body = body;
		}
	}
}
