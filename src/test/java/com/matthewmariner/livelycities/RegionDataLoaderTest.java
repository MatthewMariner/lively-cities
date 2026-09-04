package com.matthewmariner.livelycities;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Fixtures live in {@code src/test/resources/TestRegionData/} so the loader can
 * be pointed at deliberately broken data without touching the 27 shipped files.
 */
public class RegionDataLoaderTest
{
	private RegionDataLoader loader;

	private static Map<String, EntityDefinition> byName(List<EntityDefinition> entities)
	{
		Map<String, EntityDefinition> map = new HashMap<>();
		for (EntityDefinition e : entities)
		{
			map.put(e.getName(), e);
		}
		return map;
	}

	@Before
	public void setUp()
	{
		loader = new RegionDataLoader(TestGson.injected(), "TestRegionData/");
	}

	@Test
	public void parsesEveryFieldTheRenderCoreUses()
	{
		RegionDefinition region = loader.loadRegion(1001);
		assertNotNull(region);

		assertEquals(1001, region.getRegionId());
		assertEquals(0.8f, region.getVersion(), 0.0001f);
		assertEquals(0, region.getSkippedRecords());
		assertEquals(3, region.getEntityCount());
		assertEquals(2, region.getCitizenCount());
		assertEquals(1, region.getSceneryCount());

		Map<String, EntityDefinition> named = byName(region.getEntities());

		EntityDefinition alder = named.get("Alder the fletcher");
		assertNotNull(alder);
		assertEquals(EntityType.StationaryCitizen, alder.getType());
		assertEquals("Whittling a bow stave.", alder.getExamineText());
		assertEquals(new WorldPoint(3238, 3425, 0), alder.getWorldLocation());
		assertEquals(512, alder.getOrientation());
		assertArrayEqualsInt(new int[]{235, 248}, alder.getModelIds());
		// 54397 -> 33694 is a real pair out of 12850.json (Fisherman), and both
		// halves are past 32767 — the shipped data has 94 such values. The
		// client's recolor() takes the wrapped 16-bit value, so the loader must
		// preserve the negative rather than clamp it. Spelled out as literals:
		// asserting (short) 54397 against a cast of the same expression would
		// pass however the loader behaved.
		assertEquals(-11139, alder.getRecolorFind()[0]);
		assertEquals(-31842, alder.getRecolorReplace()[0]);
		assertEquals((short) 54397, alder.getRecolorFind()[0]);
		assertEquals((short) 33694, alder.getRecolorReplace()[0]);
		// And a value that does fit is left alone.
		assertEquals(8741, alder.getRecolorFind()[1]);
		assertEquals(8493, alder.getRecolorReplace()[1]);
		assertEquals(2, alder.getRecolorFind().length);
		assertEquals(LivelyAnimation.Fletching, alder.getIdleAnimation());
		assertNull(alder.getMoveAnimation());
		assertNull(alder.getScale());
		assertNull(alder.getTranslate());
		assertTrue(alder.getMergedObjects().isEmpty());

		EntityDefinition bryn = named.get("Bryn the wanderer");
		assertNotNull(bryn);
		assertEquals(EntityType.WanderingCitizen, bryn.getType());
		assertEquals(new WorldPoint(3111, 3222, 1), bryn.getWorldLocation());
		assertEquals(1, bryn.getPlane());
		assertEquals(1536, bryn.getOrientation());
		assertEquals(0, bryn.getRecolorFind().length);
		assertEquals(LivelyAnimation.HumanIdle, bryn.getIdleAnimation());
		assertEquals(LivelyAnimation.HumanWalk, bryn.getMoveAnimation());
		assertNotNull(bryn.getScale());
		assertEquals(-0.8f, bryn.getScale()[0], 0.0001f);
		assertEquals(-0.7f, bryn.getScale()[1], 0.0001f);
		assertEquals(-0.6f, bryn.getScale()[2], 0.0001f);

		// Scenery carries no name, so it is the one entry keyed under null.
		EntityDefinition scenery = named.get(null);
		assertNotNull(scenery);
		assertEquals(EntityType.Scenery, scenery.getType());
		assertTrue(!scenery.getType().isCitizen());
		assertEquals(new WorldPoint(3300, 3399, 2), scenery.getWorldLocation());
		assertEquals(1024, scenery.getOrientation());
		assertArrayEqualsInt(new int[]{1569, 2491, 2468}, scenery.getModelIds());
		assertNull(scenery.getIdleAnimation());
		assertNotNull(scenery.getTranslate());
		assertEquals(0.7f, scenery.getTranslate()[1], 0.0001f);
		assertEquals(1, scenery.getMergedObjects().size());
		assertEquals(7719, scenery.getMergedObjects().get(0).getObjectId());
		assertEquals(2, scenery.getMergedObjects().get(0).getRotations());
	}

