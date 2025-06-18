package Data;

import Banking.Position;

import java.util.HashMap;
import java.util.Map;

public class Candle {
    private double high;
    private double low;
    private double open;
    private double close;
    private double volume;

    private Map<String, Double> CandleData;

    public Candle(double high, double low, double open, double close, double volume) {
        CandleData = new HashMap<String, Double>();
        this.high = high;
        this.low = low;
        this.open = open;
        this.close = close;
        this.volume = volume;
    }

    public void addVolume(double volume) {
        this.volume += volume;
    }

    public double getVolume() {
        return volume;
    }

    public void setHigh(double high) {
        this.high = high;
    }
    public double getHigh() {
        return high;
    }
    public void setLow(double low) {
        this.low = low;
    }
    public double getLow() {
        return low;
    }
    public void setOpen(double open) {
        this.open = open;
    }
    public double getOpen() {
        return open;
    }
    public void setClose(double close) {
        this.close = close;
    }
    public double getClose() {
        return close;
    }
}
