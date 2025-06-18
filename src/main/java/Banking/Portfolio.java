package Banking;

import java.util.HashMap;
import java.util.Map;

public class Portfolio {
    private double USD;
    private double reservedCash; // Cash reserved for pending buy orders
    private String Username;
    private Map<String, Position> stockHoldings;
    private Map<String, Integer> reservedStock; // Stock reserved for pending sell orders

    public Portfolio(double USD, String Username) {
        this.USD = USD;
        this.reservedCash = 0.0;
        this.Username = Username;
        this.stockHoldings = new HashMap<String, Position>();
        this.reservedStock = new HashMap<String, Integer>();
    }

    // Reserve cash for pending buy orders
    public boolean reserveCash(double amount) {
        if (USD >= amount) {
            USD -= amount;
            reservedCash += amount;
            return true;
        }
        return false;
    }

    // Release reserved cash (when order is cancelled)
    public void releaseReservedCash(double amount) {
        reservedCash -= amount;
        USD += amount;
    }

    // Reserve stock for pending sell orders
    public boolean reserveStock(String symbol, int quantity) {
        if (stockHoldings.containsKey(symbol)) {
            Position position = stockHoldings.get(symbol);
            if (position.getStatus().equals("long")) {
                int availableQuantity = position.getQuantity() - reservedStock.getOrDefault(symbol, 0);
                if (availableQuantity >= quantity) {
                    reservedStock.put(symbol, reservedStock.getOrDefault(symbol, 0) + quantity);
                    return true;
                }
            }
        }
        return false;
    }

    // Release reserved stock (when order is cancelled)
    public void releaseReservedStock(String symbol, int quantity) {
        int currentReserved = reservedStock.getOrDefault(symbol, 0);
        int newReserved = Math.max(0, currentReserved - quantity);
        if (newReserved == 0) {
            reservedStock.remove(symbol);
        } else {
            reservedStock.put(symbol, newReserved);
        }
    }

    public void executeBuy(String stockSymbol, int quantity, double price) {
        double totalCost = quantity * price;

        // Use reserved cash for the purchase
        reservedCash -= totalCost;

        if (!stockHoldings.containsKey(stockSymbol)) {
            // New long position
            stockHoldings.put(stockSymbol, new Position(quantity, price, stockSymbol, "long"));
        } else {
            Position currentPosition = stockHoldings.get(stockSymbol);

            if (currentPosition.getStatus().equals("short")) {
                // Covering short position
                if (currentPosition.getQuantity() <= quantity) {
                    // Fully cover short position and potentially go long
                    int shortQuantity = currentPosition.getQuantity();
                    double shortPrice = currentPosition.getPrice();

                    // Calculate P&L from covering short (profit if shortPrice > currentPrice)
                    double shortPnL = shortQuantity * (shortPrice - price);
                    USD += shortPnL;

                    int remainingQuantity = quantity - shortQuantity;
                    if (remainingQuantity > 0) {
                        // Go long with remaining quantity
                        stockHoldings.put(stockSymbol, new Position(remainingQuantity, price, stockSymbol, "long"));
                    } else {
                        // Fully covered, remove position
                        stockHoldings.remove(stockSymbol);
                    }
                } else {
                    // Partially cover short position
                    double shortPrice = currentPosition.getPrice();
                    double partialPnL = quantity * (shortPrice - price);
                    USD += partialPnL;

                    int newShortQuantity = currentPosition.getQuantity() - quantity;
                    stockHoldings.put(stockSymbol, new Position(newShortQuantity, shortPrice, stockSymbol, "short"));
                }
            } else if (currentPosition.getStatus().equals("long")) {
                // Adding to long position - calculate weighted average price
                int currentQuantity = currentPosition.getQuantity();
                double currentPrice = currentPosition.getPrice();

                int newQuantity = currentQuantity + quantity;
                double newAvgPrice = ((currentQuantity * currentPrice) + (quantity * price)) / newQuantity;

                stockHoldings.put(stockSymbol, new Position(newQuantity, newAvgPrice, stockSymbol, "long"));
            }
        }
    }