	@Test
	public void skipsUnusableRecordsAndKeepsTheRest()
	{
		RegionDefinition region = loader.loadRegion(1002);
		assertNotNull(region);

		// Six records cannot render: unknown type, no location, empty models,
		// all-unusable model ids, absent type, location missing its plane.
		assertEquals(6, region.getSkippedRecords());
		assertEquals(6, region.getEntityCount());
		assertEquals(5, region.getCitizenCount());
		assertEquals(1, region.getSceneryCount());

		Map<String, EntityDefinition> named = byName(region.getEntities());
		assertNotNull(named.get("Survivor"));
		assertNull(named.get("Skip: unknown type"));
		assertNull(named.get("Skip: no location"));
		assertNull(named.get("Skip: empty models"));
		assertNull(named.get("Skip: all model ids unusable"));
		assertNull(named.get("Skip: type absent"));
		assertNull(named.get("Skip: plane missing from location"));
	}

	@Test
	public void degradesRatherThanSkipsWhenTheRecordCanStillRender()
	{
		RegionDefinition region = loader.loadRegion(1002);
		assertNotNull(region);
		Map<String, EntityDefinition> named = byName(region.getEntities());

		// An unknown animation name means a static model, not a lost entity.
		EntityDefinition degraded = named.get("Degraded animation");
		assertNotNull(degraded);
		assertNull(degraded.getIdleAnimation());

		// Three finds, one replace: only the matched pair survives.
		EntityDefinition lopsided = named.get("Lopsided recolours");
		assertNotNull(lopsided);
		assertEquals(1, lopsided.getRecolorFind().length);
		assertEquals(1, lopsided.getRecolorReplace().length);
		assertEquals((short) 11, lopsided.getRecolorFind()[0]);
		assertEquals((short) 99, lopsided.getRecolorReplace()[0]);

		// 2560 jau is 512 once wrapped into a full rotation.
		EntityDefinition spun = named.get("Over-spun");
		assertNotNull(spun);
		assertEquals(EntityType.ScriptedCitizen, spun.getType());
		assertEquals(512, spun.getOrientation());

		// Non-positive model ids are dropped individually.
		EntityDefinition mixed = named.get("Mixed model ids");
		assertNotNull(mixed);
		assertArrayEqualsInt(new int[]{42}, mixed.getModelIds());

		// A two-component translate is ignored, not applied half-way.
		EntityDefinition scenery = named.get(null);
		assertNotNull(scenery);
		assertNull(scenery.getTranslate());
	}

	/**
	 * The named anti-pattern: the predecessor returned null for the whole file
	 * when {@code version != 0.8f}, so one bumped number emptied a city.
	 */
	@Test
	public void loadsEverythingDespiteAnUnexpectedSchemaVersion()
	{
		RegionDefinition region = loader.loadRegion(1003);
		assertNotNull("an unexpected version must not discard the region", region);
		assertEquals(9.9f, region.getVersion(), 0.0001f);
		assertEquals(2, region.getEntityCount());
		assertEquals(1, region.getCitizenCount());
		assertEquals(1, region.getSceneryCount());
		assertEquals(0, region.getSkippedRecords());
	}

	@Test
	public void oneUnbindableRecordDoesNotTakeItsNeighbourDown()
	{
		RegionDefinition region = loader.loadRegion(1007);
		assertNotNull(region);
		assertEquals(1, region.getSkippedRecords());
		assertEquals(1, region.getEntityCount());
		assertEquals("Neighbour survives", region.getEntities().get(0).getName());
	}

	@Test
	public void malformedJsonYieldsNullRatherThanAnException()
	{
		assertNull(loader.loadRegion(1004));
	}

	@Test
	public void rootThatIsNotAnObjectYieldsNull()
	{
		assertNull(loader.loadRegion(1005));
	}

