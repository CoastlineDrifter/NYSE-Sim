package Bots;
import Engine.*;
import Banking.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Market Maker Bot - Provides liquidity by placing buy and sell orders around current price
 */
class MarketMakerBot {
    private Portfolio portfolio;
    private OrderBook orderBook;
    private String symbol;
    private double spread;
    private int orderSize;
    private List<Integer> activeOrders;
    private ScheduledExecutorService scheduler;

    public MarketMakerBot(String botName, double initialCash, OrderBook orderBook, double spread, int orderSize) {
        this.portfolio = new Portfolio(initialCash, botName);
        this.orderBook = orderBook;
        this.symbol = orderBook.getSymbol();
        this.spread = spread;
        this.orderSize = orderSize;
        this.activeOrders = new ArrayList<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::updateOrders, 0, 5, TimeUnit.SECONDS);
    }

    private void updateOrders() {
        try {
            double currentPrice = orderBook.getCurrentPrice();

            // Cancel existing orders
            cancelAllOrders();

            // Place new buy and sell orders around current price
            double buyPrice = currentPrice - (spread / 2);
            double sellPrice = currentPrice + (spread / 2);

            // Place buy order
            if (portfolio.getAvailableCash() >= buyPrice * orderSize) {
                Order buyOrder = new Order(symbol, Order.Type.BUY, Order.Side.LIMIT, orderSize, buyPrice, portfolio);
                orderBook.placeBuyOrder(buyOrder);
                activeOrders.add(buyOrder.getId());
            }

            // Place sell order (if we have stock or allow short selling)
            Order sellOrder = new Order(symbol, Order.Type.SELL, Order.Side.LIMIT, orderSize, sellPrice, portfolio);
            orderBook.placeSellOrder(sellOrder);
            activeOrders.add(sellOrder.getId());

        } catch (Exception e) {
            System.out.println("MarketMaker error: " + e.getMessage());
        }
    }

    private void cancelAllOrders() {
        for (Integer orderId : activeOrders) {
            orderBook.cancelBuyOrder(orderId);
            orderBook.cancelSellOrder(orderId);
        }
        activeOrders.clear();
    }

    public void stop() {
        cancelAllOrders();
        scheduler.shutdown();
    }
}
