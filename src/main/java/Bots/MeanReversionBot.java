package Bots;
import Engine.*;
import Banking.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class MeanReversionBot {
    private Portfolio portfolio;
    private OrderBook orderBook;
    private String symbol;
    private double baselinePrice;
    private double reversionThreshold;
    private int orderSize;
    private ScheduledExecutorService scheduler;
    private Queue<Double> priceHistory;
    private int historySize = 10;

    public MeanReversionBot(String botName, double initialCash, OrderBook orderBook, double threshold, int orderSize) {
        this.portfolio = new Portfolio(initialCash, botName);
        this.orderBook = orderBook;
        this.symbol = orderBook.getSymbol();
        this.baselinePrice = orderBook.getCurrentPrice();
        this.reversionThreshold = threshold;
        this.orderSize = orderSize;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.priceHistory = new LinkedList<>();
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::checkReversion, 0, 4, TimeUnit.SECONDS);
    }

    private void checkReversion() {
        try {
            double currentPrice = orderBook.getCurrentPrice();

            // Update price history and baseline
            priceHistory.offer(currentPrice);
            if (priceHistory.size() > historySize) {
                priceHistory.poll();
            }

            // Calculate moving average as baseline
            if (priceHistory.size() >= historySize) {
                baselinePrice = priceHistory.stream().mapToDouble(Double::doubleValue).average().orElse(currentPrice);
            }

            double deviation = (currentPrice - baselinePrice) / baselinePrice;

            // Buy when price is significantly below baseline
            if (deviation < -reversionThreshold && portfolio.getAvailableCash() >= currentPrice * orderSize) {
                Order buyOrder = new Order(symbol, Order.Type.BUY, Order.Side.MARKET, orderSize, 0, portfolio);
                orderBook.placeBuyOrder(buyOrder);
                System.out.println("Mean Reversion Bot: Buying at low price");
            }
            // Sell when price is significantly above baseline
            else if (deviation > reversionThreshold && portfolio.getAvailableStock(symbol) >= orderSize) {
                Order sellOrder = new Order(symbol, Order.Type.SELL, Order.Side.MARKET, orderSize, 0, portfolio);
                orderBook.placeSellOrder(sellOrder);
                System.out.println("Mean Reversion Bot: Selling at high price");
            }

        } catch (Exception e) {
            System.out.println("Mean Reversion Bot error: " + e.getMessage());
        }
    }

    public void stop() {
        scheduler.shutdown();
    }
}
