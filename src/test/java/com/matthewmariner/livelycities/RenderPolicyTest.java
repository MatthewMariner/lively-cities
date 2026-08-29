package com.matthewmariner.livelycities;

import java.nio.file.Paths;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.runelite.api.Constants;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RenderPolicyTest
{
	/**
	 * Cross-checks the local arithmetic against the client's own
	 * {@link WorldPoint#getRegionID()} rather than against a table of numbers I
	 * worked out by hand.
	 */
	@Test
	public void regionIdMatchesTheClientsOwnArithmetic()
	{
		int[][] points = {
			{3238, 3425, 0},   // Varrock, region 12853 — the busiest region in the dataset
			{3222, 3218, 0},   // Lumbridge
			{2964, 3378, 0},   // Draynor
			{1, 1, 0},         // low corner
			{3000, 9000, 2},   // underground y
			{2440, 5169, 1},   // instance-shaped coordinates
		};

		for (int[] p : points)
		{
			WorldPoint wp = new WorldPoint(p[0], p[1], p[2]);
			assertEquals(
				"region id for " + p[0] + "," + p[1],
				wp.getRegionID(),
				RenderPolicy.regionIdOf(p[0], p[1]));
		}

		// And one absolute value, so a change to both sides at once is still caught.
		assertEquals(12853, RenderPolicy.regionIdOf(3238, 3425));
	}

	@Test
	public void distanceIsChebyshevNotEuclideanOrManhattan()
	{
		WorldPoint origin = new WorldPoint(3200, 3200, 0);

		// dx 3, dy 7: Chebyshev 7, Manhattan 10, Euclidean ~7.6.
		assertEquals(7, RenderPolicy.tileDistance(origin, new WorldPoint(3203, 3207, 0)));
		// Asymmetric the other way round, and negative deltas.
		assertEquals(9, RenderPolicy.tileDistance(origin, new WorldPoint(3191, 3196, 0)));
		assertEquals(0, RenderPolicy.tileDistance(origin, new WorldPoint(3200, 3200, 0)));
		// Plane is ignored by the metric itself.
		assertEquals(4, RenderPolicy.tileDistance(origin, new WorldPoint(3204, 3200, 3)));
	}

	@Test
	public void cullRadiusIsInclusiveAtTheBoundary()
	{
		WorldPoint player = new WorldPoint(3200, 3200, 0);
		int edge = RenderPolicy.DEFAULT_CULL_RADIUS;

		assertTrue(RenderPolicy.isCandidate(player, 0, definitionAt(3200 + edge, 3200, 0), edge));
		assertFalse(RenderPolicy.isCandidate(player, 0, definitionAt(3200 + edge + 1, 3200, 0), edge));
		// Diagonal: the far corner of the box is still in range.
		assertTrue(RenderPolicy.isCandidate(player, 0, definitionAt(3200 + edge, 3200 + edge, 0), edge));
		assertFalse(RenderPolicy.isCandidate(player, 0, definitionAt(3200 + edge, 3200 + edge + 1, 0), edge));
	}

	/**
	 * The radius is a parameter, not a constant, and the boundary has to follow it
	 * — otherwise the dial would move the slider without moving the cull.
	 */
	@Test
	public void theBoundaryFollowsTheRadiusItIsGiven()
	{
		WorldPoint player = new WorldPoint(3200, 3200, 0);
		EntityDefinition twelveAway = definitionAt(3212, 3200, 0);

		assertTrue("inside a 12-tile radius", RenderPolicy.isCandidate(player, 0, twelveAway, 12));
		assertFalse("outside an 11-tile radius", RenderPolicy.isCandidate(player, 0, twelveAway, 11));
		assertTrue("and comfortably inside the widest the dial allows",
			RenderPolicy.isCandidate(player, 0, twelveAway, RenderPolicy.MAX_CULL_RADIUS));
	}

	@Test
	public void entitiesOnAnotherPlaneAreNeverCandidates()
	{
		WorldPoint player = new WorldPoint(3200, 3200, 0);
		int radius = RenderPolicy.DEFAULT_CULL_RADIUS;

		// Same tile, wrong plane.
		assertFalse(RenderPolicy.isCandidate(player, 0, definitionAt(3200, 3200, 1), radius));
		// Right plane, same tile.
		assertTrue(RenderPolicy.isCandidate(player, 1, definitionAt(3200, 3200, 1), radius));
	}

	@Test
	public void nullInputsAreNotCandidates()
	{
		int radius = RenderPolicy.DEFAULT_CULL_RADIUS;
		assertFalse(RenderPolicy.isCandidate(null, 0, definitionAt(3200, 3200, 0), radius));
		assertFalse(RenderPolicy.isCandidate(new WorldPoint(3200, 3200, 0), 0, null, radius));
	}

	@Test
	public void capacityStopsExactlyAtTheCap()
	{
		int cap = RenderPolicy.MAX_ACTIVE_OBJECTS;

		assertTrue(RenderPolicy.hasCapacity(0));
		assertTrue(RenderPolicy.hasCapacity(cap - 1));
		assertFalse(RenderPolicy.hasCapacity(cap));
		assertFalse(RenderPolicy.hasCapacity(cap + 1));
	}

	@Test
	public void theBuildBudgetStopsExactlyAtItsCap()
	{
		int budget = RenderPolicy.MAX_MODEL_BUILDS_PER_PASS;

		assertTrue(RenderPolicy.hasBuildBudget(0));
		assertTrue(RenderPolicy.hasBuildBudget(budget - 1));
		assertFalse(RenderPolicy.hasBuildBudget(budget));
		assertFalse(RenderPolicy.hasBuildBudget(budget + 1));
	}

	/**
	 * The build budget is the arithmetic it says it is, and one more build would break
	 * the threshold it was derived from.
	 *
	 * <p><b>Both halves, because only the second one is a test.</b> Asserting the cap is
	 * nine restates the constant; what makes it a claim about the measurement is that ten
	 * does not fit. The figures are the ones from the 300-tick Varrock run on 2026-08-29:
	 * a visibility pass with no building in it took 151µs at the median, one model build
	 * inside a burst costs 1.40ms (the measured p95 — see the constant for why not the
	 * mean), the region files one scene load brings in cost 3.0ms, and a crossing tick's
	 * budget is one frame.
	 *
	 * <p><b>The third half, which is new and is the point of the 2026-08-29 pass.</b>
	 * The budget the cap is derived from has to be the <i>crossing tick</i> budget and
	 * not the visibility pass's own acceptable threshold. Those are different events —
	 * one is paid at a border, the other every 600ms forever — and the old derivation
	 * conflated them. Re-running the old formula on the new figures would have tightened
	 * the cap to two; the assertion below records that, so nobody re-derives it that way
	 * again without seeing what it costs.
	 *
	 * <p>Deliberately recomputed from {@code RenderPolicy}'s own constants rather than
	 * from literals, so a re-measurement moves the cap and this test with it — and a
	 * hand-typed cap that no longer follows from the figures beside it goes red.
	 */
	@Test
	public void theBuildBudgetIsTheLargestOneTheMeasuredCrossingTickAllows()
	{
		int budget = RenderPolicy.MAX_MODEL_BUILDS_PER_PASS;
		int overhead = RenderPolicy.MEASURED_PASS_OVERHEAD_MICROS;
		int regionLoad = RenderPolicy.MEASURED_REGION_LOAD_MICROS;
		int perBuild = RenderPolicy.MEASURED_BURST_MODEL_BUILD_MICROS;
		int acceptable = RenderPolicy.CROSSING_TICK_BUDGET_MICROS;

		assertEquals("the measured figures the cap is derived from: 3.0ms of region load, "
				+ "151us of pass overhead and 1.40ms a build, against a one-frame crossing "
				+ "tick",
			9, budget);

		assertTrue("a crossing tick that spends the whole budget has to land inside the "
				+ "threshold: " + (regionLoad + overhead + budget * perBuild)
				+ "us against " + acceptable + "us",
			regionLoad + overhead + budget * perBuild <= acceptable);

		assertTrue("and one more build has to break it, or the cap is lower than the data "
				+ "allows and citizens are being made to wait for nothing: "
				+ (regionLoad + overhead + (budget + 1) * perBuild) + "us against "
				+ acceptable + "us",
			regionLoad + overhead + (budget + 1) * perBuild > acceptable);

		// The conflation that used to set this number. 2ms was registered for the
		// per-tick decision work, and charging a once-per-crossing burst to it is what
		// made the cap three when the pass overhead was 124us — and would make it two
		// now, for a spike the measurement has since attributed to first-execution cost
		// rather than to building at all.
		int visibilityAcceptableMicros = 2_000;
		assertTrue("deriving the burst cap from the visibility pass's own threshold has to "
				+ "give a strictly smaller number, or this guard is comparing the budget "
				+ "with itself",
			(visibilityAcceptableMicros - overhead) / perBuild < budget);
	}

	/**
	 * The cap is <b>computed</b> from the measured figures, not written down as a number
	 * that happens to match them.
	 *
	 * <p>Everything above checks that 9 is the right answer, and every one of those
	 * assertions passes against {@code MAX_MODEL_BUILDS_PER_PASS = 9} typed as a
	 * literal — I checked, by making exactly that change and watching all 460 tests stay
	 * green. No assertion on a value can tell a computed 9 from a typed one, because
	 * both are 9.
	 *
	 * <p>That matters because {@code RenderPolicy}'s own javadoc and the README both
	 * promise that "a re-measurement moves the cap by editing the measured figures
	 * rather than by picking a new number". With a literal, re-measuring would move the
	 * figures and leave the cap where it was — the tests above would eventually catch
	 * the resulting inconsistency, but only on the next edit, and the documented
	 * property would already be false.
	 *
	 * <p>So this reads the source, the same way
	 * {@link #everyDensityFigureInTheSourceIsTheOneItsSentenceNames()} does for the
	 * density sentences: the initialiser has to mention all three measured constants and
	 * the threshold it divides into. A literal mentions none of them.
	 */
	@Test
	public void theBuildBudgetIsComputedFromTheMeasuredFiguresRatherThanTypedIn()
		throws IOException
	{
		Path source = Paths.get(
			"src/main/java/com/matthewmariner/livelycities/RenderPolicy.java");
		assertTrue("expected " + source.toAbsolutePath()
				+ " — if the source layout moved, this test has to move with it",
			Files.isRegularFile(source));

		String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);

		int at = text.indexOf("MAX_MODEL_BUILDS_PER_PASS =");
		assertNotEquals("no initialiser for MAX_MODEL_BUILDS_PER_PASS", -1, at);
		String initialiser = text.substring(at, text.indexOf(';', at));

		for (String required : new String[]{
			"CROSSING_TICK_BUDGET_MICROS",
			"MEASURED_REGION_LOAD_MICROS",
			"MEASURED_PASS_OVERHEAD_MICROS",
			"MEASURED_BURST_MODEL_BUILD_MICROS"})
		{
			assertTrue("the cap has to be derived from " + required + ", not typed in — "
					+ "its initialiser reads: " + initialiser.replaceAll("\\s+", " "),
				initialiser.contains(required));
		}
	}

	/**
	 * The two ceilings are not the same ceiling.
	 *
	 * <p>They are one word apart in English — "how many at once" — and they bound
	 * completely different things: {@link RenderPolicy#MAX_ACTIVE_OBJECTS} bounds objects
	 * the client has registered, which a pass spends and refunds as the player walks;
	 * {@link RenderPolicy#MAX_MODEL_BUILDS_PER_PASS} bounds work one pass may do. A
	 * change that fed one to the other's guard would compile and would be silently wrong
	 * in both directions at once — three citizens visible in Varrock, or eighty models
	 * built in a tick, depending which way round it went.
	 */
	@Test
	public void theCrowdCapAndTheBuildBudgetAreDifferentNumbersWithDifferentGuards()
	{
		assertTrue("if these ever coincide, every assertion that tells them apart starts "
				+ "passing for the wrong reason",
			RenderPolicy.MAX_ACTIVE_OBJECTS != RenderPolicy.MAX_MODEL_BUILDS_PER_PASS);

		// At a count between the two, one guard says yes and the other says no.
		int between = RenderPolicy.MAX_MODEL_BUILDS_PER_PASS + 1;
		assertTrue("there is still room for more objects at " + between,
			RenderPolicy.hasCapacity(between));
		assertFalse("but no budget for another build at " + between,
			RenderPolicy.hasBuildBudget(between));
	}

	/**
	 * If the cap drops below the densest neighbourhood the dataset actually
	 * contains, real content starts getting culled in Varrock — the busiest
	 * corner of the dataset, not Keldagrim, which is region 11422 and has no
	 * region file at all — and nobody would know why.
	 *
	 * <p>Measured at {@link RenderPolicy#MAX_CULL_RADIUS}, not at the default: the
	 * cap has to survive the widest setting the dial offers, not the one it ships
	 * with. (Wandering can still push a momentary count a little past the static
	 * figure, and the cap handles that by shedding the far edge of the crowd —
	 * that is what it is for.)
	 *
	 * <p>The density is computed from the shipped files rather than written down
	 * here, so it keeps guarding the invariant as the data grows.
	 */
	@Test
	public void capLeavesRoomForTheBusiestPlaceInTheDataset()
	{
		int radius = RenderPolicy.MAX_CULL_RADIUS;
		Neighbourhood densest = densestNeighbourhood(radius);

		assertTrue("the dataset has to be loaded for this to mean anything", densest.count > 0);
		assertTrue("cap " + RenderPolicy.MAX_ACTIVE_OBJECTS + " is below the busiest neighbourhood ("
				+ densest.count + " entities within " + radius + " tiles of "
				+ densest.x + "," + densest.y + " on plane " + densest.plane + ")",
			RenderPolicy.MAX_ACTIVE_OBJECTS >= densest.count);
	}

	/**
	 * Every density figure written down in the shipped source is the one its own
	 * sentence names.
	 *
	 * <p><b>Why the sentences and not just the arithmetic.</b> The test above asserts
	 * {@code cap >= densest}, which is true of <i>both</i> ways of measuring density
	 * and therefore blind to the difference between them. There are two:
	 *
	 * <ul>
	 *   <li><b>From an arbitrary tile</b> — the largest number of entities inside one
	 *       cull radius of <i>any</i> tile a player could stand on. This is the figure
	 *       that bounds the cap, because {@link RenderPolicy#isCandidate} measures from
	 *       the player's position and a player may stand anywhere.</li>
	 *   <li><b>Centred on an entity's own tile</b> — the largest number inside one cull
	 *       radius of a tile some citizen happens to occupy. Strictly smaller, useful to
	 *       a test that has to stand somewhere in particular, and it bounds
	 *       <b>nothing</b>.</li>
	 * </ul>
	 *
	 * <p>Both have been written in {@code MAX_ACTIVE_OBJECTS}'s javadoc at different
	 * times, and the second one halves the stated margin without changing a line of
	 * code. {@code CrowdedSceneTest} carries a comment warning about exactly that
	 * confusion; a comment cannot go red, so this does. Every claim is matched against
	 * the metric recomputed from the shipped files, so the way to move one of these
	 * numbers is to change the dataset.
	 */
	@Test
	public void everyDensityFigureInTheSourceIsTheOneItsSentenceNames() throws IOException
	{
		int wide = RenderPolicy.MAX_CULL_RADIUS;
		int fromAnyTile = densestNeighbourhood(wide).count;
		int centredOnAnEntity = densestEntityCentredNeighbourhood(wide);

		assertTrue("the dataset has to be loaded for any of this to mean anything",
			fromAnyTile > 0 && centredOnAnEntity > 0);
		assertTrue("an arbitrary tile can always do at least as well as an entity's own",
			fromAnyTile >= centredOnAnEntity);
		assertTrue("if the two metrics ever agree, every assertion below starts passing "
				+ "for the wrong reason and this guard is worth nothing — "
				+ fromAnyTile + " vs " + centredOnAnEntity,
			fromAnyTile != centredOnAnEntity);

		String shipped = flattenedShippedSource();
		int claims = 0;

		// "<n> entities at/inside <the widest radius>" — the sentence that bounds the
		// cap, wherever in src/main it is written.
		claims += pinned(shipped,
			"(\\d+) (?:authored )?entities (?:at|inside) "
				+ "(?:\\{@link (?:RenderPolicy)?#MAX_CULL_RADIUS\\}|the widest render distance)",
			fromAnyTile, 3, "the density at the widest cull radius");

		// The same claim in its other spelling, half a sentence later in
		// MAX_ACTIVE_OBJECTS's javadoc.
		claims += pinned(shipped,
			"(\\d+) inside a \\{@link #MAX_CULL_RADIUS\\}-tile one",
			fromAnyTile, 1, "the density at the widest cull radius");

		// And the same metric at the default radius, in the same sentence.
		Matcher atRadius = Pattern.compile("(\\d+) entities inside a (\\d+)-tile square")
			.matcher(shipped);
		int squares = 0;
		while (atRadius.find())
		{
			squares++;
			int radius = Integer.parseInt(atRadius.group(2));
			assertEquals("'" + atRadius.group() + "' has to be the density measured from an "
					+ "arbitrary tile at radius " + radius + ", not the one centred on an entity "
					+ "(" + densestEntityCentredNeighbourhood(radius) + ")",
				densestNeighbourhood(radius).count, Integer.parseInt(atRadius.group(1)));
		}
		assertEquals("the sentence naming a plain tile radius has to still be there", 1, squares);
		claims += squares;

		// The headroom, spelled out in words, in both places that spell it out.
		int spare = RenderPolicy.MAX_ACTIVE_OBJECTS - fromAnyTile;
		claims += pinnedWord(shipped,
			"holds (?:\\d+) entities at \\{@link #MAX_CULL_RADIUS\\}, (\\w+) short of this",
			spare, 1);
		claims += pinnedWord(shipped,
			"holds (?:\\d+) entities at the widest render distance, (\\w+) slots spare",
			spare, 1);

		// The build-budget cost sentence does arithmetic on the same figure.
		Matcher passes = Pattern.compile("\\{@code ceil\\((\\d+) / (\\d+)\\)\\} = (\\d+) passes")
			.matcher(shipped);
		int fills = 0;
		while (passes.find())
		{
			fills++;
			assertEquals("'" + passes.group() + "' fills the densest neighbourhood, so it is the "
					+ "arbitrary-tile figure", fromAnyTile, Integer.parseInt(passes.group(1)));
			assertEquals("and it fills it at the build budget",
				RenderPolicy.MAX_MODEL_BUILDS_PER_PASS, Integer.parseInt(passes.group(2)));
			int builds = Integer.parseInt(passes.group(2));
			assertEquals("and the division has to be the division it prints",
				(Integer.parseInt(passes.group(1)) + builds - 1) / builds,
				Integer.parseInt(passes.group(3)));
		}
		assertEquals("the cold-walk sentence has to still be there", 1, fills);
		claims += fills;

		assertTrue("a sweep that matched nothing would pass while checking nothing: "
				+ claims + " claims found", claims >= 8);

		// And the other metric, pinned where it is correctly named — the comment that
		// exists to stop this confusion recurring, which is itself a place the wrong
		// number could be typed.
		String warning = flatten(read(
			"src/test/java/com/matthewmariner/livelycities/CrowdedSceneTest.java"));
		pinned(warning, "(\\d+) is the densest window centred on an entity's own tile",
			centredOnAnEntity, 1, "the entity-centred density");
		pinned(warning, "the densest window from an arbitrary tile holds (\\d+)",
			fromAnyTile, 1, "the arbitrary-tile density");
	}

	/**
	 * Asserts every match of {@code regex} carries {@code expected} in group 1, and
	 * that there were {@code atLeast} of them.
	 *
	 * @return how many claims were checked
	 */
	private static int pinned(String text, String regex, int expected, int atLeast, String what)
	{
		Matcher matcher = Pattern.compile(regex).matcher(text);
		int found = 0;
		while (matcher.find())
		{
			found++;
			assertEquals("'" + matcher.group() + "' states " + what
					+ ", which is " + expected + " in the shipped dataset",
				expected, Integer.parseInt(matcher.group(1)));
		}

		assertTrue("expected at least " + atLeast + " sentence(s) matching /" + regex
				+ "/ and found " + found + " — if the wording moved, this guard has to move "
				+ "with it rather than quietly stop checking", found >= atLeast);
		return found;
	}

	/** {@link #pinned} for a figure written as an English word rather than digits. */
	private static int pinnedWord(String text, String regex, int expected, int atLeast)
	{
		Matcher matcher = Pattern.compile(regex).matcher(text);
		int found = 0;
		while (matcher.find())
		{
			found++;
			assertEquals("'" + matcher.group() + "' states the headroom between the cap and the "
					+ "densest neighbourhood a player can stand in",
				numberWord(expected), matcher.group(1));
		}

		assertTrue("expected at least " + atLeast + " sentence(s) matching /" + regex
				+ "/ and found " + found, found >= atLeast);
		return found;
	}

	private static String numberWord(int value)
	{
		String[] words = {
			"zero", "one", "two", "three", "four", "five", "six", "seven", "eight",
			"nine", "ten", "eleven", "twelve"};
		return value >= 0 && value < words.length ? words[value] : String.valueOf(value);
	}

	/**
	 * Every {@code .java} file under {@code src/main/java}, run together into one
	 * string with comment furniture and line wrapping flattened away.
	 *
	 * <p>Reading the source text rather than the compiled classes because what is
	 * being checked <i>is</i> prose — javadoc and {@code //} comments, which javac
	 * throws away. Same working-directory assumption as {@code ShippedSourceTest},
	 * and the same loud failure if it ever stops holding.
	 */
	private static String flattenedShippedSource() throws IOException
	{
		File root = new File("src/main/java");
		assertTrue("expected to find " + root.getAbsolutePath()
			+ " — if the test working directory moved, this test has to move with it",
			root.isDirectory());

		List<Path> sources;
		try (Stream<Path> paths = Files.walk(root.toPath()))
		{
			sources = paths
				.filter(path -> path.getFileName().toString().endsWith(".java"))
				.sorted()
				.collect(Collectors.toList());
		}

		assertFalse("no .java files under " + root.getAbsolutePath()
			+ " — a sweep of nothing would pass", sources.isEmpty());

		StringBuilder all = new StringBuilder();
		for (Path source : sources)
		{
			all.append(flatten(read(source.toString()))).append('\n');
		}
		return all.toString();
	}

	private static String read(String path) throws IOException
	{
		File file = new File(path);
		assertTrue("expected to find " + file.getAbsolutePath(), file.isFile());
		return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
	}

	/**
	 * Strips the comment furniture — leading {@code *}, {@code //}, the javadoc
	 * delimiters and inline {@code <b>}/{@code <i>} — and collapses every run of
	 * whitespace to one space, so a sentence can be matched however it happens to be
	 * wrapped.
	 */
	private static String flatten(String source)
	{
		return source
			.replaceAll("(?m)^\\s*(?:\\*/|/\\*+|\\*|//)\\s?", " ")
			.replaceAll("</?[bi]>", "")
			.replaceAll("\\s+", " ");
	}

	/**
	 * The scene's geometry at the moment it is built, and the fact that it is only
	 * true at that moment.
	 *
	 * <p>The 104-tile scene sits on a chunk-aligned base with the player's chunk
	 * seventh of thirteen, so the player starts 48..55 tiles from every edge. The
	 * scene does not follow them: {@code FakeWorldView.around} reproduces the
	 * client's own base arithmetic, and this walks a player across a chunk to show
	 * the margin decaying step for step rather than staying at 48.
	 *
	 * <p>This test replaces one that asserted
	 * {@code DEFAULT_CULL_RADIUS <= GUARANTEED_SCENE_RADIUS} under the heading
	 * "an entity inside the cull radius must be inside the loaded scene". That was
	 * a false invariant dressed up as arithmetic, and it is the reason the pop-in
	 * was diagnosed as something other than what it is.
	 */
	@Test
	public void theSceneRadiusHoldsAtLoadAndDecaysAfterwards()
	{
		assertEquals(48, RenderPolicy.SCENE_RADIUS_AT_LOAD);
		assertEquals(Constants.SCENE_SIZE / 2 - Constants.CHUNK_SIZE / 2,
			RenderPolicy.SCENE_RADIUS_AT_LOAD);

		// At load: the closest edge is 48 tiles away at worst, 55 at best.
		for (int offsetInChunk = 0; offsetInChunk < Constants.CHUNK_SIZE; offsetInChunk++)
		{
			WorldPoint player = new WorldPoint(3200 + offsetInChunk, 3200 + offsetInChunk, 0);
			FakeWorldView freshScene = FakeWorldView.around(player, 12852);
			int margin = marginToNearestEdge(player, freshScene);
			assertTrue("at load the margin is 48..55, got " + margin,
				margin >= RenderPolicy.SCENE_RADIUS_AT_LOAD
					&& margin <= RenderPolicy.SCENE_RADIUS_AT_LOAD + Constants.CHUNK_SIZE - 1);
		}

		// Now hold the scene still and walk. Nothing recentres, so the margin
		// falls one tile per tile — which is the whole point.
		WorldPoint start = new WorldPoint(3200, 3200, 0);
		FakeWorldView stale = FakeWorldView.around(start, 12852);
		int atLoad = marginToNearestEdge(start, stale);
		int after32 = marginToNearestEdge(new WorldPoint(3200 - 32, 3200, 0), stale);

		assertEquals("walking 32 tiles costs 32 tiles of margin", atLoad - 32, after32);
		assertEquals("and lands exactly on the sustained floor",
			RenderPolicy.SUSTAINED_SCENE_RADIUS, after32);
	}

	/**
	 * The figure that actually holds, and the fact that the default cull radius is
	 * knowingly wider than it.
	 *
	 * <p>Sixteen is the game's map-rebuild margin: a new scene arrives once the
	 * player's position inside the current one leaves {@code [16, 104-16)}, so the
	 * player is never closer than 16 tiles to an edge and never guaranteed more.
	 * It is not in {@link Constants} and this test does not pretend it is; what it
	 * pins is the <i>consequence</i> — the default is above it, so pop-in is
	 * reachable at stock settings, and the config description has to keep saying
	 * so.
	 */
	@Test
	public void theSustainedSceneFloorIsBelowTheDefaultCullRadius()
	{
		assertEquals(16, RenderPolicy.SUSTAINED_SCENE_RADIUS);
		assertEquals("the floor is the at-load radius less four chunks of drift",
			RenderPolicy.SCENE_RADIUS_AT_LOAD - 4 * Constants.CHUNK_SIZE,
			RenderPolicy.SUSTAINED_SCENE_RADIUS);
		assertTrue("the sustained floor is well below what the scene offers at load",
			RenderPolicy.SUSTAINED_SCENE_RADIUS < RenderPolicy.SCENE_RADIUS_AT_LOAD);

		assertTrue("the default render distance is deliberately wider than the floor, "
				+ "so entities near its edge can be outside the loaded scene between "
				+ "scene loads — if this ever stops being true, the config description "
				+ "that warns about it has to change too",
			RenderPolicy.DEFAULT_CULL_RADIUS > RenderPolicy.SUSTAINED_SCENE_RADIUS);

		// And a wanderer can be outside the scene at any radius at all, because its
		// box reaches past the tile the cull check measured.
		assertTrue("no cull radius makes a wandering citizen always placeable",
			RenderPolicy.MIN_CULL_RADIUS + RenderPolicy.DATASET_OVERHANG_ALLOWANCE
				> RenderPolicy.SUSTAINED_SCENE_RADIUS);
	}

	/**
	 * The cull-radius dial's ceiling.
	 *
	 * <p>A choice with a bound behind it, not a derivation: an entity selected at
	 * radius R can be R + the dataset's worst overhang away by the time it is
	 * drawn, and past {@code SCENE_RADIUS_AT_LOAD} it is outside the scene even in
	 * the best case — immediately after a load — so it could never be drawn at any
	 * point in the walk cycle. Offering a setting like that would be offering
	 * nothing. Inside the ceiling, coverage is partial rather than guaranteed, and
	 * {@link #theSustainedSceneFloorIsBelowTheDefaultCullRadius} is where that is
	 * pinned.
	 *
	 * <p>The clamp has to hold whatever arrives from a hand-edited settings file,
	 * not just what the slider allows.
	 */
	@Test
	public void theCullRadiusDialStopsWhereEntitiesStopBeingDrawableAtAll()
	{
		assertEquals("the ceiling is the at-load radius less the dataset's overhang",
			RenderPolicy.SCENE_RADIUS_AT_LOAD - RenderPolicy.DATASET_OVERHANG_ALLOWANCE,
			RenderPolicy.MAX_CULL_RADIUS);
		assertTrue("the widest setting plus the overhang must at least fit the scene as loaded",
			RenderPolicy.MAX_CULL_RADIUS + RenderPolicy.DATASET_OVERHANG_ALLOWANCE
				<= RenderPolicy.SCENE_RADIUS_AT_LOAD);
		assertTrue("the default has to be a setting the dial can actually express",
			RenderPolicy.DEFAULT_CULL_RADIUS >= RenderPolicy.MIN_CULL_RADIUS
				&& RenderPolicy.DEFAULT_CULL_RADIUS <= RenderPolicy.MAX_CULL_RADIUS);

		// The clamp, which is the only thing every read goes through.
		assertEquals(RenderPolicy.MAX_CULL_RADIUS,
			RenderPolicy.clampCullRadius(RenderPolicy.MAX_CULL_RADIUS + 1));
		assertEquals(RenderPolicy.MAX_CULL_RADIUS, RenderPolicy.clampCullRadius(9999));
		assertEquals(RenderPolicy.MIN_CULL_RADIUS,
			RenderPolicy.clampCullRadius(RenderPolicy.MIN_CULL_RADIUS - 1));
		assertEquals(RenderPolicy.MIN_CULL_RADIUS, RenderPolicy.clampCullRadius(0));
		assertEquals(RenderPolicy.MIN_CULL_RADIUS, RenderPolicy.clampCullRadius(-40));
		// And a legal value is left exactly alone.
		assertEquals(RenderPolicy.DEFAULT_CULL_RADIUS,
			RenderPolicy.clampCullRadius(RenderPolicy.DEFAULT_CULL_RADIUS));
		assertEquals(RenderPolicy.MAX_CULL_RADIUS,
			RenderPolicy.clampCullRadius(RenderPolicy.MAX_CULL_RADIUS));
	}

	/**
	 * Both things that put a rendered entity further from the player than the cull
	 * check measured, computed from the shipped files.
	 *
	 * <ul>
	 *   <li><b>Misfiling.</b> Entities are found by region file name, and the
	 *       dataset misfiles one: "Dark wizard" is in {@code 12853.json} and
	 *       stands 6 tiles inside region 12852. It is still found, because the
	 *       file's region is in the scene whenever the entity is close enough to
	 *       render — but only while the cull radius plus the misfiling stays
	 *       inside the guaranteed scene.</li>
	 *   <li><b>Wandering.</b> A citizen paces its authored box while the cull
	 *       check keeps measuring from the tile it started on. The widest box in
	 *       the dataset reaches 18 tiles from that tile.</li>
	 * </ul>
	 *
	 * <p>Both have to fit inside {@link RenderPolicy#DATASET_OVERHANG_ALLOWANCE},
	 * which is the term {@link RenderPolicy#MAX_CULL_RADIUS} subtracts. Growing the
	 * dataset past it fails here rather than quietly widening the gap between the
	 * tile a cull decision was made about and the tile the citizen is drawn on.
	 *
	 * <p>The wander boxes are read from the JSON by {@link ShippedWanderBoxes},
	 * not from {@link EntityDefinition}, because that class clamps a box to the
	 * allowance: asking it would get the allowance back whatever the allowance
	 * was, and a mutation test caught exactly that tautology.
	 */
	@Test
	public void theWorstMisfilingAndTheWidestWanderBoxBothFitTheOverhangAllowance()
	{
		int allowance = RenderPolicy.DATASET_OVERHANG_ALLOWANCE;

		int worstMisfiling = 0;
		int misfiled = 0;
		String misfiledWhere = "nothing misfiled";

		for (EntityDefinition entity : shippedEntities())
		{
			if (entity.getTileRegionId() == entity.getRegionId())
			{
				continue;
			}

			misfiled++;
			int overhang = distanceToRegion(entity.getWorldLocation(), entity.getRegionId());
			if (overhang > worstMisfiling)
			{
				worstMisfiling = overhang;
				misfiledWhere = entity.label() + " filed under " + entity.getRegionId()
					+ ".json but standing in " + entity.getTileRegionId();
			}
		}

		int worstWander = 0;
		String wanderWhere = "nothing wanders";
		List<ShippedWanderBoxes.Authored> boxes = ShippedWanderBoxes.all();
		for (ShippedWanderBoxes.Authored box : boxes)
		{
			assertTrue("a WanderingCitizen with no wanderBox at all: " + box, box.hasBox());
			if (box.reach() > worstWander)
			{
				worstWander = box.reach();
				wanderWhere = box.toString();
			}
		}

		assertEquals("the shipped dataset has exactly one misfiled entity", 1, misfiled);
		assertEquals("the shipped dataset has 51 wandering citizens", 51, boxes.size());

		assertTrue("worst misfiling " + worstMisfiling + " (" + misfiledWhere
				+ ") exceeds the " + allowance + "-tile overhang allowance",
			worstMisfiling <= allowance);
		assertTrue("widest authored wander reach " + worstWander + " (" + wanderWhere
				+ ") exceeds the " + allowance + "-tile overhang allowance, so that box is being "
				+ "silently shortened",
			worstWander <= allowance);

		assertTrue("the widest cull radius plus the worst overhang ("
				+ Math.max(worstMisfiling, worstWander) + ") exceeds the "
				+ RenderPolicy.SCENE_RADIUS_AT_LOAD + " tiles the scene covers when it is "
				+ "built, so the far edge of the dial could never be drawn at all",
			RenderPolicy.MAX_CULL_RADIUS + Math.max(worstMisfiling, worstWander)
				<= RenderPolicy.SCENE_RADIUS_AT_LOAD);
	}

	/**
	 * Chebyshev distance from a tile to the nearest edge of a world view's loaded
	 * scene. Reads {@code getBaseX/getBaseY/getSizeX/getSizeY} — the same four
	 * numbers {@code LocalPoint.fromWorld} uses to decide whether a tile can be
	 * placed at all.
	 */
	private static int marginToNearestEdge(WorldPoint player, FakeWorldView view)
	{
		int minX = player.getX() - view.getBaseX();
		int minY = player.getY() - view.getBaseY();
		int maxX = view.getBaseX() + view.getSizeX() - 1 - player.getX();
		int maxY = view.getBaseY() + view.getSizeY() - 1 - player.getY();
		return Math.min(Math.min(minX, maxX), Math.min(minY, maxY));
	}

	/**
	 * Chebyshev distance from a tile to the nearest tile of a region, i.e. how
	 * far outside its own file's square an entity has been placed.
	 */
	private static int distanceToRegion(WorldPoint point, int regionId)
	{
		int x0 = (regionId >> 8) * Constants.REGION_SIZE;
		int y0 = (regionId & 0xFF) * Constants.REGION_SIZE;
		int dx = Math.max(0, Math.max(x0 - point.getX(), point.getX() - (x0 + Constants.REGION_SIZE - 1)));
		int dy = Math.max(0, Math.max(y0 - point.getY(), point.getY() - (y0 + Constants.REGION_SIZE - 1)));
		return Math.max(dx, dy);
	}

	/**
	 * The most entities that can be inside one cull radius at once, over every
	 * player position the shipped data allows.
	 *
	 * <p>A maximal window can always be slid until its low corner sits on an
	 * entity, so those corners are the only centres worth trying — checking the
	 * entities' own tiles as centres would undercount.
	 */
	private static Neighbourhood densestNeighbourhood(int radius)
	{
		List<WorldPoint> points = new ArrayList<>();
		for (EntityDefinition entity : shippedEntities())
		{
			points.add(entity.getWorldLocation());
		}

		Neighbourhood best = new Neighbourhood(0, 0, 0, 0);

		for (WorldPoint xSource : points)
		{
			for (WorldPoint ySource : points)
			{
				int cx = xSource.getX() + radius;
				int cy = ySource.getY() + radius;

				int[] perPlane = new int[Constants.MAX_Z];
				for (WorldPoint p : points)
				{
					if (Math.abs(p.getX() - cx) <= radius && Math.abs(p.getY() - cy) <= radius)
					{
						perPlane[p.getPlane()]++;
					}
				}

				for (int plane = 0; plane < perPlane.length; plane++)
				{
					if (perPlane[plane] > best.count)
					{
						best = new Neighbourhood(perPlane[plane], cx, cy, plane);
					}
				}
			}
		}

		return best;
	}

	/**
	 * The most entities that can be inside one cull radius of a tile <b>some entity
	 * already stands on</b>.
	 *
	 * <p>The other metric, and the one that bounds nothing: it is what a test can
	 * position itself on, not what the cull check measures. Kept here beside
	 * {@link #densestNeighbourhood} so the two are computed the same way and only
	 * differ in the one thing that differs.
	 */
	private static int densestEntityCentredNeighbourhood(int radius)
	{
		List<WorldPoint> points = new ArrayList<>();
		for (EntityDefinition entity : shippedEntities())
		{
			points.add(entity.getWorldLocation());
		}

		int best = 0;
		for (WorldPoint centre : points)
		{
			int count = 0;
			for (WorldPoint p : points)
			{
				if (p.getPlane() == centre.getPlane()
					&& Math.abs(p.getX() - centre.getX()) <= radius
					&& Math.abs(p.getY() - centre.getY()) <= radius)
				{
					count++;
				}
			}
			best = Math.max(best, count);
		}

		return best;
	}

	private static List<EntityDefinition> shippedEntities()
	{
		RegionDataLoader loader = new RegionDataLoader(TestGson.injected());
		List<EntityDefinition> entities = new ArrayList<>();

		for (int regionId : ShippedRegions.ids())
		{
			RegionDefinition region = loader.loadRegion(regionId);
			assertNotNull("region " + regionId + " failed to load", region);
			entities.addAll(region.getEntities());
		}

		return entities;
	}

	private static EntityDefinition definitionAt(int x, int y, int plane)
	{
		EntityDefinition definition = EntityDefinitionTest.definition(
			"StationaryCitizen", x, y, plane, new int[]{1});
		assertNotNull(definition);
		return definition;
	}

	/** The densest window found, and where it was. */
	private static final class Neighbourhood
	{
		private final int count;
		private final int x;
		private final int y;
		private final int plane;

		Neighbourhood(int count, int x, int y, int plane)
		{
			this.count = count;
			this.x = x;
			this.y = y;
			this.plane = plane;
		}
	}
}
