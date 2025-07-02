package Engine;

import java.util.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import Banking.*;
import Data.Candle;
import Data.CandleManager;

public class OrderBook {
    private String symbol;
    private List<Order> buyOrders;
    private List<Order> sellOrders;
    private List<Order> buyStops;
    private List<Order> sellStops;

    private double lastTradePrice;
    private double currentPrice; // Current market price of the stock

    // Use centralized candle manager
    private static CandleManager candleManager = new CandleManager(1000); // Keep 1000 candles per timeframe

    public OrderBook(String symbol, double initialPrice) {
        this.symbol = symbol;
        this.buyOrders = Collections.synchronizedList(new ArrayList<>());
        this.sellOrders = Collections.synchronizedList(new ArrayList<>());
        this.buyStops = Collections.synchronizedList(new ArrayList<>());
        this.sellStops = Collections.synchronizedList(new ArrayList<>());
        this.currentPrice = initialPrice;
        this.lastTradePrice = initialPrice;

        // Initialize candles for this symbol
        candleManager.initializeSymbol(symbol, initialPrice);
    }

    /**
     * Call this whenever a trade is executed - single point of candle updates
     */
    private void onTradeExecuted(double price, int volume) {
        this.lastTradePrice = price;
        this.currentPrice = price;

        // Update candles - this is the only place candles get updated
        candleManager.onTrade(symbol, price, volume, System.currentTimeMillis());

        // Check for stop order triggers after price update
        checkStopOrders();
    }

    public synchronized void placeBuyOrder(Order order) {
        if (order.getSide() == Order.Side.MARKET) {
            executeMarketBuyOrder(order);
        } else if (order.getSide() == Order.Side.STOP) {
            placeBuyStopOrder(order);
        } else {
            placeLimitBuyOrder(order);
        }
    }

    public synchronized void placeSellOrder(Order order) {
        if (order.getSide() == Order.Side.MARKET) {
            executeMarketSellOrder(order);
        } else if (order.getSide() == Order.Side.STOP) {
            placeSellStopOrder(order);
        } else {
            placeLimitSellOrder(order);
        }
    }

    private void placeBuyStopOrder(Order order) {
        buyStops.add(order);
        // Sort by stop price (lowest first for buy stops)
        buyStops.sort((a, b) -> Double.compare(a.getPrice(), b.getPrice()));
        System.out.println("Buy stop order placed: " + order.getQuantity() + " shares of " + symbol +
                " at stop price $" + order.getPrice());
    }

    private void placeSellStopOrder(Order order) {
        String stockSymbol = order.getStockSymbol();
        Portfolio portfolio = order.getUser_portfolio();
        int availableStock = portfolio.getAvailableStock(stockSymbol);

        // Check if this is a stop-loss that would result in short sell
        boolean isShortSell = availableStock < order.getQuantity();

        if (!isShortSell) {
            // For regular stop orders, reserve the stock
            if (!portfolio.reserveStock(stockSymbol, order.getQuantity())) {
                System.out.println("You do not have enough stock for this stop order");
                return;
            }
        } else {
            System.out.println("Sell stop order will result in short position when triggered");
        }

        sellStops.add(order);
        // Sort by stop price (highest first for sell stops)
        sellStops.sort((a, b) -> Double.compare(b.getPrice(), a.getPrice()));
        System.out.println("Sell stop order placed: " + order.getQuantity() + " shares of " + symbol +
                " at stop price $" + order.getPrice());
    }

