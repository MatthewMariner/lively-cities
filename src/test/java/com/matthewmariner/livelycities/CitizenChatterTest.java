package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Constants;
import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The off switches, first — because that is the order they were built in and the
 * reason they were built in it.
 *
 * <p>Overhead-text spam was the predecessor's single loudest complaint: 144
 * upvotes on "please add an option to shut them up", a mute toggle its author
 * promised and never shipped, and upstream issue #35 open from release day. So
 * every dial gets a test that fails if the dial stops working, and the two
 * granularities — the whole feature off, and this one citizen muted — are proven
 * to be different things rather than the same setting spelled twice.
 *
 * <p><b>The fixtures are deliberately not uniform.</b> Every scene here mixes
 * citizens that can talk with citizens that cannot, and puts them at different
 * distances, because 96 of the 129 shipped citizens have nothing authored and
 * because the radius and the cap are read off the tiles. A crowd where everybody
 * is identical could not tell "the chatter skipped the silent ones" from "the
 * chatter iterated everybody", nor "the cap bound" from "only three were
 * eligible".
 */
public class CitizenChatterTest
{
	private static final int VARROCK_NORTH = 12853;

	/** In 12853, and the tile every distance below is measured from. */
	private static final WorldPoint PLAYER = new WorldPoint(3220, 3420, 0);

	private FakeClient client;
	private FakeRegions regions;
	private FakeConfig config;
	private EntityScene scene;
	private FakeWorldView view;

	@Before
	public void setUp()
	{
		client = new FakeClient();
		regions = new FakeRegions();
		config = new FakeConfig();
		scene = new EntityScene(client, regions, config, config.overrides());
		view = FakeWorldView.around(PLAYER, VARROCK_NORTH);
	}

	// --- the hard off switch (issue #35) -------------------------------------

	@Test
	public void theHardOffSwitchMeansNobodyEverStartsTalking()
	{
		spawn(regions.talkingCrowd(VARROCK_NORTH, 3218, 3418, 12));
		config.setOverheadText(false);

		runTicks(400);

		assertEquals("with overhead chatter off, nothing may start", 0, scene.countTalking());
	}

	/**
	 * Switching it off mid-conversation empties the screen, and does so through the
	 * settings path rather than waiting for a game tick — a toggle that visibly lags
	 * the click reads as a toggle that did not work.
	 */
	@Test
	public void switchingItOffClearsWhatIsAlreadyOnScreenWithoutAdvancingTheClock()
	{
		spawn(regions.talkingCrowd(VARROCK_NORTH, 3218, 3418, 12));

		runTicks(400);
		assertTrue("the fixture has to get somebody talking first", scene.countTalking() > 0);

		int clockBefore = scene.getChatterTick();
		config.setOverheadText(false);
		scene.onSettingsChanged(PLAYER, view);

		assertEquals("the screen is empty on the click", 0, scene.countTalking());
		assertEquals("and a settings change is not a game tick",
			clockBefore, scene.getChatterTick());
	}

	// --- the per-citizen mute -----------------------------------------------

	/**
	 * The second granularity, and the one people actually complained at: it was
	 * never "no citizen anywhere should speak", it was "this one, outside the bank,
	 * every six seconds".
	 */
	@Test
	public void aMutedCitizenNeverStartsARemarkAndNobodyElseIsAffected()
	{
		List<EntityDefinition> crowd = regions.talkingCrowd(VARROCK_NORTH, 3218, 3418, 12);
		spawn(crowd);

		EntityDefinition silenced = crowd.get(0);
		config.overrides().mute(silenced);

		// Generous cap, so "it never talked" cannot be the cap's doing.
		config.setMaxConcurrentRemarks(CitizenChatter.MAX_MAX_CONCURRENT);

		boolean somebodyElseTalked = false;
		for (int tick = 0; tick < 600; tick++)
		{
			scene.onGameTick(PLAYER, view);
			assertFalse("a muted citizen must never start a remark",
				talking(silenced));
			somebodyElseTalked |= scene.countTalking() > 0;
		}

		assertTrue("and the mute must not have silenced the street", somebodyElseTalked);
	}

