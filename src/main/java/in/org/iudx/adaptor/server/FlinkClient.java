package in.org.iudx.adaptor.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.multipart.MultipartForm;
import static in.org.iudx.adaptor.server.util.Constants.*;

public class FlinkClient {

  private static final Logger LOGGER = LogManager.getLogger(FlinkClient.class);
  private WebClient client;
  private JsonObject flinkOptions;
  RespBuilder response = new RespBuilder();

  public FlinkClient(Vertx vertx, JsonObject flinkOptions) {
    this.client = WebClient.create(vertx);
    this.flinkOptions = flinkOptions;
  }

  /**
   * Handles the submission of Jar to the Flink Cluster.
   * 
   * @param request
   * @param handler
   * @return jsonObject response
   */
  public FlinkClient submitJar(JsonObject request, Handler<AsyncResult<JsonObject>> handler) {

    Future<JsonObject> future = httpPostFormAsync(request);
    future.onComplete(handler);
    return this;
  }

  /**
   * Handles the running of Job using existing Jar.
   * 
   * @param request
   * @param handler
   * @return jsonObject response
   */
  public FlinkClient submitJob(JsonObject request, Handler<AsyncResult<JsonObject>> handler) {

    Future<JsonObject> future = httpPostAsync(request, HttpMethod.POST);
    future.onComplete(resHandler -> {
      String jarId = request.getString(ID, "");
      if (resHandler.succeeded()) {
        handler.handle(Future.succeededFuture(
            response.withStatus(SUCCESS)
                    .withResult(jarId, POST, SUCCESS, resHandler.result().getString("jobid"))
                    .getJsonResponse()));
      } else if (resHandler.failed()) {
        handler.handle(Future.failedFuture(
            response.withStatus(ERROR)
                    .withResult(jarId, POST, FAILED)
                    .getResponse()));
      }
    });
    return this;
  }

  /**
   * Handle the operation of getting jar(s) details from Flink cluster.
   * 
   * @param request
   * @param handler
   * @return jsonObject response
   */
  public FlinkClient getJarDetails(JsonObject request, Handler<AsyncResult<JsonObject>> handler) {

    Future<JsonObject> future = httpGenAsync(request, HttpMethod.GET);
    future.onComplete(resHandler -> {
      if (resHandler.succeeded()) {
        JsonObject result = resHandler.result();
        if (result.containsKey(PLAN)) {
          handler.handle(Future.succeededFuture(
              response.withStatus(SUCCESS)
                      .withResult(resHandler.result().getJsonObject(PLAN))
                      .getJsonResponse()));
        } else if (result.containsKey(FILES)) {
          handler.handle(Future.succeededFuture(
              response.withStatus(SUCCESS)
                      .withResult(resHandler.result().getJsonArray(FILES))
                      .getJsonResponse()));
        }

      } else if (resHandler.failed()) {
        handler.handle(Future.failedFuture(
            response.withStatus(ERROR)
                    .withResult(request.getString(ID), GET, FAILED)
                    .getResponse()));
      }
    });
    return this;
  }

  /**
   * Deletes the submitted jar(s).
   * 
   * @param request
   * @param handler
   * @return response
   */
  public FlinkClient deleteItems(JsonObject request, Handler<AsyncResult<JsonObject>> handler) {

    String jarId = request.getString(ID, "");
    JsonArray idDeleted = new JsonArray();

    if (!jarId.isEmpty()) {
      httpGenAsync(request, HttpMethod.DELETE).onComplete(resHandler -> {
        if (resHandler.succeeded()) {
          handler.handle(Future.succeededFuture(
              response.withStatus(SUCCESS)
                      .withResult(jarId, DELETE, SUCCESS)
                      .getJsonResponse()));
          return;
        } else if (resHandler.failed()) {
          handler.handle(Future.failedFuture(
              response.withStatus(ERROR)
                      .withResult(jarId, DELETE, FAILED)
                      .getResponse()));
          return;
        }
      });
    } else {
      Future<JsonObject> future = httpGenAsync(request, HttpMethod.GET);
      future.onComplete(getHandler -> {
        if (!getHandler.result().getJsonArray(FILES).isEmpty()) {
          JsonArray allJars = getHandler.result().getJsonArray(FILES);
          allJars.forEach(entry -> {
            JsonObject jar = (JsonObject) entry;
            idDeleted.add(jar.getValue(ID));
            JsonObject reqBody = new JsonObject().put(URI, JARS + "/" + jar.getString(ID));
            httpGenAsync(reqBody, HttpMethod.DELETE).onComplete(resHandler -> {
              if (resHandler.failed()) {
                LOGGER.error("Error: Issue in deletion Jar, ID :" + jar.getString(ID));
              }
            });
          });

          handler.handle(Future.succeededFuture(
              response.withStatus(SUCCESS)
                      .withResult(idDeleted, DELETE, SUCCESS)
                      .getJsonResponse()));
        } else {
          handler.handle(Future.succeededFuture(
              response.withStatus(SUCCESS)
                      .withResult(idDeleted, DELETE, SUCCESS, "nothing to delete")
                      .getJsonResponse()));
        }
      });
    }
    return this;
  }

  /**
   * Get the details of job(s).
   * 
   * @param request
   * @param handler
   * @return response
   */
  public FlinkClient getJobDetails(JsonObject request, Handler<AsyncResult<JsonObject>> handler) {

    Future<JsonObject> future = httpGenAsync(request, HttpMethod.GET);
    future.onComplete(resHandler -> {
      if (resHandler.succeeded()) {
        JsonObject result = resHandler.result();
        if (result.containsKey(JOBS)) {
          handler.handle(Future.succeededFuture(
              response.withStatus(SUCCESS)
                      .withResult(resHandler.result().getJsonArray(JOBS))
                      .getJsonResponse()));
        } else {
          handler.handle(Future.succeededFuture(
              response.withStatus(SUCCESS)
                      .withResult(resHandler.result())
                      .getJsonResponse()));
        }
      } else if (resHandler.failed()) {
        handler.handle(Future.failedFuture(
            response.withStatus(ERROR)
                    .withResult(request.getString(ID), GET, FAILED)
                    .getResponse()));
      }
    });
    return this;
  }

