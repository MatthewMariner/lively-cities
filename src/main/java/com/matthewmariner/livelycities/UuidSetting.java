package com.matthewmariner.livelycities;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

/**
 * A set of entity uuids kept in one hidden config string.
 *
 * <p><b>Why a string and not a list of checkboxes.</b> RuneLite config items are
 * {@code @ConfigItem} annotations on interface methods — static declarations,
 * fixed at compile time. There is no dynamic checkbox list, and there could not
 * be one keyed on 129 uuids that ship in a data file. The established shape is
 * therefore a {@code hidden = true} string item plus a RUNELITE menu entry that
 * appends to it, and a visible "clear the list" item for the way back. This
 * class is that string, parsed.
 *
 * <p><b>The parse is cached on the raw string, and that is load-bearing rather
 * than an optimisation.</b> {@link EntityScene#updateVisibility} consults this
 * for every definition in scope on every game tick — 76 of them in Varrock — and
 * a config read is a proxy call into {@code ConfigManager}. Reading the raw value
 * once per pass and re-parsing only when the bytes actually changed is what keeps
 * "is this one hidden?" a hash lookup. The same reasoning as the
 * "read the dials once per pass, not once per entity" comment in that method.
 *
 * <p><b>Fail-soft, like everything else that reads authored or persisted
 * data.</b> A profile can be hand-edited, synced from another install, or written
 * by a future version of this plugin. Junk between the commas is dropped with one
 * log line per distinct string rather than throwing: the alternative is that one
 * bad character makes every hide the user ever set disappear, or worse, makes the
 * visibility pass throw once per tick.
 *
 * <p><b>Nothing here touches the client</b>, so it needs no client thread. The
 * write does reach {@code ConfigManager} through {@link ConfigWriter}, which is
 * thread-safe in the client and is called from the menu-click handler, i.e. from
 * the client thread anyway.
 */
@Slf4j
final class UuidSetting
{
	/** The separator in the stored string. Never appears in a UUID's toString. */
	private static final char SEPARATOR = ',';

	private final String key;
	private final Supplier<String> reader;
	private final ConfigWriter writer;

	/**
	 * The raw string {@link #cache} was built from, so a re-parse happens exactly
	 * when the value changed. {@code null} is a legitimate raw value (no setting
	 * at all), so "not parsed yet" is tracked separately rather than by
	 * {@code parsedFrom == null}.
	 */
	@Nullable
	private String parsedFrom;

	private boolean parsed;

	private Set<UUID> cache = Collections.emptySet();

	/**
	 * @param key    the {@code keyName} of the hidden {@code @ConfigItem} holding
	 *               this set
	 * @param reader the config getter for that item — a method reference, so the
	 *               interface stays the single declaration of the key's default
	 * @param writer how to persist a change
	 */
	UuidSetting(String key, Supplier<String> reader, ConfigWriter writer)
	{
		this.key = key;
		this.reader = reader;
		this.writer = writer;
	}

	/**
	 * @return the current set, unmodifiable. Cheap to call repeatedly: it re-reads
	 * the config value (one proxy call) and only re-parses when that value has
	 * changed. Callers that ask per entity should still hoist it out of the loop —
	 * see the class javadoc.
	 */
	Set<UUID> current()
	{
		String raw = reader.get();
		if (parsed && equalRaw(raw, parsedFrom))
		{
			return cache;
		}

		cache = parse(raw);
		parsedFrom = raw;
		parsed = true;
		return cache;
	}

	boolean contains(UUID uuid)
	{
		return current().contains(uuid);
	}

	/**
	 * Adds a uuid and persists the whole set.
	 *
	 * @return true if it was not already there — i.e. if anything was written. A
	 * write that would produce the same string is skipped: {@code ConfigManager}
	 * posts a {@code ConfigChanged} per {@code setConfiguration} call, and this
	 * plugin answers that by re-running the visibility pass, so a no-op write is a
	 * no-op pass for every entity in scope.
	 */
	boolean add(UUID uuid)
	{
		Set<UUID> existing = current();
		if (existing.contains(uuid))
		{
			return false;
		}

		Set<UUID> next = new LinkedHashSet<>(existing);
		next.add(uuid);
		store(next);
		return true;
	}

	/**
	 * Empties the set.
	 *
	 * @return true if there was anything to empty
	 */
	boolean clear()
	{
		if (current().isEmpty())
		{
			return false;
		}

		store(Collections.emptySet());
		return true;
	}

	int size()
	{
		return current().size();
	}

	String getKey()
	{
		return key;
	}

	/**
	 * Writes the set out and primes the cache with it.
	 *
	 * <p>Priming matters: the write is followed — synchronously, on this thread —
	 * by a {@code ConfigChanged} that makes the plugin re-run the visibility pass,
	 * and that pass has to see the new set. Waiting for the next {@link #current()}
	 * to re-read the config would also work, but only because
	 * {@code ConfigManager.setConfiguration} updates its in-memory properties
	 * before it posts; priming here means this class does not depend on that
	 * ordering.
	 */
	private void store(Set<UUID> uuids)
	{
		String serialised = serialise(uuids);
		cache = Collections.unmodifiableSet(uuids);
		parsedFrom = serialised;
		parsed = true;

		// An empty set is stored as "no setting", not as the empty string: see
		// ConfigWriter.write.
		writer.write(key, serialised.isEmpty() ? null : serialised);
	}

	private static String serialise(Set<UUID> uuids)
	{
		StringBuilder out = new StringBuilder();
		for (UUID uuid : uuids)
		{
			if (out.length() > 0)
			{
				out.append(SEPARATOR);
			}
			out.append(uuid);
		}
		return out.toString();
	}

	/**
	 * @return the uuids in {@code raw}, in the order they appear, ignoring blanks
	 * and anything that is not a uuid
	 */
	private Set<UUID> parse(@Nullable String raw)
	{
		if (raw == null || raw.trim().isEmpty())
		{
			return Collections.emptySet();
		}

		Set<UUID> out = new LinkedHashSet<>();
		int rejected = 0;
		for (String piece : raw.split(String.valueOf(SEPARATOR), -1))
		{
			String trimmed = piece.trim();
			if (trimmed.isEmpty())
			{
				continue;
			}

			try
			{
				out.add(UUID.fromString(trimmed));
			}
			catch (IllegalArgumentException e)
			{
				rejected++;
			}
		}

		if (rejected > 0)
		{
			// One line per distinct stored value, not one per entry and not one per
			// pass: the cache means this runs again only when the string changes.
			log.warn("Lively Cities: {} entr{} in the '{}' setting {} not a uuid and {} ignored; "
					+ "{} usable uuid(s) kept",
				rejected, rejected == 1 ? "y" : "ies", key,
				rejected == 1 ? "is" : "are", rejected == 1 ? "was" : "were", out.size());
		}

		return Collections.unmodifiableSet(out);
	}

	private static boolean equalRaw(@Nullable String a, @Nullable String b)
	{
		return a == null ? b == null : a.equals(b);
	}
}