    public void executeSell(String stockSymbol, int quantity, double price) {
        double totalProceeds = quantity * price;

        // Release reserved stock (only if we had reserved stock)
        if (reservedStock.containsKey(stockSymbol)) {
            int reservedAmount = Math.min(reservedStock.get(stockSymbol), quantity);
            releaseReservedStock(stockSymbol, reservedAmount);
        }

        if (!stockHoldings.containsKey(stockSymbol)) {
            // No existing position - this is a new short position
            USD += totalProceeds; // Receive cash from short sale
            stockHoldings.put(stockSymbol, new Position(quantity, price, stockSymbol, "short"));
            return;
        }

        Position currentPosition = stockHoldings.get(stockSymbol);

        if (currentPosition.getStatus().equals("long")) {
            // Selling long position
            USD += totalProceeds;

            if (currentPosition.getQuantity() == quantity) {
                // Fully sell position
                stockHoldings.remove(stockSymbol);
            } else if (currentPosition.getQuantity() > quantity) {
                // Partially sell position
                int newQuantity = currentPosition.getQuantity() - quantity;
                double avgPrice = currentPosition.getPrice();
                stockHoldings.put(stockSymbol, new Position(newQuantity, avgPrice, stockSymbol, "long"));
            } else {
                // Selling more than we own - sell all long position and go short
                int longQuantity = currentPosition.getQuantity();
                int shortQuantity = quantity - longQuantity;

                // Remove the long position
                stockHoldings.remove(stockSymbol);

                // Create new short position with remaining quantity
                if (shortQuantity > 0) {
                    stockHoldings.put(stockSymbol, new Position(shortQuantity, price, stockSymbol, "short"));
                }
            }
        } else if (currentPosition.getStatus().equals("short")) {
            // Adding to short position
            int currentQuantity = currentPosition.getQuantity();
            double currentPrice = currentPosition.getPrice();

            USD += totalProceeds; // Receive more cash from additional short sale

            int newQuantity = currentQuantity + quantity;
            double newAvgPrice = ((currentQuantity * currentPrice) + (quantity * price)) / newQuantity;

            stockHoldings.put(stockSymbol, new Position(newQuantity, newAvgPrice, stockSymbol, "short"));
        }
    }

    public double getUSD() {
        return USD;
    }

    public double getAvailableCash() {
        return USD; // USD already excludes reserved cash
    }

    public double getReservedCash() {
        return reservedCash;
    }

    public double getTotalCash() {
        return USD + reservedCash;
    }

    public void setUsername(String username) {
        this.Username = username;
    }

    public String getUsername() {
        return Username;
    }

    public Map<String, Position> getStockHoldings() {
        return new HashMap<>(stockHoldings); // Return copy to prevent external modification
    }

    public Map<String, Integer> getReservedStock() {
        return new HashMap<>(reservedStock);
    }

    // Get available quantity for a stock (total - reserved)
    public int getAvailableStock(String symbol) {
        if (!stockHoldings.containsKey(symbol)) {
            return 0;
        }
        Position position = stockHoldings.get(symbol);
        if (!position.getStatus().equals("long")) {
            return 0;
        }
        return position.getQuantity() - reservedStock.getOrDefault(symbol, 0);
    }

    // Helper method to get current portfolio value
    public double getPortfolioValue(Map<String, Double> currentPrices) {
        double totalValue = USD + reservedCash; // Include all cash

        for (Map.Entry<String, Position> entry : stockHoldings.entrySet()) {
            String symbol = entry.getKey();
            Position position = entry.getValue();

            if (currentPrices.containsKey(symbol)) {
                double currentPrice = currentPrices.get(symbol);

                if (position.getStatus().equals("long")) {
                    totalValue += position.getQuantity() * currentPrice;
                } else if (position.getStatus().equals("short")) {
                    // For short positions, value decreases as price increases
                    totalValue += position.getQuantity() * (position.getPrice() - currentPrice);
                }
            }
        }

        return totalValue;
    }

    // Calculate unrealized P&L for a specific position
    public double getUnrealizedPnL(String symbol, double currentPrice) {
        if (!stockHoldings.containsKey(symbol)) {
            return 0.0;
        }

        Position position = stockHoldings.get(symbol);
        if (position.getStatus().equals("long")) {
            return position.getQuantity() * (currentPrice - position.getPrice());
        } else if (position.getStatus().equals("short")) {
            return position.getQuantity() * (position.getPrice() - currentPrice);
        }

        return 0.0;
    }
}