/*
 * Copyright (c) 2014 Red Hat, Inc. and others
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 */

package io.vertx.config.tests.spi;

import io.vertx.config.impl.spi.JsonConfigStoreFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class JsonConfigStoreTest extends ConfigStoreTestBase {

  private static final String JSON = "{\n" +
      "  \"key\": \"value\",\n" +
      "  \"sub\": {\n" +
      "    \"foo\": \"bar\"\n" +
      "  },\n" +
      "  \"array\": [\n" +
      "    1,\n" +
      "    2,\n" +
      "    3\n" +
      "  ],\n" +
      "  \"int\": 5,\n" +
      "  \"float\": 25.3,\n" +
      "  \"true\": true,\n" +
      "  \"false\": false\n" +
      "}";

  @Before
  public void init() {
    factory = new JsonConfigStoreFactory();
  }


  @Test
  public void testWithConfiguration(TestContext tc) {
    Async async = tc.async();
    store = factory.create(vertx, new JsonObject(JSON));

    getJsonConfiguration(vertx, store, ar -> {
      ConfigChecker.check(ar);
      async.complete();
    });
  }

  @Test
  public void testName() {
    assertThat(factory.name()).isNotNull().isEqualTo("json");
  }

}
