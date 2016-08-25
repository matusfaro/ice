package com.kik.config.ice.source;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.kik.config.ice.ConfigConfigurator;
import com.kik.config.ice.ConfigSystem;
import com.kik.config.ice.annotations.DefaultValue;
import java.time.Duration;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class DebugControllerTest
{

    public interface Config1
    {
        @DefaultValue("false")
        boolean enabled();

        @DefaultValue("123")
        long timeout();

        @DefaultValue("a test string")
        String connectionString();
    }

    public interface Config2
    {
        @DefaultValue("PT1H")
        Duration expiry();

        // TODO: Fix Generics
        // @DefaultValue("abc");
        // Optional<String> foo();
    }

    @Before
    public void setup()
    {
        Injector injector = Guice.createInjector(new AbstractModule()
        {
            @Override
            protected void configure()
            {
                install(ConfigConfigurator.testModules());
                install(ConfigSystem.configModule(Config1.class));
                install(ConfigSystem.configModule(Config2.class));

                // TODO: include this in the testModules method?
                bind(DebugController.class);
            }
        });

        injector.injectMembers(this);
    }

    @Inject
    private DebugController dc;

    @Inject
    private Config1 c1;

    @Inject
    private Config2 c2;

    @Test
    public void testDebugController()
    {
        // Check basic Config1 changes
        assertEquals(false, c1.enabled());
        assertEquals(123L, c1.timeout(), 123L);
        assertEquals("a test string", c1.connectionString());

        // NOTE: the identifying proxy is kept in a variable and re-used here, but keeping this in-line is a reasonable
        // and expected use case.
        Config1 c1Proxy = dc.id(Config1.class);
        dc.set(c1Proxy.enabled()).toValue(true);
        dc.set(c1Proxy.timeout()).toValue(10_000L);

        assertEquals(true, c1.enabled());
        assertEquals(10_000L, c1.timeout());

        // Check basic Config2 changes
        assertEquals(Duration.ofHours(1), c2.expiry());

        Config2 c2Proxy = dc.id(Config2.class);
        dc.set(c2Proxy.expiry()).toValue(Duration.ofSeconds(222));

        assertEquals(Duration.ofSeconds(222), c2.expiry());

        // Check Config1 reversions; Also check the new method id proxy is the same instance
        Config1 c1ProxyB = dc.id(Config1.class);
        // direct reference comparison is intended here
        assertTrue(c1Proxy == c1ProxyB);

        dc.set(dc.id(Config1.class).enabled()).toEmpty();
        assertEquals(false, c1.enabled());

        dc.set(dc.id(Config1.class).timeout()).toEmpty();
        assertEquals(123L, c1.timeout());

        dc.set(dc.id(Config1.class).connectionString()).toEmpty();
        assertEquals("a test string", c1.connectionString());
    }
}