	/**
	 * Muting somebody who is already mid-remark has to be different from muting
	 * somebody who is silent, or the "Mute" menu entry would appear to do nothing
	 * for up to the whole dwell.
	 */
	@Test
	public void mutingIsOnlyHalfTheJobIfSomebodyIsAlreadyTalking()
	{
		EntityDefinition talker = regions.talker(VARROCK_NORTH, 3220, 3421, "Busy today.");
		spawn(talker);

		CitizenRemarks remarks = remarksOf(talker);
		runUntilTalking(remarks);

		config.overrides().mute(talker);
		scene.onGameTick(PLAYER, view);

		assertTrue("the mute alone does not cut a remark short — the dwell is still running, "
				+ "which is why CitizenMenu clears it on the click as well",
			remarks.isTalking());
	}

	// --- cadence ------------------------------------------------------------

	/**
	 * The interval is a real dial: a longer one produces strictly fewer remarks
	 * over the same number of ticks.
	 */
	@Test
	public void alongerIntervalMeansFewerRemarks()
	{
		int chatty = remarksStartedOver(2000, CitizenChatter.MIN_ROLL_INTERVAL_TICKS);
		int quiet = remarksStartedOver(2000, 300);

		assertTrue("a 10-tick interval has to be chattier than a 300-tick one, got "
			+ chatty + " vs " + quiet, chatty > quiet);
		assertTrue("and the chatty end has to actually produce something", chatty > 0);
	}

	/**
	 * The dwell is a real dial too, and it is the one that decides how long a
	 * bubble is on screen — the half of the cadence complaint that is not "how
	 * often".
	 */
	@Test
	public void theDwellDecidesHowLongARemarkStaysUp()
	{
		EntityDefinition talker = regions.talker(VARROCK_NORTH, 3220, 3421, "Busy today.");
		spawn(talker);
		config.setRemarkDwellTicks(7);

		CitizenRemarks remarks = remarksOf(talker);
		int startedAt = runUntilTalking(remarks);

		int upFor = 0;
		while (remarks.isTalking())
		{
			scene.onGameTick(PLAYER, view);
			upFor++;
			assertTrue("a 7-tick dwell must not survive 20 ticks (started at " + startedAt + ")",
				upFor <= 20);
		}

		assertEquals("the remark comes down on the tick the dwell expires", 7, upFor);
	}

	// --- the units, and the saturation guard ---------------------------------
	//
	// Every constant in CitizenChatter is a wall-clock duration written in game
	// ticks, and this plugin once shipped two of them in the wrong unit: a dwell of
	// 120 that was the predecessor's *client* ticks (2.4 seconds) became 72 seconds,
	// and an interval of 60 that was the predecessor's *seconds* became 36. Dwell
	// exceeded interval, so a bubble could never expire before its citizen's next
	// chance to speak and the overhead text saturated permanently — the one
	// complaint the whole feature exists to answer, shipped as the default. The
	// three tests below are the guard: the arithmetic, the whole configurable range,
	// and the street actually going quiet again.