	@Test
	public void rostersOfTheWrongShapeLoadAsEmpty()
	{
		RegionDefinition region = loader.loadRegion(1006);
		assertNotNull(region);
		assertEquals(0, region.getEntityCount());
		assertEquals(0, region.getSkippedRecords());
		// The file name is authoritative even though the body says 6006.
		assertEquals(1006, region.getRegionId());
	}

	@Test
	public void absentRegionFileYieldsNull()
	{
		assertNull(loader.loadRegion(999999));
	}

	/**
	 * The shipped dataset, loaded through the real resource prefix. Audited by
	 * hand: 27 files, 269 citizens (103 vendored, plus the six cameos, the 33
	 * townsfolk authored for the five thin cities on 2026-08-29 and the 127 liveried
	 * townsfolk authored on 2026-09-01), 42 scenery, every record valid.
	 */
	@Test
	public void loadsTheWholeShippedDatasetWithoutSkippingAnything()
	{
		RegionDataLoader shipped = new RegionDataLoader(TestGson.injected());

		int files = 0;
		int citizens = 0;
		int scenery = 0;
		int skipped = 0;

		for (int regionId : ShippedRegions.ids())
		{
			RegionDefinition region = shipped.loadRegion(regionId);
			assertNotNull("region " + regionId + " failed to load", region);
			files++;
			citizens += region.getCitizenCount();
			scenery += region.getSceneryCount();
			skipped += region.getSkippedRecords();
			assertEquals("region " + regionId + " has the wrong id", regionId, region.getRegionId());
			assertEquals(RegionDataLoader.EXPECTED_VERSION, region.getVersion(), 0.0001f);
		}

		assertEquals("region file count", 27, files);
		assertEquals("citizen count", 269, citizens);
		assertEquals("scenery count", 42, scenery);
		assertEquals("nothing in the shipped data should be skipped", 0, skipped);
	}

	/**
	 * The three citizen flavours, counted — the row the README's headline table quotes.
	 *
	 * <p>That row said "51 wander, 86 stand still, 5 follow a script" until 2026-08-29,
	 * and the last third of it was false: {@code startScript} is parsed and executed
	 * nowhere (see {@code ShippedSourceTest.noShippedClassReadsTheStartScriptField}), so
	 * the five {@code ScriptedCitizen}s stand as still as the stationary ones. The row
	 * now reads "63 wander, 206 stand still" with the five named as a limitation, and
	 * this is what stops the numbers behind it drifting again. It is also the count that
	 * would notice a {@code ScriptedCitizen} quietly becoming something else, which the
	 * 269 total would not.
	 */
	@Test
	public void theShippedRosterSplitsIntoTheThreeCitizenFlavoursTheReadmeQuotes()
	{
		RegionDataLoader shipped = new RegionDataLoader(TestGson.injected());

		Map<EntityType, Integer> byType = new EnumMap<>(EntityType.class);
		for (EntityType type : EntityType.values())
		{
			byType.put(type, 0);
		}

		for (int regionId : ShippedRegions.ids())
		{
			RegionDefinition region = shipped.loadRegion(regionId);
			assertNotNull("region " + regionId + " failed to load", region);
			for (EntityDefinition entity : region.getEntities())
			{
				byType.merge(entity.getType(), 1, Integer::sum);
			}
		}

		assertEquals("wandering citizens", 63, (int) byType.get(EntityType.WanderingCitizen));
		assertEquals("stationary citizens", 201, (int) byType.get(EntityType.StationaryCitizen));
		assertEquals("scripted citizens, which behave exactly like stationary ones because "
			+ "nothing runs their script", 5, (int) byType.get(EntityType.ScriptedCitizen));
		assertEquals("scenery", 42, (int) byType.get(EntityType.Scenery));

		assertEquals("the four flavours have to be the whole dataset", 311,
			byType.values().stream().mapToInt(Integer::intValue).sum());
		assertEquals("and the README's \"206 stand still\" is the two motionless flavours "
				+ "added together", 206,
			byType.get(EntityType.StationaryCitizen) + byType.get(EntityType.ScriptedCitizen));
	}

