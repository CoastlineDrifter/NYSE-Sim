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

        // Create order book for AAPL
        OrderBook AAPL = new OrderBook("AAPL", 150.0);

        // Create bot manager
        BotManager botManager = new BotManager();

        for (int i = 1; i <= 10; i++) {
            botManager.addMarketMaker("MarketMaker " + i, 50000, AAPL, 2.0, 10);
        }
        // Add different types of bots
         // $2 spread, 10 shares
        for (int i = 1; i <= 10; i++) {
            botManager.addMomentumBot("MomentumTrader", 25000, AAPL, 0.02, 5);
        }

        for (int i = 1; i <= 10; i++) {
            botManager.addMeanReversionBot("MeanReverter" + i, 30000, AAPL, 0.03, 8);
        }

        for (int i = 1; i <= 10; i++) {
            botManager.addRandomBot("RandomTrader" + i, 20000, AAPL, 5);
        }

        for (int i = 1; i <= 10; i++) {
            botManager.addScalpingBot("Scalper" + i, 15000, AAPL, 3);
        }

        System.out.println("=== NYSE Simulator Started ===");
        System.out.println("Initial AAPL price: $" + AAPL.getCurrentPrice());
        System.out.println();

    }
}