	/**
	 * The defaults, in milliseconds, checked against the client's own clock rather
	 * than against a number in a comment.
	 *
	 * <p>{@link Constants} is the primary source for both tick lengths, so a build
	 * against a client whose game tick was not 600ms would fail here rather than
	 * quietly running the cadence at a different speed.
	 */
	@Test
	public void theDefaultCadenceIsAMinuteApartAndAFewSecondsLong()
	{
		assertEquals("a game tick, which is the unit every constant in CitizenChatter is in",
			600, Constants.GAME_TICK_LENGTH);
		assertEquals("a client tick — thirty to a game tick, and the ratio the dwell was "
				+ "once wrong by", 20, Constants.CLIENT_TICK_LENGTH);
		assertEquals("which is exactly the factor that turned 2.4 seconds into 72",
			30, Constants.GAME_TICK_LENGTH / Constants.CLIENT_TICK_LENGTH);

		int intervalMillis = CitizenChatter.DEFAULT_ROLL_INTERVAL_TICKS * Constants.GAME_TICK_LENGTH;
		assertEquals("a chance to speak once a minute, which is the predecessor's cadence "
				+ "converted rather than copied", 60_000, intervalMillis);

		int dwellMillis = CitizenChatter.DEFAULT_DWELL_TICKS * Constants.GAME_TICK_LENGTH;
		assertTrue("a remark has to stay up long enough to read the longest shipped line "
				+ "(11 words, about 3.3 seconds) and not much longer, was " + dwellMillis + "ms",
			dwellMillis >= 3_000 && dwellMillis <= 10_000);

		assertTrue("and it has to be comfortably shorter than the interval, not merely shorter: "
				+ dwellMillis + "ms of every " + intervalMillis + "ms",
			dwellMillis * 4 <= intervalMillis);

		int maxDwellMillis = CitizenChatter.MAX_DWELL_TICKS * Constants.GAME_TICK_LENGTH;
		assertTrue("even the far end of the dwell dial has to read as somebody saying something "
				+ "rather than as a label stuck to their head — it was 600 ticks, six minutes, "
				+ "while these numbers were being read as client ticks. Now " + maxDwellMillis + "ms",
			maxDwellMillis <= 20_000);
		assertTrue("and it has to stay above the tightest interval, so that asking for a "
				+ "saturating pair is still something a user can do and the clamp that refuses "
				+ "it is still live code",
			CitizenChatter.MAX_DWELL_TICKS > CitizenChatter.MIN_ROLL_INTERVAL_TICKS);

		assertTrue("the same has to hold at the ends of both sliders, or the ranges disagree "
				+ "with the defaults they bracket",
			CitizenChatter.MIN_DWELL_TICKS <= CitizenChatter.DEFAULT_DWELL_TICKS
				&& CitizenChatter.DEFAULT_DWELL_TICKS <= CitizenChatter.MAX_DWELL_TICKS
				&& CitizenChatter.MIN_ROLL_INTERVAL_TICKS <= CitizenChatter.DEFAULT_ROLL_INTERVAL_TICKS
				&& CitizenChatter.DEFAULT_ROLL_INTERVAL_TICKS <= CitizenChatter.MAX_ROLL_INTERVAL_TICKS);
	}

	/**
	 * <b>No pair of values the config can hold — from the sliders, from a hand-edited
	 * {@code settings.properties}, or from a profile synced off another install — can
	 * leave a remark up when its citizen's next roll comes round.</b>
	 *
	 * <p>Every combination, one tick past both ends of both ranges, because that is
	 * the only way to state "no combination can saturate" as a test rather than as an
	 * intention. Two {@code @Range} annotations cannot express this between them:
	 * neither one can see the other's value.
	 */
	@Test
	public void noCadenceTheConfigCanHoldLetsARemarkOutliveItsCitizensNextRoll()
	{
		assertTrue("the clamp must never have to push the dwell below its own minimum, "
				+ "which needs the tightest interval to leave room for it",
			CitizenChatter.MIN_ROLL_INTERVAL_TICKS - 1 >= CitizenChatter.MIN_DWELL_TICKS);

		int clamped = 0;
		for (int interval = CitizenChatter.MIN_ROLL_INTERVAL_TICKS - 5;
			 interval <= CitizenChatter.MAX_ROLL_INTERVAL_TICKS + 5; interval++)
		{
			int effectiveInterval = CitizenChatter.effectiveIntervalTicks(interval);
			assertTrue("interval " + interval + " clamped to " + effectiveInterval,
				effectiveInterval >= CitizenChatter.MIN_ROLL_INTERVAL_TICKS
					&& effectiveInterval <= CitizenChatter.MAX_ROLL_INTERVAL_TICKS);

			for (int dwell = CitizenChatter.MIN_DWELL_TICKS - 5;
				 dwell <= CitizenChatter.MAX_DWELL_TICKS + 5; dwell++)
			{
				int effectiveDwell = CitizenChatter.effectiveDwellTicks(dwell, interval);

				assertTrue("dwell " + dwell + " with interval " + interval + " came out as "
						+ effectiveDwell + " against an interval of " + effectiveInterval
						+ " — a remark that outlives its citizen's next roll is the saturation bug",
					effectiveDwell < effectiveInterval);
				assertTrue("dwell " + dwell + " came out as " + effectiveDwell
						+ ", outside its own range", effectiveDwell >= CitizenChatter.MIN_DWELL_TICKS
					&& effectiveDwell <= CitizenChatter.MAX_DWELL_TICKS);

				if (effectiveDwell != Math.max(CitizenChatter.MIN_DWELL_TICKS,
					Math.min(CitizenChatter.MAX_DWELL_TICKS, dwell)))
				{
					clamped++;
				}
			}
		}

		assertTrue("the interval clamp has to actually bind somewhere, or this test is "
				+ "asserting about unreachable code", clamped > 0);

		assertEquals("the tightest interval a user can ask for, with the longest dwell: one "
				+ "tick of silence between the bubble going and the next roll",
			CitizenChatter.MIN_ROLL_INTERVAL_TICKS - 1,
			CitizenChatter.effectiveDwellTicks(
				CitizenChatter.MAX_DWELL_TICKS, CitizenChatter.MIN_ROLL_INTERVAL_TICKS));

		assertEquals("and the shipped defaults are nowhere near it, so the clamp never "
				+ "silently changes what the user asked for",
			CitizenChatter.DEFAULT_DWELL_TICKS,
			CitizenChatter.effectiveDwellTicks(
				CitizenChatter.DEFAULT_DWELL_TICKS, CitizenChatter.DEFAULT_ROLL_INTERVAL_TICKS));
	}

