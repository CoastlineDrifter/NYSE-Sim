package Bots;
import Engine.*;
import Banking.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class MarketMakerBot {
    private Portfolio portfolio;
    private OrderBook orderBook;
    private String symbol;
    private double spread;
    private int orderSize;
    private List<Integer> activeOrders;
    private ScheduledExecutorService scheduler;
    private double minPrice = 0.50; // Minimum price to prevent unrealistic orders
    private double maxSpreadFromCurrent = 5.0; // Maximum spread from current price

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
        scheduler.scheduleAtFixedRate(this::updateOrders, 0, 500, TimeUnit.MILLISECONDS);
    }

    private void updateOrders() {
        try {
            double currentPrice = orderBook.getCurrentPrice();
            Double bestBid = orderBook.getBestBid();
            Double bestAsk = orderBook.getBestAsk();

            // Calculate target prices - use current price as fallback when no bid/ask exists
            double targetBuyPrice, targetSellPrice;

            if (bestBid == null && bestAsk == null) {
                // No liquidity - create initial market around current price
                targetBuyPrice = currentPrice - (spread / 2);
                targetSellPrice = currentPrice + (spread / 2);
            } else if (bestBid == null) {
                // No bids - place buy orders below best ask
                targetBuyPrice = Math.min(bestAsk - 0.01, currentPrice - (spread / 2));
                targetSellPrice = bestAsk + 0.01;
            } else if (bestAsk == null) {
                // No asks - place sell orders above best bid
                targetBuyPrice = bestBid - 0.01;
                targetSellPrice = Math.max(bestBid + 0.01, currentPrice + (spread / 2));
            } else {
                // Normal market - place orders inside the spread
                double currentSpread = bestAsk - bestBid;
                if (currentSpread > spread) {
                    // Wide spread - place tighter orders
                    targetBuyPrice = bestBid + 0.01;
                    targetSellPrice = bestAsk - 0.01;
                } else {
                    // Tight spread - place orders at current levels or slightly outside
                    targetBuyPrice = bestBid;
                    targetSellPrice = bestAsk;
                }
            }

            // Safety checks
            targetBuyPrice = Math.max(targetBuyPrice, minPrice);
            targetSellPrice = Math.max(targetSellPrice, minPrice);

            // Prevent extreme prices
            if (Math.abs(targetBuyPrice - currentPrice) > maxSpreadFromCurrent) {
                targetBuyPrice = currentPrice - maxSpreadFromCurrent;
            }
            if (Math.abs(targetSellPrice - currentPrice) > maxSpreadFromCurrent) {
                targetSellPrice = currentPrice + maxSpreadFromCurrent;
            }

            // Only update orders if prices have moved significantly or no orders exist
            boolean shouldUpdateBuy = bestBid == null || activeOrders.isEmpty() ||
                    Math.abs(bestBid - targetBuyPrice) > 0.25;
            boolean shouldUpdateSell = bestAsk == null || activeOrders.isEmpty() ||
                    Math.abs(bestAsk - targetSellPrice) > 0.25;

            if (shouldUpdateBuy) {
                cancelBuyOrders();
                placeBuyOrders(targetBuyPrice);
            }

            if (shouldUpdateSell) {
                cancelSellOrders();
                placeSellOrders(targetSellPrice);
            }

        } catch (Exception e) {
            System.out.println("MarketMaker error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void placeBuyOrders(double targetBuyPrice) {
        // Place multiple buy orders at different levels for better coverage
        for (int i = 0; i < 3; i++) {
            double buyPrice = Math.max(targetBuyPrice - (i * 0.25), minPrice);
            if (portfolio.getAvailableCash() >= buyPrice * orderSize) {
                try {
                    Order buyOrder = new Order(symbol, Order.Type.BUY, Order.Side.LIMIT, orderSize, buyPrice, portfolio);
                    if (buyOrder != null) {
                        orderBook.placeBuyOrder(buyOrder);
                        activeOrders.add(buyOrder.getId());
                    }
                } catch (Exception e) {
                    System.out.println("Failed to create buy order: " + e.getMessage());
                }
            }
        }
    }

    private void placeSellOrders(double targetSellPrice) {
        // Place multiple sell orders at different levels
        for (int i = 0; i < 3; i++) {
            double sellPrice = Math.max(targetSellPrice + (i * 0.25), minPrice);
            try {
                Order sellOrder = new Order(symbol, Order.Type.SELL, Order.Side.LIMIT, orderSize, sellPrice, portfolio);
                if (sellOrder != null) {
                    orderBook.placeSellOrder(sellOrder);
                    activeOrders.add(sellOrder.getId());
                }
            } catch (Exception e) {
                System.out.println("Failed to create sell order: " + e.getMessage());
            }
        }
    }

    private void cancelAllOrders() {
        Iterator<Integer> iterator = activeOrders.iterator();
        while (iterator.hasNext()) {
            Integer orderId = iterator.next();
            boolean cancelledBuy = orderBook.cancelBuyOrder(orderId);
            boolean cancelledSell = orderBook.cancelSellOrder(orderId);
            if (cancelledBuy || cancelledSell) {
                iterator.remove();
            }
        }
    }

    private void cancelBuyOrders() {
        Iterator<Integer> iterator = activeOrders.iterator();
        while (iterator.hasNext()) {
            Integer orderId = iterator.next();
            if (orderBook.cancelBuyOrder(orderId)) {
                iterator.remove();
            }
        }
    }

    private void cancelSellOrders() {
        Iterator<Integer> iterator = activeOrders.iterator();
        while (iterator.hasNext()) {
            Integer orderId = iterator.next();
            if (orderBook.cancelSellOrder(orderId)) {
                iterator.remove();
            }
        }
    }

    public void stop() {
        cancelAllOrders();
        scheduler.shutdown();
    }

    // Getter methods for monitoring
    public Portfolio getPortfolio() {
        return portfolio;
    }

    public List<Integer> getActiveOrders() {
        return new ArrayList<>(activeOrders);
    }

    public String getSymbol() {
        return symbol;
    }
}