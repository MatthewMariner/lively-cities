package com.matthewmariner.livelycities;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The hidden config string that stands in for the dynamic checkbox list RuneLite
 * does not have.
 *
 * <p>Its whole job is a round trip through a string, so that is what is tested:
 * write, read back, parse, and still hold the same uuids. Nothing here uses a
 * {@code Set<UUID>} field to short-circuit the serialisation, because the
 * serialisation is the only part that can be wrong.
 */
public class UuidSettingTest
{
	private static final String KEY = "hiddenCitizens";

	private static final UUID ONE = UUID.fromString("00000000-0000-4000-8000-000000000001");
	private static final UUID TWO = UUID.fromString("00000000-0000-4000-8000-000000000002");
	private static final UUID THREE = UUID.fromString("00000000-0000-4000-8000-000000000003");

	/**
	 * The store, and a record of every write.
	 *
	 * <p>The write log matters as much as the value: "a write that would produce the
	 * same string is skipped" is a real requirement — {@code ConfigManager} posts a
	 * {@code ConfigChanged} per write and this plugin answers each one with a full
	 * visibility pass — and it is invisible unless the writes are counted.
	 */
	private static final class Store
	{
		@Nullable
		private String value;

		private final List<String> writes = new ArrayList<>();

		private ConfigWriter writer()
		{
			return (key, newValue) ->
			{
				assertEquals("it must only ever write its own key", KEY, key);
				value = newValue;
				writes.add(String.valueOf(newValue));
			};
		}

		@Nullable
		private String read()
		{
			return value;
		}
	}

	@Test
	public void aUuidSurvivesTheRoundTripThroughTheString()
	{
		Store store = new Store();
		UuidSetting setting = new UuidSetting(KEY, store::read, store.writer());

		assertTrue(setting.add(ONE));
		assertEquals("the stored form is the canonical uuid string",
			ONE.toString(), store.read());

		// A second instance reading the same string is the real test: it has never
		// seen the add, so everything it knows came out of the bytes.
		UuidSetting reloaded = new UuidSetting(KEY, store::read, store.writer());
		assertTrue("a fresh reader has to find it", reloaded.contains(ONE));
		assertEquals(1, reloaded.size());
	}

	@Test
	public void severalUuidsRoundTripAndKeepTheirOrder()
	{
		Store store = new Store();
		UuidSetting setting = new UuidSetting(KEY, store::read, store.writer());

		setting.add(ONE);
		setting.add(TWO);
		setting.add(THREE);

		assertEquals(ONE + "," + TWO + "," + THREE, store.read());

		UuidSetting reloaded = new UuidSetting(KEY, store::read, store.writer());
		assertEquals(3, reloaded.size());
		assertTrue(reloaded.contains(TWO));
	}

	/**
	 * A profile can be hand-edited, synced from another install, or written by a
	 * later version. One bad character must not lose every hide the user ever set,
	 * and must certainly not throw inside a visibility pass that runs every tick.
	 */
	@Test
	public void junkBetweenTheCommasIsDroppedAndTheRestSurvives()
	{
		Store store = new Store();
		store.value = "  " + ONE + " ,not-a-uuid,, " + TWO + ",42,";

		UuidSetting setting = new UuidSetting(KEY, store::read, store.writer());

		assertEquals("both real uuids, neither of the three junk entries", 2, setting.size());
		assertTrue(setting.contains(ONE));
		assertTrue(setting.contains(TWO));
		assertTrue("reading must not rewrite the setting", store.writes.isEmpty());
	}

	@Test
	public void anAbsentOrEmptyStringIsAnEmptySet()
	{
		Store absent = new Store();
		assertEquals(0, new UuidSetting(KEY, absent::read, absent.writer()).size());

		Store blank = new Store();
		blank.value = "   ";
		assertEquals(0, new UuidSetting(KEY, blank::read, blank.writer()).size());
	}

	/**
	 * Clearing unsets the key rather than writing the empty string. A key left in
	 * the profile reads as a user override forever; "the user has no setting here"
	 * is the honest end state for a button.
	 */
	@Test
	public void clearingUnsetsTheKey()
	{
		Store store = new Store();
		UuidSetting setting = new UuidSetting(KEY, store::read, store.writer());
		setting.add(ONE);
		setting.add(TWO);

		assertTrue(setting.clear());
		assertNull("cleared means unset, not the empty string", store.read());
		assertEquals(0, setting.size());
		assertFalse(setting.contains(ONE));

		assertFalse("clearing an empty set writes nothing", setting.clear());
		assertEquals("so there is exactly one write for the two adds' worth of state",
			3, store.writes.size());
	}