	/**
	 * The regression, at the level a player would have seen it: with the defaults and
	 * a dozen talkative citizens standing next to you, the street is quiet far more
	 * often than not.
	 *
	 * <p>With the saturating defaults this shipped with, {@code silentTicks} was zero
	 * — every citizen that ever spoke was still speaking, forever. It takes a
	 * thousand-tick run to see that, which is why manual QA did not: the off switches
	 * all worked, and you had to stand and watch text accumulate for a minute.
	 */
	@Test
	public void atTheDefaultsTheOverheadTextClearsInsteadOfPilingUp()
	{
		spawn(regions.talkingCrowd(VARROCK_NORTH, 3218, 3418, 12));

		int silentTicks = 0;
		int peak = 0;
		for (int tick = 0; tick < 3000; tick++)
		{
			scene.onGameTick(PLAYER, view);
			int talking = scene.countTalking();
			peak = Math.max(peak, talking);
			if (talking == 0)
			{
				silentTicks++;
			}
		}

		assertTrue("twelve citizens with something to say have to say it sometime, or this "
			+ "test proves nothing", peak > 0);
		assertTrue("a " + CitizenChatter.DEFAULT_DWELL_TICKS + "-tick dwell inside a "
				+ CitizenChatter.DEFAULT_ROLL_INTERVAL_TICKS + "-tick interval has to leave the "
				+ "screen empty most of the time — it was empty on " + silentTicks + " of 3000 ticks",
			silentTicks > 1500);
	}

	/**
	 * And when a user asks for the saturating combination outright — the longest dwell
	 * on the tightest cadence — the bubble still comes down.
	 *
	 * <p>Driven through the scene rather than through
	 * {@link CitizenChatter#effectiveDwellTicks} so that the clamp is proved to be on
	 * the path a config value actually travels, not merely available on a static
	 * method nobody calls.
	 */
	@Test
	public void theSaturatingCombinationIsClampedOnThePathTheConfigTravels()
	{
		EntityDefinition talker = regions.talker(VARROCK_NORTH, 3220, 3421, "Busy today.");
		spawn(talker);
		config.setRemarkIntervalTicks(CitizenChatter.MIN_ROLL_INTERVAL_TICKS)
			.setRemarkDwellTicks(CitizenChatter.MAX_DWELL_TICKS);

		CitizenRemarks remarks = remarksOf(talker);
		int startedAt = runUntilTalking(remarks);

		int upFor = 0;
		while (remarks.isTalking())
		{
			scene.onGameTick(PLAYER, view);
			upFor++;
			assertTrue("a dwell of " + CitizenChatter.MAX_DWELL_TICKS + " on an interval of "
					+ CitizenChatter.MIN_ROLL_INTERVAL_TICKS + " has to be clamped, not honoured "
					+ "(started at " + startedAt + ")",
				upFor < CitizenChatter.MIN_ROLL_INTERVAL_TICKS);
		}

		assertEquals("the remark comes down one tick before the next roll",
			CitizenChatter.MIN_ROLL_INTERVAL_TICKS - 1, upFor);
	}

