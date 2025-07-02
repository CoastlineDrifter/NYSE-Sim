package org.example.nysesim;

import Data.CandleManager;
import Engine.*;
import Banking.*;
import Bots.BotManager;

public class Main {
    public static void main(String[] args) {
        // Create human traders
        Portfolio user1 = new Portfolio(10000, "Alice");
        Portfolio user2 = new Portfolio(10000, "Bob");

        OrderBook AAPL = new OrderBook("AAPL", 5);

        BotManager botManager = new BotManager();

        for (int i = 1; i <= 4; i++) {
            botManager.addMarketMaker("MarketMaker " + i, 50000, AAPL, 2.0, 10);
        }

        for (int i = 1; i <= 3; i++) {
            botManager.addHFMarketMakerBot("HFMM " + i, 50000, AAPL, 2.0, 10);
        }

        for (int i = 1; i <= 3; i++) {
            botManager.addMomentumBot("MomentumTrader" + i, 25000, AAPL, 0.02, 5);
        }

        for (int i = 1; i <= 3; i++) {
            botManager.addMeanReversionBot("MeanReverter" + i, 30000, AAPL, 0.03, 8);
        }

        for (int i = 1; i <= 3; i++) {
            botManager.addRandomBot("RandomTrader" + i, 20000, AAPL, 5);
        }

        System.out.println("=== NYSE Simulator Started ===");
        System.out.println("Initial AAPL price: $" + AAPL.getCurrentPrice());
        System.out.println("Live charts initialized for real-time monitoring");
        System.out.println();

        Thread manualTradesThread = new Thread(() -> {
            try {
                Thread.sleep(5000);

                System.out.println("Starting manual trading simulation...");

                for (int i = 0; i < 20; i++) {
                    double basePrice = AAPL.getCurrentPrice();

                    Order buyOrder = new Order("AAPL", Order.Type.BUY, Order.Side.LIMIT,
                            10 + (int)(Math.random() * 20),
                            basePrice + (Math.random() * 2), user1);
                    AAPL.placeBuyOrder(buyOrder);

                    Thread.sleep(2000);

                    Order sellOrder = new Order("AAPL", Order.Type.SELL, Order.Side.LIMIT,
                            10 + (int)(Math.random() * 20),
                            basePrice - (Math.random() * 2), user2);
                    AAPL.placeSellOrder(sellOrder);

                    Thread.sleep(3000);
                    if (i % 5 == 0) {
                        Order marketBuy = new Order("AAPL", Order.Type.BUY, Order.Side.MARKET,
                                5, 0, user1);
                        AAPL.placeBuyOrder(marketBuy);

                        Thread.sleep(1000);

                        Order marketSell = new Order("AAPL", Order.Type.SELL, Order.Side.MARKET,
                                5, 0, user2);
                        AAPL.placeSellOrder(marketSell);
                    }
                }

                System.out.println("Manual trading simulation completed. Bots continue trading...");

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        manualTradesThread.start();

        // Print periodic statistics
        Thread statsThread = new Thread(() -> {
            try {
                while (true) {
                    Thread.sleep(30000);
                    System.out.println("\n=== Market Statistics ===");
                    System.out.println(AAPL.getOrderBookStats());
                    System.out.println("Current Price: $" + String.format("%.2f", AAPL.getCurrentPrice()));

                    var recentCandles = AAPL.getRecentCandles(CandleManager.TimeFrame.ONE_MINUTE, 3);
                    System.out.println("Recent 1-minute candles:");
                    for (int i = 0; i < recentCandles.size(); i++) {
                        var candle = recentCandles.get(i);
                        System.out.printf("  Candle %d: O=%.2f H=%.2f L=%.2f C=%.2f V=%.0f%n",
                                i+1, candle.getOpen(), candle.getHigh(),
                                candle.getLow(), candle.getClose(), candle.getVolume());
                    }
                    System.out.println("========================\n");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        statsThread.setDaemon(true);
        statsThread.start();
    }
}