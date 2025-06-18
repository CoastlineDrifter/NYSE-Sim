package Data;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import Banking.*;
import Data.Candle;

/**
 * Event-driven candle system that updates only when trades occur
 * More efficient and accurate than timer-based approach
 */
public class CandleManager {

    public enum TimeFrame {
        FIVE_SECOND(5),
        ONE_MINUTE(60),
        FIVE_MINUTE(300),
        FIFTEEN_MINUTE(900),
        ONE_HOUR(3600),
        ONE_DAY(86400);

        private final int seconds;

        TimeFrame(int seconds) {
            this.seconds = seconds;
        }

        public int getSeconds() {
            return seconds;
        }

        public long getMilliseconds() {
            return seconds * 1000L;
        }
    }

    // Store candles for each symbol and timeframe
    private final Map<String, Map<TimeFrame, NavigableMap<Long, Candle>>> candleData;
    private final Map<String, Map<TimeFrame, Long>> currentCandleTimes;
    private final int maxCandlesPerTimeframe;

    public CandleManager(int maxCandlesPerTimeframe) {
        this.candleData = new ConcurrentHashMap<>();
        this.currentCandleTimes = new ConcurrentHashMap<>();
        this.maxCandlesPerTimeframe = maxCandlesPerTimeframe;
    }

    /**
     * Initialize candles for a new symbol
     */
    public void initializeSymbol(String symbol, double initialPrice) {
        candleData.put(symbol, new ConcurrentHashMap<>());
        currentCandleTimes.put(symbol, new ConcurrentHashMap<>());

        long currentTime = System.currentTimeMillis();

        for (TimeFrame tf : TimeFrame.values()) {
            candleData.get(symbol).put(tf, new TreeMap<>());
            long candleTime = getCandleStartTime(currentTime, tf);
            currentCandleTimes.get(symbol).put(tf, candleTime);

            // Create initial candle
            candleData.get(symbol).get(tf).put(candleTime,
                    new Candle(initialPrice, initialPrice, initialPrice, initialPrice, 0));
        }
    }

    /**
     * Update candles when a trade occurs - this is the main method
     */
    public void onTrade(String symbol, double price, int volume, long timestamp) {
        if (!candleData.containsKey(symbol)) {
            initializeSymbol(symbol, price);
        }

        Map<TimeFrame, NavigableMap<Long, Candle>> symbolCandles = candleData.get(symbol);
        Map<TimeFrame, Long> symbolCurrentTimes = currentCandleTimes.get(symbol);

        for (TimeFrame tf : TimeFrame.values()) {
            long candleTime = getCandleStartTime(timestamp, tf);
            long currentCandleTime = symbolCurrentTimes.get(tf);

            NavigableMap<Long, Candle> timeframeCandles = symbolCandles.get(tf);

            // Check if we need a new candle
            if (candleTime > currentCandleTime) {
                // Close previous candle and create new one
                createNewCandleAndCleanup(timeframeCandles, candleTime, price);
                symbolCurrentTimes.put(tf, candleTime);
            }

            // Update current candle
            Candle currentCandle = timeframeCandles.get(symbolCurrentTimes.get(tf));
            if (currentCandle != null) {
                updateCandle(currentCandle, price, volume);
            }
        }
    }

    private void createNewCandleAndCleanup(NavigableMap<Long, Candle> candles,
                                           long candleTime, double openPrice) {
        // Create new candle
        candles.put(candleTime, new Candle(openPrice, openPrice, openPrice, openPrice, 0));

        // Cleanup old candles to prevent memory leaks
        if (candles.size() > maxCandlesPerTimeframe) {
            candles.pollFirstEntry(); // Remove oldest candle
        }
    }

    private void updateCandle(Candle candle, double price, int volume) {
        // Update high
        if (price > candle.getHigh()) {
            candle.setHigh(price);
        }

        // Update low
        if (price < candle.getLow()) {
            candle.setLow(price);
        }

        // Update close (always latest price)
        candle.setClose(price);

        // Add volume
        candle.addVolume(volume);
    }

    /**
     * Get candle start time aligned to timeframe boundaries
     */
    private long getCandleStartTime(long timestamp, TimeFrame timeFrame) {
        long intervalMs = timeFrame.getMilliseconds();
        return (timestamp / intervalMs) * intervalMs;
    }

    /**
     * Get current candle for symbol and timeframe
     */
    public Candle getCurrentCandle(String symbol, TimeFrame timeFrame) {
        if (!candleData.containsKey(symbol)) return null;

        Long currentTime = currentCandleTimes.get(symbol).get(timeFrame);
        if (currentTime == null) return null;

        return candleData.get(symbol).get(timeFrame).get(currentTime);
    }

    /**
     * Get historical candles for symbol and timeframe
     */
    public List<Candle> getCandles(String symbol, TimeFrame timeFrame, int count) {
        if (!candleData.containsKey(symbol)) return new ArrayList<>();

        NavigableMap<Long, Candle> candles = candleData.get(symbol).get(timeFrame);

        // Option 1: Using standard collectors (recommended)
        return candles.descendingMap().values().stream()
                .limit(count)
                .collect(Collectors.toList());

    }
    public List<Candle> getCandlesInRange(String symbol, TimeFrame timeFrame,
                                          long startTime, long endTime) {
        if (!candleData.containsKey(symbol)) return new ArrayList<>();

        NavigableMap<Long, Candle> candles = candleData.get(symbol).get(timeFrame);
        return new ArrayList<>(candles.subMap(startTime, true, endTime, true).values());
    }

    /**
     * Get latest price for symbol (close of current candle)
     */
    public Double getLatestPrice(String symbol) {
        Candle currentCandle = getCurrentCandle(symbol, TimeFrame.FIVE_SECOND);
        return currentCandle != null ? currentCandle.getClose() : null;
    }
}