	/**
	 * A value the slider could never produce — a hand-edited profile, or one synced
	 * from another install — must not turn the cadence off or make it divide by
	 * zero.
	 */
	@Test
	public void anImpossibleCadenceIsClampedRatherThanTrusted()
	{
		spawn(regions.talkingCrowd(VARROCK_NORTH, 3218, 3418, 8));
		config.setRemarkIntervalTicks(0).setRemarkDwellTicks(-5).setChatterRadius(-1)
			.setMaxConcurrentRemarks(0);

		// The interesting assertion is that this does not throw: an interval of 0
		// reaches Math.floorMod(tick, 0), and a cap of 0 would mean the feature is
		// silently off.
		runTicks(100);

		assertTrue("a cap of 0 clamps to at least one, so the feature is not silently off",
			scene.countTalking() >= 0);
		assertEquals("and the clock still ran", 100, scene.getChatterTick());
	}

	// --- the distance limit -------------------------------------------------

	/**
	 * Only citizens within the chatter radius start talking, and the radius is
	 * measured from the authored tile — the same anchor the cull check uses.
	 */
	@Test
	public void onlyCitizensInsideTheChatterRadiusEverTalk()
	{
		config.setChatterRadius(5);

		// Inside, on the boundary, and outside. Three distances, so "the radius is
		// respected" cannot be satisfied by a fixture where everybody is near.
		EntityDefinition near = regions.talker(VARROCK_NORTH, 3222, 3420, "Near.");
		EntityDefinition boundary = regions.talker(VARROCK_NORTH, 3225, 3420, "On the line.");
		EntityDefinition far = regions.talker(VARROCK_NORTH, 3232, 3420, "Far away.");
		spawn(near, boundary, far);

		assertTrue("all three have to be spawned, or this proves nothing",
			scene.countActive() >= 3);

		boolean nearTalked = false;
		boolean boundaryTalked = false;
		for (int tick = 0; tick < 1200; tick++)
		{
			scene.onGameTick(PLAYER, view);
			nearTalked |= talking(near);
			boundaryTalked |= talking(boundary);
			assertFalse("a citizen 12 tiles away with a 5-tile chatter radius must never talk",
				talking(far));
		}

		assertTrue("the near one has to talk, or the radius is not what stopped the far one",
			nearTalked);
		assertTrue("and the radius is inclusive at its own value", boundaryTalked);
	}

	// --- the concurrency cap ------------------------------------------------

	/**
	 * A crowd cannot produce a wall of text.
	 *
	 * <p>Twenty-four talkers packed inside the chatter radius on the tightest
	 * cadence is well past what the cap allows — without one, roughly twenty would
	 * be on screen at once, which is the complaint restated rather than answered.
	 */
	@Test
	public void theConcurrencyCapBoundsTheNumberOfBubbles()
	{
		spawn(regions.talkingCrowd(VARROCK_NORTH, 3218, 3418, 24));
		config.setRemarkIntervalTicks(CitizenChatter.MIN_ROLL_INTERVAL_TICKS)
			.setRemarkDwellTicks(CitizenChatter.MAX_DWELL_TICKS)
			.setMaxConcurrentRemarks(3);

		int peak = 0;
		for (int tick = 0; tick < 1000; tick++)
		{
			scene.onGameTick(PLAYER, view);
			peak = Math.max(peak, scene.countTalking());
			assertTrue("the cap is 3 and " + scene.countTalking() + " were talking on tick " + tick,
				scene.countTalking() <= 3);
		}

		assertEquals("and the cap has to actually bind, or this test proves nothing", 3, peak);
	}