	/**
	 * <b>The test count the README and the submission checklist quote is the number of
	 * tests there are.</b>
	 *
	 * <p>It sits here, in a class about the dataset, for one reason: the README is
	 * already read two methods down and splitting one file's claims across two test
	 * classes is how half of them stop being checked. The pass that added the place-table
	 * guard below added it for the row about places and not for the row about tests, two
	 * lines above it in the same list — and by 2026-08-30 the README said <b>477</b> in
	 * its badge, <b>485</b> in its own "every guard has been broken on purpose" bullet
	 * and <b>477</b> again in the build command, while {@code docs/SUBMISSION.md} said
	 * <b>485</b> and the suite ran <b>492</b>. Three different wrong numbers in one file.
	 *
	 * <p>The count is the number of {@code @Test} methods in {@code src/test/java},
	 * which is exactly what Gradle reports because nothing here is parameterised or
	 * repeated. That equivalence is itself worth stating: if somebody adds a
	 * {@code @RunWith(Parameterized.class)} class, this test will start disagreeing with
	 * the runner and should be fixed by counting properly rather than by loosening.
	 *
	 * <p>Both files are declared inputs of the {@code test} task in {@code build.gradle},
	 * or an edit to either would leave the task up to date and this method would not run.
	 */
	@Test
	public void theTestCountTheDocumentsQuoteIsTheNumberOfTestsThereAre() throws IOException
	{
		int tests = 0;
		List<File> sources = new ArrayList<>();
		collectJavaSources(new File("src/test/java"), sources);
		assertTrue("no test sources found, so this method counted nothing at all",
			sources.size() > 20);

		for (File source : sources)
		{
			for (String line : new String(Files.readAllBytes(source.toPath()),
				StandardCharsets.UTF_8).split("\n"))
			{
				String trimmed = line.trim();
				if (trimmed.equals("@Test") || trimmed.startsWith("@Test("))
				{
					tests++;
				}
			}
		}

		String readme = new String(
			Files.readAllBytes(new File("README.md").toPath()), StandardCharsets.UTF_8);
		String submission = new String(
			Files.readAllBytes(new File("docs/SUBMISSION.md").toPath()), StandardCharsets.UTF_8);

		assertEquals("the README's badge", 1, count(readme,
			"[![Tests](https://img.shields.io/badge/tests-" + tests + "-brightgreen)]"));
		assertEquals("the README's \"every guard has been broken on purpose\" bullet",
			1, count(readme, "- **" + tests + " tests**, and every guard"));
		assertEquals("the README's build command comment",
			1, count(readme, "# compile and run the " + tests + " tests"));
		assertEquals("the submission checklist's Tests row",
			1, count(submission, "| all green (" + tests + ") |"));
	}

	/**
	 * <b>Every setting a user can see has something about it in the README.</b>
	 *
	 * <p>Three did not, and had not since the day they were added:
	 * {@code chatterRadius}, {@code maxConcurrentRemarks} and {@code unmuteAll}. The last
	 * is the one that mattered — the README told a reader how to mute an individual
	 * citizen and never told them the way back, so a documented feature was a one-way
	 * door.
	 *
	 * <p>It sits here with the other README guards for the reason the test above gives:
	 * splitting one file's claims across two test classes is how half of them stop being
	 * checked.
	 *
	 * <p><b>The map below is meant to go red when a setting is added.</b> That is the
	 * design, not a maintenance cost: it is checked for equality against the
	 * {@code @ConfigItem} declarations in {@code LivelyCitiesConfig}, so a new visible
	 * item fails this test until somebody has written a line of README for it and named
	 * the phrase here. Do not fix that by deleting the row — a setting with no
	 * documentation behind it is the whole defect. Hidden items are exempt, because they
	 * have no control for a reader to find.
	 *
	 * <p>The phrase for each is a distinctive fragment of the README's own prose rather
	 * than the RuneLite label: several are described in sentences rather than by their
	 * control names, and rewording the sentence out from under the setting should not
	 * quietly pass. The nine city checkboxes share one — the README documents them as a
	 * group, and {@link #theReadmesPlaceTableIsTheDatasetItDescribes()} is what checks
	 * them one at a time. Each phrase must appear exactly once.
	 */
	@Test
	public void everySettingAUserCanSeeIsDescribedInTheReadme() throws IOException
	{
		final String cityCheckboxes = "**9 city checkboxes** — turn any place off";

		Map<String, String> documented = new TreeMap<>();
		documented.put("cullRadius", "**Render distance** — 5 to 30 tiles, default 25");
		documented.put("crowdDensity", "**Crowd density** — `Sparse` · `Normal` · `Full` · `Crowded`");
		documented.put("cameos", "**Friend cameos** — off by default");
		documented.put("overheadText", "**Overhead text** — a hard off switch");
		documented.put("remarkIntervalTicks", "how often anyone speaks");
		documented.put("remarkDwellTicks", "how long a line stays up");
		documented.put("chatterRadius", "**Chatter distance** (1 to 30 tiles, default 15)");
		documented.put("maxConcurrentRemarks", "**At most on screen** (1 to 12, default 3)");
		documented.put(CitizenOverrides.UNHIDE_ALL_KEY, "\"Unhide all\" brings them back");
		documented.put(CitizenOverrides.UNMUTE_ALL_KEY,
			"**Unmute all citizens** gives everybody their voice back");
		for (String key : new String[]{
			"cityAlKharid", "cityArdougne", "cityCatherby", "cityDraynor", "cityEdgeville",
			"cityFalador", "cityGrandExchange", "cityLumbridge", "cityVarrock"})
		{
			documented.put(key, cityCheckboxes);
		}

		assertEquals("the settings this test knows about have to be the settings there are — "
				+ "a new @ConfigItem belongs in the README first and in the map above second",
			visibleConfigKeys(), documented.keySet());

		String readme = new String(
			Files.readAllBytes(new File("README.md").toPath()), StandardCharsets.UTF_8);
		for (Map.Entry<String, String> entry : documented.entrySet())
		{
			assertEquals("the README's line about " + entry.getKey(),
				1, count(readme, entry.getValue()));
		}
	}