	/**
	 * Adding the same uuid twice must not write twice. Every write posts a
	 * {@code ConfigChanged}, and this plugin answers each one by re-running the
	 * visibility pass over every entity in scope.
	 */
	@Test
	public void addingAUuidTwiceWritesOnce()
	{
		Store store = new Store();
		UuidSetting setting = new UuidSetting(KEY, store::read, store.writer());

		assertTrue(setting.add(ONE));
		assertFalse("the second add is a no-op", setting.add(ONE));
		assertEquals(1, store.writes.size());
		assertEquals(1, setting.size());
	}

	/**
	 * The parse is cached, and the cache has to notice the string changing
	 * underneath it — which is what happens when another instance writes, or when
	 * the user switches profile.
	 */
	@Test
	public void theCacheNoticesTheStringChangingUnderneathIt()
	{
		Store store = new Store();
		UuidSetting reader = new UuidSetting(KEY, store::read, store.writer());
		assertEquals(0, reader.size());

		// Somebody else writes. The reader has already parsed once, so this is the
		// case a naive "parse on first use" cache gets wrong.
		store.value = ONE + "," + TWO;
		assertEquals("the cache must re-parse when the value changes", 2, reader.size());

		store.value = null;
		assertEquals("including all the way back to nothing", 0, reader.size());
	}

	// --- taking one back out -------------------------------------------------

	/**
	 * Removing one uuid keeps the others, and keeps them through the string.
	 *
	 * <p>The whole reason {@code remove} exists: until the side panel there was only
	 * {@link UuidSetting#clear()}, so taking back one decision meant losing every other
	 * one. The assertion that matters is the second one — that the two who were not
	 * named are still there <b>after a re-parse</b>, which is where a remove that
	 * rewrote the string wrongly would show up. A fresh {@code UuidSetting} over the
	 * same store is what forces that re-parse; asking the same instance would be asking
	 * its primed cache.
	 */
	@Test
	public void removingOneKeepsTheRestThroughTheString()
	{
		Store store = new Store();
		UuidSetting setting = new UuidSetting(KEY, store::read, store.writer());

		setting.add(ONE);
		setting.add(TWO);
		setting.add(THREE);

		assertTrue("TWO was in the set", setting.remove(TWO));
		assertEquals(2, setting.size());

		UuidSetting reread = new UuidSetting(KEY, store::read, store.writer());
		assertEquals("and the string it wrote holds the other two", 2, reread.size());
		assertTrue(reread.contains(ONE));
		assertFalse(reread.contains(TWO));
		assertTrue(reread.contains(THREE));
	}

	/**
	 * Removing a uuid that is not there writes nothing.
	 *
	 * <p>The same rule {@code add} keeps in the other direction, and for the same
	 * reason: {@code ConfigManager} posts a {@code ConfigChanged} per write and this
	 * plugin answers each one with a full visibility pass over every entity in scope, so
	 * a write that changes no bytes is a pass over a hundred citizens for nothing.
	 */
	@Test
	public void removingSomebodyWhoIsNotThereWritesNothing()
	{
		Store store = new Store();
		UuidSetting setting = new UuidSetting(KEY, store::read, store.writer());

		setting.add(ONE);
		int writesBefore = store.writes.size();

		assertFalse(setting.remove(TWO));
		assertEquals("no write", writesBefore, store.writes.size());
		assertEquals(1, setting.size());

		assertFalse("nor on an empty set",
			new UuidSetting("mutedCitizens", () -> null, (key, value) ->
			{
				throw new AssertionError("an empty set must not be written to");
			}).remove(ONE));
	}

	/**
	 * Removing the last one unsets the key rather than storing an empty string.
	 *
	 * <p>{@link ConfigWriter}'s contract: a key left in the profile reads as a user
	 * override forever, and "the user has no setting here" is the honest end state.
	 * {@code clear()} already did this; {@code remove()} reaching the same state by a
	 * different route has to leave the profile in the same place, or a user who
	 * restored their citizens one at a time would end up with a profile subtly unlike
	 * one who pressed the button.
	 */
	@Test
	public void removingTheLastOneUnsetsTheKey()
	{
		Store store = new Store();
		UuidSetting setting = new UuidSetting(KEY, store::read, store.writer());

		setting.add(ONE);
		assertEquals(String.valueOf(ONE), store.read());

		assertTrue(setting.remove(ONE));
		assertNull("an empty set is stored as no setting at all", store.read());
		assertEquals("null", store.writes.get(store.writes.size() - 1));
		assertEquals(0, setting.size());
	}
}