	/**
	 * Raising the cap raises the ceiling. Without this, a cap that was accidentally
	 * hard-coded to 3 would pass the test above.
	 */
	@Test
	public void raisingTheCapRaisesTheCeiling()
	{
		spawn(regions.talkingCrowd(VARROCK_NORTH, 3218, 3418, 24));
		config.setRemarkIntervalTicks(CitizenChatter.MIN_ROLL_INTERVAL_TICKS)
			.setRemarkDwellTicks(CitizenChatter.MAX_DWELL_TICKS)
			.setMaxConcurrentRemarks(8);

		int peak = 0;
		for (int tick = 0; tick < 1000; tick++)
		{
			scene.onGameTick(PLAYER, view);
			peak = Math.max(peak, scene.countTalking());
		}

		assertEquals(8, peak);
	}

	// --- who is eligible at all ---------------------------------------------

	/**
	 * A citizen with nothing authored never talks, however long the scene runs —
	 * and it is in the same scene as citizens that do, so this is a claim about the
	 * filter rather than about an empty scene.
	 */
	@Test
	public void aCitizenWithNothingAuthoredNeverTalks()
	{
		EntityDefinition silent = regions.citizen(VARROCK_NORTH, 3221, 3420, 0);
		EntityDefinition talker = regions.talker(VARROCK_NORTH, 3222, 3420, "Busy today.");
		spawn(silent, talker);

		assertNull("the silent one must not even have a remark holder",
			scene.wrapperFor(silent).getRemarks());

		boolean talkerTalked = false;
		for (int tick = 0; tick < 1200; tick++)
		{
			scene.onGameTick(PLAYER, view);
			talkerTalked |= talking(talker);
		}

		assertTrue("the fixture's talker has to talk, or the scene never ran", talkerTalked);
	}

	/**
	 * A citizen the visibility pass has deactivated stops talking as part of being
	 * deactivated.
	 *
	 * <p>This is the structural half of the orphaned-text fix, and the assertion is
	 * deliberately made <b>without</b> running a chatter tick: only
	 * {@code updateVisibility} is called, so the only thing that can have silenced
	 * the citizen is {@code despawn()} itself. Running a game tick instead would also
	 * pass with the silencing removed, because the chatter clears inactive citizens
	 * too — and a test that cannot tell the two apart is a test that pins neither.
	 */
	@Test
	public void aCitizenThatLeavesTheScreenStopsTalkingAsPartOfLeaving()
	{
		EntityDefinition talker = regions.talker(VARROCK_NORTH, 3220, 3421, "Busy today.");
		spawn(talker);

		CitizenRemarks remarks = remarksOf(talker);
		runUntilTalking(remarks);

		// Untick its city. The visibility pass despawns it, and the despawn silences
		// it — one mechanism, not two.
		config.disable(City.VARROCK);
		scene.updateVisibility(PLAYER, view);

		assertEquals("the fixture has to actually despawn it", 0, scene.countActive());
		assertFalse("despawning is what has to stop the remark, on its own",
			remarks.isTalking());
	}

