package Bots;
import Engine.*;
import Banking.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class MomentumBot {
    private Portfolio portfolio;
    private OrderBook orderBook;
    private String symbol;
    private double lastPrice;
    private double priceChangeThreshold;
    private int orderSize;
    private ScheduledExecutorService scheduler;
    private boolean hasPosition;

    public MomentumBot(String botName, double initialCash, OrderBook orderBook, double threshold, int orderSize) {
        this.portfolio = new Portfolio(initialCash, botName);
        this.orderBook = orderBook;
        this.symbol = orderBook.getSymbol();
        this.lastPrice = orderBook.getCurrentPrice();
        this.priceChangeThreshold = threshold;
        this.orderSize = orderSize;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.hasPosition = false;
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::checkMomentum, 0, 3, TimeUnit.SECONDS);
    }

    private void checkMomentum() {
        try {
            double currentPrice = orderBook.getCurrentPrice();
            double priceChange = (currentPrice - lastPrice) / lastPrice;

            // Buy on upward momentum
            if (priceChange > priceChangeThreshold && !hasPosition && portfolio.getAvailableCash() >= currentPrice * orderSize) {
                Order buyOrder = new Order(symbol, Order.Type.BUY, Order.Side.MARKET, orderSize, 0, portfolio);
                orderBook.placeBuyOrder(buyOrder);
                hasPosition = true;
                System.out.println("Momentum Bot: Buying on upward momentum");
            }
            // Sell on downward momentum
            else if (priceChange < -priceChangeThreshold && hasPosition) {
                Order sellOrder = new Order(symbol, Order.Type.SELL, Order.Side.MARKET, orderSize, 0, portfolio);
                orderBook.placeSellOrder(sellOrder);
                hasPosition = false;
                System.out.println("Momentum Bot: Selling on downward momentum");
            }

            lastPrice = currentPrice;
        } catch (Exception e) {
            System.out.println("Momentum Bot error: " + e.getMessage());
        }
    }

    public void stop() {
        scheduler.shutdown();
    }
}