    private synchronized void checkStopOrders() {
        // Check buy stop orders (trigger when price goes above stop price)
        List<Order> triggeredBuyStops = new ArrayList<>();
        synchronized(buyStops) {
            Iterator<Order> buyStopIterator = buyStops.iterator();
            while (buyStopIterator.hasNext()) {
                Order stopOrder = buyStopIterator.next();
                if (stopOrder != null && currentPrice >= stopOrder.getPrice()) {
                    triggeredBuyStops.add(stopOrder);
                    buyStopIterator.remove();
                }
            }
        }

        // Execute triggered buy stops outside of synchronized block
        for (Order stopOrder : triggeredBuyStops) {
            Order marketOrder = new Order(stopOrder.getStockSymbol(), Order.Type.BUY,
                    Order.Side.MARKET, stopOrder.getQuantity(), 0, stopOrder.getUser_portfolio());
            executeMarketBuyOrder(marketOrder);
            System.out.println("Buy stop triggered at $" + currentPrice);
        }

        // Check sell stop orders (trigger when price goes below stop price)
        List<Order> triggeredSellStops = new ArrayList<>();
        synchronized(sellStops) {
            Iterator<Order> sellStopIterator = sellStops.iterator();
            while (sellStopIterator.hasNext()) {
                Order stopOrder = sellStopIterator.next();
                if (stopOrder != null && currentPrice <= stopOrder.getPrice()) {
                    triggeredSellStops.add(stopOrder);
                    sellStopIterator.remove();
                }
            }
        }

        // Execute triggered sell stops outside of synchronized block
        for (Order stopOrder : triggeredSellStops) {
            Order marketOrder = new Order(stopOrder.getStockSymbol(), Order.Type.SELL,
                    Order.Side.MARKET, stopOrder.getQuantity(), 0, stopOrder.getUser_portfolio());
            executeMarketSellOrder(marketOrder);
            System.out.println("Sell stop triggered at $" + currentPrice);
        }
    }

    private void executeMarketBuyOrder(Order order) {
        synchronized(sellOrders) {
            if (sellOrders.isEmpty()) {
                System.out.println("No sell orders available to fulfill market buy order");
                return;
            }

            int remainingQuantity = order.getQuantity();
            double totalCost = 0;

            // Calculate total cost by walking through sell orders
            for (Order sellOrder : sellOrders) {
                if (sellOrder == null || remainingQuantity <= 0) break;

                int tradeQuantity = Math.min(remainingQuantity, sellOrder.getQuantity());
                totalCost += tradeQuantity * sellOrder.getPrice();
                remainingQuantity -= tradeQuantity;
            }

            if (remainingQuantity > 0) {
                System.out.println("Not enough liquidity to fulfill entire market buy order");
                return;
            }

            // Check if user has enough cash
            if (order.getUser_portfolio().getUSD() < totalCost) {
                System.out.println("You do not have enough cash to fulfill this market buy order");
                return;
            }

            // Execute the market order
            remainingQuantity = order.getQuantity();
            Iterator<Order> sellIterator = sellOrders.iterator();
            while (remainingQuantity > 0 && sellIterator.hasNext()) {
                Order lowestSell = sellIterator.next();
                if (lowestSell == null) continue;

                int tradeQuantity = Math.min(remainingQuantity, lowestSell.getQuantity());
                double tradePrice = lowestSell.getPrice();

                // Execute the trade
                order.getUser_portfolio().executeBuy(symbol, tradeQuantity, tradePrice);
                lowestSell.getUser_portfolio().executeSell(symbol, tradeQuantity, tradePrice);

                // Update prices and candles
                onTradeExecuted(tradePrice, tradeQuantity);

                // Update quantities
                remainingQuantity -= tradeQuantity;
                lowestSell.reduceQuantity(tradeQuantity);

                // Remove fully executed sell order
                if (lowestSell.getQuantity() == 0) {
                    sellIterator.remove();
                }

                System.out.println("Market buy executed: " + tradeQuantity + " shares of " + symbol +
                        " at $" + tradePrice + " per share");
            }
        }
    }

    private void placeLimitBuyOrder(Order order) {
        double requiredCash = order.getQuantity() * order.getPrice();
        double userCash = order.getUser_portfolio().getUSD();

        if (userCash < requiredCash) {
            System.out.println("You do not have enough cash to buy this order");
            return;
        }

        // Reserve the cash when placing the order
        order.getUser_portfolio().reserveCash(requiredCash);

        // Add order to the list
        buyOrders.add(order);

        // Sort buy orders by price (highest first), then by timestamp for same price
        buyOrders.sort((a, b) -> {
            if (a == null && b == null) return 0;
            if (a == null) return 1;
            if (b == null) return -1;

            int priceComparison = Double.compare(b.getPrice(), a.getPrice());
            if (priceComparison == 0) {
                return Long.compare(a.getTimestamp(), b.getTimestamp()); // Earlier orders first
            }
            return priceComparison;
        });

        // Attempt to match orders
        matchOrders();
    }

