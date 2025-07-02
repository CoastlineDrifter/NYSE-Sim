package Bots;

import Engine.*;
import Banking.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * High-Frequency Market Maker - Ultra-fast market making with tighter spreads
 */
class HFMarketMakerBot {
    private Portfolio portfolio;
    private OrderBook orderBook;
    private String symbol;
    private double minSpread;
    private int orderSize;
    private List<Integer> activeOrders;
    private ScheduledExecutorService scheduler;
    private double lastPrice;

    public HFMarketMakerBot(String botName, double initialCash, OrderBook orderBook, double minSpread, int orderSize) {
        this.portfolio = new Portfolio(initialCash, botName);
        this.orderBook = orderBook;
        this.symbol = orderBook.getSymbol();
        this.minSpread = minSpread;
        this.orderSize = orderSize;
        this.activeOrders = new ArrayList<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.lastPrice = orderBook.getCurrentPrice();
    }

    public void start() {
        // Run every 100ms for high frequency
        scheduler.scheduleAtFixedRate(this::updateOrdersHF, 0, 100, TimeUnit.MILLISECONDS);
    }

    private void updateOrdersHF() {
        try {
            double currentPrice = orderBook.getCurrentPrice();
            Double bestBid = orderBook.getBestBid();
            Double bestAsk = orderBook.getBestAsk();

            // Calculate dynamic spread based on volatility
            double priceChange = Math.abs(currentPrice - lastPrice) / lastPrice;
            double dynamicSpread = Math.max(minSpread, minSpread * (1 + priceChange * 10));

            // Aggressive pricing - get inside the spread
            double targetBuyPrice = bestAsk != null ? bestAsk - 0.01 : currentPrice - (dynamicSpread / 2);
            double targetSellPrice = bestBid != null ? bestBid + 0.01 : currentPrice + (dynamicSpread / 2);

            // Quick order replacement - only if price moved significantly
            if (Math.abs(currentPrice - lastPrice) > 0.05) {
                cancelAllOrders();

                // Place tight orders
                if (portfolio.getAvailableCash() >= targetBuyPrice * orderSize) {
                    Order buyOrder = new Order(symbol, Order.Type.BUY, Order.Side.LIMIT, orderSize, targetBuyPrice, portfolio);
                    orderBook.placeBuyOrder(buyOrder);
                    activeOrders.add(buyOrder.getId());
                }

                Order sellOrder = new Order(symbol, Order.Type.SELL, Order.Side.LIMIT, orderSize, targetSellPrice, portfolio);
                orderBook.placeSellOrder(sellOrder);
                activeOrders.add(sellOrder.getId());

                lastPrice = currentPrice;
            }

        } catch (Exception e) {
            // Suppress frequent error messages in HF trading
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