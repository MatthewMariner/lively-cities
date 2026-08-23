package com.matthewmariner.livelycities;

import com.google.gson.Gson;
import net.runelite.http.api.RuneLiteAPI;

/**
 * Hands the tests the exact {@link Gson} instance RuneLite binds in Guice.
 *
 * <p>{@code RuneLiteModule} does {@code bind(Gson.class).toInstance(RuneLiteAPI.GSON)},
 * so this is not an approximation of the injected Gson — it is the same object.
 * That matters: a locally-built Gson could differ in naming policy or leniency
 * and the loader tests would then be verifying a parser the plugin never uses.
 * It also keeps {@code new Gson(} — a Plugin Hub disallowed API — out of the
 * repository entirely, test sources included.
 */
final class TestGson
{
	private TestGson()
	{
	}

	static Gson injected()
	{
		return RuneLiteAPI.GSON;
	}
}
