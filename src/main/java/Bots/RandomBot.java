package Bots;
import Engine.*;
import Banking.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class RandomBot {
    private Portfolio portfolio;
    private OrderBook orderBook;
    private String symbol;
    private int maxOrderSize;
    private ScheduledExecutorService scheduler;
    private Random random;

    public RandomBot(String botName, double initialCash, OrderBook orderBook, int maxOrderSize) {
        this.portfolio = new Portfolio(initialCash, botName);
        this.orderBook = orderBook;
        this.symbol = orderBook.getSymbol();
        this.maxOrderSize = maxOrderSize;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.random = new Random();
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::makeRandomTrade, 0, 8, TimeUnit.SECONDS);
    }

    private void makeRandomTrade() {
        try {
            double currentPrice = orderBook.getCurrentPrice();
            int orderSize = random.nextInt(maxOrderSize) + 1;

            if (random.nextBoolean()) {
                // Random buy
                if (portfolio.getAvailableCash() >= currentPrice * orderSize) {
                    double priceVariation = 0.95 + (random.nextDouble() * 0.1); // ±5% price variation
                    double orderPrice = currentPrice * priceVariation;

                    Order buyOrder = new Order(symbol, Order.Type.BUY, Order.Side.LIMIT, orderSize, orderPrice, portfolio);
                    orderBook.placeBuyOrder(buyOrder);
                }
            } else {
                // Random sell
                double priceVariation = 0.95 + (random.nextDouble() * 0.1);
                double orderPrice = currentPrice * priceVariation;

                Order sellOrder = new Order(symbol, Order.Type.SELL, Order.Side.LIMIT, orderSize, orderPrice, portfolio);
                orderBook.placeSellOrder(sellOrder);
            }

        } catch (Exception e) {
            System.out.println("Random Bot error: " + e.getMessage());
        }
    }

    public void stop() {
        scheduler.shutdown();
    }
}