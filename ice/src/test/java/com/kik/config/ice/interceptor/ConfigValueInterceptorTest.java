/*
 * Copyright 2016 Kik Interactive, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.kik.config.ice.interceptor;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.kik.config.ice.ConfigConfigurator;
import com.kik.config.ice.ConfigSystem;
import com.kik.config.ice.ExplicitBindingModule;
import com.kik.config.ice.annotations.DefaultValue;
import com.kik.config.ice.source.DebugDynamicConfigSource;
import java.util.Optional;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class ConfigValueInterceptorTest
{
    private static final String cfgName1 = "com.kik.config.ice.interceptor.ConfigValueInterceptorTest$Example$Config.myValue";

    //<editor-fold defaultstate="collapsed" desc="example class">
    @Singleton
    private static class Example
    {
        public interface Config
        {
            @DefaultValue("asdf")
            String myValue();
        }

        @VisibleForTesting
        @Inject
        public Config config;

        public static Module module()
        {
            return new AbstractModule()
            {
                @Override
                protected void configure()
                {
                    bind(Example.class);
                    install(ConfigSystem.configModule(Config.class));
                }
            };
        }
    }
    //</editor-fold>

    @Inject
    DebugDynamicConfigSource debugSource;

    @Inject
    Example example;

    @Test(timeout = 5000)
    public void testInterceptedValue() throws Exception
    {
        Injector injector = Guice.createInjector(
            new ExplicitBindingModule(),
            ConfigConfigurator.testModules(),
            Example.module(),
            ExampleInterceptor.module(5),
            UnwantedInterceptor.module(10));

        injector.injectMembers(this);

        assertEquals("asdf", example.config.myValue());

        debugSource.fireEvent(cfgName1, Optional.of("abcd"));
        assertEquals("abcdefgh", example.config.myValue());
    }
}