	/**
	 * The {@code keyName} of every {@code @ConfigItem} in {@code LivelyCitiesConfig} that
	 * RuneLite actually draws a control for.
	 *
	 * <p>Read out of the source rather than off the interface, because what this needs is
	 * the {@code hidden = true} flag and the constants two of the annotations reference,
	 * and neither survives into anything a test could ask about without reflection —
	 * which this project does not use anywhere, shipped or not.
	 *
	 * <p>The scan walks parentheses rather than matching a regex across the whole file:
	 * an annotation argument list contains commas, quotes and nested calls, and a lazy
	 * {@code .*?} that stopped at the first {@code )} would silently read half of one.
	 */
	private static Set<String> visibleConfigKeys() throws IOException
	{
		String source = new String(Files.readAllBytes(
			new File("src/main/java/com/matthewmariner/livelycities/LivelyCitiesConfig.java")
				.toPath()), StandardCharsets.UTF_8);

		// The keyNames that are constants rather than literals, resolved through the
		// constants themselves so a rename travels here instead of being re-typed.
		Map<String, String> constants = new HashMap<>();
		constants.put("CitizenOverrides.HIDDEN_KEY", CitizenOverrides.HIDDEN_KEY);
		constants.put("CitizenOverrides.MUTED_KEY", CitizenOverrides.MUTED_KEY);
		constants.put("CitizenOverrides.UNHIDE_ALL_KEY", CitizenOverrides.UNHIDE_ALL_KEY);
		constants.put("CitizenOverrides.UNMUTE_ALL_KEY", CitizenOverrides.UNMUTE_ALL_KEY);

		Set<String> keys = new TreeSet<>();
		Pattern keyName = Pattern.compile("keyName\\s*=\\s*(?:\"([^\"]+)\"|([\\w.]+))");
		final String marker = "@ConfigItem(";
		int declarations = 0;

		for (int at = source.indexOf(marker); at >= 0; at = source.indexOf(marker, at + 1))
		{
			int open = at + marker.length();
			int depth = 1;
			int end = open;
			while (depth > 0)
			{
				assertTrue("unbalanced @ConfigItem at offset " + at, end < source.length());
				char c = source.charAt(end++);
				if (c == '(')
				{
					depth++;
				}
				else if (c == ')')
				{
					depth--;
				}
			}

			String block = source.substring(open, end - 1);
			declarations++;
			if (block.contains("hidden = true"))
			{
				continue;
			}

			Matcher match = keyName.matcher(block);
			assertTrue("every @ConfigItem must name a key: " + block, match.find());
			if (match.group(1) != null)
			{
				keys.add(match.group(1));
			}
			else
			{
				String resolved = constants.get(match.group(2));
				assertNotNull("unresolved keyName constant " + match.group(2)
					+ " — add it to the table above rather than skipping it", resolved);
				keys.add(resolved);
			}
		}

		assertEquals("the scan has to find every @ConfigItem in the file, or this test is "
			+ "checking a subset it picked for itself", 21, declarations);
		return keys;
	}

