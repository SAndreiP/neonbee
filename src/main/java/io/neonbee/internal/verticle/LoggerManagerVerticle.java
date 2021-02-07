package io.neonbee.internal.verticle;

import static io.neonbee.internal.verticle.LoggerConfiguration.getLoggerConfigurations;
import static io.vertx.core.Future.succeededFuture;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.base.Strings;

import io.neonbee.NeonBeeDeployable;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataMap;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * This is a system verticle, which will be deployed on each NeonBee instance.
 *
 * This verticle is responsible for returning the logging settings of the current NeonBee instance, or setting a new
 * logger value to all instances
 */
@SuppressWarnings({ "PMD.MoreThanOneLogger", "PMD.AvoidInstantiatingObjectsInLoops" })
@NeonBeeDeployable(namespace = NeonBeeDeployable.NEONBEE_NAMESPACE)
public class LoggerManagerVerticle extends DataVerticle<JsonArray> {
    public static final String QUALIFIED_NAME =
            DataVerticle.createQualifiedName(NeonBeeDeployable.NEONBEE_NAMESPACE, LoggerManagerVerticle.NAME);

    /**
     * Query parameter to only read the configuration of certain the logger names, separated by comma, semicolon or
     * space.
     */
    public static final String QUERY_PARAMETER_LOGGERS = "loggers";

    /**
     * Query parameter to limit setting the log level to the current cluster node, without distributing it via the
     * cluster manager.
     */
    public static final String QUERY_PARAMETER_LOCAL = "local";

    private static final String NAME = "LogLevel";

    private static final String EVENT_BUS_CHANGE_LOG_LEVEL_ADDRESS =
            LoggerManagerVerticle.class.getSimpleName() + "ChangeLogLevel";

    private static final String PARAMETER_DELIMITERS = "[,; ]";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void start(Promise<Void> promise) {
        Promise<Void> superPromise = Promise.promise();
        super.start(superPromise);

        superPromise.future().compose(nothing -> {
            vertx.eventBus().consumer(EVENT_BUS_CHANGE_LOG_LEVEL_ADDRESS, message -> {
                ((JsonArray) message.body()).stream().map(JsonObject.class::cast).map(LoggerConfiguration::fromJson)
                        .forEach(LoggerConfiguration::applyConfiguredLevel);
            });

            return succeededFuture();
        }).<Void>mapEmpty().onComplete(promise);
    }

    @Override
    public Future<JsonArray> retrieveData(DataQuery query, DataMap require, DataContext context) {
        Stream<LoggerConfiguration> loggerConfigurationStream;

        String loggersParameter = query.getParameter(QUERY_PARAMETER_LOGGERS);
        if (!Strings.isNullOrEmpty(loggersParameter)) {
            String[] loggers = loggersParameter.split(PARAMETER_DELIMITERS);
            loggerConfigurationStream = Arrays.stream(loggers).map(LoggerConfiguration::getLoggerConfiguration);
        } else {
            loggerConfigurationStream = getLoggerConfigurations().stream();
        }

        return succeededFuture(
                new JsonArray(loggerConfigurationStream.map(LoggerConfiguration::toJson).collect(Collectors.toList())));
    }

    @Override
    public Future<JsonArray> updateData(DataQuery query, DataContext context) {
        vertx.eventBus().publish(EVENT_BUS_CHANGE_LOG_LEVEL_ADDRESS,
                Optional.ofNullable(query.getBody()).map(Buffer::toJsonArray).orElse(new JsonArray()),
                new DeliveryOptions()
                        .setLocalOnly(Boolean.toString(true).equals(query.getParameter(QUERY_PARAMETER_LOCAL))));
        return succeededFuture();
    }
}
