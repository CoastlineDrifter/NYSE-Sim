package Bots;
import Engine.*;
import Banking.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BotManager {
    private List<Object> bots;

    public BotManager() {
        this.bots = new ArrayList<>();
    }

    public void addMarketMaker(String name, double cash, OrderBook orderBook, double spread, int size) {
        MarketMakerBot bot = new MarketMakerBot(name, cash, orderBook, spread, size);
        bots.add(bot);
        bot.start();
        System.out.println("Started Market Maker Bot: " + name);
    }

    public void addMomentumBot(String name, double cash, OrderBook orderBook, double threshold, int size) {
        MomentumBot bot = new MomentumBot(name, cash, orderBook, threshold, size);
        bots.add(bot);
        bot.start();
        System.out.println("Started Momentum Bot: " + name);
    }

    public void addMeanReversionBot(String name, double cash, OrderBook orderBook, double threshold, int size) {
        MeanReversionBot bot = new MeanReversionBot(name, cash, orderBook, threshold, size);
        bots.add(bot);
        bot.start();
        System.out.println("Started Mean Reversion Bot: " + name);
    }

    public void addRandomBot(String name, double cash, OrderBook orderBook, int maxSize) {
        RandomBot bot = new RandomBot(name, cash, orderBook, maxSize);
        bots.add(bot);
        bot.start();
        System.out.println("Started Random Bot: " + name);
    }

    public void addScalpingBot(String name, double cash, OrderBook orderBook, int size) {
        ScalpingBot bot = new ScalpingBot(name, cash, orderBook, size);
        bots.add(bot);
        bot.start();
        System.out.println("Started Scalping Bot: " + name);
    }

    public void stopAllBots() {
        for (Object bot : bots) {
            try {
                if (bot instanceof MarketMakerBot) ((MarketMakerBot) bot).stop();
                else if (bot instanceof MomentumBot) ((MomentumBot) bot).stop();
                else if (bot instanceof MeanReversionBot) ((MeanReversionBot) bot).stop();
                else if (bot instanceof RandomBot) ((RandomBot) bot).stop();
                else if (bot instanceof ScalpingBot) ((ScalpingBot) bot).stop();
            } catch (Exception e) {
                System.out.println("Error stopping bot: " + e.getMessage());
            }
        }
        bots.clear();
        System.out.println("All bots stopped");
    }
}