	private static void collectJavaSources(File dir, List<File> into)
	{
		File[] children = dir.listFiles();
		if (children == null)
		{
			return;
		}
		for (File child : children)
		{
			if (child.isDirectory())
			{
				collectJavaSources(child, into);
			}
			else if (child.getName().endsWith(".java"))
			{
				into.add(child);
			}
		}
	}

	private static int count(String haystack, String needle)
	{
		int found = 0;
		for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + 1))
		{
			found++;
		}
		return found;
	}

	/**
	 * The README's own place table, checked against the dataset it describes.
	 *
	 * <p><b>These are not the numbers the test below pins.</b> That one counts
	 * <i>citizens</i> per city; this row counts <i>entities</i>, scenery included, which
	 * is what a reader of the table is being told. They are nine different numbers about
	 * the same nine cities, and the two sets have disagreed before: the Grand Exchange
	 * has nine citizens and ten entities, and the README said ten while the test said
	 * nine, both correctly.
	 *
	 * <p>It is checked here because nothing checked it, and because it is the first
	 * thing anybody reads. A README that overstates a city is the plugin's own version of
	 * the defect this suite spends most of its time on.
	 */
	@Test
	public void theReadmesPlaceTableIsTheDatasetItDescribes() throws IOException
	{
		String readme = new String(
			Files.readAllBytes(new File("README.md").toPath()), StandardCharsets.UTF_8);

		Matcher row = Pattern.compile("\\| \\*\\*9 places\\*\\* \\| ([^|]+) \\|").matcher(readme);
		assertTrue("the README has no nine-places row, and it is the headline table",
			row.find());

		Map<String, Integer> declared = new TreeMap<>();
		Matcher place = Pattern.compile("(?:the )?([A-Za-z' ]+?) \\((\\d+)\\)").matcher(row.group(1));
		while (place.find())
		{
			// "the Grand Exchange" in prose is "Grand Exchange" in City.getLabel(); the
			// article is the README's grammar and not part of the name.
			String label = place.group(1).trim().replaceFirst("^the ", "");
			assertEquals("the README lists " + label + " twice", null,
				declared.put(label, Integer.parseInt(place.group(2))));
		}

		RegionDataLoader shipped = new RegionDataLoader(TestGson.injected());
		Map<String, Integer> actual = new TreeMap<>();
		int total = 0;
		for (City city : City.values())
		{
			int entities = 0;
			for (int regionId : city.getRegionIds())
			{
				entities += shipped.loadRegion(regionId).getEntities().size();
			}
			actual.put(city.getLabel(), entities);
			total += entities;
		}

		assertEquals("the README has to name every city, or one could grow unnoticed",
			actual.keySet(), declared.keySet());
		assertEquals("the README's per-city entity counts", actual, declared);
		assertEquals("and they have to be the whole dataset", 311, total);
	}

	/**
	 * How many citizens each city holds, city by city.
	 *
	 * <p><b>The total is not this claim.</b>
	 * {@link #theShippedRosterSplitsIntoTheThreeCitizenFlavoursTheReadmeQuotes} pins 269
	 * citizens across 27 files, and 269 is a sum: deleting a figure from Draynor and adding one to
	 * Varrock leaves it at 269 and leaves every other count in the suite — the echo
	 * figures, the remarks partition, the wander-box count — reachable by luck. What
	 * the 2026-08-29 top-up actually claims is that five thin cities were brought to
	 * <b>ten citizens each</b>, and that is a per-city claim which nothing was
	 * checking. {@code docs/CITY-TOP-UP-CHECK.md} told a reader this class held it,
	 * and it did not; this is the assertion that makes the sentence true.
	 *
	 * <p>All nine cities are pinned rather than the ones that moved, because a target
	 * only means anything alongside the ones that are deliberately not it: after the
	 * 2026-09-01 livery pass Varrock is 71 and Edgeville is 22, and the two numbers are
	 * arrived at from opposite directions — Varrock is capped by
	 * {@link RenderPolicy#MAX_ACTIVE_OBJECTS} rather than by taste, and Edgeville was
	 * the thinnest city in the dataset. A row that changes says which city changed,
	 * which the total never could.
	 *
	 * <p>Counted by the <b>file</b> a record ships in, which is how {@link City}
	 * resolves a checkbox — not by the {@code regionId} written inside the record.
	 * The two disagree for one shipped citizen ("Dark wizard", who claims 12853 and
	 * stands in 12852), and it is the file that decides which city's checkbox turns
	 * a figure off.
	 */
	@Test
	public void everyCityHoldsTheNumberOfCitizensItIsSupposedTo()
	{
		RegionDataLoader shipped = new RegionDataLoader(TestGson.injected());

		Map<City, Integer> expected = new EnumMap<>(City.class);
		expected.put(City.AL_KHARID, 24);
		expected.put(City.ARDOUGNE, 24);
		expected.put(City.CATHERBY, 24);
		expected.put(City.DRAYNOR, 24);
		expected.put(City.EDGEVILLE, 22);
		expected.put(City.FALADOR, 26);
		expected.put(City.GRAND_EXCHANGE, 24);
		expected.put(City.LUMBRIDGE, 30);
		expected.put(City.VARROCK, 71);

		assertEquals("every city has to be listed, or one could empty out unnoticed",
			City.values().length, expected.size());

		int total = 0;
		int regions = 0;
		for (City city : City.values())
		{
			int citizens = 0;
			for (int regionId : city.getRegionIds())
			{
				RegionDefinition region = shipped.loadRegion(regionId);
				assertNotNull("region " + regionId + " failed to load", region);
				citizens += region.getCitizenCount();
				regions++;
			}

			assertEquals("citizens in " + city.getLabel(),
				(int) expected.get(city), citizens);
			total += citizens;
		}

		// Both halves, because either alone can be satisfied by an accident: the
		// per-city rows would still pass if a tenth region file went unclaimed by any
		// city, and the totals would still pass if a figure moved between two cities.
		assertEquals("the nine cities between them claim every shipped region file",
			27, regions);
		assertEquals("and hold every shipped citizen", 269, total);
	}

	private static void assertArrayEqualsInt(int[] expected, int[] actual)
	{
		assertEquals("length", expected.length, actual.length);
		for (int i = 0; i < expected.length; i++)
		{
			assertEquals("element " + i, expected[i], actual[i]);
		}
	}

	/**
	 * The "86 {@code StationaryCitizen}s" aside in the README's Known limitations
	 * section, about the five {@code ScriptedCitizen}s that behave no differently
	 * from an ordinary stationary one. It drifted 115 away from the real count
	 * (201) — the same 201 {@link #theShippedRosterSplitsIntoTheThreeCitizenFlavoursTheReadmeQuotes}
	 * pins two methods up — because that guard checks the headline table's row and
	 * this is a second, independent sentence about the same number, three sections
	 * later, that nothing was reading.
	 */
	@Test
	public void theReadmesScriptedCitizenAsideNamesTheActualStationaryCount() throws IOException
	{
		RegionDataLoader shipped = new RegionDataLoader(TestGson.injected());
		int stationary = 0;
		for (int regionId : ShippedRegions.ids())
		{
			RegionDefinition region = shipped.loadRegion(regionId);
			assertNotNull("region " + regionId + " failed to load", region);
			for (EntityDefinition entity : region.getEntities())
			{
				if (entity.getType() == EntityType.StationaryCitizen)
				{
					stationary++;
				}
			}
		}

		String readme = new String(
			Files.readAllBytes(new File("README.md").toPath()), StandardCharsets.UTF_8);
		assertEquals("the Known limitations aside about the five ScriptedCitizens", 1,
			count(readme, "indistinguishable in behaviour from the " + stationary
				+ " `StationaryCitizen`s"));
	}

	/**
	 * How many ways {@code NOTICE} says the vendored dataset has stopped being
	 * byte-identical to upstream — stated three times, in {@code NOTICE} itself
	 * twice and in the README's Credits section once — checked against the number
	 * of numbered items {@code NOTICE} actually lists. README said "eight" after a
	 * ninth, tenth, eleventh and twelfth modification had landed and nothing had
	 * recounted either side.
	 */
	@Test
	public void theModificationCountIsHowManyNumberedItemsNoticeLists() throws IOException
	{
		String notice = new String(
			Files.readAllBytes(new File("NOTICE").toPath()), StandardCharsets.UTF_8);
		Matcher item = Pattern.compile("(?m)^ {1,2}\\d{1,2}\\. \\S").matcher(notice);
		int items = 0;
		while (item.find())
		{
			items++;
		}
		assertTrue("no numbered modification found, so this counted nothing at all",
			items > 5);

		String word = spellOut(items);
		assertEquals("NOTICE's own \"no longer byte-identical ... in N ways\" sentence",
			1, count(notice, "no longer byte-identical to upstream, in " + word + " ways"));
		assertEquals("NOTICE's closing \"BSD-2 permits all N modifications\" sentence",
			1, count(notice, "BSD-2 permits all " + word + " modifications"));

		String readme = new String(
			Files.readAllBytes(new File("README.md").toPath()), StandardCharsets.UTF_8);
		assertEquals("the README's own count, in its Credits section",
			1, count(readme, word + " modifications we have made to their data"));
	}

	/**
	 * The seating and leaning walk's own totals — {@code docs/SEATING-CHECK.md} and
	 * the README both said "thirty-one" (twenty-nine seated, two leaning) after the
	 * 2026-09-01 livery pass added fifteen more {@code HumanLeanReady} figures
	 * without either document being recounted. A lean is a standing pose, so
	 * nothing about the livery pass being "all standing poses" implies the seating
	 * walk is unchanged.
	 */
	@Test
	public void theSeatingWalksTotalsAreTheDatasetsSeatedAndLeaningCounts() throws IOException
	{
		RegionDataLoader shipped = new RegionDataLoader(TestGson.injected());
		Set<LivelyAnimation> seatedPoses = EnumSet.of(LivelyAnimation.Sitting,
			LivelyAnimation.DwarfSit, LivelyAnimation.CatSit, LivelyAnimation.ChurchSitting,
			LivelyAnimation.GoblinPull);
		Set<LivelyAnimation> leaningPoses = EnumSet.of(LivelyAnimation.HumanLeanReady,
			LivelyAnimation.DwarfLean);

		int seated = 0;
		int leaning = 0;
		for (int regionId : ShippedRegions.ids())
		{
			RegionDefinition region = shipped.loadRegion(regionId);
			assertNotNull("region " + regionId + " failed to load", region);
			for (EntityDefinition entity : region.getEntities())
			{
				if (!entity.getType().isCitizen())
				{
					// Three scenery records also play `Sitting` — a sitting prop, not
					// a citizen whose seat wants verifying — and this walk is about
					// citizens. `theShippedRosterSplitsIntoTheThreeCitizenFlavoursThe
					// ReadmeQuotes` already covers scenery as its own flavour.
					continue;
				}
				LivelyAnimation anim = entity.getIdleAnimation();
				if (anim == null)
				{
					continue;
				}
				if (seatedPoses.contains(anim))
				{
					seated++;
				}
				if (leaningPoses.contains(anim))
				{
					leaning++;
				}
			}
		}

		assertEquals("seated citizens (Sitting, DwarfSit, CatSit, ChurchSitting, GoblinPull)",
			29, seated);
		assertEquals("leaning citizens (HumanLeanReady, DwarfLean)", 17, leaning);

		String seated_word = spellOut(seated);
		String leaning_word = spellOut(leaning);
		String total_word = spellOut(seated + leaning);

		String seatingCheck = new String(
			Files.readAllBytes(new File("docs/SEATING-CHECK.md").toPath()), StandardCharsets.UTF_8);
		assertEquals("the seating walk's Leaning section header",
			1, count(seatingCheck, "## Leaning — " + leaning));
		assertEquals("the seating walk's Ground Markers paragraph",
			1, count(seatingCheck, total_word + " entries and not " + seated_word));

		String readme = new String(
			Files.readAllBytes(new File("README.md").toPath()), StandardCharsets.UTF_8);
		assertEquals("the README's own leaning-figure count in Known limitations",
			1, count(readme, leaning_word + " more who *lean*"));
		assertEquals("the README's own combined seating-walk total",
			1, count(readme, total_word + " figures on"));
	}

	private static final String[] ONES = {
		"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
		"ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
		"seventeen", "eighteen", "nineteen"
	};
	private static final String[] TENS = {
		"", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"
	};

	/** Spells out a number 0-99 the way this project's prose does. */
	private static String spellOut(int n)
	{
		assertTrue("this helper only spells numbers 0-99", n >= 0 && n < 100);
		if (n < 20)
		{
			return ONES[n];
		}
		int tens = n / 10;
		int ones = n % 10;
		return ones == 0 ? TENS[tens] : TENS[tens] + "-" + ONES[ones];
	}
}