    private void executeMarketSellOrder(Order order) {
        synchronized(buyOrders) {
            if (buyOrders.isEmpty()) {
                System.out.println("No buy orders available to fulfill market sell order");
                return;
            }

            String stockSymbol = order.getStockSymbol();
            Portfolio portfolio = order.getUser_portfolio();
            int availableStock = portfolio.getAvailableStock(stockSymbol);

            // Check if this is a short sell (user doesn't have enough stock)
            boolean isShortSell = availableStock < order.getQuantity();

            if (isShortSell) {
                System.out.println("Market sell order will result in short position");
            }

            // Check if there's enough liquidity
            int availableLiquidity = 0;
            for (Order buyOrder : buyOrders) {
                if (buyOrder != null) {
                    availableLiquidity += buyOrder.getQuantity();
                    if (availableLiquidity >= order.getQuantity()) break;
                }
            }

            if (availableLiquidity < order.getQuantity()) {
                System.out.println("Not enough liquidity to fulfill entire market sell order");
                return;
            }

            // Execute the market sell order
            int remainingQuantity = order.getQuantity();
            Iterator<Order> buyIterator = buyOrders.iterator();
            while (remainingQuantity > 0 && buyIterator.hasNext()) {
                Order highestBuy = buyIterator.next();
                if (highestBuy == null) continue;

                int tradeQuantity = Math.min(remainingQuantity, highestBuy.getQuantity());
                double tradePrice = highestBuy.getPrice();

                // Execute the trade
                order.getUser_portfolio().executeSell(symbol, tradeQuantity, tradePrice);
                highestBuy.getUser_portfolio().executeBuy(symbol, tradeQuantity, tradePrice);

                // Update prices and candles
                onTradeExecuted(tradePrice, tradeQuantity);

                // Update quantities
                remainingQuantity -= tradeQuantity;
                highestBuy.reduceQuantity(tradeQuantity);

                // Remove fully executed buy order
                if (highestBuy.getQuantity() == 0) {
                    buyIterator.remove();
                }

                if (isShortSell) {
                    System.out.println("Market short sell executed: " + tradeQuantity + " shares of " + symbol +
                            " at $" + tradePrice + " per share");
                } else {
                    System.out.println("Market sell executed: " + tradeQuantity + " shares of " + symbol +
                            " at $" + tradePrice + " per share");
                }
            }
        }
    }

    private void placeLimitSellOrder(Order order) {
        String stockSymbol = order.getStockSymbol();
        Portfolio portfolio = order.getUser_portfolio();
        int availableStock = portfolio.getAvailableStock(stockSymbol);

        // Check if this is a short sell
        boolean isShortSell = availableStock < order.getQuantity();

        if (!isShortSell) {
            // For regular sell orders, reserve the stock
            if (!portfolio.reserveStock(stockSymbol, order.getQuantity())) {
                System.out.println("You do not have enough stock to sell");
                return;
            }
        }

        sellOrders.add(order);

        // Sort sell orders by price (lowest first), then by timestamp for same price
        sellOrders.sort((a, b) -> {
            if (a == null && b == null) return 0;
            if (a == null) return 1;
            if (b == null) return -1;

            int priceComparison = Double.compare(a.getPrice(), b.getPrice());
            if (priceComparison == 0) {
                return Long.compare(a.getTimestamp(), b.getTimestamp()); // Earlier orders first
            }
            return priceComparison;
        });

        matchOrders();
    }

    private synchronized void matchOrders() {
        while (!buyOrders.isEmpty() && !sellOrders.isEmpty()) {
            Order highestBuy = null;
            Order lowestSell = null;

            // Find first non-null buy and sell orders
            synchronized(buyOrders) {
                for (Order order : buyOrders) {
                    if (order != null) {
                        highestBuy = order;
                        break;
                    }
                }
            }

            synchronized(sellOrders) {
                for (Order order : sellOrders) {
                    if (order != null) {
                        lowestSell = order;
                        break;
                    }
                }
            }

            if (highestBuy == null || lowestSell == null) {
                break;
            }

            // Only match limit orders here (market orders are executed immediately)
            if (highestBuy.getSide() == Order.Side.LIMIT && lowestSell.getSide() == Order.Side.LIMIT) {
                if (highestBuy.getPrice() >= lowestSell.getPrice()) {
                    executeOrders(highestBuy, lowestSell);
                } else {
                    // No more matches possible
                    break;
                }
            } else {
                // This shouldn't happen as market orders are executed immediately
                break;
            }
        }
    }

