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

package io.vertx.config.tests;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
@RunWith(VertxUnitRunner.class)
public class ScanAndBroadcastTest {

  private Vertx vertx;
  private JsonObject http;
  private HttpServer server;
  private ConfigRetriever retriever;

  @Before
  public void setUp(TestContext tc) throws Exception {
    vertx = Vertx.vertx();
    vertx.exceptionHandler(tc.exceptionHandler());

    http = new JsonObject();

    vertx.createHttpServer()
      .requestHandler(request -> {
        if (request.path().endsWith("/conf")) {
          request.response().end(http.encodePrettily());
        }
      })
      .listen(8080)
      .await(20, TimeUnit.SECONDS);
  }

  @After
  public void tearDown() throws Exception {
    retriever.close();
    vertx.close().await(20, TimeUnit.SECONDS);
  }

  private static List<ConfigStoreOptions> stores() {
    List<ConfigStoreOptions> options = new ArrayList<>();
    options.add(new ConfigStoreOptions().setType("file")
      .setConfig(new JsonObject().put("path", "src/test/resources/file/regular.json")));
    options.add(new ConfigStoreOptions().setType("sys")
      .setConfig(new JsonObject().put("cache", false)));
    options.add(new ConfigStoreOptions().setType("http")
      .setConfig(new JsonObject()
        .put("host", "localhost")
        .put("port", 8080)
        .put("path", "/conf")));
    return options;
  }

  @Test
  public void testScanning(TestContext tc) {
    Async done = tc.async();
    vertx.runOnContext(v -> {
      retriever = ConfigRetriever.create(vertx,
        new ConfigRetrieverOptions().setScanPeriod(1000).setStores(stores()));

      AtomicReference<JsonObject> current = new AtomicReference<>();
      retriever.getConfig().onComplete(json -> {
        retriever.listen(change -> {
          if (current.get() != null && !current.get().equals(change.getPreviousConfiguration())) {
            throw new IllegalStateException("Previous configuration not correct");
          }
          current.set(change.getNewConfiguration());
        });
        current.set(json.result());
      });

      assertWaitUntil(() -> current.get() != null, x -> {
        current.set(null);
        http.put("some-key", "some-value");
        assertWaitUntil(() -> current.get() != null, x2 -> {
          assertThat(current.get().getString("some-key")).isEqualTo("some-value");
          done.complete();
        });
      });
    });
    done.awaitSuccess(20_000);
  }

  @Test
  public void testScanningWithBeforeAndAfterFunctions(TestContext tc) {
    Async done = tc.async();
    AtomicInteger before = new AtomicInteger();
    vertx.runOnContext(v -> {
      retriever = ConfigRetriever.create(vertx,
        new ConfigRetrieverOptions().setScanPeriod(1000).setStores(stores()))
      .setBeforeScanHandler(x -> before.incrementAndGet())
      .setConfigurationProcessor(json -> {
        if (json.containsKey("some-key")) {
          json.put("some-key", json.getString("some-key").toUpperCase());
        }
        return json;
      });

      AtomicReference<JsonObject> current = new AtomicReference<>();
      retriever.getConfig().onComplete(json -> {
        retriever.listen(change -> {
          if (current.get() != null && !current.get().equals(change.getPreviousConfiguration())) {
            throw new IllegalStateException("Previous configuration not correct");
          }
          current.set(change.getNewConfiguration());
        });
        current.set(json.result());
      });

      assertWaitUntil(() -> current.get() != null, x -> {
        current.set(null);
        http.put("some-key", "some-value");
        assertWaitUntil(() -> current.get() != null, x2 -> {
          assertThat(current.get().getString("some-key")).isEqualTo("SOME-VALUE");
          done.complete();
        });
      });
    });
    done.awaitSuccess(20_000);
    assertTrue(before.get() >= 1);
  }

  private void assertWaitUntil(Callable<Boolean> condition, Handler<AsyncResult<Void>> next) {
    assertWaitUntil(new AtomicInteger(), condition, next);
  }

  private void assertWaitUntil(AtomicInteger counter, Callable<Boolean> condition, Handler<AsyncResult<Void>> next) {
    boolean success;
    try {
      success = condition.call();
    } catch (Exception e) {
      success = false;
    }

    if (success) {
      next.handle(Future.succeededFuture());
    } else {
      if (counter.get() >= 10000) {
        next.handle(Future.failedFuture("timeout"));
      } else {
        counter.incrementAndGet();
        vertx.setTimer(10, l -> assertWaitUntil(counter, condition, next));
      }
    }
  }

  @Test
  public void testScanningWhenNoChanges(TestContext tc) {
    Async async = tc.async();
    vertx.runOnContext(v -> {
      retriever = ConfigRetriever.create(vertx,
        new ConfigRetrieverOptions().setScanPeriod(500).setStores(stores()));

      AtomicReference<JsonObject> current = new AtomicReference<>();
      retriever.getConfig().onComplete(json -> {
        retriever.listen(change -> {
          if (current.get() != null && !current.get().equals(change.getPreviousConfiguration())) {
            throw new IllegalStateException("Previous configuration not correct");
          }
          current.set(change.getNewConfiguration());
        });
        http.put("some-key", "some-value-2");
      });

      assertWaitUntil(() -> current.get() != null, r -> {
        if (r.failed()) {
          tc.fail(r.cause());
        } else {
          assertThat(current.get().getString("some-key")).isEqualTo("some-value-2");
          http.put("some-key", "some-value-2");
          current.set(null);

          vertx.setTimer(1000, l -> {
            assertThat(current.get()).isNull();
            async.complete();
          });
        }
      });
    });

  }

}