  /**
   * Performs POST Multipart/Form request to Flink Cluster.
   * 
   * @param requestBody
   * @return promise
   */
  private Future<JsonObject> httpPostFormAsync(JsonObject requestBody) {

    Promise<JsonObject> promise = Promise.promise();
    RequestOptions options = new RequestOptions(flinkOptions);
    options.setURI(requestBody.getString(URI));
    MultipartForm bodyForm = MultipartForm.create();

    bodyForm.binaryFileUpload(
        requestBody.getString(NAME), requestBody.getString(NAME),
        requestBody.getString(PATH), MULTIPART_FORM_DATA);

    client.request(HttpMethod.POST, options).sendMultipartForm(bodyForm, reqHandler -> {
      if (reqHandler.succeeded()) {
        LOGGER.debug("Info: FLink upload Completed");
        promise.complete(reqHandler.result().bodyAsJsonObject());
      } else if (reqHandler.failed()) {
        LOGGER.error("Error: Flink upload Failed; " + reqHandler.cause());
        promise.fail(reqHandler.cause());
      }
    });
    return promise.future();
  }

  /**
   * Future to handles http post request to Flink Cluster.
   * 
   * @param requestBody
   * @param method
   * @return promise
   */
  private Future<JsonObject> httpPostAsync(JsonObject requestBody, HttpMethod method) {

    Promise<JsonObject> promise = Promise.promise();
    RequestOptions options = new RequestOptions(flinkOptions);
    options.setURI(requestBody.getString(URI));

    client.request(method, options).sendJsonObject(requestBody.getJsonObject(DATA), reqHandler -> {
      if (reqHandler.succeeded()) {
        if (reqHandler.result().statusCode() == 200) {
          LOGGER.debug("Info: Flink request completed");
          promise.complete(reqHandler.result().bodyAsJsonObject());
          return;
        } else {
          LOGGER.error("Error: Flink request failed; " + reqHandler.result().bodyAsString());
          promise.fail(reqHandler.result().bodyAsString());
          return;
        }
      } else if (reqHandler.failed()) {
        LOGGER.debug("Error: Flink request failed; " + reqHandler.cause().getMessage());
        promise.fail(reqHandler.cause());
        return;
      }
    });
    return promise.future();
  }

  /**
   * Future to handle HTTP requests- GET, DELETE.
   * 
   * @param requestBody
   * @param method
   * @return promise
   */
  private Future<JsonObject> httpGenAsync(JsonObject requestBody, HttpMethod method) {

    Promise<JsonObject> promise = Promise.promise();
    RequestOptions options = new RequestOptions(flinkOptions);
    options.setURI(requestBody.getString(URI));

    client.request(method, options).send(reqHandler -> {
      if (reqHandler.succeeded()) {
        if (reqHandler.result().statusCode() == 200) {
          LOGGER.debug("Info: Flink request completed");
          promise.complete(reqHandler.result().bodyAsJsonObject());
          return;
        } else {
          LOGGER.error("Error: Flink request failed; " + reqHandler.result().bodyAsString());
          promise.fail(reqHandler.result().bodyAsString());
          return;
        }
      } else if (reqHandler.failed()) {
        LOGGER.error("Error: Flink request failed; " + reqHandler.cause());
        promise.fail(reqHandler.cause());
        return;
      }
    });
    return promise.future();
  }


  /**
   * RespBuilder Response Message builder for search APIs
   */
  private class RespBuilder {
    private JsonObject response = new JsonObject();

    public RespBuilder withStatus(String status) {
      response.put(STATUS, status);
      return this;
    }

    public RespBuilder withDescription(String description) {
      response.put(DESC, description);
      return this;
    }

    public RespBuilder withResult(String id, String method, String status) {
      JsonObject resultAttrs = new JsonObject().put(ID, id).put(METHOD, method).put(STATUS, status);
      response.put(RESULTS, new JsonArray().add(resultAttrs));
      return this;
    }

    public RespBuilder withResult(JsonArray id, String method, String status) {
      JsonObject resultAttrs = new JsonObject().put(ID, id).put(METHOD, method).put(STATUS, status);
      response.put(RESULTS, new JsonArray().add(resultAttrs));
      return this;
    }

    public RespBuilder withResult(String id, String method, String status, String description) {
      JsonObject resultAttrs = new JsonObject().put(ID, id).put(METHOD, method).put(STATUS, status)
          .put(DESC, description);
      response.put(RESULTS, new JsonArray().add(resultAttrs));
      return this;
    }

    public RespBuilder withResult(JsonArray id, String method, String status, String description) {
      JsonObject resultAttrs = new JsonObject().put(ID, id).put(METHOD, method).put(STATUS, status)
          .put(DESC, description);
      response.put(RESULTS, new JsonArray().add(resultAttrs));
      return this;
    }

    public RespBuilder withResult(JsonObject results) {
      response.put(RESULTS, new JsonArray().add(results));
      return this;
    }
    
    public RespBuilder withResult(JsonArray results) {
      response.put(RESULTS, results);
      return this;
    }

    public JsonObject getJsonResponse() {
      return response;
    }

    public String getResponse() {
      return response.toString();
    }
  }
}