    private void executeOrders(Order buyOrder, Order sellOrder) {
        int tradeQuantity = Math.min(buyOrder.getQuantity(), sellOrder.getQuantity());
        double tradePrice = sellOrder.getPrice(); // Use sell price as execution price

        // Check if the sell is a short sell
        Portfolio sellPortfolio = sellOrder.getUser_portfolio();
        int availableStock = sellPortfolio.getAvailableStock(sellOrder.getStockSymbol());
        boolean isShortSell = availableStock < tradeQuantity;

        // Execute the trade in both portfolios
        buyOrder.getUser_portfolio().executeBuy(symbol, tradeQuantity, tradePrice);
        sellOrder.getUser_portfolio().executeSell(symbol, tradeQuantity, tradePrice);

        // Update prices and candles
        onTradeExecuted(tradePrice, tradeQuantity);

        // Update order quantities
        buyOrder.reduceQuantity(tradeQuantity);
        sellOrder.reduceQuantity(tradeQuantity);

        // Remove fully executed orders
        synchronized(buyOrders) {
            if (buyOrder.getQuantity() == 0) {
                buyOrders.remove(buyOrder);
            }
        }
        synchronized(sellOrders) {
            if (sellOrder.getQuantity() == 0) {
                sellOrders.remove(sellOrder);
            }
        }

        if (isShortSell) {
            System.out.println("Short sell trade executed: " + tradeQuantity + " shares of " + symbol +
                    " at $" + tradePrice + " per share");
        } else {
            System.out.println("Trade executed: " + tradeQuantity + " shares of " + symbol +
                    " at $" + tradePrice + " per share");
        }
    }

    // Cancel a buy order and release reserved cash (only for limit orders)
    public synchronized boolean cancelBuyOrder(int orderId) {
        // Check regular buy orders
        synchronized(buyOrders) {
            Iterator<Order> iterator = buyOrders.iterator();
            while (iterator.hasNext()) {
                Order order = iterator.next();
                if (order != null && order.getId() == orderId) {
                    if (order.getSide() == Order.Side.LIMIT) {
                        // Release reserved cash
                        double reservedCash = order.getQuantity() * order.getPrice();
                        order.getUser_portfolio().releaseReservedCash(reservedCash);
                    }
                    iterator.remove();
                    return true;
                }
            }
        }

        // Check buy stop orders
        synchronized(buyStops) {
            Iterator<Order> iterator = buyStops.iterator();
            while (iterator.hasNext()) {
                Order order = iterator.next();
                if (order != null && order.getId() == orderId) {
                    iterator.remove();
                    return true;
                }
            }
        }
        return false;
    }

    // Cancel a sell order and release reserved stock (only for limit orders)
    public synchronized boolean cancelSellOrder(int orderId) {
        // Check regular sell orders
        synchronized(sellOrders) {
            Iterator<Order> iterator = sellOrders.iterator();
            while (iterator.hasNext()) {
                Order order = iterator.next();
                if (order != null && order.getId() == orderId) {
                    if (order.getSide() == Order.Side.LIMIT) {
                        // Only release reserved stock if it's not a short sell
                        Portfolio portfolio = order.getUser_portfolio();
                        int availableStock = portfolio.getAvailableStock(order.getStockSymbol());

                        // If the order quantity is less than or equal to available stock,
                        // it means we reserved stock for this order
                        if (order.getQuantity() <= availableStock + portfolio.getReservedStock().getOrDefault(order.getStockSymbol(), 0)) {
                            portfolio.releaseReservedStock(order.getStockSymbol(), order.getQuantity());
                        }
                    }
                    iterator.remove();
                    return true;
                }
            }
        }

        // Check sell stop orders
        synchronized(sellStops) {
            Iterator<Order> iterator = sellStops.iterator();
            while (iterator.hasNext()) {
                Order order = iterator.next();
                if (order != null && order.getId() == orderId) {
                    if (order.getSide() == Order.Side.STOP) {
                        // Release reserved stock for stop orders
                        Portfolio portfolio = order.getUser_portfolio();
                        int availableStock = portfolio.getAvailableStock(order.getStockSymbol());

                        if (order.getQuantity() <= availableStock + portfolio.getReservedStock().getOrDefault(order.getStockSymbol(), 0)) {
                            portfolio.releaseReservedStock(order.getStockSymbol(), order.getQuantity());
                        }
                    }
                    iterator.remove();
                    return true;
                }
            }
        }
        return false;
    }

    // Get the best bid (highest buy price)
    public Double getBestBid() {
        synchronized(buyOrders) {
            for (Order order : buyOrders) {
                if (order != null) {
                    return order.getPrice();
                }
            }
            return null;
        }
    }

