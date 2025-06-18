package Engine;
import Banking.*;

public class Order {
    public enum Side { LIMIT, MARKET , STOP}
    public enum Type {BUY, SELL };

    private static int nextId = 1;
    private int id;
    private String stockSymbol;
    private Type type;
    private Side side;
    private int quantity;
    private double price;
    private long timestamp;
    private Portfolio user_portfolio;

    public Order(String stockSymbol, Type type, Side side, int quantity, double price, Portfolio user_portfolio) {
        this.id = nextId++;
        this.stockSymbol = stockSymbol;
        this.type = type;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.timestamp = System.currentTimeMillis();
        this.user_portfolio = user_portfolio;
    }

    // Getters
    public int getId() { return id; }
    public String getStockSymbol() { return stockSymbol; }
    public Type getType() { return type; }
    public Side getSide() { return side; }
    public int getQuantity() { return quantity; }
    public double getPrice() { return price; }
    public long getTimestamp() { return timestamp; }
    public Portfolio getUser_portfolio(){ return user_portfolio; }

    public void reduceQuantity(int amount) {
        this.quantity -= amount;
    }
}