package listeners.messages;

import com.slack.api.app_backend.events.payload.EventsApiPayload;
import com.slack.api.bolt.App;
import com.slack.api.bolt.context.builtin.EventContext;
import com.slack.api.bolt.handler.BoltEventHandler;
import com.slack.api.bolt.response.Response;
import com.slack.api.model.event.MessageEvent;
import java.util.HashMap;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import services.Recommendations;
import services.Reminders;
import services.WeatherService;

public class WeatherHandler implements BoltEventHandler<MessageEvent> {

    private static final Pattern WEATHER_PATTERN =
            Pattern.compile("\\b(weather|temperature|degree|forecast)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern THANK_YOU_PATTERN =
            Pattern.compile("\\b(thank you|thanks|thx|thnx|tnx)\\b", Pattern.CASE_INSENSITIVE);

    private final App app;

    // Hashmaps - track conversation state
    private final HashMap<String, Boolean> awaitingCityName = new HashMap<>();
    private final HashMap<String, Boolean> awaitingSunscreenRecommendation = new HashMap<>();

    public WeatherHandler(App app) {
        this.app = app;
    }

    @Override
    public Response apply(EventsApiPayload<MessageEvent> payload, EventContext ctx) {
        this.applyAsync(payload, ctx);
        return Response.ok();
    }

    public Future<?> applyAsync(EventsApiPayload<MessageEvent> payload, EventContext ctx) {
        return this.app.executorService().submit(() -> {
            try {
                var event = payload.getEvent();
                var message = event.getText();
                String userId = event.getUser();
                String botUserId = ctx.getBotUserId();

                if (message.contains("<@" + botUserId + ">")) {
                    message = message.replace("<@" + botUserId + ">", "").trim();

                    Matcher thanksMatcher = THANK_YOU_PATTERN.matcher(message);
                    if (thanksMatcher.find()) {
                        awaitingCityName.put(userId, false);
                        awaitingSunscreenRecommendation.put(userId, false);
                        ctx.say("You're welcome! Let me know if you need anything else.");
                        return;
                    }

                    if (awaitingSunscreenRecommendation.getOrDefault(userId, false)) {
                        if (message.trim().equalsIgnoreCase("yes")) {
                            String sunscreenRecommendations = Recommendations.getSunscreenRecommendations();
                            ctx.say(sunscreenRecommendations);
                        } else {
                            ctx.say("Enjoy your day and have fun!");
                        }
                        awaitingSunscreenRecommendation.put(userId, false);
                        return;
                    }

                    if (awaitingCityName.getOrDefault(userId, false)) {
                        String cityName = message.trim();
                        String weatherData = WeatherService.getWeatherForCity(cityName, userId);
                        ctx.say(weatherData);
                        ctx.say(Reminders.getDailyReminders());
                        ctx.say(Reminders.askForSunscreenRecommendation());
                        awaitingSunscreenRecommendation.put(userId, true);
                        awaitingCityName.put(userId, false);
                        return;
                    } else {
                        Matcher matcher = WEATHER_PATTERN.matcher(message);
                        if (matcher.find()) {
                            String promptMessage = "What city are you looking for?";
                            ctx.say(promptMessage);
                            awaitingCityName.put(userId, true);
                        }
                    }
                }
            } catch (Exception e) {

                e.printStackTrace();
            }
        });
    }
}