    // Get the best ask (lowest sell price)
    public Double getBestAsk() {
        synchronized(sellOrders) {
            for (Order order : sellOrders) {
                if (order != null) {
                    return order.getPrice();
                }
            }
            return null;
        }
    }

    // Get the bid-ask spread
    public Double getSpread() {
        Double bid = getBestBid();
        Double ask = getBestAsk();
        return (bid != null && ask != null) ? ask - bid : null;
    }

    // Public methods to access candle data
    public Candle getCurrentCandle(CandleManager.TimeFrame timeFrame) {
        return candleManager.getCurrentCandle(symbol, timeFrame);
    }

    public List<Candle> getRecentCandles(CandleManager.TimeFrame timeFrame, int count) {
        return candleManager.getCandles(symbol, timeFrame, count);
    }

    public List<Candle> getCandlesInRange(CandleManager.TimeFrame timeFrame,
                                          long startTime, long endTime) {
        return candleManager.getCandlesInRange(symbol, timeFrame, startTime, endTime);
    }

    // Getter methods for monitoring
    public List<Order> getBuyOrders() {
        synchronized(buyOrders) {
            return buyOrders.stream().filter(order -> order != null).collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        }
    }

    public List<Order> getSellOrders() {
        synchronized(sellOrders) {
            return sellOrders.stream().filter(order -> order != null).collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        }
    }

    public List<Order> getBuyStops() {
        synchronized(buyStops) {
            return buyStops.stream().filter(order -> order != null).collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        }
    }

    public List<Order> getSellStops() {
        synchronized(sellStops) {
            return sellStops.stream().filter(order -> order != null).collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        }
    }

    public String getSymbol() {
        return symbol;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public double getLastTradePrice() {
        return lastTradePrice;
    }

    public synchronized void setCurrentPrice(double price) {
        this.currentPrice = price;
        // Don't update candles here - only update on actual trades
    }

    // Static method to get the candle manager (for global access)
    public static CandleManager getCandleManager() {
        return candleManager;
    }

    // Method to get market depth
    public String getMarketDepth(int levels) {
        StringBuilder depth = new StringBuilder();
        depth.append("Market Depth for ").append(symbol).append(":\n");
        depth.append("Current Price: $").append(String.format("%.2f", currentPrice)).append("\n");
        depth.append("Last Trade: $").append(String.format("%.2f", lastTradePrice)).append("\n\n");

        depth.append("ASKS (Sell Orders):\n");
        synchronized(sellOrders) {
            int count = 0;
            for (int i = Math.min(levels, sellOrders.size()) - 1; i >= 0 && count < levels; i--) {
                Order order = sellOrders.get(i);
                if (order != null) {
                    depth.append(String.format("$%.2f - %d shares\n", order.getPrice(), order.getQuantity()));
                    count++;
                }
            }
        }

        depth.append("\nBIDS (Buy Orders):\n");
        synchronized(buyOrders) {
            int count = 0;
            for (int i = 0; i < buyOrders.size() && count < levels; i++) {
                Order order = buyOrders.get(i);
                if (order != null) {
                    depth.append(String.format("$%.2f - %d shares\n", order.getPrice(), order.getQuantity()));
                    count++;
                }
            }
        }

        return depth.toString();
    }

    // Method to get order book statistics
    public String getOrderBookStats() {
        StringBuilder stats = new StringBuilder();
        stats.append("Order Book Statistics for ").append(symbol).append(":\n");

        synchronized(buyOrders) {
            stats.append("Buy Orders: ").append(buyOrders.stream().mapToInt(order -> order != null ? 1 : 0).sum()).append("\n");
        }
        synchronized(sellOrders) {
            stats.append("Sell Orders: ").append(sellOrders.stream().mapToInt(order -> order != null ? 1 : 0).sum()).append("\n");
        }
        synchronized(buyStops) {
            stats.append("Buy Stop Orders: ").append(buyStops.stream().mapToInt(order -> order != null ? 1 : 0).sum()).append("\n");
        }
        synchronized(sellStops) {
            stats.append("Sell Stop Orders: ").append(sellStops.stream().mapToInt(order -> order != null ? 1 : 0).sum()).append("\n");
        }

        Double spread = getSpread();
        if (spread != null) {
            stats.append("Bid-Ask Spread: $").append(String.format("%.2f", spread)).append("\n");
        }

        return stats.toString();
    }
}