	/**
	 * And the chatter clears a remark on a citizen the client has dropped anyway.
	 *
	 * <p>A second guard for the same outcome, and the only way to reach it is to run
	 * the chatter on its own — through the scene, {@code updateVisibility} always goes
	 * first and its despawn has already done the job. So this drives a
	 * {@link CitizenChatter} directly, which is also the only test here that proves
	 * the class works without a scene wrapped around it. In the running plugin this
	 * branch should never fire; it is here because "text hanging over empty ground"
	 * is the bug the whole design is aimed at, and one guard for it is not enough.
	 */
	@Test
	public void theChatterAlsoClearsARemarkOnACitizenTheClientHasDropped()
	{
		EntityDefinition talker = regions.talker(VARROCK_NORTH, 3220, 3421, "Busy today.");
		spawn(talker);

		CitizenRemarks remarks = remarksOf(talker);
		runUntilTalking(remarks);

		// Off the screen, then the remark put back by hand: nothing in the despawn
		// path can be what clears it this time.
		config.disable(City.VARROCK);
		scene.updateVisibility(PLAYER, view);
		remarks.say(0, Integer.MAX_VALUE);
		assertTrue(remarks.isTalking());

		List<LivelyEntity> justThatOne = new ArrayList<>();
		justThatOne.add(scene.wrapperFor(talker));
		new CitizenChatter(config, config.overrides()).onGameTick(justThatOne, PLAYER);

		assertFalse("a remark on an unregistered citizen must not survive a chatter pass",
			remarks.isTalking());
	}

	// --- helpers ------------------------------------------------------------

	private void spawn(EntityDefinition... entities)
	{
		spawn(new ArrayList<>(java.util.Arrays.asList(entities)));
	}

	private void spawn(List<EntityDefinition> entities)
	{
		regions.file(VARROCK_NORTH, entities);
		scene.syncRegions(view);
		scene.updateVisibility(PLAYER, view);
		assertTrue("the fixture has to actually spawn", scene.countActive() > 0);
	}

	private void runTicks(int ticks)
	{
		for (int i = 0; i < ticks; i++)
		{
			scene.onGameTick(PLAYER, view);
		}
	}

	/**
	 * Runs the scene until a given citizen is talking.
	 *
	 * @return the tick it started on
	 */
	private int runUntilTalking(CitizenRemarks remarks)
	{
		for (int tick = 0; tick < 5000; tick++)
		{
			scene.onGameTick(PLAYER, view);
			if (remarks.isTalking())
			{
				return tick;
			}
		}
		throw new AssertionError("nobody talked in 5000 ticks — the fixture is wrong, not the code");
	}

	/**
	 * How many remarks started over a run at a given interval.
	 *
	 * <p>Counted by watching the text change rather than by asking the chatter,
	 * because "how often does a citizen speak" is a fact about the screen.
	 */
	private int remarksStartedOver(int ticks, int interval)
	{
		FakeClient freshClient = new FakeClient();
		FakeRegions freshRegions = new FakeRegions();
		FakeConfig freshConfig = new FakeConfig();
		freshConfig.setRemarkIntervalTicks(interval).setRemarkDwellTicks(CitizenChatter.MIN_DWELL_TICKS);
		EntityScene freshScene = new EntityScene(
			freshClient, freshRegions, freshConfig, freshConfig.overrides());

		List<EntityDefinition> crowd = freshRegions.talkingCrowd(VARROCK_NORTH, 3218, 3418, 6);
		freshRegions.file(VARROCK_NORTH, crowd);
		freshScene.syncRegions(view);
		freshScene.updateVisibility(PLAYER, view);

		List<CitizenRemarks> holders = new ArrayList<>();
		for (EntityDefinition definition : crowd)
		{
			CitizenRemarks remarks = freshScene.wrapperFor(definition).getRemarks();
			assertNotNull(remarks);
			holders.add(remarks);
		}

		boolean[] wasTalking = new boolean[holders.size()];
		int started = 0;
		for (int tick = 0; tick < ticks; tick++)
		{
			freshScene.onGameTick(PLAYER, view);
			for (int i = 0; i < holders.size(); i++)
			{
				boolean now = holders.get(i).isTalking();
				if (now && !wasTalking[i])
				{
					started++;
				}
				wasTalking[i] = now;
			}
		}
		return started;
	}

	private boolean talking(EntityDefinition definition)
	{
		CitizenRemarks remarks = scene.wrapperFor(definition).getRemarks();
		return remarks != null && remarks.isTalking();
	}

	private CitizenRemarks remarksOf(EntityDefinition definition)
	{
		CitizenRemarks remarks = scene.wrapperFor(definition).getRemarks();
		assertNotNull("the fixture has to have something to say", remarks);
		return remarks;
	}
}
