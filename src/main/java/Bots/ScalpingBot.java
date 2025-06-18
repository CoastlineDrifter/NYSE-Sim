package Bots;
import Engine.*;
import Banking.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


class ScalpingBot {
    private Portfolio portfolio;
    private OrderBook orderBook;
    private String symbol;
    private int orderSize;
    private ScheduledExecutorService scheduler;

    public ScalpingBot(String botName, double initialCash, OrderBook orderBook, int orderSize) {
        this.portfolio = new Portfolio(initialCash, botName);
        this.orderBook = orderBook;
        this.symbol = orderBook.getSymbol();
        this.orderSize = orderSize;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::scalp, 0, 2, TimeUnit.SECONDS);
    }

    private void scalp() {
        try {
            Double bestBid = orderBook.getBestBid();
            Double bestAsk = orderBook.getBestAsk();

            if (bestBid != null && bestAsk != null) {
                double spread = bestAsk - bestBid;

                // Only scalp if spread is profitable
                if (spread > 0.02) { // Minimum 2 cents spread
                    // Place buy order just above best bid
                    double buyPrice = bestBid + 0.01;
                    if (portfolio.getAvailableCash() >= buyPrice * orderSize) {
                        Order buyOrder = new Order(symbol, Order.Type.BUY, Order.Side.LIMIT, orderSize, buyPrice, portfolio);
                        orderBook.placeBuyOrder(buyOrder);
                    }

                    // Place sell order just below best ask
                    double sellPrice = bestAsk - 0.01;
                    Order sellOrder = new Order(symbol, Order.Type.SELL, Order.Side.LIMIT, orderSize, sellPrice, portfolio);
                    orderBook.placeSellOrder(sellOrder);
                }
            }

        } catch (Exception e) {
            System.out.println("Scalping Bot error: " + e.getMessage());
        }
    }

    public void stop() {
        scheduler.shutdown();
    }
}